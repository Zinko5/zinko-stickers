package com.stickerforge.app.provider

import android.content.Context
import android.net.Uri
import com.stickerforge.app.domain.ContentsJsonGenerator
import com.stickerforge.app.domain.StickerProcessor
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object StickerPackStorage {
    private const val PACKS_DIR = "packs"

    // Registro en memoria compartido entre el ViewModel y el ContentProvider
    private val memoryRegistry = mutableMapOf<String, StickerPack>()

    fun registerInMemory(pack: StickerPack) {
        memoryRegistry[pack.identifier] = pack
    }

    /**
     * Sincroniza y guarda el paquete de stickers en el almacenamiento de la app (files/packs/<identifier>)
     * para que WhatsApp pueda leer contents.json, los stickers .webp y el tray icon .png.
     */
    fun savePackForExport(context: Context, pack: StickerPack): File {
        val baseDir = File(context.filesDir, PACKS_DIR).apply {
            if (!exists()) mkdirs()
        }
        val packDir = File(baseDir, pack.identifier).apply {
            if (!exists()) mkdirs()
        }

        // 1. Guardar contents.json
        val jsonContent = ContentsJsonGenerator.generateContentsJson(pack)
        ContentsJsonGenerator.saveContentsJson(packDir, jsonContent)

        // 2. Sincronizar icono de bandeja (tray icon)
        val trayFile = pack.trayImageFile ?: File(context.filesDir, "tray_icons/tray_${pack.identifier}.png")
        val destTrayFile = File(packDir, trayFile.name)
        if (trayFile.exists()) {
            if (!destTrayFile.exists() || destTrayFile.length() != trayFile.length()) {
                trayFile.copyTo(destTrayFile, overwrite = true)
            }
        } else {
            // Si no existe, copiar Pepe Smolder por defecto desde assets
            try {
                context.assets.open(StickerProcessor.DEFAULT_TRAY_ASSET_NAME).use { input ->
                    FileOutputStream(destTrayFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Sincronizar archivos de stickers WebP al directorio del paquete
        for (sticker in pack.stickers) {
            val srcFile = sticker.processedSticker.file
            val destFile = File(packDir, srcFile.name)
            if (srcFile.exists()) {
                if (!destFile.exists() || destFile.length() != srcFile.length()) {
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }
        }

        val updatedPack = pack.copy(trayImageFile = destTrayFile)
        registerInMemory(updatedPack)
        return packDir
    }

    /**
     * Obtiene todos los paquetes disponibles (combinando memoria y disco).
     */
    fun getAllPacks(context: Context): List<StickerPack> {
        val diskPacks = loadAllPacksFromDisk(context)
        val map = mutableMapOf<String, StickerPack>()
        for (pack in diskPacks) {
            map[pack.identifier] = pack
        }
        for ((id, pack) in memoryRegistry) {
            map[id] = pack
        }
        return map.values.toList()
    }

    /**
     * Obtiene un paquete especifico por su identificador.
     */
    fun getPack(context: Context, identifier: String): StickerPack? {
        val inMem = memoryRegistry[identifier]
        if (inMem != null) return inMem

        val packDir = File(context.filesDir, "$PACKS_DIR/$identifier")
        val contentsFile = File(packDir, "contents.json")
        if (contentsFile.exists()) {
            val loaded = parseContentsJson(contentsFile, packDir)
            if (loaded != null) {
                memoryRegistry[loaded.identifier] = loaded
                return loaded
            }
        }
        return null
    }

    private fun loadAllPacksFromDisk(context: Context): List<StickerPack> {
        val baseDir = File(context.filesDir, PACKS_DIR)
        if (!baseDir.exists()) return emptyList()

        val packs = mutableListOf<StickerPack>()
        baseDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val contentsFile = File(dir, "contents.json")
            if (contentsFile.exists()) {
                val pack = parseContentsJson(contentsFile, dir)
                if (pack != null) {
                    packs.add(pack)
                }
            }
        }
        return packs
    }

    /**
     * Parsea un archivo contents.json conformando un StickerPack completo.
     */
    fun parseContentsJson(contentsFile: File, packDir: File): StickerPack? {
        return try {
            val jsonText = contentsFile.readText()
            val root = JSONObject(jsonText)
            val packsArray = root.getJSONArray("sticker_packs")
            if (packsArray.length() == 0) return null

            val packObj = packsArray.getJSONObject(0)
            val identifier = packObj.getString("identifier")
            val name = packObj.getString("name")
            val publisher = packObj.getString("publisher")
            val trayFileName = packObj.optString("tray_image_file", "tray_$identifier.png")
            val isAnimated = packObj.optBoolean("animated_sticker_pack", false)
            val publisherEmail = packObj.optString("publisher_email", "")
            val publisherWebsite = packObj.optString("publisher_website", "")
            val privacyPolicy = packObj.optString("privacy_policy_website", "")
            val licenseAgreement = packObj.optString("license_agreement_website", "")

            val stickersArray = packObj.optJSONArray("stickers") ?: JSONArray()
            val stickersList = mutableListOf<PackStickerItem>()
            for (i in 0 until stickersArray.length()) {
                val sObj = stickersArray.getJSONObject(i)
                val imgFile = sObj.getString("image_file")
                val emojisJson = sObj.optJSONArray("emojis") ?: JSONArray()
                val emojis = mutableListOf<String>()
                for (j in 0 until emojisJson.length()) {
                    emojis.add(emojisJson.getString(j))
                }
                val accessibilityText = sObj.optString("accessibility_text", "")

                val stickerFile = File(packDir, imgFile)
                stickersList.add(
                    PackStickerItem(
                        id = imgFile,
                        sourceUri = Uri.fromFile(stickerFile),
                        processedSticker = ProcessedSticker(
                            file = stickerFile,
                            uri = Uri.fromFile(stickerFile),
                            sizeBytes = if (stickerFile.exists()) stickerFile.length() else 0L
                        ),
                        cropParams = CropParameters(),
                        name = accessibilityText,
                        emojis = emojis,
                        accessibilityText = accessibilityText,
                        isAnimated = isAnimated
                    )
                )
            }

            val trayFile = File(packDir, trayFileName)
            StickerPack(
                id = identifier,
                identifier = identifier,
                name = name,
                publisher = publisher,
                trayImageFile = trayFile,
                isAnimated = isAnimated,
                publisherEmail = publisherEmail,
                publisherWebsite = publisherWebsite,
                privacyPolicyWebsite = privacyPolicy,
                licenseAgreementWebsite = licenseAgreement,
                stickers = stickersList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
