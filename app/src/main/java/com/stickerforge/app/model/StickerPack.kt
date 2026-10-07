package com.stickerforge.app.model

import java.util.UUID

data class StickerPack(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Mi Pack de Stickers",
    val publisher: String = "Stickers Zinko",
    val isAnimated: Boolean = false,
    val stickers: List<PackStickerItem> = emptyList()
) {
    val count: Int
        get() = stickers.size

    val canExport: Boolean
        get() = stickers.size in 3..30

    val remainingToMin: Int
        get() = (3 - stickers.size).coerceAtLeast(0)

    val isFull: Boolean
        get() = stickers.size >= 30
}
