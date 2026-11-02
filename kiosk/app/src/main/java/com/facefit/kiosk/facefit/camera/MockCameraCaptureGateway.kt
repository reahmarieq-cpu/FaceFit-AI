package com.facefit.kiosk.facefit.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File

class MockCameraCaptureGateway(
    private val readiness: CameraReadiness = CameraReadiness.Ready
) : CameraCaptureGateway {

    override fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit,
        onUnavailable: (Throwable?) -> Unit
    ) {
        when (readiness) {
            CameraReadiness.Ready -> onReady()
            CameraReadiness.Unavailable -> onUnavailable(null)
        }
    }

    override fun captureImage(
        outputFile: File,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        onError(IllegalStateException("Mock camera capture is not available."))
    }

    override fun stopPreview() = Unit
}

sealed class CameraReadiness {
    data object Ready : CameraReadiness()
    data object Unavailable : CameraReadiness()
}
