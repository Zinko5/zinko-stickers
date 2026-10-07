package com.stickerforge.app.model

import android.net.Uri
import java.io.File

data class ProcessedSticker(
    val file: File,
    val uri: Uri,
    val width: Int = 512,
    val height: Int = 512,
    val sizeBytes: Long,
    val isUnder100KB: Boolean = sizeBytes <= 100 * 1024
) {
    val formattedSize: String
        get() = String.format("%.1f KB", sizeBytes / 1024.0)
}
