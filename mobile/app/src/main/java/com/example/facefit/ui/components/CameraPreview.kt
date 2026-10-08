package com.example.facefit.ui.components

import android.util.Log
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
import com.example.facefit.logic.FaceAnalyzer
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onResult: (com.example.facefit.logic.FaceAnalyzer.FaceMetrics) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val onResultCallback by rememberUpdatedState(onResult)
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )
    DisposableEffect(lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var analyzer: FaceAnalyzer? = null
            cameraProviderFuture.addListener({
                if (disposed) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val faceAnalyzer = FaceAnalyzer(context) { result ->
                    ContextCompat.getMainExecutor(context).execute {
                        if (!disposed) onResultCallback(result)
                    }
                }
                analyzer = faceAnalyzer
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            executor,
                            faceAnalyzer
                        )
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    boundPreview = preview
                    boundAnalysis = imageAnalysis
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
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
