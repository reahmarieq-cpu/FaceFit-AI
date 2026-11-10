package com.facefit.kiosk.model

data class FrameItem(
    val id: String,
    val name: String,
    val shape: String,
    val color: String,
    val material: String,
    val isAvailable: Boolean,
    val tryOn: com.facefit.kiosk.vto.ui.catalog.VtoFrame? = null,
    val imageAssetPath: String? = null
)

