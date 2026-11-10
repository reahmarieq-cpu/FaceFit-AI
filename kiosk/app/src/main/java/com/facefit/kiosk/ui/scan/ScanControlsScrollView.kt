package com.facefit.kiosk.ui.scan

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** Long instructions and accessibility text can scroll without covering the whole camera. */
class ScanControlsScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val limit = MeasureSpec.getSize(heightMeasureSpec)
        val capped = if (mode != MeasureSpec.UNSPECIFIED && limit > 0) {
            MeasureSpec.makeMeasureSpec((limit * 0.55f).toInt(), MeasureSpec.AT_MOST)
        } else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, capped)
    }
}
