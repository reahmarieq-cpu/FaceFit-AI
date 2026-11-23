package com.example.facefit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.ContextCompat
import com.example.facefit.notifications.FrameNotifications
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.navigation.FaceFitNavHost
import com.example.facefit.ui.theme.FacefitTheme

class MainActivity : ComponentActivity() {
    private val authManager by lazy { AuthManager() }
    private var notificationFrame by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationFrame = FrameNotifications.frameFromIntent(intent)
    }
    private var composeReady = false
    private var nativeSplashGone by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)
        FrameNotifications.createChannel(this)
        if (savedInstanceState == null) notificationFrame = FrameNotifications.frameFromIntent(intent)
        val freshLaunch = savedInstanceState == null
        val reduceMotion = Settings.Global.getFloat(contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        systemSplash.setKeepOnScreenCondition { !composeReady }
        systemSplash.setOnExitAnimationListener { provider ->
            provider.remove()
            nativeSplashGone = true
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            FacefitTheme {
                var startupVisible by rememberSaveable { mutableStateOf(freshLaunch && !reduceMotion) }
                val showStartup = freshLaunch && startupVisible
                // Observe native removal even when reduced motion skips the Compose overlay.
                // The OS restores post-splash system-bar flags during this handoff.
                val nativeGone = nativeSplashGone
                LaunchedEffect(showStartup, nativeGone) {
                    val style = if (showStartup) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    // Reassert after Android's splash/theme restoration on Samsung/Android 12+.
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !showStartup
                        isAppearanceLightNavigationBars = !showStartup
                        if (showStartup) hide(WindowInsetsCompat.Type.systemBars())
                        else show(WindowInsetsCompat.Type.systemBars())
                    }
                    withFrameNanos { }
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !showStartup
                        isAppearanceLightNavigationBars = !showStartup
                        if (showStartup) hide(WindowInsetsCompat.Type.systemBars())
                        else show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                var hasCameraPermission by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    hasCameraPermission = it
                }
                Box(Modifier.fillMaxSize().onGloballyPositioned { composeReady = true }) {
                    // The startup route checks the cached Firebase session; no camera or model warm-up blocks it.
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = if (showStartup) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                            else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
                    ) { innerPadding ->
                        FaceFitNavHost(
                            authManager = authManager,
                            notificationFrame = notificationFrame,
                            onNotificationHandled = { notificationFrame = null },
                            hasCameraPermission = hasCameraPermission,
                            onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(innerPadding),
                            showStartup = showStartup,
                            startupReady = nativeGone,
                            onStartupFinished = {
                                WindowCompat.getInsetsController(window, window.decorView).apply {
                                    isAppearanceLightStatusBars = true
                                    isAppearanceLightNavigationBars = true
                                    show(WindowInsetsCompat.Type.systemBars())
                                }
                                startupVisible = false
                            },
                        )
                    }
                }
            }
        }
    }
}
