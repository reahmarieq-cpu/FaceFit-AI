package com.example.facefit.ui.camera

import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.facefit.facefit.analysis.FaceAnalyzer
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onError: (String) -> Unit = {},
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val onResultCallback by rememberUpdatedState(onResult)
    val onErrorCallback by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )
    DisposableEffect(lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var analyzer: FaceAnalyzer? = null
        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider

                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder().setTargetRotation(rotation).build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                var frameFailed = false
                var receivedFrame = false
                var detectedFace = false
                val faceAnalyzer = FaceAnalyzer(context, reuseRgbaBuffers = true, maxFaces = 2,
                    scanQualityChecks = true) { result ->
                    if (!detectedFace && result.isFaceDetected) {
                        detectedFace = true
                        Log.d("CameraPreview", "Scan received its first face landmarks")
                    }
                    mainExecutor.execute {
                        if (!disposed) onResultCallback(result)
                    }
                }
                analyzer = faceAnalyzer
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor) { image ->
                    if (!receivedFrame) {
                        receivedFrame = true
                        Log.d("CameraPreview", "Scan received ${image.width}x${image.height} camera frames, rotation ${image.imageInfo.rotationDegrees}")
                    }
                    try {
                        faceAnalyzer.analyze(image)
                        frameFailed = false
                    } catch (error: Exception) {
                        if (!frameFailed) {
                            frameFailed = true
                            Log.e("CameraPreview", "Scan frame analysis failed", error)
                            mainExecutor.execute {
                                if (!disposed) onErrorCallback("Camera frame could not be analyzed. Tap refresh to retry.")
                            }
                        }
                    }
                }
                boundPreview = preview
                boundAnalysis = imageAnalysis
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (error: Exception) {
                Log.e("CameraPreview", "Scan camera setup failed", error)
                onErrorCallback("Front camera could not start. Tap refresh to retry.")
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            boundAnalysis?.clearAnalyzer()
            boundPreview?.let { provider?.unbind(it) }
            boundAnalysis?.let { provider?.unbind(it) }
            executor.execute { analyzer?.close() }
            executor.shutdown()
        }
    }
}
