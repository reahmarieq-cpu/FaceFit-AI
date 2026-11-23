package com.example.facefit.facefit.tryon.geometry

import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.abs

data class TryOnPoint(val x: Float, val y: Float, val z: Float = 0f)

data class TryOnPose(
    val left: TryOnPoint,
    val right: TryOnPoint,
    val eyeDistance: Float,
    val depthAwareDistance: Float,
    val downX: Float,
    val downY: Float,
    val yaw: Float,
    val templeVisibility: Float
)

object TryOnGeometry {
    internal const val FIRST_OUTER_EYE = 33
    internal const val SECOND_OUTER_EYE = 263
    internal const val FIRST_INNER_EYE = 133
    internal const val SECOND_INNER_EYE = 362
    internal const val NASAL_BRIDGE = 168

    internal fun ipd(point: (Int) -> Vector3): Float? {
        val delta = point(SECOND_OUTER_EYE) - point(FIRST_OUTER_EYE)
        return hypot(hypot(delta.x, delta.y), delta.z)
            .takeIf { it.isFinite() && it > 0f }
    }

    internal fun pose(rig: GlassesRig): TryOnPose? {
        val across = rig.xAxis * rig.size
        val span = hypot(across.x, across.y)
        if (!span.isFinite() || span <= 0f) return null
        fun point(v: Vector3) = TryOnPoint(v.x, v.y, v.z)
        val yaw = rig.xAxis.z
        return TryOnPose(
            point(rig.origin - across * .5f), point(rig.origin + across * .5f),
            span, rig.size, rig.yAxis.x, rig.yAxis.y, yaw, templeVisibility(yaw)
        )
    }

    fun pose(first: TryOnPoint, second: TryOnPoint, scale: Float = 1f, heightOffset: Float = 0f): TryOnPose? {
        if (listOf(first.x, first.y, first.z, second.x, second.y, second.z, scale, heightOffset).any { !it.isFinite() } || scale <= 0f) return null
        val (left, right) = if (first.x <= second.x) first to second else second to first
        val dx = right.x - left.x
        val dy = right.y - left.y
        val distance = hypot(dx, dy)
        if (distance <= 0f || !distance.isFinite()) return null
        val dz = right.z - left.z
        // Depth uses the same pixel scale as x after the CameraX preview mapping.
        val spatialDistance = sqrt(distance * distance + dz * dz)
        val yaw = (dz / spatialDistance).coerceIn(-0.95f, 0.95f)
        val downX = -dy / distance
        val downY = dx / distance
        val centerX = (left.x + right.x) / 2 + downX * spatialDistance * heightOffset
        val centerY = (left.y + right.y) / 2 + downY * spatialDistance * heightOffset
        return TryOnPose(
            TryOnPoint(centerX - dx * scale / 2, centerY - dy * scale / 2, left.z),
            TryOnPoint(centerX + dx * scale / 2, centerY + dy * scale / 2, right.z),
            distance * scale, spatialDistance * scale, downX, downY, yaw,
            templeVisibility(yaw)
        )
    }

    private fun templeVisibility(yaw: Float): Float = ((abs(yaw) - 0.06f) / 0.18f).coerceIn(0f, 1f)
}
