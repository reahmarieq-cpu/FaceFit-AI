package com.facefit.kiosk.vto.ui.tryon

import com.facefit.kiosk.vto.ui.catalog.VtoFrame
import com.facefit.kiosk.vto.ui.components.ResultIconButton

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew


import androidx.compose.material3.*
import androidx.compose.runtime.*

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
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.camera.TrackingBackend
import com.facefit.kiosk.vto.ui.tryon.tracking.VtoTrackingFeed
import com.facefit.kiosk.vto.ui.camera.TryOnCameraPreview
import com.facefit.kiosk.vto.ui.tryon.rendering.LayeredImageTryOnOverlay
import com.facefit.kiosk.vto.ui.tryon.rendering.TryOnOverlay
import com.facefit.kiosk.vto.ui.tryon.rendering.FilamentGlassesOverlay
import com.facefit.kiosk.vto.ui.tryon.rendering.Glasses3DOverlay
import com.facefit.kiosk.vto.ui.tryon.rendering.Glasses3DState
import com.facefit.kiosk.vto.ui.theme.*




@Composable
fun VirtualTryOnScreen(frame: VtoFrame, onBack: () -> Unit) {
    val context = LocalContext.current
    val glbPath = frame.assetPath?.takeIf { it.endsWith(".glb", ignoreCase = true) }
    val trackingFeed=remember(glbPath) { VtoTrackingFeed() }
    var faceDetected by remember(glbPath) { mutableStateOf(false) }
    var modelLoading by remember(glbPath) { mutableStateOf(glbPath != null) }
    var modelError by remember(glbPath) { mutableStateOf<String?>(null) }
    var backendNotice by remember { mutableStateOf<String?>(null) }
    var trackingStatus by remember { mutableStateOf<String?>("Starting face tracking...") }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var metrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    val bitmap = remember(frame.tryOnAssetPath) {
        frame.tryOnAssetPath?.takeUnless { it.endsWith(".glb", ignoreCase = true) }?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    val templeBitmap = remember(frame.templeAssetPath) {
        frame.templeAssetPath?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    val leftArm = remember(frame.leftArmAssetPath) {
        frame.leftArmAssetPath?.let { path -> runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    }
    val rightArm = remember(frame.rightArmAssetPath) {
        frame.rightArmAssetPath?.let { path -> runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission && (frame.has3DModel || bitmap != null)) {
            TryOnCameraPreview(onResult = {
                if(glbPath!=null) {
                    trackingFeed.publish(it)
                    faceDetected=it.isFaceDetected
                } else metrics=it
            }, onError = { cameraError = it; metrics = null; trackingFeed.publish(null); faceDetected=false },
                backend = TrackingBackend.GPU,
                onBackendFallback = { backendNotice = it },
                onTrackingStatus = { trackingStatus = it })
            if (glbPath != null) {
                FilamentGlassesOverlay(
                    assetPath = glbPath,
                    state = Glasses3DState(if (cameraError == null) metrics else null, 1f, 0f,
                        headerHeight, footerHeight),
                    trackingFeed=if(cameraError==null) trackingFeed else null,
                    onReady = { modelLoading = false },
                    onError = { modelError = it; modelLoading = false }
                )
            } else if (frame.has3DModel) {
                Glasses3DOverlay(
                    browline = frame.category == "Browline",
                    assetPath = frame.assetPath,
                    state = Glasses3DState(if (cameraError == null) metrics else null, 1f, 0f, headerHeight, footerHeight),
                    onError = { cameraError = it }
                )
            } else if (bitmap != null && leftArm != null && rightArm != null &&
                frame.leftArmAnchors != null && frame.rightArmAnchors != null) {
                LayeredImageTryOnOverlay(if(cameraError==null) metrics else null,bitmap,leftArm,rightArm,
                    frame.lensAnchors,frame.leftArmAnchors,frame.rightArmAnchors)
            } else if (bitmap != null) {
                TryOnOverlay(metrics = if (cameraError == null) metrics else null, front = bitmap,
                    temple = templeBitmap, anchors = frame.lensAnchors, scale = 1f, heightOffset = 0f)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { headerHeight = it.height }
            .background(Color(0x99000000)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultIconButton(onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "Back") }
                Text(if (frame.has3DModel) "3D Virtual Try-On" else "Virtual Try-On", Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)) { Text("Allow Camera") }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { footerHeight = it.height }
            .softContainerShadow(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color.White).padding(18.dp)) {
            Text(frame.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
            Text(if (frame.has3DModel || bitmap != null) {
                cameraError ?: modelError ?: if (modelLoading && hasPermission) "Loading glasses..." else if (!hasPermission) "Camera permission needed"
                else if (trackingStatus != null) trackingStatus!!
                else if (if(glbPath!=null) faceDetected else metrics?.isFaceDetected == true) "${frame.category} - ${frame.colorName}"
                else "Face the camera to try on your frame"
            } else "Frame image unavailable", fontSize = 13.sp, color = FaceFitTextSecondary)
            backendNotice?.let { notice ->
                Text(notice, fontSize = 11.sp, color = FaceFitTextSecondary)
            }
        }
    }
}
