package com.facefit.kiosk.facefit.analysis

import android.content.Context
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.facefit.features.FacialFeatureExtractor
import com.facefit.kiosk.facefit.features.FacialFeatureThresholds
import com.facefit.kiosk.facefit.features.FacialFeatureExtractionResult
import com.facefit.kiosk.facefit.mobilescan.FaceScanSamples
import com.facefit.kiosk.facefit.mobilescan.FaceShapeDecision
import com.facefit.kiosk.facefit.mobilescan.FaceShapeImageModel
import com.facefit.kiosk.facefit.mobilescan.FaceShapePrediction
import com.facefit.kiosk.facefit.recommendation.FacialExplanationRules
import com.facefit.kiosk.model.*
import com.facefit.kiosk.ui.scan.mobile.FaceAnalyzer as MobileCaptureAnalyzer

/** Builds kiosk scan results and explanations from the mobile classifier. */
class MobileKioskAnalyzer(context: Context, private val session: KioskSessionStore) : KioskAnalyzer {
    private val appContext = context.applicationContext
    private val thresholds by lazy { FacialFeatureThresholds.load(appContext) }
    private val explanationRules by lazy { FacialExplanationRules.load(appContext) }
    override fun analyze(capturedImagePath: String): KioskAnalysisOutcome {
        val batch = session.capturedScanMetrics ?: return KioskAnalysisOutcome.Failure
        if (capturedImagePath != session.capturedFaceImagePath ||
            batch.shapeSampleCount < FaceScanSamples.MIN_SAMPLES) return KioskAnalysisOutcome.Failure
        val rgb = batch.shapeImageRgb ?: return KioskAnalysisOutcome.Failure
        val assessment = try {
            val raw = FaceShapeImageModel.open(appContext).use { it.predict(rgb) }
            FaceShapeDecision.calibrate(raw, batch.shapeMeasurements)
        } catch (error: Exception) {
            android.util.Log.e("MobileKioskAnalyzer", "Face model analysis failed", error)
            return KioskAnalysisOutcome.Failure
        } catch (error: LinkageError) {
            android.util.Log.e("MobileKioskAnalyzer", "Face model runtime unavailable", error)
            return KioskAnalysisOutcome.Failure
        }
        val scan = FaceLandmarkScan(batch.landmarks.mapIndexed { i, p -> FaceLandmarkPoint(i, p.x, p.y, p.z) },
            batch.imageWidth, batch.imageHeight, capturedImagePath)
        return buildProfile(
            scan, assessment, batch.shapeMeasurements?.lengthToWidth, batch.foreheadWidth,
            batch.jawline, batch.eyeSpacing, "geometricAspectCalibratedV1"
        )
    }

    internal fun fromMobileCapture(capture: MobileCaptureAnalyzer.FaceMetrics): KioskAnalysisOutcome {
        if (capture.shapeModelId != FaceShapeImageModel.MODEL_ID || capture.landmarks.isEmpty()) {
            return KioskAnalysisOutcome.Failure
        }
        val scan = FaceLandmarkScan(
            capture.landmarks.mapIndexed { index, point -> FaceLandmarkPoint(index, point.x, point.y, point.z) },
            capture.imageWidth,
            capture.imageHeight,
            sourceImagePath = null
        )
        return buildProfile(
            scan,
            FaceShapePrediction(capture.faceShape, capture.shapeModelScores),
            capture.shapeMeasurements?.lengthToWidth,
            capture.foreheadWidth,
            capture.jawline,
            capture.eyeSpacing,
            capture.shapeDecisionPolicy
        )
    }

    private fun buildProfile(
        scan: FaceLandmarkScan,
        assessment: FaceShapePrediction,
        aspect: Float?,
        forehead: Float,
        jawline: Float,
        eyeSpacing: Float,
        decisionPolicy: String?
    ): KioskAnalysisOutcome {
        val shape = FaceShape.fromLabel(assessment.label)
        if (shape == FaceShape.Undetermined) return KioskAnalysisOutcome.Failure
        val extracted = FacialFeatureExtractor().extract(scan)
        val raw = (extracted as? FacialFeatureExtractionResult.Success)?.metrics
        val ratios = raw?.copy(
            faceShape = shape,
            faceAspectRatio = aspect?.toDouble() ?: raw.faceAspectRatio,
            foreheadToFaceWidthRatio = forehead.toDouble(),
            jawToFaceWidthRatio = jawline.toDouble(),
            eyeSpacingToFaceWidthRatio = eyeSpacing.toDouble())
        val bands = thresholds
        val profileCopy = if (ratios == null || bands == null) null else explanationRules?.find(
            shape.name,
            bands.jawline.classify(jawline),
            bands.upperFace.classify(forehead),
            bands.eyeSpacing.classify(eyeSpacing, eyeSpacing = true)
        )
        return KioskAnalysisOutcome.Success(FaceScanResult(
            faceShape = shape, metrics = ratios, isEstimate = true,
            notes = listOfNotNull(profileCopy?.noticed),
            profileHeadline = profileCopy?.headline,
            suggestedFrameStyle = profileCopy?.suggestedStyle,
            profileStyleExplanation = profileCopy?.styles,
            modelId = FaceShapeImageModel.MODEL_ID, modelScores = assessment.scores,
            modelDecisionPolicy = decisionPolicy
        ), scan)
    }
}
