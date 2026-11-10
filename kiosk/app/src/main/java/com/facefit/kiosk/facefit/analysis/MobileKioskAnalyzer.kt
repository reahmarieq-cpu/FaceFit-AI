package com.facefit.kiosk.facefit.analysis

import android.content.Context
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.facefit.features.FacialFeatureExtractor
import com.facefit.kiosk.facefit.features.FacialFeatureExtractionResult
import com.facefit.kiosk.facefit.mobilescan.FaceScanSamples
import com.facefit.kiosk.facefit.mobilescan.FaceShapeOnnxModel
import com.facefit.kiosk.model.*

/** Same multi-frame model and decision policy as mobile; never falls back to a shape heuristic. */
class MobileKioskAnalyzer(context: Context, private val session: KioskSessionStore) : KioskAnalyzer {
    private val appContext = context.applicationContext
    override fun analyze(capturedImagePath: String): KioskAnalysisOutcome {
        val batch = session.capturedScanMetrics ?: return KioskAnalysisOutcome.Failure
        if (capturedImagePath != session.capturedFaceImagePath ||
            batch.shapeSampleCount < FaceScanSamples.MIN_SAMPLES ||
            batch.shapeFeatureSamples.size < FaceScanSamples.MIN_SAMPLES) return KioskAnalysisOutcome.Failure
        val assessment = try {
            FaceShapeOnnxModel.open(appContext).use { it.assess(batch.shapeFeatureSamples, batch.stabilityFeatures) }
        } catch (error: LinkageError) {
            android.util.Log.e("MobileKioskAnalyzer", "Face model runtime unavailable", error)
            return KioskAnalysisOutcome.Failure
        }
        val shape = FaceShape.fromLabel(assessment.label)
        if (shape == FaceShape.Undetermined) return KioskAnalysisOutcome.Failure
        val scan = FaceLandmarkScan(batch.landmarks.mapIndexed { i, p -> FaceLandmarkPoint(i, p.x, p.y, p.z) },
            batch.imageWidth, batch.imageHeight, capturedImagePath)
        val extracted = FacialFeatureExtractor().extract(scan)
        val raw = (extracted as? FacialFeatureExtractionResult.Success)?.metrics
        val ratios = raw?.copy(
            faceShape = shape,
            faceAspectRatio = batch.shapeMeasurements?.lengthToWidth?.toDouble()
                ?: raw.faceAspectRatio,
            foreheadToFaceWidthRatio = batch.foreheadWidth.toDouble(),
            jawToFaceWidthRatio = batch.jawline.toDouble(),
            eyeSpacingToFaceWidthRatio = batch.eyeSpacing.toDouble())
        return KioskAnalysisOutcome.Success(FaceScanResult(
            faceShape = shape, metrics = ratios, isEstimate = true,
            modelId = FaceShapeOnnxModel.MODEL_ID, modelScores = assessment.scores,
            modelDecisionPolicy = FaceShapeOnnxModel.POLICY_ID, modelAgreement = assessment.agreement
        ), scan)
    }
}
