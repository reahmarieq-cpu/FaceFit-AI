package com.facefit.kiosk.facefit.camera

class MockCameraCaptureGateway(
    private val readiness: CameraReadiness = CameraReadiness.Ready
) : CameraCaptureGateway {

    override fun requestCameraReadiness(): CameraReadiness = readiness
}

