package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.LayeredImageGeometry
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.facefit.ui.catalog.FrameLensAnchors
import com.example.facefit.ui.catalog.FrameArmAnchors
import kotlin.math.abs
import kotlin.math.hypot

/** 2.5D try-on from original front and separate arm pixels. Intended for moderate turns. */
@Composable
fun LayeredImageTryOnOverlay(face: FaceAnalyzer.FaceMetrics?,front: Bitmap,left: Bitmap,right: Bitmap,
    lenses: FrameLensAnchors,leftAnchors: FrameArmAnchors,rightAnchors: FrameArmAnchors) {
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        if(face?.isFaceDetected!=true || face.landmarks.size<468) return@Canvas
        val rig=VtoHeadPose.rig(face,size.width.toInt(),size.height.toInt(),1f,0f) ?: return@Canvas
        val lensSpan=(lenses.rightX-lenses.leftX)*front.width
        if(lensSpan<=0f) return@Canvas
        val unit=rig.size/lensSpan
        val centerX=(lenses.leftX+lenses.rightX)*.5f*front.width
        val centerY=(lenses.leftY+lenses.rightY)*.5f*front.height
        fun project(x: Float,y: Float)=LayeredImageGeometry.imagePoint(rig,x,y,centerX,centerY,unit)
        val source=floatArrayOf(0f,0f,front.width.toFloat(),0f,front.width.toFloat(),front.height.toFloat(),0f,front.height.toFloat())
        val target=FloatArray(8)
        for(i in 0..3) {
            val p=project(source[i*2],source[i*2+1]) ?: return@Canvas
            target[i*2]=p.x;target[i*2+1]=p.y
        }
        val frontMatrix=Matrix()
        if(!frontMatrix.setPolyToPoly(source,0,target,0,4)) return@Canvas
        fun point(i: Int)=face.landmarks[i].let { Vector3(it.x*size.width,it.y*size.height,it.z*size.width) }
        val mask=Path()
        val oval=intArrayOf(10,338,297,332,284,251,389,356,454,323,361,288,397,365,379,378,400,377,152,148,176,149,150,136,172,58,132,93,234,127,162,21,54,103,67,109)
        oval.forEachIndexed { i,index -> val p=point(index);if(i==0) mask.moveTo(p.x,p.y) else mask.lineTo(p.x,p.y) };mask.close()
        val yaw=rig.zAxis.x
        val visibility=((abs(yaw)-.04f)/.20f).coerceIn(0f,1f)
        val sides=listOf(point(127),point(356)).sortedBy { it.x }
        drawIntoCanvas { canvas ->
            val native=canvas.nativeCanvas
            fun arm(image: Bitmap,anchors: FrameArmAnchors,leftSide: Boolean,near: Boolean) {
                if(visibility<=0f) return
                val hinge=project(anchors.frontX*front.width,anchors.frontY*front.height) ?: return
                val side=sides[if(leftSide) 0 else 1]
                val ear=side+rig.yAxis*(rig.size*.12f)
                val dx=ear.x-hinge.x;val dy=ear.y-hinge.y
                val length=hypot(dx,dy)
                // No ear landmarks are available; never stretch an arm across the face.
                val horizontal=dx*rig.xAxis.x+dy*rig.xAxis.y
                if(length<2f || length>rig.size*1.25f || (leftSide && horizontal>=-1f) || (!leftSide && horizontal<=1f)) return
                val src=floatArrayOf(anchors.hingeX*image.width,anchors.hingeY*image.height,
                    anchors.tipX*image.width,anchors.tipY*image.height,
                    anchors.widthX*image.width,anchors.widthY*image.height)
                val thickness=rig.size*.045f
                val dst=floatArrayOf(hinge.x,hinge.y,ear.x,ear.y,
                    hinge.x+rig.yAxis.x*thickness,hinge.y+rig.yAxis.y*thickness)
                val matrix=Matrix()
                if(!matrix.setPolyToPoly(src,0,dst,0,3)) return
                val saved=native.save()
                // The far arm is hidden by the face. Restrict the near arm to the
                // exposed hinge-to-ear segment; its tip must not reappear past the ear.
                if(!near) native.clipOutPath(mask)
                val segment=Path().apply {
                    val nx=-dy/length*thickness*1.6f;val ny=dx/length*thickness*1.6f
                    moveTo(hinge.x+nx,hinge.y+ny);lineTo(ear.x+nx,ear.y+ny)
                    lineTo(ear.x-nx,ear.y-ny);lineTo(hinge.x-nx,hinge.y-ny);close()
                }
                native.clipPath(segment)
                paint.alpha=(visibility*255).toInt()
                native.drawBitmap(image,matrix,paint)
                native.restoreToCount(saved);paint.alpha=255
            }
            val leftNear=yaw>0f
            if(leftNear) { arm(right,rightAnchors,false,false);arm(left,leftAnchors,true,true) }
            else { arm(left,leftAnchors,true,false);arm(right,rightAnchors,false,true) }
            native.drawBitmap(front,frontMatrix,paint)
        }
    }
}
