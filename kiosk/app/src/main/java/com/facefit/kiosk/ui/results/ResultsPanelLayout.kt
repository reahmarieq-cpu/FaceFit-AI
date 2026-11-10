package com.facefit.kiosk.ui.results

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

/** Fits the two result panels to the actual window, including resized tablet windows. */
class ResultsPanelLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val gap get() = (16 * resources.displayMetrics.density).roundToInt()
    private var horizontal = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        horizontal = availableWidth >= availableHeight && availableWidth >= 600 * resources.displayMetrics.density
        val profile = getChildAt(0)
        val frames = getChildAt(1)
        if (horizontal) {
            val profileWidth = ((availableWidth - gap) * 0.40f).roundToInt()
            profile.measure(exact(profileWidth), exact(availableHeight))
            frames.measure(exact(availableWidth - gap - profileWidth), exact(availableHeight))
        } else {
            // Keep a useful frame viewport while preserving the original profile proportions.
            profile.measure(exact(availableWidth), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val profileHeight = min(profile.measuredHeight, (availableHeight * 0.60f).roundToInt())
            profile.measure(exact(availableWidth), exact(profileHeight))
            frames.measure(exact(availableWidth), exact(availableHeight - gap - profileHeight))
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val profile = getChildAt(0)
        val frames = getChildAt(1)
        profile.layout(paddingLeft, paddingTop, paddingLeft + profile.measuredWidth, paddingTop + profile.measuredHeight)
        val x = if (horizontal) profile.right + gap else paddingLeft
        val y = if (horizontal) paddingTop else profile.bottom + gap
        frames.layout(x, y, x + frames.measuredWidth, y + frames.measuredHeight)
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    override fun generateLayoutParams(attrs: AttributeSet?) = LayoutParams(context, attrs)
    private fun exact(size: Int) = MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)
}

/** Original design sizes are retained when they fit; smaller windows scale the profile uniformly. */
class FittedProfilePanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val content = getChildAt(0)
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            content.measuredHeight else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val content = getChildAt(0)
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val scale = min(1f, height.toFloat() / content.measuredHeight.coerceAtLeast(1))
        content.pivotX = 0f
        content.pivotY = 0f
        content.scaleX = scale
        content.scaleY = scale
        content.translationX = (width - content.measuredWidth * scale) / 2f
        content.translationY = 0f
    }
}
