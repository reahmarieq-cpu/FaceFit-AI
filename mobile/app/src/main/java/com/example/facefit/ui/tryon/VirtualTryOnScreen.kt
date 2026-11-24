package com.example.facefit.ui.tryon

import com.example.facefit.ui.catalog.SampleFrame
import com.example.facefit.ui.catalog.FrameDetailsContent
import com.example.facefit.ui.components.ResultIconButton

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ArrowBackIosNew


import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoPoseSmoother
import com.example.facefit.ui.tryon.tracking.VtoMotionFeed
import com.example.facefit.ui.tryon.tracking.VtoTrackingFeed
import com.example.facefit.ui.camera.TryOnCameraPreview
import com.example.facefit.ui.tryon.rendering.LayeredImageTryOnOverlay
import com.example.facefit.ui.tryon.rendering.TryOnOverlay
import com.example.facefit.ui.tryon.rendering.FilamentGlassesOverlay
import com.example.facefit.ui.tryon.rendering.Glasses3DOverlay
import com.example.facefit.ui.tryon.rendering.Glasses3DState
import com.example.facefit.ui.theme.*




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualTryOnScreen(
    frame: SampleFrame,
    onBack: () -> Unit,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    onFindNearbyShops: (SampleFrame) -> Unit,
    faceShape: String? = null,
) {
    val context = LocalContext.current
    val glbPath = frame.assetPath?.takeIf { it.endsWith(".glb", ignoreCase = true) }
    val motionFeed = remember(glbPath) { glbPath?.let { VtoMotionFeed() } }
    val overlaySmoother = remember(glbPath) { if (glbPath == null) VtoPoseSmoother() else null }
    val trackingFeed = remember(glbPath) { glbPath?.let { VtoTrackingFeed() } }
    var faceDetected by remember(glbPath) { mutableStateOf(false) }
    var modelLoading by remember(glbPath) { mutableStateOf(glbPath != null) }
    var modelError by remember(glbPath) { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var metrics by remember { mutableStateOf<FaceAnalyzer.FaceMetrics?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberBottomSheetScaffoldState()
    val detailsScroll = rememberScrollState()
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
    BottomSheetScaffold(
        scaffoldState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        sheetPeekHeight = 110.dp,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = FaceFitBackground,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 36.dp,
                height = 4.dp,
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFBFC8CC),
            )
        },
        sheetContent = {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)
                .verticalScroll(detailsScroll).navigationBarsPadding()
                .padding(horizontal = 24.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(frame.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                    Text(frame.category + " · " + frame.colorName,
                        fontSize = 13.sp, color = FaceFitTextSecondary)
                    val status = cameraError ?: modelError ?: when {
                        !hasPermission -> "Camera permission needed"
                        modelLoading -> "Loading glasses..."
                        !faceDetected && glbPath != null -> "Face the camera to try on your frame"
                        else -> null
                    }
                    status?.let { Text(it, fontSize = 12.sp, color = FaceFitTextSecondary) }
                }
                FrameDetailsContent(frame, faceShape = faceShape)
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onFavoriteChange(!isFavorite) },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6F61), contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.weight(.42f).heightIn(min = 52.dp),
                    ) {
                        Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFavorite) "Saved" else "Save", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Button(
                        onClick = { onFindNearbyShops(frame) },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White, contentColor = Color(0xFF0F766E)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.weight(.58f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Find nearby shops", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) {
        // The preview retains its full viewport while Material moves the sheet over it.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission && (frame.has3DModel || bitmap != null)) {
            TryOnCameraPreview(onResult = {
                cameraError = null
                metrics = if (overlaySmoother == null) it else overlaySmoother.sample(it, SystemClock.elapsedRealtimeNanos())
                faceDetected = it.isFaceDetected
            }, onError = {
                cameraError = it
                metrics = null
                faceDetected = false
                overlaySmoother?.reset()
                motionFeed?.clear()
                trackingFeed?.publish(null)
            },
                motionFeed = motionFeed,
                onTimedResult = { face, sample, delivery -> trackingFeed?.publish(face, sample, delivery) },
                maxInputDimension = 480)
            if (glbPath != null) {
                if (modelError == null) FilamentGlassesOverlay(
                    assetPath = glbPath,
                    motionFeed = motionFeed,
                    trackingFeed = trackingFeed,
                    state = Glasses3DState(if (cameraError == null) metrics else null, 1f, 0f,
                        0, 0),
                    onReady = { modelLoading = false },
                    onError = { modelError = it; modelLoading = false }
                )
            } else if (frame.has3DModel) {
                Glasses3DOverlay(
                    browline = frame.category == "Browline",
                    assetPath = frame.assetPath,
                    state = Glasses3DState(if (cameraError == null) metrics else null, 1f, 0f, 0, 0),
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
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding()
            .padding(start = 16.dp, top = 16.dp)) {
            ResultIconButton(onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = FaceFitTextPrimary)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding()
            .padding(end = 16.dp, top = 16.dp)) {
            ResultIconButton(onClick = { onFavoriteChange(!isFavorite) }) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Favorites" else "Save to Favorites",
                    tint = if (isFavorite) Color(0xFFFF6F61) else FaceFitTextPrimary,
                )
            }
        }
        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)) { Text("Allow Camera") }
        }
        }
    }
}
