package com.facefit.kiosk.vto.ui.camera

import android.graphics.Matrix
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import kotlin.math.hypot

/** Maps unrotated analysis landmarks into the cropped, mirrored preview. */
internal object VtoCameraFrame {
    fun map(
        face: FaceAnalyzer.FaceMetrics,
        transform: Matrix,
        width: Int,
        height: Int
    ): FaceAnalyzer.FaceMetrics {
        require(width > 0 && height > 0)
        val xy = FloatArray(face.landmarks.size * 2)
        face.landmarks.forEachIndexed { i, point ->
            xy[i * 2] = point.x * face.imageWidth
            xy[i * 2 + 1] = point.y * face.imageHeight
        }
        transform.mapPoints(xy)
        val horizontal = floatArrayOf(face.imageWidth.toFloat(), 0f)
        transform.mapVectors(horizontal)
        val depthScale = hypot(horizontal[0], horizontal[1]) / width
        return face.copy(
            landmarks = face.landmarks.mapIndexed { i, point ->
                FaceAnalyzer.LandmarkPoint(xy[i * 2] / width, xy[i * 2 + 1] / height, point.z * depthScale)
            },
            imageWidth = width,
            imageHeight = height
        )
    }
}
