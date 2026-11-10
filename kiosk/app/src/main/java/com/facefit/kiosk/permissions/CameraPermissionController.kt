package com.facefit.kiosk.permissions

interface CameraPermissionController {
    fun isCameraPermissionGranted(): Boolean
    fun shouldShowCameraPermissionRationale(): Boolean
    fun requestCameraPermission(onResult: (Boolean) -> Unit)
    fun openAppSettings()
}
