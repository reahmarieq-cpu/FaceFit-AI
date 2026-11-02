package com.facefit.kiosk.facefit.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors

class CameraXCameraCaptureGateway(
    private val context: Context
) : CameraCaptureGateway {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewGeneration = 0

    override fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit,
        onUnavailable: (Throwable?) -> Unit
    ) {
        val generation = ++previewGeneration
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (generation == previewGeneration) {
                    previewView.doOnLayout {
                        if (generation == previewGeneration) {
                            try {
                                val provider = providerFuture.get()
                                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                val nextPreview = Preview.Builder().setTargetRotation(rotation).build()
                                val nextImageCapture = ImageCapture.Builder()
                                    .setTargetRotation(rotation)
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                val viewPort = requireNotNull(previewView.viewPort) {
                                    "Camera preview must have a visible size."
                                }
                                // Match the saved photo's crop to the full-screen preview.
                                val useCases = UseCaseGroup.Builder()
                                    .addUseCase(nextPreview)
                                    .addUseCase(nextImageCapture)
                                    .setViewPort(viewPort)
                                    .build()
                                provider.unbindAll()
                                nextPreview.setSurfaceProvider(previewView.surfaceProvider)
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, useCases)
                                cameraProvider = provider
                                preview = nextPreview
                                imageCapture = nextImageCapture
                                onReady()
                            } catch (error: Exception) {
                                onUnavailable(error)
                            }
                        }
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    override fun captureImage(
        outputFile: File,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Camera capture is not ready."))
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            captureExecutor,
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
            }
        )
    }

    private companion object {
        // Shared across Activity recreation; JPEG work must not block the UI thread.
        val captureExecutor = Executors.newSingleThreadExecutor()
    }

    override fun stopPreview() {
        previewGeneration++
        cameraProvider?.unbindAll()
        preview = null
        imageCapture = null
    }
}
