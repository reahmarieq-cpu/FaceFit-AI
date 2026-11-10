package com.facefit.kiosk.facefit.mobilescan

internal data class ScanReadiness(
    val canCapture: Boolean,
    val message: String
)

internal fun FaceAnalyzer.FaceMetrics?.getScanReadiness(): ScanReadiness {
    if ((this?.detectedFaceCount ?: 0) > 1) {
        return ScanReadiness(false, "Only one person should be in the frame")
    }
    if (this?.isFaceDetected != true || landmarks.isEmpty()) {
        return ScanReadiness(false, "No face detected. Face the camera in good light.")
    }

    val requiredLandmarks = listOf(1, 10, 13, 14, 17, 33, 61, 133, 148, 152, 172, 263, 291, 362, 377, 397, 454)
    if (requiredLandmarks.any { it !in landmarks.indices }) {
        return ScanReadiness(false, "Include your full face in the frame")
    }

    if (imageWidth <= 0 || imageHeight <= 0 || landmarks.any {
        !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite()
    }) return ScanReadiness(false, "Include your full face in the frame")
    val importantPoints = requiredLandmarks.map { landmarks[it] }
    val clippedAtImageEdge = importantPoints.any { point ->
        point.x < 0.06f || point.x > 0.94f || point.y < 0.06f || point.y > 0.94f
    }
    if (clippedAtImageEdge) {
        return ScanReadiness(false, "Include your full face in the frame")
    }

    if (!ScanStabilityFeatures.isValid(stabilityFeatures)) {
        return ScanReadiness(false, "Face forward so we can measure your features")
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
    val leftEyeOuter = landmarks[33]
    val leftEyeInner = landmarks[133]
    val rightEyeOuter = landmarks[263]
    val rightEyeInner = landmarks[362]
    val eyeDx = ((rightEyeOuter.x + rightEyeInner.x) - (leftEyeOuter.x + leftEyeInner.x)) * imageWidth / 2
    val eyeDy = ((rightEyeOuter.y + rightEyeInner.y) - (leftEyeOuter.y + leftEyeInner.y)) * imageHeight / 2
    val eyeTilt = Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(eyeDy), kotlin.math.abs(eyeDx)).toDouble())
    val sizeRatio = FaceScanPose.cheekSpan(this) / minOf(imageWidth, imageHeight)
    val pose = headPose
    val lighting = scanLighting
    val noseCentered = kotlin.math.abs(nose.x - centerX) <= faceWidth * 0.24f

    return when {
        sizeRatio < 0.32f -> ScanReadiness(false, "Move closer to the camera")
        sizeRatio > 0.42f || faceWidth > 0.62f || faceHeight > 0.74f -> ScanReadiness(false, "Move farther from the camera")
        faceOutsideFrame -> ScanReadiness(false, "Include your full face in the frame")
        pose == null || !listOf(pose.pitch, pose.yaw, pose.roll).all { it.isFinite() } ->
            ScanReadiness(false, "Face the camera directly")
        kotlin.math.abs(pose.yaw) > 8f || !noseCentered || !FaceScanPose.isFacingCamera(this) ->
            ScanReadiness(false, "Face the camera directly")
        kotlin.math.abs(pose.pitch) > 10f -> ScanReadiness(false, "Keep your chin level")
        kotlin.math.abs(pose.roll) > 5f || eyeTilt > 5.0 -> ScanReadiness(false, "Keep your head level")
        lighting == null -> ScanReadiness(false, "Position your face in even lighting")
        lighting.guidance() != null -> ScanReadiness(false, lighting.guidance()!!)
        centerX < 0.4f -> ScanReadiness(false, "Move your face slightly right")
        centerX > 0.6f -> ScanReadiness(false, "Move your face slightly left")
        centerY < 0.34f -> ScanReadiness(false, "Move your face slightly down")
        centerY > 0.62f -> ScanReadiness(false, "Move your face slightly up")
        else -> ScanReadiness(true, "Hold still - capturing automatically")
    }
}

private data class FaceBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)
