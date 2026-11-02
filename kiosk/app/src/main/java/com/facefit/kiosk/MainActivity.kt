package com.facefit.kiosk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.databinding.ActivityMainBinding
import com.facefit.kiosk.facefit.analysis.MediaPipeFaceAnalysisGateway
import com.facefit.kiosk.facefit.camera.CameraXCameraCaptureGateway
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import com.facefit.kiosk.permissions.CameraPermissionController
import com.facefit.kiosk.ui.auth.ShopLoginView
import com.facefit.kiosk.ui.consent.KioskConsentView
import com.facefit.kiosk.ui.processing.KioskProcessingView
import com.facefit.kiosk.ui.scan.KioskFacePositioningView
import com.facefit.kiosk.ui.welcome.KioskWelcomeView

class MainActivity : AppCompatActivity(), KioskNavigator, CameraPermissionController {

    private lateinit var binding: ActivityMainBinding
    private val kioskState by lazy { ViewModelProvider(this)[KioskStateViewModel::class.java] }
    private val sessionStore get() = kioskState.sessionStore
    private val authRepository = MockShopAuthRepository()
    private val cameraGateway by lazy { CameraXCameraCaptureGateway(applicationContext) }
    private var faceAnalysisGateway: MediaPipeFaceAnalysisGateway? = null
    private var pendingCameraPermissionResult: ((Boolean) -> Unit)? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingCameraPermissionResult?.invoke(isGranted)
        pendingCameraPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        navigateTo(kioskState.destination)
    }

    override fun navigateTo(destination: KioskDestination) {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (destination == KioskDestination.FacePositioning) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
        if (destination in listOf(
                KioskDestination.ShopLogin, KioskDestination.Welcome, KioskDestination.Consent,
                KioskDestination.FacePositioning, KioskDestination.Processing
            )
        ) {
            kioskState.destination = destination
        }
        when (destination) {
            KioskDestination.ShopLogin -> showShopLogin()
            KioskDestination.Welcome -> showWelcome()
            KioskDestination.Consent -> showConsent()
            KioskDestination.FacePositioning -> showFacePositioning()
            KioskDestination.Processing -> showProcessing()
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

    private fun showProcessing() {
        val view = KioskProcessingView(
            context = this,
            sessionStore = sessionStore,
            faceAnalysisGateway = getFaceAnalysisGateway(),
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun getFaceAnalysisGateway(): MediaPipeFaceAnalysisGateway {
        return faceAnalysisGateway ?: MediaPipeFaceAnalysisGateway(applicationContext).also {
            faceAnalysisGateway = it
        }
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
            lifecycleOwner = this,
            cameraGateway = cameraGateway,
            permissionController = this,
            sessionStore = sessionStore,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    override fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun shouldShowCameraPermissionRationale(): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.CAMERA
        )
    }

    override fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        pendingCameraPermissionResult = onResult
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    override fun onDestroy() {
        faceAnalysisGateway?.close()
        super.onDestroy()
    }

    private fun showFutureScreen(destination: KioskDestination) {
        Toast.makeText(
            this,
            getString(R.string.future_not_ready) + " (${destination.routeName})",
            Toast.LENGTH_LONG
        ).show()
    }
}
