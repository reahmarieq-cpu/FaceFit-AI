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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.analysis.FaceScanSamples
import com.example.facefit.facefit.analysis.FaceShapeOnnxModel
import com.example.facefit.facefit.analysis.getScanReadiness
import com.example.facefit.facefit.analysis.ScanDiagnostics
import com.example.facefit.BuildConfig
import com.example.facefit.ui.camera.CameraPreview
import com.example.facefit.ui.scan.FaceScanOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onComplete: (FaceAnalyzer.FaceMetrics) -> Unit
) {
    var metrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var capturedMetrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var captureProgress by remember { mutableStateOf(0f) }
    var waitingForStability by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraAttempt by remember { mutableStateOf(0) }
    var waitingForFrames by remember { mutableStateOf(false) }
    val scanSamples = remember { FaceScanSamples() }

    val scanReadiness = remember(metrics) { metrics.getScanReadiness() }
    LaunchedEffect(cameraAttempt, capturedMetrics) {
        waitingForFrames = false
        if (capturedMetrics == null) {
            delay(6000)
            waitingForFrames = metrics == null
        }
    }

    AnimatedContent(
        targetState = capturedMetrics,
        label = "ScanModeTransition"
    ) { analyzingMetrics ->
        if (analyzingMetrics == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                key(cameraAttempt) {
                    CameraPreview(onError = { error ->
                        if (capturedMetrics == null) {
                            cameraError = error
                            metrics = null
                            scanSamples.reset()
                            captureProgress = 0f
                            waitingForStability = false
                        }
                    }) { result ->
                        if (capturedMetrics == null) {
                            cameraError = null
                            metrics = result
                            val update = scanSamples.add(
                                result, result.getScanReadiness().canCapture,
                                android.os.SystemClock.elapsedRealtime()
                            )
                            captureProgress = update.progress
                            waitingForStability = update.waitingForStability
                            update.captured?.let {
                                capturedMetrics = it
                                scanSamples.reset()
                            }
                        }
                    }
                }

                FaceScanOverlay(
                    isFaceDetected = metrics?.isFaceDetected == true,
                    statusText = cameraError ?: when {
                        metrics == null && waitingForFrames -> "No camera frames yet. Tap refresh to retry."
                        metrics == null -> "Starting front camera…"
                        waitingForStability && scanReadiness.canCapture -> "Hold still for a clearer scan"
                        else -> scanReadiness.message
                    },
                    onBack = onBack,
                    onRefresh = {
                        scanSamples.reset()
                        captureProgress = 0f
                        waitingForStability = false
                        metrics = null
                        cameraError = null
                        cameraAttempt++
                    },
                    captureProgress = captureProgress
                )
            }
        } else {
            AnalyzeCapturedFace(
                metrics = analyzingMetrics,
                onComplete = onComplete,
                onBack = onBack,
                onRetry = {
                    scanSamples.reset()
                    captureProgress = 0f
                    waitingForStability = false
                    metrics = null
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
            val assessment = withContext(Dispatchers.Default) {
                FaceShapeOnnxModel.open(context).use {
                    it.assess(metrics.shapeFeatureSamples, metrics.stabilityFeatures)
                }
            }
            val result = metrics.copy(
                faceShape = assessment.label,
                shapeModelId = FaceShapeOnnxModel.MODEL_ID,
                shapeModelScores = assessment.scores,
                shapeDecisionReason = assessment.reason,
                shapeAgreement = assessment.agreement,
                shapeDecisionPolicy = FaceShapeOnnxModel.POLICY_ID,
                shapeFeatureSamples = if (BuildConfig.DEBUG) metrics.shapeFeatureSamples else emptyList()
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
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200)
        )
        delay(250)
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
