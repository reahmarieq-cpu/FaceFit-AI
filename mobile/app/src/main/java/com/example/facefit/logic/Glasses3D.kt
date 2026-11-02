package com.example.facefit.logic

import kotlin.math.*

data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = Vector3(x * value, y * value, z * value)
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
    fun length() = sqrt(dot(this))
    fun normalized(): Vector3 = this * (1f / length().coerceAtLeast(0.00001f))
    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
}

data class GlassesRig(val origin: Vector3, val xAxis: Vector3, val yAxis: Vector3, val zAxis: Vector3, val size: Float) {
    fun transform(point: Vector3) = origin + (xAxis * point.x + yAxis * point.y + zAxis * point.z) * size
    fun matrix() = floatArrayOf(
        xAxis.x * size, xAxis.y * size, xAxis.z * size, 0f,
        yAxis.x * size, yAxis.y * size, yAxis.z * size, 0f,
        zAxis.x * size, zAxis.y * size, zAxis.z * size, 0f,
        origin.x, origin.y, origin.z, 1f
    )

    companion object {
        fun fromEyes(first: Vector3, second: Vector3, chin: Vector3, scale: Float, heightOffset: Float,
            forehead: Vector3? = null, noseBridge: Vector3? = null): GlassesRig? {
            if (listOfNotNull(first, second, chin, forehead, noseBridge).any { !it.isFinite() } || !scale.isFinite() || scale <= 0 || !heightOffset.isFinite()) return null
            val (left, right) = if (first.x < second.x) first to second else second to first
            val eyeVector = right - left
            val distance = eyeVector.length()
            if (distance < 10) return null
            val x = eyeVector.normalized()
            val center = (left + right) * 0.5f
            val down = chin - (forehead ?: center)
            val yVector = down - x * down.dot(x)
            if (yVector.length() < 10) return null
            val y = yVector.normalized()
            var z = x.cross(y).normalized()
            // Front-camera mirroring reverses handedness; positive z must still point behind the head.
            if (z.z < 0) z = z * -1f
            val depthOffset = noseBridge?.let { (it - center).dot(z) - distance * 0.08f }
                ?: (-distance * 0.16f)
            val origin = center + z * depthOffset + y * (distance * heightOffset)
            return GlassesRig(origin, x, y, z, distance * scale)
        }
    }
}

object GlassesMesh {
    data class Parts(val front: FloatArray, val temples: FloatArray, val lenses: FloatArray)

    fun create(browline: Boolean): FloatArray = parts(browline).let { it.front + it.temples }

    // Interleaved position, surface normal and RGB. Temples extend along local +z, towards the ears.
    fun parts(browline: Boolean): Parts {
        val frontVertices = ArrayList<Float>()
        val templeVertices = ArrayList<Float>()
        val lensVertices = ArrayList<Float>()
        var vertices = frontVertices
        val black = Vector3(0.035f, 0.038f, 0.042f)
        val metal = Vector3(0.32f, 0.34f, 0.36f)
        fun tube(path: List<Vector3>, radius: (Int) -> Float, color: (Int) -> Vector3,
            closed: Boolean = false, depthScale: Float = 1f) {
            val count = path.size
            val rings = path.indices.map { i ->
                val previous = path[if (i > 0) i - 1 else if (closed) count - 1 else 0]
                val next = path[if (i < count - 1) i + 1 else if (closed) 0 else count - 1]
                val tangent = (next - previous).normalized()
                val reference = if (abs(tangent.y) < 0.9f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
                val a = tangent.cross(reference).normalized()
                val b = tangent.cross(a).normalized()
                List(8) { side ->
                    val angle = side * 2f * PI.toFloat() / 8
                    val normal = (a * (cos(angle) / depthScale) + b * sin(angle)).normalized()
                    (path[i] + (a * (cos(angle) * depthScale) + b * sin(angle)) * radius(i)) to normal
                }
            }
            fun vertex(ring: Int, side: Int) {
                val (p, n) = rings[ring][side % 8]
                val c = color(ring)
                vertices.addAll(listOf(p.x, p.y, p.z, n.x, n.y, n.z, c.x, c.y, c.z))
            }
            for (i in 0 until if (closed) count else count - 1) {
                val next = (i + 1) % count
                for (side in 0 until 8) {
                    vertex(i, side); vertex(next, side); vertex(next, side + 1)
                    vertex(i, side); vertex(next, side + 1); vertex(i, side + 1)
                }
            }
        }
        for (side in listOf(-1f, 1f)) {
            val contour = List(48) { i ->
                val angle = i * 2f * PI.toFloat() / 48
                val cx = cos(angle)
                val sy = sin(angle)
                val exponent = if (browline && sy > 0f) 0.85f else 0.55f
                Vector3(side * 0.5f + sign(cx) * abs(cx).pow(exponent) * 0.43f,
                    0.025f + sign(sy) * abs(sy).pow(exponent) *
                        (if (browline && sy > 0f) 0.29f else 0.245f), 0.015f * abs(cx))
            }
            tube(contour,
                radius = { i -> if (browline) {
                    val upper = ((0.06f - contour[i].y) / 0.12f).coerceIn(0f, 1f)
                    0.010f + 0.017f * upper
                } else 0.010f },
                color = { i -> if (browline && contour[i].y < 0.02f) black else metal },
                closed = true, depthScale = if (browline) 1.3f else 0.9f)
            // Tiny clear lens fans add a subtle reflection without obscuring the camera image.
            val center = Vector3(side * 0.5f, 0.025f, -0.006f)
            fun lensVertex(p: Vector3) {
                lensVertices.addAll(listOf(p.x, p.y, p.z, 0f, 0f, -1f, 0.86f, 0.94f, 0.98f))
            }
            for (i in contour.indices) {
                lensVertex(center)
                lensVertex(contour[i])
                lensVertex(contour[(i + 1) % contour.size])
            }
            tube(listOf(Vector3(side * 0.915f, -0.07f, 0.015f),
                Vector3(side * 1.01f, -0.07f, 0.04f)), { 0.018f }, { metal })
            tube(listOf(Vector3(side * 0.095f, 0.02f, 0.025f),
                Vector3(side * 0.13f, 0.10f, 0.065f)), { 0.009f }, { metal })
            vertices = templeVertices
            val arm = List(24) { i ->
                // Stop before the ear hook: try-on shows only the straight temple section.
                val t = i / 23f * 0.78f
                Vector3(side * (1.01f + 0.16f * sin(t * PI.toFloat() / 2)), -0.07f, 0.04f + 1.48f * t)
            }
            tube(arm, { 0.014f }, { if (browline) black else metal }, depthScale = 0.75f)
            vertices = frontVertices
        }
        val bridge = List(16) { i ->
            val t = i / 15f
            Vector3(-0.10f + 0.20f * t, -0.06f - sin(t * PI.toFloat()) * 0.045f, -0.015f)
        }
        tube(bridge, { 0.012f }, { metal })
        return Parts(frontVertices.toFloatArray(), templeVertices.toFloatArray(), lensVertices.toFloatArray())
    }
}
