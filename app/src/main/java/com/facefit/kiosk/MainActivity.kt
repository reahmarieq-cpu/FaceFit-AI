package com.facefit.kiosk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.databinding.ActivityMainBinding
import com.facefit.kiosk.facefit.camera.MockCameraCaptureGateway
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import com.facefit.kiosk.ui.auth.ShopLoginView
import com.facefit.kiosk.ui.consent.KioskConsentView
import com.facefit.kiosk.ui.scan.KioskFacePositioningView
import com.facefit.kiosk.ui.welcome.KioskWelcomeView

class MainActivity : AppCompatActivity(), KioskNavigator {

    private lateinit var binding: ActivityMainBinding
    private val sessionStore = InMemoryKioskSessionStore()
    private val authRepository = MockShopAuthRepository()
    private val cameraGateway = MockCameraCaptureGateway()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            navigateTo(KioskDestination.ShopLogin)
        }
    }

    override fun navigateTo(destination: KioskDestination) {
        when (destination) {
            KioskDestination.ShopLogin -> showShopLogin()
            KioskDestination.Welcome -> showWelcome()
            KioskDestination.Consent -> showConsent()
            KioskDestination.FacePositioning -> showFacePositioning()
            KioskDestination.Processing -> showFutureScreen(destination)
            KioskDestination.Results -> showFutureScreen(destination)
            KioskDestination.FrameDetails -> showFutureScreen(destination)
            KioskDestination.SelectionConfirmation -> showFutureScreen(destination)
            KioskDestination.SessionComplete -> showFutureScreen(destination)
        }
    }

    private fun showShopLogin() {
        val view = ShopLoginView(
            context = this,
            authRepository = authRepository,
            sessionStore = sessionStore,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showWelcome() {
        if (sessionStore.activeShop == null) {
            Toast.makeText(this, R.string.welcome_shop_missing, Toast.LENGTH_LONG).show()
            showShopLogin()
            return
        }

        val view = KioskWelcomeView(
            context = this,
            sessionStore = sessionStore,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showConsent() {
        val view = KioskConsentView(
            context = this,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showFacePositioning() {
        val view = KioskFacePositioningView(
            context = this,
            cameraGateway = cameraGateway,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showFutureScreen(destination: KioskDestination) {
        Toast.makeText(
            this,
            getString(R.string.future_not_ready) + " (${destination.routeName})",
            Toast.LENGTH_LONG
        ).show()
    }
}
