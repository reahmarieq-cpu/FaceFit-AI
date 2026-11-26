package com.facefit.kiosk.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A guide drawn in the same coordinate space as the camera preview. */
class ScanFaceGuideView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 7f * density), 0f)
    }
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val guideWidth = minOf(width * 0.8f, height * 0.70f * 0.72f, 360f * density)
        val guideHeight = guideWidth / 0.72f
        val centerX = width / 2f
        val centerY = height * .46f
        bounds.set(centerX - guideWidth / 2, centerY - guideHeight / 2,
            centerX + guideWidth / 2, centerY + guideHeight / 2)
        canvas.drawOval(bounds, outline)
    }
}
