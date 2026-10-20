package com.example.facefit.ui.components

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
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.TryOnGeometry
import com.example.facefit.logic.TryOnPoint
import com.example.facefit.ui.screens.FrameLensAnchors
import kotlin.math.hypot

private val faceOval = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
    234, 127, 162, 21, 54, 103, 67, 109)

@Composable
fun TryOnOverlay(
    metrics: FaceAnalyzer.FaceMetrics?,
    front: Bitmap,
    temple: Bitmap?,
    anchors: FrameLensAnchors,
    scale: Float,
    heightOffset: Float
) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val face = metrics ?: return@Canvas
        if (!face.isFaceDetected || face.landmarks.size <= 454) return@Canvas
        fun point(index: Int): TryOnPoint {
            val p = face.landmarks[index]
            return TryOnPoint(p.x * size.width, p.y * size.height, p.z * size.width)
        }
        fun eye(outer: Int, inner: Int): TryOnPoint {
            val a = point(outer)
            val b = point(inner)
            return TryOnPoint((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        }
        val pose = TryOnGeometry.pose(eye(33, 133), eye(263, 362), scale, heightOffset) ?: return@Canvas
        val sourceWidth = (anchors.rightX - anchors.leftX) * front.width
        if (sourceWidth <= 0f || anchors.leftY != anchors.rightY) return@Canvas
        val verticalScale = pose.depthAwareDistance / sourceWidth
        val source = floatArrayOf(anchors.leftX * front.width, anchors.leftY * front.height,
            anchors.rightX * front.width, anchors.rightY * front.height,
            anchors.leftX * front.width, anchors.leftY * front.height + 100f)
        val target = floatArrayOf(pose.left.x, pose.left.y, pose.right.x, pose.right.y,
            pose.left.x + pose.downX * verticalScale * 100, pose.left.y + pose.downY * verticalScale * 100)
        val frontMatrix = Matrix().apply { setPolyToPoly(source, 0, target, 0, 3) }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (temple != null && pose.templeVisibility > 0f) {
                val headMask = Path()
                faceOval.forEachIndexed { i, index ->
                    val p = point(index)
                    if (i == 0) headMask.moveTo(p.x, p.y) else headMask.lineTo(p.x, p.y)
                }
                headMask.close()
                val sides = listOf(point(234), point(454)).sortedBy { it.x }
                val hinges = floatArrayOf(front.width * 0.016f, front.height * 0.31f,
                    front.width * 0.984f, front.height * 0.31f)
                frontMatrix.mapPoints(hinges)
                fun drawArm(leftSide: Boolean, near: Boolean) {
                    val index = if (leftSide) 0 else 1
                    val hingeX = hinges[index * 2]
                    val hingeY = hinges[index * 2 + 1]
                    val side = sides[index]
                    // Face mesh has no ear landmark: estimate ear height from the outer cheek.
                    val earX = side.x - pose.downX * pose.depthAwareDistance * 0.30f
                    val earY = side.y - pose.downY * pose.depthAwareDistance * 0.30f
                    val horizontal = (earX - hingeX) * pose.downY - (earY - hingeY) * pose.downX
                    if ((leftSide && horizontal >= -2f) || (!leftSide && horizontal <= 2f)) return
                    if (hypot(earX - hingeX, earY - hingeY) > pose.depthAwareDistance * 1.3f) return
                    val armSource = floatArrayOf(temple.width * 0.025f, temple.height * 0.435f,
                        temple.width * 0.94f, temple.height * 0.435f,
                        temple.width * 0.025f, temple.height * 0.535f)
                    val thickness = pose.depthAwareDistance * 0.07f
                    val armTarget = floatArrayOf(hingeX, hingeY, earX, earY,
                        hingeX + pose.downX * thickness, hingeY + pose.downY * thickness)
                    val armMatrix = Matrix().apply { setPolyToPoly(armSource, 0, armTarget, 0, 3) }
                    val saved = native.save()
                    if (!near) native.clipOutPath(headMask)
                    paint.alpha = (255 * pose.templeVisibility).toInt()
                    native.drawBitmap(temple, armMatrix, paint)
                    native.restoreToCount(saved)
                    paint.alpha = 255
                }
                val leftIsNear = pose.yaw > 0
                drawArm(leftSide = !leftIsNear, near = false)
                drawArm(leftSide = leftIsNear, near = true)
            }
            native.drawBitmap(front, frontMatrix, paint)
        }
    }
}
