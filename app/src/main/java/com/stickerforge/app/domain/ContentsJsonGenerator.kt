package com.stickerforge.app.domain

import com.stickerforge.app.model.StickerPack
import java.io.File
import java.io.FileOutputStream

object ContentsJsonGenerator {

    /**
     * Serializa un StickerPack en el formato JSON estricto requerido por WhatsApp Stickers.
     */
    fun generateContentsJson(pack: StickerPack): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"android_play_store_link\": \"\",\n")
        sb.append("  \"ios_app_store_link\": \"\",\n")
        sb.append("  \"sticker_packs\": [\n")
        sb.append("    {\n")
        sb.append("      \"identifier\": \"${escapeJson(pack.identifier)}\",\n")
        sb.append("      \"name\": \"${escapeJson(pack.name)}\",\n")
        sb.append("      \"publisher\": \"${escapeJson(pack.publisher)}\",\n")
        val trayName = pack.trayImageFile?.name ?: "tray_${pack.identifier}.png"
        sb.append("      \"tray_image_file\": \"${escapeJson(trayName)}\",\n")
        sb.append("      \"publisher_email\": \"${escapeJson(pack.publisherEmail)}\",\n")
        sb.append("      \"publisher_website\": \"${escapeJson(pack.publisherWebsite)}\",\n")
        sb.append("      \"privacy_policy_website\": \"${escapeJson(pack.privacyPolicyWebsite)}\",\n")
        sb.append("      \"license_agreement_website\": \"${escapeJson(pack.licenseAgreementWebsite)}\",\n")
        sb.append("      \"animated_sticker_pack\": ${pack.isAnimated},\n")
        sb.append("      \"stickers\": [\n")

        pack.stickers.forEachIndexed { index, stickerItem ->
            val fileName = stickerItem.processedSticker.file.name
            val emojisList = stickerItem.emojis.take(3)
            val emojisJson = emojisList.joinToString(separator = ", ") { "\"${escapeJson(it)}\"" }
            val accessibilityText = stickerItem.effectiveSearchKeywords

            sb.append("        {\n")
            sb.append("          \"image_file\": \"${escapeJson(fileName)}\",\n")
            sb.append("          \"emojis\": [$emojisJson],\n")
            sb.append("          \"accessibility_text\": \"${escapeJson(accessibilityText)}\"\n")
            if (index < pack.stickers.size - 1) {
                sb.append("        },\n")
            } else {
                sb.append("        }\n")
            }
        }

        sb.append("      ]\n")
        sb.append("    }\n")
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Guarda el archivo contents.json generado en el directorio de destino.
     */
    fun saveContentsJson(directory: File, jsonContent: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "contents.json")
        FileOutputStream(file).use { fos ->
            fos.write(jsonContent.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
        return file
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
