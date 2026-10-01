package com.example.facefit.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.ui.components.CameraPreview
import com.example.facefit.ui.components.FaceScanOverlay
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onComplete: (FaceAnalyzer.FaceMetrics) -> Unit
) {
    var metrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var capturedMetrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }

    val scanReadiness = remember(metrics) { metrics.getScanReadiness() }

    AnimatedContent(
        targetState = capturedMetrics,
        label = "ScanModeTransition"
    ) { analyzingMetrics ->
        if (analyzingMetrics == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview { result ->
                    metrics = result
                }

                FaceScanOverlay(
                    isFaceDetected = scanReadiness.canCapture,
                    canCapture = scanReadiness.canCapture,
                    statusText = scanReadiness.message,
                    onBack = onBack,
                    onRefresh = { metrics = null },
                    onCapture = {
                        metrics?.takeIf { scanReadiness.canCapture }?.let { capturedMetrics = it }
                    }
                )
            }
        } else {
            AnalysisProgressScreen(
                onComplete = { onComplete(analyzingMetrics) }
            )
        }
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
                text = "Our AI is mapping your facial\nlandmarks and matching frame\ngeometry",
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
            AnalysisStep("Compatibility scoring engine")
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

private data class ScanReadiness(
    val canCapture: Boolean,
    val message: String
)

private fun FaceAnalyzer.FaceMetrics?.getScanReadiness(): ScanReadiness {
    if (this?.isFaceDetected != true || landmarks.isEmpty()) {
        return ScanReadiness(false, "Position your face in the frame")
    }

    val requiredLandmarks = listOf(1, 10, 13, 14, 17, 33, 61, 133, 148, 152, 172, 263, 291, 362, 377, 397, 454)
    if (requiredLandmarks.any { it !in landmarks.indices }) {
        return ScanReadiness(false, "Include your full face in the frame")
    }

    val importantPoints = requiredLandmarks.map { landmarks[it] }
    val clippedAtImageEdge = importantPoints.any { point ->
        point.x < 0.06f || point.x > 0.94f || point.y < 0.06f || point.y > 0.94f
    }
    if (clippedAtImageEdge) {
        return ScanReadiness(false, "Include your full face in the frame")
    }

    val faceOvalIndices = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378,
        400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21,
        54, 103, 67, 109
    )
    val faceOvalPoints = faceOvalIndices.mapNotNull { landmarks.getOrNull(it) }
    if (faceOvalPoints.size < faceOvalIndices.size) {
        return ScanReadiness(false, "Include your full face in the frame")
    }

    val bounds = faceOvalPoints.fold(
        FaceBounds(
            minX = Float.POSITIVE_INFINITY,
            maxX = Float.NEGATIVE_INFINITY,
            minY = Float.POSITIVE_INFINITY,
            maxY = Float.NEGATIVE_INFINITY
        )
    ) { acc, point ->
        FaceBounds(
            minX = minOf(acc.minX, point.x),
            maxX = maxOf(acc.maxX, point.x),
            minY = minOf(acc.minY, point.y),
            maxY = maxOf(acc.maxY, point.y)
        )
    }

    val faceWidth = bounds.maxX - bounds.minX
    val faceHeight = bounds.maxY - bounds.minY
    val centerX = (bounds.minX + bounds.maxX) / 2f
    val centerY = (bounds.minY + bounds.maxY) / 2f
    val frameLeft = 0.18f
    val frameRight = 0.82f
    val frameTop = 0.18f
    val frameBottom = 0.88f
    val faceOutsideFrame = bounds.minX < frameLeft ||
        bounds.maxX > frameRight ||
        bounds.minY < frameTop ||
        bounds.maxY > frameBottom
    val nose = landmarks[1]
    val forehead = landmarks[10]
    val upperLip = landmarks[13]
    val mouthCenter = landmarks[14]
    val lowerLip = landmarks[17]
    val chin = landmarks[152]
    val leftCheek = landmarks[234]
    val rightCheek = landmarks[454]
    val lowerLeftJaw = landmarks[172]
    val lowerRightJaw = landmarks[397]
    val leftEyeOuter = landmarks[33]
    val leftEyeInner = landmarks[133]
    val rightEyeOuter = landmarks[263]
    val rightEyeInner = landmarks[362]
    val leftMouth = landmarks[61]
    val rightMouth = landmarks[291]
    val leftFaceWidth = kotlin.math.abs(nose.x - leftCheek.x)
    val rightFaceWidth = kotlin.math.abs(rightCheek.x - nose.x)
    val cheekBalance = minOf(leftFaceWidth, rightFaceWidth) / maxOf(leftFaceWidth, rightFaceWidth)
    val leftEyeWidth = kotlin.math.abs(leftEyeOuter.x - leftEyeInner.x)
    val rightEyeWidth = kotlin.math.abs(rightEyeOuter.x - rightEyeInner.x)
    val eyeBalance = minOf(leftEyeWidth, rightEyeWidth) / maxOf(leftEyeWidth, rightEyeWidth)
    val leftMouthWidth = kotlin.math.abs(nose.x - leftMouth.x)
    val rightMouthWidth = kotlin.math.abs(rightMouth.x - nose.x)
    val mouthBalance = minOf(leftMouthWidth, rightMouthWidth) / maxOf(leftMouthWidth, rightMouthWidth)
    val noseCentered = kotlin.math.abs(nose.x - centerX) <= faceWidth * 0.18f
    val faceBalanced = cheekBalance >= 0.62f &&
        eyeBalance >= 0.58f &&
        mouthBalance >= 0.55f &&
        noseCentered
    val upperFaceHeight = nose.y - forehead.y
    val lowerFaceHeight = chin.y - nose.y
    val lipToChinHeight = chin.y - lowerLip.y
    val jawBottomY = maxOf(chin.y, lowerLeftJaw.y, lowerRightJaw.y)
    val lowerFaceComplete = lowerFaceHeight >= upperFaceHeight * 0.72f &&
        lipToChinHeight >= faceHeight * 0.20f &&
        jawBottomY >= bounds.maxY - (faceHeight * 0.08f)
    val eyeCenterY = (leftEyeOuter.y + leftEyeInner.y + rightEyeOuter.y + rightEyeInner.y) / 4f
    val eyeToNoseHeight = nose.y - eyeCenterY
    val noseToMouthHeight = mouthCenter.y - nose.y
    val mouthToChinHeight = chin.y - upperLip.y
    val headLevel = eyeToNoseHeight >= faceHeight * 0.10f &&
        noseToMouthHeight >= faceHeight * 0.10f &&
        mouthToChinHeight >= faceHeight * 0.22f &&
        forehead.y >= frameTop + (faceHeight * 0.04f) &&
        chin.y <= frameBottom - (faceHeight * 0.04f)

    return when {
        faceOutsideFrame -> ScanReadiness(false, "Include your full face in the frame")
        !lowerFaceComplete -> ScanReadiness(false, "Include your full face in the frame")
        !headLevel -> ScanReadiness(false, "Include your full face in the frame")
        !faceBalanced -> ScanReadiness(false, "Include your full face in the frame")
        centerX < 0.4f -> ScanReadiness(false, "Move your face slightly right")
        centerX > 0.6f -> ScanReadiness(false, "Move your face slightly left")
        centerY < 0.34f -> ScanReadiness(false, "Move your face slightly down")
        centerY > 0.62f -> ScanReadiness(false, "Move your face slightly up")
        faceWidth < 0.24f || faceHeight < 0.30f -> ScanReadiness(false, "Move closer to the camera")
        faceWidth > 0.62f || faceHeight > 0.74f -> ScanReadiness(false, "Move farther from the camera")
        else -> ScanReadiness(true, "Face aligned - ready to capture")
    }
}

private data class FaceBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)
