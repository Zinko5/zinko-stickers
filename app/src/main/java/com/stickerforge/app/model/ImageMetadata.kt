package com.stickerforge.app.model

import android.net.Uri

data class ImageMetadata(
    val uri: Uri,
    val fileName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val isAnimated: Boolean = false
) {
    val isSquare: Boolean
        get() = width > 0 && width == height

    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }

    val isLargerThan10MB: Boolean
        get() = sizeBytes > 10 * 1024 * 1024
}
