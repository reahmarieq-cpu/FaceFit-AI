package com.facefit.kiosk.facefit.camera

import android.content.Context
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import java.io.File
import java.util.concurrent.Executors

class CameraXCameraCaptureGateway(private val context: Context) : CameraCaptureGateway {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null
    private var analysisWorker: java.util.concurrent.ExecutorService? = null
    private var releaseAnalyzer: (() -> Unit)? = null
    @Volatile private var previewGeneration = 0

    override fun startPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView,
        onReady: () -> Unit, onUnavailable: (Throwable?) -> Unit,
        onAnalysis: ((FaceAnalyzer.FaceMetrics) -> Unit)?) {
        stopPreview()
        val generation = ++previewGeneration
        val main = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (generation != previewGeneration) return@addListener
            previewView.doOnLayout {
                if (generation == previewGeneration) try {
                    val provider = providerFuture.get()
                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    val nextPreview = Preview.Builder().setTargetRotation(rotation).build()
                    val nextCapture = ImageCapture.Builder().setTargetRotation(rotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    val group = UseCaseGroup.Builder().addUseCase(nextPreview).addUseCase(nextCapture)
                        .setViewPort(requireNotNull(previewView.viewPort))
                    if (onAnalysis != null) {
                        val worker = Executors.newSingleThreadExecutor()
                        analysisWorker = worker
                        var detector: FaceAnalyzer? = null // confined to this worker
                        releaseAnalyzer = { detector?.close() }
                        val nextAnalysis = ImageAnalysis.Builder().setTargetRotation(rotation)
                            .setTargetResolution(Size(640, 480))
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        nextAnalysis.setAnalyzer(worker) { image ->
                            if (generation != previewGeneration) image.close() else try {
                                val active = detector ?: FaceAnalyzer(context.applicationContext,
                                    reuseRgbaBuffers = true, maxFaces = 2, scanQualityChecks = true) { metrics ->
                                    main.execute { if (generation == previewGeneration) onAnalysis(metrics) }
                                }.also { detector = it }
                                active.analyze(image)
                            } catch (error: Exception) {
                                image.close()
                                main.execute {
                                    if (generation == previewGeneration) { stopPreview(); onUnavailable(error) }
                                }
                            }
                        }
                        analysis = nextAnalysis
                        group.addUseCase(nextAnalysis)
                    }
                    cameraProvider = provider
                    preview = nextPreview
                    imageCapture = nextCapture
                    nextPreview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, group.build())
                    onReady()
                } catch (error: Exception) {
                    stopPreview()
                    onUnavailable(error)
                }
            }
        }, main)
    }

    override fun captureImage(outputFile: File, onSuccess: (File) -> Unit, onError: (Throwable) -> Unit) {
        val capture = imageCapture ?: return onError(IllegalStateException("Camera capture is not ready."))
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(outputFile).build(), captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        FrontCameraImageNormalizer.normalize(outputFile)
                        ContextCompat.getMainExecutor(context).execute { onSuccess(outputFile) }
                    } catch (error: Exception) {
                        outputFile.delete()
                        ContextCompat.getMainExecutor(context).execute { onError(error) }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute { onError(exception) }
                }
            })
    }

    override fun stopPreview() {
        previewGeneration++
        analysis?.clearAnalyzer()
        val bound = listOfNotNull(preview, imageCapture, analysis)
        if (bound.isNotEmpty()) cameraProvider?.unbind(*bound.toTypedArray())
        preview = null; imageCapture = null; analysis = null
        val close = releaseAnalyzer
        analysisWorker?.let { worker -> if (close != null) worker.execute { close() }; worker.shutdown() }
        releaseAnalyzer = null; analysisWorker = null
    }

    private companion object { val captureExecutor = Executors.newSingleThreadExecutor() }
}
