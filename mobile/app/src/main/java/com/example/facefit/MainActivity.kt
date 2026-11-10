package com.example.facefit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.navigation.FaceFitNavHost
import com.example.facefit.ui.theme.FacefitTheme

class MainActivity : ComponentActivity() {
    private val authManager by lazy { AuthManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            FacefitTheme {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasCameraPermission = isGranted
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FaceFitNavHost(
                        authManager = authManager,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
