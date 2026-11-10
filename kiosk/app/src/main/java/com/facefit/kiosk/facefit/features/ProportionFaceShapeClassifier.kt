package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.FacialFeatureMetrics

/**
 * Prototype visual-category heuristic, not a trained or validated classifier.
 * These initial thresholds need calibration against labeled, front-facing scans.
 * Jaw width is only a proxy for outline shape; it does not measure jaw angularity.
 * Never interpret these categories as measured confidence or physical fit.
 */
object ProportionFaceShapeClassifier : FaceShapeClassifier {
    override fun classify(metricsWithoutShape: FacialFeatureMetrics): FaceShape {
        val aspect = metricsWithoutShape.faceAspectRatio
        val forehead = metricsWithoutShape.foreheadToFaceWidthRatio
        val jaw = metricsWithoutShape.jawToFaceWidthRatio
        if (!aspect.isFinite() || !forehead.isFinite() || !jaw.isFinite() ||
            aspect !in 0.8..2.2 || forehead !in 0.5..1.2 || jaw !in 0.4..1.1
        ) return FaceShape.Undetermined

        return when {
            aspect >= 1.55 -> FaceShape.Oblong
            forehead / jaw >= 1.22 && jaw < 0.82 -> FaceShape.Heart
            aspect <= 1.40 && jaw >= 0.85 -> FaceShape.Square
            aspect <= 1.35 -> FaceShape.Round
            else -> FaceShape.Oval
        }
    }
}