package com.stickerforge.app.model

import android.net.Uri
import java.util.UUID

data class PackStickerItem(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    val processedSticker: ProcessedSticker,
    val cropParams: CropParameters = CropParameters(),
    val isAnimated: Boolean = false,
    val name: String = "",
    val emojis: List<String> = emptyList(),
    val accessibilityText: String = ""
) {
    val hasValidEmojis: Boolean
        get() = emojis.isNotEmpty() && emojis.size <= 3

    val effectiveSearchKeywords: String
        get() = accessibilityText.ifBlank { name }.take(125)
}
