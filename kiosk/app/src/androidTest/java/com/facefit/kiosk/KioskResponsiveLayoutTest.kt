package com.facefit.kiosk

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.model.ShopAccount
import com.facefit.kiosk.navigation.KioskDestination
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskResponsiveLayoutTest {
    @Test fun screensFitCompactAndTabletWindows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (width in listOf(320, 600, 800, 1280)) {
                val base = instrumentation.targetContext
                val config = Configuration(base.resources.configuration).apply {
                    screenWidthDp = width
                    screenHeightDp = 800
                    smallestScreenWidthDp = minOf(width, 800)
                    orientation = if (width > 800) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                }
                val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_FaceFitKiosk)
                val density = context.resources.displayMetrics.density
                for (layout in listOf(R.layout.screen_shop_login, R.layout.screen_kiosk_welcome,
                        R.layout.screen_camera_consent, R.layout.screen_face_positioning, R.layout.screen_processing)) {
                    val screen = LayoutInflater.from(context).inflate(layout, null)
                    screen.findViewById<TextView>(R.id.shopNameText)?.text = "Connected to FaceFit Demo Shop"
                    fun populateCards(view: View) {
                        if (view.id == R.id.stepTitleText) (view as TextView).text = "Find your best frames"
                        if (view.id == R.id.stepBodyText) (view as TextView).text = "Scan your face to find eyewear that fits your features."
                        if (view is ViewGroup) for (i in 0 until view.childCount) populateCards(view.getChildAt(i))
                    }
                    populateCards(screen)
                    // Exercise error/outcome controls as well as the initial layout.
                    listOf(R.id.processingActions, R.id.retakeButton, R.id.generalErrorText).forEach {
                        screen.findViewById<View>(it)?.visibility = View.VISIBLE
                    }
                    screen.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.EXACTLY))
                    screen.layout(0, 0, screen.measuredWidth, screen.measuredHeight)
                    assertControlsFit(screen, "width=$width layout=$layout")
                    if (layout == R.layout.screen_face_positioning) {
                        val preview = screen.findViewById<View>(R.id.cameraPreviewView)
                        assertEquals(screen.width, preview.width)
                        assertEquals(screen.height, preview.height)
                        assertTrue(screen.findViewById<View>(R.id.scanGuideArea).height > 0)
                    }
                }
            }
        }
    }

    private fun assertControlsFit(view: View, description: String) {
        if (view.visibility == View.GONE) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.visibility == View.GONE) continue
                if (child is TextView || child is ViewGroup) {
                    assertTrue("$description: horizontal clipping of ${child.id}", child.left >= 0 && child.right <= view.width)
                    if (view !is ScrollView) {
                        assertTrue("$description: vertical clipping of ${child.id}: top=${child.top} bottom=${child.bottom} parentHeight=${view.height}", child.top >= 0 && child.bottom <= view.height)
                    }
                }
                assertControlsFit(child, description)
            }
        }
    }

    @Test fun recreationKeepsShopAndCurrentScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeFalse("Unlock the tablet to run the activity recreation check",
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                state.sessionStore.activeShop = ShopAccount("test-shop", "Test Shop", "demo@facefit.local")
                activity.navigateTo(KioskDestination.Consent)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertEquals("test-shop", state.sessionStore.activeShop?.id)
                assertEquals(KioskDestination.Consent, state.destination)
                assertNotNull(activity.findViewById<View>(R.id.agreeButton))
            }
        }
    }
}
