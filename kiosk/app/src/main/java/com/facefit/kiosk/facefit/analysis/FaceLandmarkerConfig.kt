package com.facefit.kiosk.facefit.analysis

object FaceLandmarkerConfig {
    const val MODEL_ASSET_PATH = "face_landmarker.task"
    const val MAX_FACE_COUNT = 2
    const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
    const val MIN_FACE_PRESENCE_CONFIDENCE = 0.5f
    const val MIN_TRACKING_CONFIDENCE = 0.5f
    const val MAX_DECODE_DIMENSION = 1600
}
