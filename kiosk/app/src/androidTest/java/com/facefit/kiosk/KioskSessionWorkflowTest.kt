package com.facefit.kiosk

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.navigation.KioskDestination
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskSessionWorkflowTest {
    @Test fun endSessionAndSignOutRequireConfirmationAndResetDifferentState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val profile = FaceScanResult(FaceShape.Round)
            lateinit var capture: File
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                state.demoMode = true
                state.sessionStore.activeShop = MockShopAuthRepository.demoShop
                activity.navigateTo(KioskDestination.Welcome)
                capture = File.createTempFile("session-test", ".jpg", activity.cacheDir)
                state.sessionStore.capturedFaceImagePath = capture.path
                state.sessionStore.analysisResult = profile
                activity.navigateTo(KioskDestination.Results)
            }

            onView(withId(R.id.kioskEndSession)).perform(click())
            onView(withText(R.string.kiosk_continue_session)).perform(click())
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertSame(profile, state.sessionStore.analysisResult)
                assertTrue(capture.exists())
            }

            onView(withId(R.id.kioskEndSession)).perform(click())
            onView(withText(R.string.kiosk_end_session)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertEquals(KioskDestination.Welcome, state.destination)
                assertSame(MockShopAuthRepository.demoShop, state.sessionStore.activeShop)
                assertNull(state.sessionStore.analysisResult)
                assertFalse(capture.exists())
            }

            onView(withId(R.id.kioskSettings)).perform(click())
            onView(withText(R.string.kiosk_sign_out)).perform(click())
            onView(withText(android.R.string.cancel)).perform(click())
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertEquals(KioskDestination.Welcome, state.destination)
                assertNotNull(state.sessionStore.activeShop)
            }

            onView(withId(R.id.kioskSettings)).perform(click())
            onView(withText(R.string.kiosk_sign_out)).perform(click())
            onView(withText(R.string.kiosk_sign_out)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertEquals(KioskDestination.ShopLogin, state.destination)
                assertNull(state.sessionStore.activeShop)
                activity.navigateTo(KioskDestination.Results)
                assertEquals(KioskDestination.ShopLogin, state.destination)
            }
        }
    }
}
