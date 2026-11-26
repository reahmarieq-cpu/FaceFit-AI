package com.facefit.kiosk.ui.scan.mobile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.facefit.kiosk.R
import com.facefit.kiosk.facefit.analysis.KioskAnalysisOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun KioskMobileScanEntry(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    analyzeCapture: (FaceAnalyzer.FaceMetrics) -> KioskAnalysisOutcome,
    onComplete: (KioskAnalysisOutcome) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(hasCamera()) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionRequest.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraGranted = hasCamera()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (cameraGranted) {
            MobileScanScreen(
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                onComplete = { capture ->
                    scope.launch {
                        val result = withContext(Dispatchers.Default) { analyzeCapture(capture) }
                        onComplete(result)
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.scan_tip_permission_denied),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_request_permission_button))
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.scan_open_settings_button), color = Color.White)
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(android.R.string.cancel), color = Color.White)
                }
            }
        }
    }
}
