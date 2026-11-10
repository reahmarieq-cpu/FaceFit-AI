package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer

import kotlin.math.exp
import kotlin.math.hypot

/** VTO-only filtering: react to rotation as well as translation, without queuing old poses. */
internal class VtoPoseSmoother {
    private var source: FaceAnalyzer.FaceMetrics? = null
    private var displayed: FaceAnalyzer.FaceMetrics? = null
    private var sourceTime = 0L
    private var frameTime = 0L
    private var velocity = FloatArray(0)
    private var motionSpeed = 0f

    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): FaceAnalyzer.FaceMetrics? {
        if (face?.isFaceDetected != true || face.landmarks.size < 468) {
            source = null; displayed = null; velocity.fill(0f); motionSpeed = 0f
            frameTime = now
            return face
        }
        val old = source
        val elapsed = (now - frameTime).coerceAtLeast(0L) / 1_000_000_000f
        frameTime = now
        if (old == null || displayed == null || elapsed > .25f || old.landmarks.size != face.landmarks.size) {
            return reset(face, now)
        }
        if (face !== old) {
            val seconds = ((now - sourceTime) / 1_000_000_000f).coerceIn(.008f, .25f)
            val dx = centerX(face) - centerX(old)
            val dy = centerY(face) - centerY(old)
            if (hypot(dx, dy) > .18f) return reset(face, now)
            motionSpeed = 0f
            for (i in face.landmarks.indices) {
                val a = old.landmarks[i]
                val b = face.landmarks[i]
                velocity[i * 3] = (b.x - a.x) / seconds
                velocity[i * 3 + 1] = (b.y - a.y) / seconds
                velocity[i * 3 + 2] = (b.z - a.z) / seconds
            }
            // Eyes, bridge, forehead and chin reveal roll/yaw/pitch even at a fixed center.
            for (i in intArrayOf(33, 133, 263, 362, 168, 10, 152)) {
                motionSpeed = maxOf(motionSpeed,
                    hypot(velocity[i * 3], velocity[i * 3 + 1]), kotlin.math.abs(velocity[i * 3 + 2]))
            }
            source = face; sourceTime = now
        }
        val age = (now - sourceTime).coerceAtLeast(0L) / 1_000_000_000f
        val lead = minOf(age, .020f) * exp(-maxOf(age - .04f, 0f) * 40f)
        val tau = if (motionSpeed > .12f) .003f else .020f
        val alpha = (1f - exp(-elapsed / tau)).coerceIn(0f, 1f)
        val previous = requireNotNull(displayed)
        return face.copy(landmarks = face.landmarks.mapIndexed { i, target ->
            val point = previous.landmarks[i]
            val px = (velocity[i * 3] * lead).coerceIn(-.020f, .020f)
            val py = (velocity[i * 3 + 1] * lead).coerceIn(-.020f, .020f)
            val pz = (velocity[i * 3 + 2] * lead).coerceIn(-.015f, .015f)
            FaceAnalyzer.LandmarkPoint(
                point.x + (target.x + px - point.x) * alpha,
                point.y + (target.y + py - point.y) * alpha,
                point.z + (target.z + pz - point.z) * alpha)
        }).also { displayed = it }
    }
    private fun reset(face: FaceAnalyzer.FaceMetrics, now: Long): FaceAnalyzer.FaceMetrics {
        source = face; displayed = face; sourceTime = now
        velocity = FloatArray(face.landmarks.size * 3); motionSpeed = 0f
        return face
    }
    private fun centerX(face: FaceAnalyzer.FaceMetrics) = (face.landmarks[33].x + face.landmarks[263].x) * .5f
    private fun centerY(face: FaceAnalyzer.FaceMetrics) = (face.landmarks[33].y + face.landmarks[263].y) * .5f
}