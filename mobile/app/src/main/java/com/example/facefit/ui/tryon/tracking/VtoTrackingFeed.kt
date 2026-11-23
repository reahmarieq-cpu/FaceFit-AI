package com.example.facefit.ui.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.TryOnGeometry
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoOneEuro
import com.example.facefit.facefit.tryon.tracking.landmarkDisplacement
import com.example.facefit.facefit.tryon.tracking.secondsBetween

/** Main-thread sensor handoff and display-time translation of the rigid eye anchor. */
class VtoTrackingFeed {
    private var latest: FaceAnalyzer.FaceMetrics? = null
    private var velocity: Vector3? = null
    private var sensorTimestampNanos: Long? = null
    private var deliveredAt: Long? = null
    internal var deliveryPeriodNanos = 0L
        private set
    private var freshnessWindowNanos: Long? = null
    private val restingNoise = VtoOneEuro()

    fun publish(
        face: FaceAnalyzer.FaceMetrics?,
        sampleNanos: Long? = null,
        deliveryNanos: Long? = null,
    ) {
        val before = sensorTimestampNanos
        val previous = latest
        val previousDelivery = deliveredAt
        val deliveryInterval = if (deliveryNanos != null && previousDelivery != null) {
            deliveryNanos - previousDelivery
        } else 0L
        val measuredLatency = if (sampleNanos != null && deliveryNanos != null) {
            (deliveryNanos - sampleNanos).coerceAtLeast(0L)
        } else 0L
        val recent = deliveryInterval > 0L &&
            (deliveryPeriodNanos == 0L || freshnessWindowNanos?.let { deliveryInterval < it } != false)
        velocity = if (recent && sampleNanos != null && before != null && sampleNanos > before &&
            face?.isFaceDetected == true && previous?.isFaceDetected == true) {
            measureMotion(previous, face, sampleNanos, before)
        } else null
        if (velocity == null) sampleNanos?.let(restingNoise::reset)

        latest = face
        sensorTimestampNanos = sampleNanos
        deliveredAt = deliveryNanos
        deliveryPeriodNanos = if (recent) deliveryInterval else 0L
        // Allow one missing delivery at the observed cadence and account for inference age.
        freshnessWindowNanos = maxOf(measuredLatency, deliveryPeriodNanos)
            .takeIf { it > 0L }?.let { it.coerceAtMost(Long.MAX_VALUE / 2) * 2 }
    }

    internal fun latestAt(frameNanos: Long): FaceAnalyzer.FaceMetrics? =
        if (deliveredAt?.let { delivery -> freshnessWindowNanos?.let { frameNanos - delivery >= it } } == true) null else latest

    internal fun displayPose(
        face: FaceAnalyzer.FaceMetrics?,
        targetVsyncNanos: Long,
    ): FaceAnalyzer.FaceMetrics? =
        if (face != null && face === latest) predictAt(targetVsyncNanos) ?: face else face

    internal fun predictAt(
        targetVsyncNanos: Long,
        holdAnchor: Boolean = false,
        frameNanos: Long = targetVsyncNanos,
    ): FaceAnalyzer.FaceMetrics? {
        val face = (if (holdAnchor) latest else latestAt(frameNanos)) ?: return null
        val offset = translation(face, targetVsyncNanos) ?: return face
        return face.copy(landmarks = face.landmarks.map { it.copy(x = it.x + offset.x, y = it.y + offset.y) })
    }

    /** Screen-normalized offset. The rigid renderer only needs the anchor displacement. */
    internal fun translationAt(face: FaceAnalyzer.FaceMetrics?, targetVsyncNanos: Long): Vector3 =
        if (face != null && face === latest) translation(face, targetVsyncNanos) ?: ZERO else ZERO

    private fun translation(face: FaceAnalyzer.FaceMetrics, targetVsyncNanos: Long): Vector3? {
        val motion = velocity ?: return null
        val sensorTime = sensorTimestampNanos ?: return null
        val leadNanos = (targetVsyncNanos - sensorTime).coerceAtLeast(0L)
        val effectiveLead = minOf(leadNanos, 16_000_000L)
        if (effectiveLead <= 0L || motion.length() <= restingNoise.restingNoiseLimit) return null

        val aspect = face.imageHeight.toFloat() / face.imageWidth
        val ipd = TryOnGeometry.ipd { point(face, it, aspect) } ?: return null
        val offset = motion * (secondsBetween(effectiveLead, 0L).toFloat() * ipd)
        return Vector3(offset.x, offset.y / aspect, 0f)
    }

    private fun measureMotion(
        previous: FaceAnalyzer.FaceMetrics, current: FaceAnalyzer.FaceMetrics, now: Long, before: Long
    ): Vector3? {
        val probes = landmarkDisplacement(current, previous) ?: return null
        restingNoise.observe(probes, now, current.snrFactor)
        val aspect = current.imageHeight.toFloat() / current.imageWidth
        val ipd = TryOnGeometry.ipd { point(current, it, aspect) } ?: return null
        fun midpoint(face: FaceAnalyzer.FaceMetrics) =
            (point(face, TryOnGeometry.FIRST_OUTER_EYE, aspect) +
                point(face, TryOnGeometry.SECOND_OUTER_EYE, aspect)) * .5f
        // Nasal motion during a turn is not translation of the entire rig.
        val midpointDelta = (midpoint(current) - midpoint(previous)) * (1f / ipd)
        val velocity = Vector3(midpointDelta.x, midpointDelta.y, 0f) * (1.0 / secondsBetween(now, before)).toFloat()
        return velocity.takeIf { it.isFinite() }
    }

    private fun point(face: FaceAnalyzer.FaceMetrics, index: Int, aspect: Float) =
        face.landmarks[index].let { Vector3(it.x, it.y * aspect, it.z) }

    private companion object {
        val ZERO = Vector3(0f, 0f, 0f)
    }
}
