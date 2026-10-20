package com.facefit.kiosk.facefit.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File

interface CameraCaptureGateway {
    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit,
        onUnavailable: (Throwable?) -> Unit
    )

    fun captureImage(
        outputFile: File,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    )

    fun stopPreview()
}
