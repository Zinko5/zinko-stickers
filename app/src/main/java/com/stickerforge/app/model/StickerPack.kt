package com.stickerforge.app.model

import java.io.File
import java.util.UUID

data class StickerPack(
    val id: String = UUID.randomUUID().toString(),
    val identifier: String = sanitizeIdentifier(id),
    val name: String = "Mi Pack de Stickers",
    val publisher: String = "Zinko Stickers",
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val isAnimated: Boolean = false,
    val trayImageFile: File? = null,
    val stickers: List<PackStickerItem> = emptyList()
) {
    val count: Int
        get() = stickers.size

    val canExportCount: Boolean
        get() = stickers.size in 3..30

    val remainingToMin: Int
        get() = (3 - stickers.size).coerceAtLeast(0)

    val isFull: Boolean
        get() = stickers.size >= 30

    val typeLabel: String
        get() = if (isAnimated) "Animado" else "Estatico"

    val hasValidIdentifier: Boolean
        get() = identifier.isNotBlank() && !identifier.contains(".")

    val allStickersHaveEmojis: Boolean
        get() = stickers.isNotEmpty() && stickers.all { it.hasValidEmojis }

    val stickersMissingEmojisCount: Int
        get() = stickers.count { !it.hasValidEmojis }

    val canExport: Boolean
        get() = canExportCount

    val isReadyForWhatsAppExport: Boolean
        get() = canExportCount && hasValidIdentifier && allStickersHaveEmojis && trayImageFile != null

    fun getValidationIssues(): List<String> {
        val issues = mutableListOf<String>()
        if (count < 3) {
            issues.add("Faltan stickers: WhatsApp requiere al menos 3 (actual: $count)")
        } else if (count > 30) {
            issues.add("Exceso de stickers: WhatsApp permite maximo 30 (actual: $count)")
        }
        if (!hasValidIdentifier) {
            issues.add("El identificador del paquete contiene puntos o caracteres no permitidos")
        }
        val missingEmojis = stickersMissingEmojisCount
        if (missingEmojis > 0) {
            issues.add("$missingEmojis sticker(s) no tienen entre 1 y 3 emojis asignados")
        }
        if (trayImageFile == null) {
            issues.add("Falta generar el icono de bandeja (tray icon de 96x96 px)")
        }
        return issues
    }

    companion object {
        fun sanitizeIdentifier(rawId: String): String {
            val clean = rawId.lowercase()
                .replace(".", "_")
                .replace("-", "_")
                .replace(" ", "_")
                .filter { it.isLetterOrDigit() || it == '_' }
            return if (clean.startsWith("zinko_pack_")) clean else "zinko_pack_${clean.take(16)}"
        }
    }
}
