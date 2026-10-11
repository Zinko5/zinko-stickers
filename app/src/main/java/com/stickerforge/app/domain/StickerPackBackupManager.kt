package com.stickerforge.app.domain

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class BackupExportResult {
    data class Success(
        val packsCount: Int,
        val stickersCount: Int,
        val bytesWritten: Long
    ) : BackupExportResult()

    data class Error(val message: String) : BackupExportResult()
}

sealed class BackupImportResult {
    data class Success(
        val importedPacks: List<StickerPack>,
        val packsCount: Int,
        val stickersCount: Int
    ) : BackupImportResult()

    data class Error(val message: String) : BackupImportResult()
}

object StickerPackBackupManager {

    const val MANIFEST_FILE_NAME = "manifest.json"
    const val FORMAT_VERSION = 1
    const val APP_IDENTIFIER = "Zinko Stickers"

    var fileUriProvider: (Context, File) -> Uri = { context, file ->
        try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    /**
     * Exporta los paquetes seleccionados a un stream ZIP.
     * Incluye un manifest.json con todos los metadatos y los archivos WebP y PNG correspondientes.
     */
    fun exportPacksToZip(
        packsToExport: List<StickerPack>,
        outputStream: OutputStream
    ): BackupExportResult {
        if (packsToExport.isEmpty()) {
            return BackupExportResult.Error("No se seleccionaron paquetes para exportar.")
        }

        return try {
            val countingStream = CountingOutputStream(outputStream)
            val zipOut = ZipOutputStream(BufferedOutputStream(countingStream))

            var totalStickers = 0

            // 1. Construir el manifiesto JSON con la metadata de todos los paquetes seleccionados
            val root = JSONObject()
            root.put("format_version", FORMAT_VERSION)
            root.put("app_name", APP_IDENTIFIER)
            val now = System.currentTimeMillis()
            root.put("export_timestamp", now)
            val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            root.put("export_date", isoDateFormat.format(Date(now)))
            root.put("packs_count", packsToExport.size)

            val packsArray = JSONArray()

            // Lista para registrar los archivos a escribir en el archivo comprimido
            data class FileZipEntry(val zipPath: String, val sourceFile: File)
            val filesToWrite = mutableListOf<FileZipEntry>()

            for (pack in packsToExport) {
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

                // Tray icon relativo dentro del ZIP
                var trayRelativePath = ""
                val trayFile = pack.trayImageFile
                if (trayFile != null && trayFile.exists()) {
                    trayRelativePath = "packs/${pack.identifier}/tray_${pack.identifier}.png"
                    filesToWrite.add(FileZipEntry(trayRelativePath, trayFile))
                }
                packObj.put("tray_image_file", trayRelativePath)

                // Stickers
                val stickersArray = JSONArray()
                for (sticker in pack.stickers) {
                    val sFile = sticker.processedSticker.file
                    if (sFile.exists()) {
                        totalStickers++
                        val sObj = JSONObject()
                        val relativeStickerPath = "packs/${pack.identifier}/stickers/${sFile.name}"
                        filesToWrite.add(FileZipEntry(relativeStickerPath, sFile))

                        sObj.put("id", sticker.id)
                        sObj.put("file_name", relativeStickerPath)
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
                }
                packObj.put("stickers", stickersArray)
                packsArray.put(packObj)
            }
            root.put("packs", packsArray)

            // 2. Escribir manifest.json como primera entrada del ZIP
            val manifestBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            zipOut.putNextEntry(ZipEntry(MANIFEST_FILE_NAME))
            zipOut.write(manifestBytes)
            zipOut.closeEntry()

            // 3. Escribir todos los archivos multimedia (WebP y tray icons)
            val buffer = ByteArray(8192)
            for (item in filesToWrite) {
                zipOut.putNextEntry(ZipEntry(item.zipPath))
                FileInputStream(item.sourceFile).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                    }
                }
                zipOut.closeEntry()
            }

            zipOut.flush()
            zipOut.close()

            BackupExportResult.Success(
                packsCount = packsToExport.size,
                stickersCount = totalStickers,
                bytesWritten = countingStream.bytesCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BackupExportResult.Error("Error al exportar paquetes a ZIP: ${e.message ?: "Desconocido"}")
        }
    }

    /**
     * Importa paquetes de stickers desde un stream ZIP.
     * Descomprime de forma segura los archivos hacia el almacenamiento interno de la app
     * y reconstruye los paquetes con toda su informacion.
     */
    fun importPacksFromZip(
        context: Context,
        inputStream: InputStream,
        existingIdentifiers: Set<String> = emptySet()
    ): BackupImportResult {
        val tempImportDir = File(context.cacheDir, "import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        if (!tempImportDir.mkdirs()) {
            return BackupImportResult.Error("No se pudo crear el directorio temporal para descompresion.")
        }

        return try {
            // 1. Descomprimir el archivo ZIP con validacion de seguridad contra Zip Slip
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            var entry: ZipEntry?
            val buffer = ByteArray(8192)

            while (zipIn.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name.replace("\\", "/")

                // Prevencion de Path Traversal (Zip Slip)
                if (entryName.contains("..") || entryName.startsWith("/")) {
                    zipIn.closeEntry()
                    continue
                }

                val destinationFile = File(tempImportDir, entryName)

                // Validacion canonica de ruta
                if (!destinationFile.canonicalPath.startsWith(tempImportDir.canonicalPath)) {
                    zipIn.closeEntry()
                    continue
                }

                if (entry!!.isDirectory) {
                    destinationFile.mkdirs()
                } else {
                    destinationFile.parentFile?.mkdirs()
                    FileOutputStream(destinationFile).use { fos ->
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                    }
                }
                zipIn.closeEntry()
            }
            zipIn.close()

            // 2. Localizar y validar manifest.json
            val manifestFile = File(tempImportDir, MANIFEST_FILE_NAME)
            if (!manifestFile.exists()) {
                return BackupImportResult.Error("El archivo seleccionado no es un respaldo valido de Zinko Stickers (falta manifest.json).")
            }

            val manifestContent = manifestFile.readText(Charsets.UTF_8)
            val root = JSONObject(manifestContent)
            val packsArray = root.optJSONArray("packs")
                ?: return BackupImportResult.Error("El archivo manifest.json no contiene una lista de paquetes valida.")

            val targetStickersDir = File(context.filesDir, "stickers").apply { if (!exists()) mkdirs() }
            val targetTrayDir = File(context.filesDir, "tray_icons").apply { if (!exists()) mkdirs() }

            val importedPacks = mutableListOf<StickerPack>()
            var totalImportedStickers = 0

            // 3. Procesar e importar cada paquete
            for (i in 0 until packsArray.length()) {
                val packObj = packsArray.getJSONObject(i)
                val rawId = packObj.optString("id", UUID.randomUUID().toString())
                var identifier = packObj.optString("identifier", StickerPack.sanitizeIdentifier(rawId))
                val name = packObj.optString("name", "Pack Importado")
                val publisher = packObj.optString("publisher", "Zinko Stickers")
                val publisherEmail = packObj.optString("publisher_email", "")
                val publisherWebsite = packObj.optString("publisher_website", "")
                val privacyPolicy = packObj.optString("privacy_policy_website", "")
                val licenseAgreement = packObj.optString("license_agreement_website", "")
                val isAnimated = packObj.optBoolean("is_animated", false)

                // Evitar colision con paquetes existentes
                if (existingIdentifiers.contains(identifier) || importedPacks.any { it.identifier == identifier }) {
                    identifier = "${identifier}_${UUID.randomUUID().toString().take(4)}"
                }

                // Restaurar tray icon
                val trayRelativePath = packObj.optString("tray_image_file", "")
                var importedTrayFile: File? = null
                if (trayRelativePath.isNotBlank()) {
                    val extractedTray = File(tempImportDir, trayRelativePath)
                    if (extractedTray.exists()) {
                        val destTray = File(targetTrayDir, "tray_${identifier}.png")
                        extractedTray.copyTo(destTray, overwrite = true)
                        importedTrayFile = destTray
                    }
                }

                // Restaurar stickers
                val stickersArray = packObj.optJSONArray("stickers") ?: JSONArray()
                val importedStickersList = mutableListOf<PackStickerItem>()

                for (j in 0 until stickersArray.length()) {
                    val sObj = stickersArray.getJSONObject(j)
                    val sRelativePath = sObj.optString("file_name", "")
                    val extractedSticker = File(tempImportDir, sRelativePath)

                    if (extractedSticker.exists()) {
                        val stickerExt = if (extractedSticker.name.endsWith(".png", ignoreCase = true)) "png" else "webp"
                        val destStickerFile = File(
                            targetStickersDir,
                            "sticker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$stickerExt"
                        )
                        extractedSticker.copyTo(destStickerFile, overwrite = true)

                        val contentUri = fileUriProvider(context, destStickerFile)

                        val sWidth = sObj.optInt("width", 512)
                        val sHeight = sObj.optInt("height", 512)
                        val sSize = sObj.optLong("size_bytes", destStickerFile.length())
                        val sIsAnimated = sObj.optBoolean("is_animated", isAnimated)
                        val sName = sObj.optString("name", "Sticker ${j + 1}")
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

                        val stickerItem = PackStickerItem(
                            id = UUID.randomUUID().toString(),
                            sourceUri = contentUri,
                            processedSticker = ProcessedSticker(
                                file = destStickerFile,
                                uri = contentUri,
                                width = sWidth,
                                height = sHeight,
                                sizeBytes = sSize
                            ),
                            cropParams = cropParams,
                            isAnimated = sIsAnimated,
                            name = sName,
                            emojis = emojis,
                            accessibilityText = accessibilityText
                        )

                        importedStickersList.add(stickerItem)
                        totalImportedStickers++
                    }
                }

                val isExported = packObj.optBoolean("is_exported_to_whatsapp", false)

                importedPacks.add(
                    StickerPack(
                        id = UUID.randomUUID().toString(),
                        identifier = identifier,
                        name = name,
                        publisher = publisher,
                        publisherEmail = publisherEmail,
                        publisherWebsite = publisherWebsite,
                        privacyPolicyWebsite = privacyPolicy,
                        licenseAgreementWebsite = licenseAgreement,
                        isAnimated = isAnimated,
                        isExportedToWhatsApp = isExported,
                        trayImageFile = importedTrayFile,
                        stickers = importedStickersList
                    )
                )
            }

            if (importedPacks.isEmpty()) {
                BackupImportResult.Error("No se encontraron paquetes con archivos validos en el respaldo.")
            } else {
                BackupImportResult.Success(
                    importedPacks = importedPacks,
                    packsCount = importedPacks.size,
                    stickersCount = totalImportedStickers
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupImportResult.Error("Error al importar el respaldo: ${e.message ?: "Archivo comprimido corrupto o invalido"}")
        } finally {
            tempImportDir.deleteRecursively()
        }
    }

    private class CountingOutputStream(private val wrapped: OutputStream) : OutputStream() {
        var bytesCount: Long = 0L
            private set

        override fun write(b: Int) {
            wrapped.write(b)
            bytesCount++
        }

        override fun write(b: ByteArray) {
            wrapped.write(b)
            bytesCount += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            wrapped.write(b, off, len)
            bytesCount += len
        }

        override fun flush() {
            wrapped.flush()
        }

        override fun close() {
            wrapped.close()
        }
    }
}
