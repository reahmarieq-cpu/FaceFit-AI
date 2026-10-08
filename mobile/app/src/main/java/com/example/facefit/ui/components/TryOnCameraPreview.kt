package com.example.facefit.ui.components

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.facefit.logic.FaceAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot

@Composable
fun TryOnCameraPreview(
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(previewView, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        var analyzer: FaceAnalyzer? = null
        var sourceTransform: OutputTransform? = null
        val latestResult = AtomicLong(0L)
        val transformFactory = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
            isUsingCropRect = false
        }
        val bindCamera = Runnable {
            // Both layout and provider readiness can trigger this callback on startup.
            if (!disposed && preview == null && previewView.width > 0 && previewView.height > 0) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    val cameraPreview = Preview.Builder()
                        .setTargetRotation(previewView.display.rotation).build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    preview = cameraPreview
                    val cameraAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build())
                        .setTargetRotation(previewView.display.rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis = cameraAnalysis
                    val faceAnalyzer = FaceAnalyzer(context, videoTracking = true) { result ->
                        val source = sourceTransform
                        val resultId = latestResult.incrementAndGet()
                        mainExecutor.execute {
                            if (!disposed && resultId == latestResult.get()) {
                                val target = previewView.outputTransform
                                if (result.isFaceDetected && source != null && target != null) {
                                    // MediaPipe points are in the rotated bitmap; CameraX supplies crop and mirror mapping.
                                    val points = FloatArray(result.landmarks.size * 2)
                                    result.landmarks.forEachIndexed { index, point ->
                                        points[index * 2] = point.x * result.imageWidth
                                        points[index * 2 + 1] = point.y * result.imageHeight
                                    }
                                    val transform = CoordinateTransform(source, target)
                                    transform.mapPoints(points)
                                    val basis = floatArrayOf(0f, 0f, 1f, 0f)
                                    transform.mapPoints(basis)
                                    val depthScale = hypot(basis[2] - basis[0], basis[3] - basis[1]) * result.imageWidth / previewView.width
                                    resultCallback(result.copy(
                                        landmarks = result.landmarks.indices.map { index ->
                                            FaceAnalyzer.LandmarkPoint(points[index * 2] / previewView.width,
                                                points[index * 2 + 1] / previewView.height,
                                                result.landmarks[index].z * depthScale)
                                        },
                                        imageWidth = previewView.width,
                                        imageHeight = previewView.height
                                    ))
                                } else {
                                    resultCallback(result.copy(isFaceDetected = false, landmarks = emptyList()))
                                }
                            }
                        }
                    }
                    analyzer = faceAnalyzer
                    cameraAnalysis.setAnalyzer(executor) { image ->
                        try {
                            sourceTransform = transformFactory.getOutputTransform(image)
                            faceAnalyzer.analyze(image)
                        } catch (error: Exception) {
                            Log.e("TryOnCamera", "Face tracking failed", error)
                            mainExecutor.execute { if (!disposed) errorCallback("Face tracking failed. Reopen try-on to retry.") }
                        }
                    }
                    val useCases = UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(cameraAnalysis)
                    previewView.viewPort?.let { useCases.setViewPort(it) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, useCases.build())
                } catch (error: Exception) {
                    Log.e("TryOnCamera", "Camera setup failed", error)
                    errorCallback("Camera could not start. Go back and reopen try-on.")
                }
            }
        }
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (preview == null && providerFuture.isDone) bindCamera.run()
        }
        previewView.addOnLayoutChangeListener(layoutListener)
        providerFuture.addListener(bindCamera, mainExecutor)
        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layoutListener)
            analysis?.clearAnalyzer()
            preview?.let { provider?.unbind(it) }
            analysis?.let { provider?.unbind(it) }
            executor.execute { analyzer?.close() }
            executor.shutdown()
        }
    }
}
