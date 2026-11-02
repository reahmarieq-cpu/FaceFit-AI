package com.facefit.kiosk.facefit.features

object FaceLandmarkIndices {
    // Official MediaPipe face oval top contour point.
    const val FACE_OVAL_TOP = 10

    // Official MediaPipe face oval chin point.
    const val CHIN = 152

    // Official MediaPipe face oval left/right outer contour points near the widest mid-face span.
    const val FACE_WIDTH_LEFT = 234
    const val FACE_WIDTH_RIGHT = 454

    // Symmetric upper face oval points near the temple-side forehead region; this is not hairline detection.
    const val UPPER_FACE_LEFT = 103
    const val UPPER_FACE_RIGHT = 332

    // Symmetric lower face oval points used as an estimated jawline-width span.
    const val JAWLINE_LEFT = 172
    const val JAWLINE_RIGHT = 397

    // Official MediaPipe iris contour landmarks. Centers are calculated by averaging each contour.
    val RIGHT_IRIS_CONTOUR = listOf(469, 470, 471, 472)
    val LEFT_IRIS_CONTOUR = listOf(474, 475, 476, 477)
}
