package com.facefit.kiosk

import android.Manifest
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import com.facefit.kiosk.ui.frames.KioskFrameDetailsView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.facefit.kiosk.data.auth.FirebaseAuthRepository
import com.facefit.kiosk.data.auth.ShopAuthRepository
import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.data.frames.FirebaseFrameCatalog
import com.facefit.kiosk.data.frames.FrameCatalog
import com.facefit.kiosk.data.frames.MockFrameCatalog
import com.facefit.kiosk.facefit.recommendation.FrameCompatibility
import com.facefit.kiosk.facefit.analysis.MobileKioskAnalyzer
import com.facefit.kiosk.databinding.ActivityMainBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import com.facefit.kiosk.permissions.CameraPermissionController
import com.facefit.kiosk.ui.auth.ShopLoginView
import com.facefit.kiosk.ui.consent.KioskConsentView
import com.facefit.kiosk.ui.processing.KioskProcessingView
import com.facefit.kiosk.ui.results.KioskResultsView
import com.facefit.kiosk.ui.scan.KioskFacePositioningView
import com.facefit.kiosk.facefit.camera.CameraXCameraCaptureGateway
import com.facefit.kiosk.ui.welcome.KioskWelcomeView

class MainActivity : AppCompatActivity(), KioskNavigator, CameraPermissionController {

    private lateinit var binding: ActivityMainBinding
    private val kioskState by lazy { ViewModelProvider(this)[KioskStateViewModel::class.java] }
    private val sessionStore get() = kioskState.sessionStore
    private val authRepository: ShopAuthRepository by lazy { FirebaseAuthRepository() }
    private val analyzer by lazy { MobileKioskAnalyzer(applicationContext, sessionStore) }
    private val cameraGateway by lazy { CameraXCameraCaptureGateway(applicationContext) }
    private val frameCompatibility by lazy { FrameCompatibility.load(applicationContext) }
    private val firebaseFrameCatalog by lazy { FirebaseFrameCatalog(compatibility = { frameCompatibility }) }
    private val demoFrameCatalog: FrameCatalog by lazy { MockFrameCatalog(compatibility = { frameCompatibility }) }
    private val isDebuggable get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val frameCatalog: FrameCatalog
        get() = if (isDebuggable && kioskState.demoMode) demoFrameCatalog else firebaseFrameCatalog
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var confirmation: AlertDialog? = null
    private val authListener = FirebaseAuth.AuthStateListener {
        if (kioskState.destination != KioskDestination.ShopLogin && !hasShopSession()) {
            clearRetailerSession()
        }
    }

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (kioskState.destination == KioskDestination.VirtualTryOn) {
                    navigateTo(KioskDestination.FrameDetails)
                } else if (kioskState.destination == KioskDestination.FrameDetails) {
                    navigateTo(kioskState.frameDetailsBackTo)
                } else if (kioskState.destination == KioskDestination.FacePositioning) {
                    navigateTo(KioskDestination.Welcome)
                } else if (kioskState.destination == KioskDestination.Welcome && kioskState.demoMode) {
                    navigateTo(KioskDestination.ShopLogin)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        binding.kioskSettings.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(R.string.kiosk_sign_out)
                setOnMenuItemClickListener {
                    confirmSignOut()
                    true
                }
                show()
            }
        }
        binding.kioskEndSession.setOnClickListener { confirmEndSession() }
        navigateTo(kioskState.destination)
        firebaseAuth.addAuthStateListener(authListener)
    }

    override fun onDestroy() {
        firebaseAuth.removeAuthStateListener(authListener)
        confirmation?.dismiss()
        super.onDestroy()
    }

    private fun hasShopSession(): Boolean {
        val shop = sessionStore.activeShop ?: return false
        return if (kioskState.demoMode) isDebuggable && shop.id == MockShopAuthRepository.demoShop.id
        else authRepository.currentShopId == shop.id
    }

    private fun confirmSignOut() {
        if (kioskState.destination != KioskDestination.Welcome || !hasShopSession()) return
        confirm(R.string.kiosk_sign_out_title, R.string.kiosk_sign_out_body,
            android.R.string.cancel, R.string.kiosk_sign_out) {
            authRepository.signOut()
            clearRetailerSession()
        }
    }

    private fun confirmEndSession() {
        if (!hasShopSession()) return
        confirm(R.string.kiosk_end_session_title, R.string.kiosk_end_session_body,
            R.string.kiosk_continue_session, R.string.kiosk_end_session) {
            navigateTo(KioskDestination.Welcome)
        }
    }

    private fun confirm(title: Int, message: Int, cancel: Int, accept: Int, onConfirm: () -> Unit) {
        if (confirmation?.isShowing == true) return
        confirmation = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(cancel, null)
            .setPositiveButton(accept) { _, _ -> onConfirm() }
            .show()
    }

    private fun clearRetailerSession() {
        binding.screenContainer.removeAllViews()
        sessionStore.clearAll()
        firebaseFrameCatalog.clear()
        kioskState.demoMode = false
        kioskState.frameDetailsBackTo = KioskDestination.Results
        pendingCameraPermissionResult = null
        navigateTo(KioskDestination.ShopLogin)
    }

    override fun navigateTo(destination: KioskDestination) {
        confirmation?.dismiss()
        confirmation = null
        if (destination != KioskDestination.ShopLogin && !hasShopSession()) {
            clearRetailerSession()
            return
        }
        if (destination == KioskDestination.Welcome) {
            binding.screenContainer.removeAllViews()
            sessionStore.clearCustomerSession()
            kioskState.frameDetailsBackTo = KioskDestination.Results
            pendingCameraPermissionResult = null
        }
        val hasSettings = destination == KioskDestination.Welcome
        val hasEndSession = destination == KioskDestination.Results ||
            destination == KioskDestination.FrameDetails
        binding.kioskActionBar.visibility = if (hasSettings || hasEndSession) View.VISIBLE else View.GONE
        binding.kioskSettings.visibility = if (hasSettings) View.VISIBLE else View.GONE
        binding.kioskEndSession.visibility = if (hasEndSession) View.VISIBLE else View.GONE

        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (destination == KioskDestination.FacePositioning || destination == KioskDestination.VirtualTryOn) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
        if (destination in listOf(
                KioskDestination.ShopLogin, KioskDestination.Welcome, KioskDestination.Consent,
                KioskDestination.FacePositioning, KioskDestination.Processing, KioskDestination.Results, KioskDestination.FrameDetails, KioskDestination.VirtualTryOn
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
            KioskDestination.Results -> showResults()
            KioskDestination.VirtualTryOn -> showVirtualTryOn()
            KioskDestination.FrameDetails -> showFrameDetails()
            KioskDestination.SelectionConfirmation -> showFutureScreen(destination)
            KioskDestination.SessionComplete -> showFutureScreen(destination)
        }
    }

    private fun showShopLogin() {
        if (kioskState.demoMode) {
            sessionStore.clearAll()
            kioskState.demoMode = false
        }
        val view = ShopLoginView(
            context = this,
            authRepository = authRepository,
            sessionStore = sessionStore,
            navigator = this
        )
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showResults() {
        kioskState.frameDetailsBackTo = KioskDestination.Results
        if (sessionStore.analysisResult == null) {
            navigateTo(KioskDestination.FacePositioning)
            return
        }
        val view = KioskResultsView(this, sessionStore, this, frameCatalog)
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showVirtualTryOn() {
        val frame = sessionStore.selectedRecommendation?.frame?.tryOn
        if (frame == null || !frame.canTryOn) {
            navigateTo(KioskDestination.FrameDetails)
            return
        }
        val view = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                androidx.compose.material3.MaterialTheme {
                    com.facefit.kiosk.vto.ui.tryon.VirtualTryOnScreen(
                        frame = frame,
                        onBack = { navigateTo(KioskDestination.FrameDetails) },
                        onEndSession = ::confirmEndSession
                    )
                }
            }
        }
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showFrameDetails() {
        val backTo = kioskState.frameDetailsBackTo
        if (sessionStore.selectedRecommendation == null ||
            (backTo == KioskDestination.Results && sessionStore.analysisResult == null)
        ) {
            navigateTo(backTo)
            return
        }
        val view = KioskFrameDetailsView(this, sessionStore, this, backTo)
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(view)
    }

    private fun showProcessing() {
        val view = KioskProcessingView(
            context = this,
            sessionStore = sessionStore,
            analyzer = analyzer,
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

    private fun showFutureScreen(destination: KioskDestination) {
        Toast.makeText(
            this,
            getString(R.string.future_not_ready) + " (${destination.routeName})",
            Toast.LENGTH_LONG
        ).show()
    }
}
