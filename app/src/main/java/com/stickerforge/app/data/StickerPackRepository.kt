package com.stickerforge.app.data

import android.content.Context
import android.net.Uri
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import com.stickerforge.app.provider.StickerPackStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class SavedPacksState(
    val packs: List<StickerPack>,
    val activePackId: String?
)

class StickerPackRepository(
    private val filesDir: File,
    private val dbFileName: String = DATABASE_FILE_NAME,
    private val context: Context? = null
) {
    constructor(context: Context, dbFileName: String = DATABASE_FILE_NAME) : this(
        filesDir = context.filesDir,
        dbFileName = dbFileName,
        context = context
    )

    private val dbFile: File
        get() = File(filesDir, dbFileName)

    var uriParser: (String) -> Uri = { uriStr ->
        try {
            Uri.parse(uriStr) ?: Uri.EMPTY
        } catch (e: Throwable) {
            Uri.EMPTY
        }
    }

    /**
     * Guarda la coleccion de paquetes y el id del paquete activo en almacenamiento persistente.
     * Utiliza escritura atomica mediante archivo temporal para evitar corrupcion de datos.
     */
    @Synchronized
    fun savePacks(packs: List<StickerPack>, activePackId: String? = null): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("active_pack_id", activePackId ?: "")

            val packsArray = JSONArray()
            for (pack in packs) {
                val packObj = JSONObject()
                packObj.put("id", pack.id)
                packObj.put("identifier", pack.identifier)
                packObj.put("name", pack.name)
                packObj.put("publisher", pack.publisher)
                packObj.put("publisher_email", pack.publisherEmail)
                packObj.put("publisher_website", pack.publisherWebsite)
                packObj.put("privacy_policy_website", pack.privacyPolicyWebsite)
                packObj.put("license_agreement_website", pack.licenseAgreementWebsite)
                packObj.put("is_animated", pack.isAnimated)
                packObj.put("is_exported_to_whatsapp", pack.isExportedToWhatsApp)
                packObj.put("tray_image_file", pack.trayImageFile?.absolutePath ?: "")

                val stickersArray = JSONArray()
                for (sticker in pack.stickers) {
                    val sObj = JSONObject()
                    sObj.put("id", sticker.id)
                    sObj.put("source_uri", sticker.sourceUri.toString())
                    sObj.put("file_path", sticker.processedSticker.file.absolutePath)
                    sObj.put("content_uri", sticker.processedSticker.uri.toString())
                    sObj.put("width", sticker.processedSticker.width)
                    sObj.put("height", sticker.processedSticker.height)
                    sObj.put("size_bytes", sticker.processedSticker.sizeBytes)
                    sObj.put("is_animated", sticker.isAnimated)
                    sObj.put("name", sticker.name)
                    sObj.put("accessibility_text", sticker.accessibilityText)

                    val emojisArray = JSONArray()
                    for (emoji in sticker.emojis) {
                        emojisArray.put(emoji)
                    }
                    sObj.put("emojis", emojisArray)

                    // Parametros de recorte
                    val cropObj = JSONObject()
                    cropObj.put("scale", sticker.cropParams.scale.toDouble())
                    cropObj.put("offset_x", sticker.cropParams.offsetX.toDouble())
                    cropObj.put("offset_y", sticker.cropParams.offsetY.toDouble())
                    cropObj.put("rotation", sticker.cropParams.rotationDegrees.toDouble())
                    cropObj.put("viewport_size", sticker.cropParams.viewportSizePx.toDouble())
                    sObj.put("crop_params", cropObj)

                    stickersArray.put(sObj)
                }
                packObj.put("stickers", stickersArray)

                packsArray.put(packObj)
            }
            root.put("packs", packsArray)

            // Escritura atomica
            val tempFile = File(filesDir, "${dbFileName}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            if (tempFile.renameTo(dbFile)) {
                true
            } else {
                tempFile.copyTo(dbFile, overwrite = true)
                tempFile.delete()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Carga el estado guardado de paquetes.
     * Si no existe el archivo principal de base de datos, intenta migrar/importar paquetes existentes
     * guardados previamente en el directorio de exportacion (StickerPackStorage).
     */
    @Synchronized
    fun loadPacks(): SavedPacksState? {
        if (!dbFile.exists()) {
            return tryImportExistingPacks()
        }

        return try {
            val jsonText = dbFile.readText(Charsets.UTF_8)
            val root = JSONObject(jsonText)
            val activePackId = root.optString("active_pack_id", "").ifEmpty { null }
            val packsArray = root.optJSONArray("packs") ?: JSONArray()

            val loadedPacks = mutableListOf<StickerPack>()
            for (i in 0 until packsArray.length()) {
                val pObj = packsArray.getJSONObject(i)
                val id = pObj.getString("id")
                val identifier = pObj.optString("identifier", StickerPack.sanitizeIdentifier(id))
                val name = pObj.getString("name")
                val publisher = pObj.optString("publisher", "Zinko Stickers")
                val publisherEmail = pObj.optString("publisher_email", "")
                val publisherWebsite = pObj.optString("publisher_website", "")
                val privacyPolicy = pObj.optString("privacy_policy_website", "")
                val licenseAgreement = pObj.optString("license_agreement_website", "")
                val isAnimated = pObj.optBoolean("is_animated", false)
                val isExported = pObj.optBoolean("is_exported_to_whatsapp", false)
                val trayPath = pObj.optString("tray_image_file", "")
                val trayFile = if (trayPath.isNotBlank()) File(trayPath).takeIf { it.exists() } else null

                val stickersArray = pObj.optJSONArray("stickers") ?: JSONArray()
                val stickersList = mutableListOf<PackStickerItem>()
                for (j in 0 until stickersArray.length()) {
                    val sObj = stickersArray.getJSONObject(j)
                    val stickerId = sObj.getString("id")
                    val sourceUriStr = sObj.optString("source_uri", "")
                    val filePath = sObj.getString("file_path")
                    val stickerFile = File(filePath)

                    // Solo conservar stickers cuyo archivo procesado siga existiendo fisicamente
                    if (stickerFile.exists()) {
                        val contentUriStr = sObj.optString("content_uri", "")
                        val effectiveContentStr = contentUriStr.ifEmpty { "file://${stickerFile.absolutePath}" }
                        val uri = uriParser(effectiveContentStr)

                        val effectiveSourceStr = sourceUriStr.ifEmpty { effectiveContentStr }
                        val sourceUri = uriParser(effectiveSourceStr)
                        val width = sObj.optInt("width", 512)
                        val height = sObj.optInt("height", 512)
                        val sizeBytes = sObj.optLong("size_bytes", stickerFile.length())
                        val sIsAnimated = sObj.optBoolean("is_animated", isAnimated)
                        val sName = sObj.optString("name", "")
                        val accessibilityText = sObj.optString("accessibility_text", "")

                        val emojisArray = sObj.optJSONArray("emojis") ?: JSONArray()
                        val emojis = mutableListOf<String>()
                        for (k in 0 until emojisArray.length()) {
                            emojis.add(emojisArray.getString(k))
                        }

                        val cropObj = sObj.optJSONObject("crop_params")
                        val cropParams = if (cropObj != null) {
                            CropParameters(
                                scale = cropObj.optDouble("scale", 1.0).toFloat(),
                                offsetX = cropObj.optDouble("offset_x", 0.0).toFloat(),
                                offsetY = cropObj.optDouble("offset_y", 0.0).toFloat(),
                                rotationDegrees = cropObj.optDouble("rotation", 0.0).toFloat(),
                                viewportSizePx = cropObj.optDouble("viewport_size", 512.0).toFloat()
                            )
                        } else {
                            CropParameters()
                        }

                        stickersList.add(
                            PackStickerItem(
                                id = stickerId,
                                sourceUri = sourceUri,
                                processedSticker = ProcessedSticker(
                                    file = stickerFile,
                                    uri = uri,
                                    width = width,
                                    height = height,
                                    sizeBytes = sizeBytes
                                ),
                                cropParams = cropParams,
                                isAnimated = sIsAnimated,
                                name = sName,
                                emojis = emojis,
                                accessibilityText = accessibilityText
                            )
                        )
                    }
                }

                loadedPacks.add(
                    StickerPack(
                        id = id,
                        identifier = identifier,
                        name = name,
                        publisher = publisher,
                        publisherEmail = publisherEmail,
                        publisherWebsite = publisherWebsite,
                        privacyPolicyWebsite = privacyPolicy,
                        licenseAgreementWebsite = licenseAgreement,
                        isAnimated = isAnimated,
                        isExportedToWhatsApp = isExported,
                        trayImageFile = trayFile,
                        stickers = stickersList
                    )
                )
            }

            if (loadedPacks.isNotEmpty()) {
                SavedPacksState(packs = loadedPacks, activePackId = activePackId)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Intenta recuperar paquetes previamente guardados o exportados mediante StickerPackStorage.
     */
    private fun tryImportExistingPacks(): SavedPacksState? {
        val ctx = context ?: return null
        return try {
            val existing = StickerPackStorage.getAllPacks(ctx)
            if (existing.isNotEmpty()) {
                savePacks(existing, existing.first().id)
                SavedPacksState(packs = existing, activePackId = existing.first().id)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val DATABASE_FILE_NAME = "packs_metadata.json"
    }
}
