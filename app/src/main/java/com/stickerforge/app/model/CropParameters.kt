package com.stickerforge.app.model

data class CropParameters(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val viewportSizePx: Float = 512f
)
