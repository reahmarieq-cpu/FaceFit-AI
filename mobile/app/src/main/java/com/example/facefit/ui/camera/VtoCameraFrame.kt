package com.example.facefit.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.example.facefit.facefit.analysis.FaceAnalyzer
import kotlin.math.hypot

/** Direct CameraX input and its display-coordinate mapping, used only by VTO. */
internal object VtoCameraFrame {
    /** Upright analysis buffer -> sensor -> displayed preview. Mirror is in sensorToView.
     * Do not add another front-camera flip or swap axes based on device orientation.
     */
    fun bufferToPreview(sensorToBuffer: Matrix, sensorToView: Matrix): Matrix? {
        val bufferToSensor = Matrix()
        if (!sensorToBuffer.invert(bufferToSensor)) return null
        return Matrix().apply { setConcat(sensorToView, bufferToSensor) }
    }

    /** Map CameraX's shared upright crop to FILL_CENTER, mirroring the front preview once. */
    fun frontViewportToPreview(crop: Rect, width: Int, height: Int): Matrix? {
        if (crop.isEmpty || width <= 0 || height <= 0) return null
        val scale = maxOf(width.toFloat() / crop.width(), height.toFloat() / crop.height())
        return Matrix().apply {
            setScale(-scale, scale)
            postTranslate(width * .5f + crop.exactCenterX() * scale,
                height * .5f - crop.exactCenterY() * scale)
        }
    }

    fun coversPreview(transform: Matrix, bufferWidth: Int, bufferHeight: Int, width: Int, height: Int): Boolean {
        if (bufferWidth <= 0 || bufferHeight <= 0) return false
        val bounds = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        transform.mapRect(bounds)
        return VtoPreviewBounds.coversView(bounds.left, bounds.top, bounds.right, bounds.bottom, width, height)
    }

    fun copyRgba(image: ImageProxy): Bitmap = VtoRgbaBuffer().copy(image)

    /** Undo the inference-only crop and front-camera input mirror, before sensor mapping.
     * Landmarks keep the same semantic handedness as inference on the old mirrored preview.
     */
    fun restoreCrop(face: FaceAnalyzer.FaceMetrics, width: Int, height: Int,
        crop: Rect, mirrored: Boolean): FaceAnalyzer.FaceMetrics {
        require(width>0 && height>0 && !crop.isEmpty && crop.left>=0 && crop.top>=0 &&
            crop.right<=width && crop.bottom<=height)
        if(!mirrored && crop.left==0 && crop.top==0 && crop.width()==width && crop.height()==height) return face
        return face.copy(landmarks=face.landmarks.map { p -> FaceAnalyzer.LandmarkPoint(
            (crop.left+(if(mirrored) 1f-p.x else p.x)*crop.width())/width,
            (crop.top+p.y*crop.height())/height,p.z*crop.width()/width)
        },imageWidth=width,imageHeight=height)
    }

    fun map(face: FaceAnalyzer.FaceMetrics, transform: Matrix, width: Int, height: Int, mirroredInput: Boolean = false): FaceAnalyzer.FaceMetrics {
        require(width>0 && height>0)
        val xy=FloatArray(face.landmarks.size*2)
        face.landmarks.forEachIndexed { i,p -> xy[i*2]=(if(mirroredInput) 1f-p.x else p.x)*face.imageWidth; xy[i*2+1]=p.y*face.imageHeight }
        transform.mapPoints(xy)
        val horizontal=floatArrayOf(face.imageWidth.toFloat(),0f)
        transform.mapVectors(horizontal)
        val depthScale=hypot(horizontal[0],horizontal[1])/width
        return face.copy(landmarks=face.landmarks.mapIndexed { i,p ->
            FaceAnalyzer.LandmarkPoint(xy[i*2]/width,xy[i*2+1]/height,p.z*depthScale)
        },imageWidth=width,imageHeight=height)
    }
}
