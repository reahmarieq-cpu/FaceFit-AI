package com.example.facefit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.ui.components.TryOnCameraPreview
import com.example.facefit.ui.components.TryOnOverlay
import com.example.facefit.ui.components.Glasses3DOverlay
import com.example.facefit.ui.components.Glasses3DState
import com.example.facefit.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VirtualTryOnScreen(frame: SampleFrame, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var metrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var lastResultTime by remember { mutableLongStateOf(0L) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var adjust by rememberSaveable { mutableStateOf(false) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var heightOffset by rememberSaveable { mutableFloatStateOf(0f) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    val bitmap = remember(frame.tryOnAssetPath) {
        frame.tryOnAssetPath?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    val templeBitmap = remember(frame.templeAssetPath) {
        frame.templeAssetPath?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            if (SystemClock.elapsedRealtime() - lastResultTime > 1000) metrics = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission && (frame.has3DModel || bitmap != null)) {
            TryOnCameraPreview(onResult = {
                metrics = it
                lastResultTime = SystemClock.elapsedRealtime()
            }, onError = { cameraError = it; metrics = null })
            if (frame.has3DModel) {
                Glasses3DOverlay(browline = frame.category == "Browline",
                    state = Glasses3DState(if (cameraError == null) metrics else null, scale, heightOffset, headerHeight, footerHeight),
                    onError = { cameraError = it })
            } else if (bitmap != null) {
                TryOnOverlay(metrics = if (cameraError == null) metrics else null, front = bitmap,
                    temple = templeBitmap, anchors = frame.lensAnchors, scale = scale, heightOffset = heightOffset)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { headerHeight = it.height }
            .background(Color(0x99000000)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultIconButton(onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "Back") }
                Text(if (frame.has3DModel) "3D Virtual Try-On" else "Virtual Try-On", Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconToggleButton(checked = adjust, onCheckedChange = { adjust = it }) {
                    Icon(Icons.Outlined.Tune, "Adjust fit", tint = Color.White)
                }
            }
        }
        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)) { Text("Allow Camera") }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { footerHeight = it.height }
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color.White).padding(18.dp)) {
            Text(frame.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
            Text(if (frame.has3DModel || bitmap != null) {
                cameraError ?: if (!hasPermission) "Camera permission needed"
                else if (metrics?.isFaceDetected == true) "${frame.category} - ${frame.colorName}"
                else "Face the camera to try on your frame"
            } else "Frame image unavailable", fontSize = 13.sp, color = FaceFitTextSecondary)
            if (adjust) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fit", Modifier.weight(1f), color = FaceFitTextPrimary)
                    IconButton(onClick = { scale = 1f; heightOffset = 0f }) {
                        Icon(Icons.Outlined.Refresh, "Reset fit")
                    }
                }
                Text("Size", color = FaceFitTextSecondary, fontSize = 12.sp)
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.8f..1.3f)
                Text("Height", color = FaceFitTextSecondary, fontSize = 12.sp)
                Slider(value = heightOffset, onValueChange = { heightOffset = it }, valueRange = -0.25f..0.25f)
            }
        }
    }
}
