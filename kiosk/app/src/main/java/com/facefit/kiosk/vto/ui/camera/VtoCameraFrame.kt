package com.facefit.kiosk.vto.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import kotlin.math.hypot

/** Direct CameraX input and its display-coordinate mapping, used only by VTO. */
internal object VtoCameraFrame {
    fun copyRgba(image: ImageProxy): Bitmap {
        val plane=image.planes[0]
        val buffer=plane.buffer.duplicate()
        val pixels=IntArray(image.width*image.height)
        val base=buffer.position()
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val offset=base+y*plane.rowStride+x*plane.pixelStride
            val r=buffer.get(offset).toInt() and 255
            val g=buffer.get(offset+1).toInt() and 255
            val b=buffer.get(offset+2).toInt() and 255
            val a=buffer.get(offset+3).toInt() and 255
            pixels[y*image.width+x]=(a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels,image.width,image.height,Bitmap.Config.ARGB_8888)
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
