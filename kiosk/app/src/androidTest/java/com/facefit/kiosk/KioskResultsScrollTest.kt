package com.facefit.kiosk

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskResultsScrollTest {
    @Test fun onlyFrameListScrollsWithinFixedPanels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for ((width, height) in listOf(493 to 790, 753 to 1133, 800 to 600, 1133 to 700)) {
                val base = instrumentation.targetContext
                val configuration = Configuration(base.resources.configuration).apply {
                    screenWidthDp = width
                    screenHeightDp = height
                    smallestScreenWidthDp = minOf(width, height)
                    orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                }
                val context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_FaceFitKiosk)
                val density = context.resources.displayMetrics.density
                val screen = LayoutInflater.from(context).inflate(R.layout.screen_results, null)
                assertFalse(screen is ScrollView)
                val frames = screen.findViewById<LinearLayout>(R.id.resultsFrames)
                frames.visibility = View.VISIBLE
                screen.findViewById<View>(R.id.resultsEmptyMatches).visibility = View.GONE
                screen.findViewById<View>(R.id.resultsInventoryNote).visibility = View.GONE
                repeat(20) {
                    val row = LayoutInflater.from(context).inflate(R.layout.item_frame_recommendation, frames, false)
                    frames.addView(row)
                }
                screen.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                screen.layout(0, 0, screen.measuredWidth, screen.measuredHeight)
                val scroll = screen.findViewById<ScrollView>(R.id.resultsFramesScroll)
                val profile = screen.findViewById<ViewGroup>(R.id.resultsProfilePanel)
                val rescan = screen.findViewById<View>(R.id.resultsRescanButton)
                val label = screen.findViewById<View>(R.id.resultsFaceShapeText)
                val before = label.top
                assertTrue(scroll.clipChildren)
                assertTrue(scroll.clipToPadding)
                assertTrue(screen.findViewById<ViewGroup>(R.id.resultsFramesPanel).clipChildren)
                assertTrue(screen.findViewById<View>(R.id.resultsFramesPanel).clipToOutline)
                val description = "${width}x$height"
                assertTrue("$description frame viewport too small: ${scroll.height / density}", scroll.height >= 120 * density)
                assertTrue("$description Scan Again clipped", rescan.bottom * screen.findViewById<View>(R.id.resultsProfileContent).scaleY <= profile.height + 1)
                assertTrue("$description frame panel exceeds screen", profile.bottom <= screen.height)
                if (width > height) assertEquals(profile.height, screen.findViewById<View>(R.id.resultsFramesPanel).height)
                scroll.scrollTo(0, frames.height)
                assertTrue("$description frame list cannot scroll", scroll.scrollY > 0)
                assertEquals("$description list scrolls beyond its bottom", frames.height - (scroll.height - scroll.paddingTop - scroll.paddingBottom), scroll.scrollY)
                assertEquals("$description profile moved", before, label.top)
            }
        }
    }
}
