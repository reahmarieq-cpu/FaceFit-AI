package com.facefit.kiosk.facefit.tryon

import com.facefit.kiosk.model.FrameItem

interface VirtualTryOnGateway {
    fun preparePreview(frame: FrameItem): TryOnPreviewState
}

sealed class TryOnPreviewState {
    data object Ready : TryOnPreviewState()
    data object CameraUnavailable : TryOnPreviewState()
    data object Failed : TryOnPreviewState()
}

