package com.facefit.kiosk.vto.facefit.tryon.tracking

import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import kotlin.math.abs

/** Holds small changes at rest and follows larger changes in the stabilized rig. */
internal class VtoPoseSmoother {
    private var displayed: GlassesRig? = null

    fun update(measured: GlassesRig?) {
        val current = displayed
        if (measured == null || current == null) {
            displayed = measured
            return
        }

        val displacement = measured.origin - current.origin
        val positionDelta = displacement.length()
        val rotationDelta = abs(1f - measured.xAxis.dot(current.xAxis)) +
            abs(1f - measured.yAxis.dot(current.yAxis))

        if (positionDelta < 1.2f && rotationDelta < .002f) return

        val positionProgress = ((positionDelta - 1.2f) / 8.8f).coerceIn(0f, 1f)
        val rotationProgress = ((rotationDelta - .002f) / .018f).coerceIn(0f, 1f)
        val progress = maxOf(positionProgress, rotationProgress)
        val eased = progress * progress * (3f - 2f * progress)
        val factor = .15f + .85f * eased

        // Damp width even on a fast turn so the fitting scale does not jump with yaw.
        val width = current.faceWidth + (measured.faceWidth - current.faceWidth) * .08f
        if (factor >= .99f) {
            displayed = if (width == measured.faceWidth) measured else measured.copy(faceWidth = width)
            return
        }

        val right = (current.xAxis + (measured.xAxis - current.xAxis) * factor).normalized()
        val downHint = current.yAxis + (measured.yAxis - current.yAxis) * factor
        val behind = right.cross(downHint).normalized()
        val down = behind.cross(right).normalized()
        displayed = measured.copy(
            origin = current.origin + displacement * factor,
            xAxis = right,
            yAxis = down,
            zAxis = behind,
            size = current.size + (measured.size - current.size) * (factor * .5f),
            faceWidth = width
        )
    }

    fun latest(): GlassesRig? = displayed
}
