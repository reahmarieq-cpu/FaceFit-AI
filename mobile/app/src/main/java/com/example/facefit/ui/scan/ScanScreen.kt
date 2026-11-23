package com.example.facefit.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.analysis.FaceShapeDecision
import com.example.facefit.facefit.analysis.FaceShapeImageModel
import com.example.facefit.facefit.analysis.getScanReadiness
import com.example.facefit.facefit.analysis.ScanDiagnostics
import com.example.facefit.ui.camera.CameraPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onComplete: (FaceAnalyzer.FaceMetrics) -> Unit
) {
    var capturedMetrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var retakeMessage by remember { mutableStateOf<String?>(null) }
    var distanceHint by remember { mutableStateOf<com.example.facefit.facefit.analysis.ScanDistanceGuide.Hint?>(null) }
    var cameraAttempt by remember { mutableStateOf(0) }
    val captureRequested = remember(cameraAttempt) { AtomicBoolean(false) }

    fun restartCamera() {
        captureRequested.set(false)
        capturing = false
        cameraReady = false
        cameraError = null
        retakeMessage = null
        cameraAttempt++
    }

    LaunchedEffect(cameraAttempt, cameraReady, capturedMetrics) {
        if (!cameraReady && capturedMetrics == null && cameraError == null) {
            delay(6000)
            cameraError = "No camera frames yet. Tap refresh to retry."
        }
    }
    LaunchedEffect(cameraAttempt, capturing) {
        if (capturing) {
            // Feature measurement may analyse up to 70 frames before finishing.
            delay(10000)
            android.util.Log.i("FACEFIT_FEATURE_JSON", org.json.JSONObject()
                .put("type", "rejected_capture").put("scan_id", "capture_${System.currentTimeMillis()}")
                .put("stage", "timeout").put("guidance_shown", "Capture timed out. Tap refresh to retry.")
                .toString())
            captureRequested.set(false)
            cameraReady = false
            capturing = false
            cameraError = "Capture timed out. Tap refresh to retry."
        }
    }

    AnimatedContent(
        targetState = capturedMetrics,
        label = "ScanModeTransition"
    ) { analyzingMetrics ->
        if (analyzingMetrics == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                val attempt = cameraAttempt
                key(attempt) {
                    CameraPreview(
                        captureRequested = captureRequested,
                        onDistanceHint = { hint ->
                            if (attempt == cameraAttempt && capturedMetrics == null) distanceHint = hint
                        },
                        onReady = {
                            if (attempt == cameraAttempt && capturedMetrics == null) {
                                cameraReady = true
                                cameraError = null
                            }
                        },
                        onError = { error ->
                            if (attempt == cameraAttempt && capturedMetrics == null) {
                                captureRequested.set(false)
                                capturing = false
                                cameraReady = false
                                cameraError = error
                            }
                        }
                    ) { result, insideGuide ->
                        if (attempt == cameraAttempt && capturing && capturedMetrics == null) {
                            val validation = result.getScanReadiness()
                            capturing = false
                            when {
                                result.captureGuidance != null -> retakeMessage = result.captureGuidance
                                !validation.canCapture -> retakeMessage = validation.message
                                insideGuide == null -> retakeMessage = "Camera preview is still loading. Try again."
                                !insideGuide -> retakeMessage = "Keep your full face inside the dashed outline."
                                result.shapeImageRgb == null ->
                                    retakeMessage = "Could not measure your face. Face the camera and try again."
                                else -> capturedMetrics = result
                            }
                        }
                    }
                }

                FaceScanOverlay(
                    statusText = cameraError ?: when {
                        capturing -> "Photo taken · analyzing…"
                        retakeMessage != null -> retakeMessage.orEmpty()
                        cameraReady -> "Center your face and tap Capture"
                        else -> "Starting front camera…"
                    },
                    onBack = onBack,
                    onRefresh = ::restartCamera,
                    distanceHint = distanceHint.takeIf { cameraReady && !capturing && cameraError == null },
                    captureEnabled = cameraReady && !capturing && capturedMetrics == null,
                    capturing = capturing,
                    isRetake = retakeMessage != null,
                    onCapture = {
                        val hint = distanceHint
                        logCaptureTap(hint, refused = hint != null && !hint.inRange)
                        if (cameraReady && !capturing && capturedMetrics == null && hint != null && !hint.inRange) {
                            // Wrong distance or smiling: refuse before starting a capture.
                            retakeMessage = hint.refusal
                        } else if (cameraReady && !capturing && capturedMetrics == null) {
                            retakeMessage = null
                            capturing = true
                            captureRequested.set(true)
                        }
                    }
                )
            }
        } else {
            AnalyzeCapturedFace(
                metrics = analyzingMetrics,
                onComplete = onComplete,
                onBack = onBack,
                onRetry = {
                    restartCamera()
                    capturedMetrics = null
                }
            )
        }
    }
}

@Composable
private fun AnalyzeCapturedFace(
    metrics: FaceAnalyzer.FaceMetrics,
    onComplete: (FaceAnalyzer.FaceMetrics) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var analyzed by remember(metrics) { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var failed by remember(metrics) { mutableStateOf(false) }
    var animationFinished by remember(metrics) { mutableStateOf(false) }
    LaunchedEffect(metrics) {
        try {
            val rawPrediction = withContext(Dispatchers.Default) {
                FaceShapeImageModel.open(context).use {
                    it.predict(requireNotNull(metrics.shapeImageRgb))
                }
            }
            val prediction = FaceShapeDecision.calibrate(rawPrediction, metrics.shapeMeasurements)
            val result = metrics.copy(
                faceShape = prediction.label,
                shapeModelId = FaceShapeImageModel.MODEL_ID,
                shapeModelScores = prediction.scores,
                shapeDecisionPolicy = "geometricAspectCalibratedV1",
            )
            withContext(Dispatchers.IO) {
                ScanDiagnostics.saveLatest(context, result)
            }
            analyzed = result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("FaceShapeModel", "Face analysis failed", error)
            failed = true
        } catch (error: LinkageError) {
            android.util.Log.e("FaceShapeModel", "Native runtime unavailable", error)
            failed = true
        }
    }
    LaunchedEffect(animationFinished, analyzed) {
        if (animationFinished) analyzed?.let { onComplete(it) }
    }
    if (failed) {
        Box(Modifier.fillMaxSize().background(Color(0xFF063D46)).padding(32.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Face analysis could not finish", color = Color.White, fontSize = 20.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text("Scan again") }
                OutlinedButton(onClick = onBack) { Text("Back", color = Color.White) }
            }
        }
    } else {
        AnalysisProgressScreen(onComplete = { animationFinished = true })
    }
}

@Composable
private fun AnalysisProgressScreen(
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Short on purpose: the face-shape result is usually ready before this finishes, and the
        // screen still waits for it if not.
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700)
        )
        delay(100)
        onComplete()
    }

    val percent = (progress.value * 100).toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF063D46),
                        Color(0xFF06787D),
                        Color(0xFF0A8B8B)
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0x33000000),
                        radius = size.minDimension / 2f
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.88f),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$percent%",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "ANALYZING",
                        color = Color.White.copy(alpha = 0.74f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Analyzing facial features...",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Measuring facial proportions and\nestimating your face shape",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(34.dp))

            AnalysisStep("Facial landmark detection")
            Spacer(modifier = Modifier.height(14.dp))
            AnalysisStep("Feature geometry analysis")
            Spacer(modifier = Modifier.height(14.dp))
            AnalysisStep("Face shape prediction")
        }
    }
}

@Composable
private fun AnalysisStep(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(14.dp)) {
            drawCircle(Color.White.copy(alpha = 0.28f), radius = size.minDimension / 2f)
            drawCircle(Color.White, radius = size.minDimension * 0.24f)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** One line per Capture tap: live distance and smile score, and whether the tap was refused. */
private fun logCaptureTap(hint: com.example.facefit.facefit.analysis.ScanDistanceGuide.Hint?, refused: Boolean) {
    runCatching {
        android.util.Log.i("FACEFIT_FEATURE_JSON", org.json.JSONObject()
            .put("type", "capture_tap")
            .put("scan_id", "tap_${System.currentTimeMillis()}")
            .put("refused", refused)
            .put("distance_cm", hint?.distanceCm)
            .put("smile_score", hint?.smileScore?.takeIf { it.isFinite() }?.toDouble())
            .put("upper_lip_raise", hint?.upperLipRaise?.takeIf { it.isFinite() }?.toDouble())
            .put("pitch_deg", hint?.pitchDeg?.takeIf { it.isFinite() }?.toDouble())
            .put("yaw_deg", hint?.yawDeg?.takeIf { it.isFinite() }?.toDouble())
            .put("smiling", hint?.smiling)
            .put("guidance_shown", if (refused) hint?.refusal else null)
            .toString())
    }
}
