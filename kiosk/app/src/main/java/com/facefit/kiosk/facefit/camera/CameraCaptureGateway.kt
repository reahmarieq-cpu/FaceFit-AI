package com.facefit.kiosk.facefit.camera

interface CameraCaptureGateway {
    fun requestCameraReadiness(): CameraReadiness
}

sealed class CameraReadiness {
    data object Ready : CameraReadiness()
    data object PermissionDenied : CameraReadiness()
    data object Unavailable : CameraReadiness()
}

