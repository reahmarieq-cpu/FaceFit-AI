package com.example.facefit.facefit.analysis

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

    val topHairline = landmarks[10]
    val midBrow = landmarks.getOrNull(151) ?: landmarks[1]
    val chinBottom = landmarks[152]
    val foreheadHeight = (midBrow.y - topHairline.y) * imageHeight
    val fullFaceHeight = (chinBottom.y - topHairline.y) * imageHeight
    val foreheadRatio = if (fullFaceHeight > 10f) foreheadHeight / fullFaceHeight else 0f

    return when {
        // 1. Distance check: Hold phone at arm's length (50-60 cm) to eliminate wide-angle lens distortion
        sizeRatio > 0.40f || faceWidth > 0.60f || faceHeight > 0.72f ->
            ScanReadiness(false, "Hold phone farther at arm's length (50–60 cm)")
        sizeRatio < 0.28f ->
            ScanReadiness(false, "Move slightly closer while keeping phone at arm's length")

        // 2. Head & Eye Level Alignment: Keep phone parallel to eyes, chin level
        pose == null || !listOf(pose.pitch, pose.yaw, pose.roll).all { it.isFinite() } ->
            ScanReadiness(false, "Face the camera directly")
        kotlin.math.abs(pose.yaw) > 8f || !noseCentered || !FaceScanPose.isFacingCamera(this) ->
            ScanReadiness(false, "Face the camera directly")
        pose.pitch > 7f ->
            ScanReadiness(false, "Raise phone to eye level (do not tilt head down)")
        pose.pitch < -7f ->
            ScanReadiness(false, "Hold phone at eye level parallel to your eyes")
        kotlin.math.abs(pose.roll) > 5f || eyeTilt > 5.0 ->
            ScanReadiness(false, "Keep your head level")

        // 3. Lighting checks
        lighting == null -> ScanReadiness(false, "Position your face in even lighting")
        lighting.guidance() != null -> ScanReadiness(false, lighting.guidance()!!)
        else -> ScanReadiness(true, "Capture passed the face checks")
    }
}

/** The numbers behind "Face the camera directly", for tuning that check from phone logs. */
internal fun FaceAnalyzer.FaceMetrics.facingDiagnostics(): org.json.JSONObject {
    val json = org.json.JSONObject()
    if (landmarks.size <= 454 || imageWidth <= 0 || imageHeight <= 0) return json
    val xs = landmarks.map { it.x }
    val width = (xs.max() - xs.min()).takeIf { it > 0f } ?: return json
    fun distance(a: Int, b: Int) = kotlin.math.hypot(
        (landmarks[a].x - landmarks[b].x) * imageWidth, (landmarks[a].y - landmarks[b].y) * imageHeight)
    val leftNose = distance(234, 1)
    val rightNose = distance(454, 1)
    val cheek = distance(234, 454)
    return json.put("yaw", headPose?.yaw?.toDouble()).put("pitch", headPose?.pitch?.toDouble())
        .put("roll", headPose?.roll?.toDouble())
        // Limits: nose offset 0.24 of face width, asymmetry 0.12, depth difference 0.25.
        .put("nose_offset", (kotlin.math.abs(landmarks[1].x - (xs.max() + xs.min()) / 2f) / width).toDouble())
        .put("asymmetry", (kotlin.math.abs(leftNose - rightNose) / (leftNose + rightNose)).toDouble())
        .put("depth_difference", (kotlin.math.abs(landmarks[234].z - landmarks[454].z) * imageWidth / cheek).toDouble())
        .put("face_center_x", ((xs.max() + xs.min()) / 2f).toDouble())
}

private data class FaceBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)
