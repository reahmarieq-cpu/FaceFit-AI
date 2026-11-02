package com.facefit.kiosk.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A transparent guide that stays between the header and controls in either orientation. */
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
        val guideWidth = minOf(width * 0.8f, height * 0.8f * 0.72f, 280f * density)
        val guideHeight = guideWidth / 0.72f
        bounds.set((width - guideWidth) / 2, (height - guideHeight) / 2,
            (width + guideWidth) / 2, (height + guideHeight) / 2)
        canvas.drawOval(bounds, outline)
    }
}
