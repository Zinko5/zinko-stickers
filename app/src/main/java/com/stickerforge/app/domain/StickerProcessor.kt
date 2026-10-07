package com.stickerforge.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.ProcessedSticker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object StickerProcessor {

    const val TARGET_SIZE_PX = 512
    const val MAX_FILE_SIZE_BYTES = 100 * 1024 // 100 KB
    private const val SAFETY_MARGIN_BYTES = 98 * 1024 // 98 KB para garantizar aceptacion estricta

    const val TRAY_SIZE_PX = 96
    const val MAX_TRAY_SIZE_BYTES = 50 * 1024 // 50 KB
    private const val TRAY_SAFETY_MARGIN_BYTES = 48 * 1024

    /**
     * Procesa una imagen desde una URI aplicando ajuste/recorte 1:1, escalado exacto a 512x512
     * preservando bordes transparentes si la imagen no es cuadrada, y compresion WebP < 100 KB.
     */
    fun processImage(
        context: Context,
        sourceUri: Uri,
        cropParameters: CropParameters = CropParameters()
    ): ProcessedSticker? {
        val loadedBitmap = loadSampledBitmap(context, sourceUri, maxDimension = 1536) ?: return null
        return try {
            val (finalBitmap, webpBytes) = processBitmap(loadedBitmap, cropParameters)

            // Guardar en el directorio interno seguro de la app
            val stickersDir = File(context.filesDir, "stickers").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "sticker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.webp"
            val outputFile = File(stickersDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                fos.write(webpBytes)
                fos.flush()
            }

            finalBitmap.recycle()

            val contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
            } catch (e: Exception) {
                Uri.fromFile(outputFile)
            }

            ProcessedSticker(
                file = outputFile,
                uri = contentUri,
                width = TARGET_SIZE_PX,
                height = TARGET_SIZE_PX,
                sizeBytes = outputFile.length()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (!loadedBitmap.isRecycled) {
                loadedBitmap.recycle()
            }
        }
    }

    const val DEFAULT_TRAY_ASSET_NAME = "tray_pepe_smolder.png"

    /**
     * Copia o genera el icono de bandeja predeterminado adaptado (Pepe Smolder 96x96 px PNG <50 KB con canal alfa).
     */
    fun createDefaultPepeTrayIcon(
        context: Context,
        packIdentifier: String
    ): File? {
        return try {
            val trayDir = File(context.filesDir, "tray_icons").apply {
                if (!exists()) mkdirs()
            }
            val trayFile = File(trayDir, "tray_${packIdentifier}.png")
            context.assets.open(DEFAULT_TRAY_ASSET_NAME).use { input ->
                FileOutputStream(trayFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            trayFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera el icono de bandeja oficial de WhatsApp (96x96 px, <50 KB, canal alfa).
     */
    fun generateTrayIcon(
        context: Context,
        sourceFile: File,
        packIdentifier: String
    ): File? {
        if (!sourceFile.exists()) return null
        val sourceBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null

        return try {
            val trayBitmap = Bitmap.createBitmap(TRAY_SIZE_PX, TRAY_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(trayBitmap)

            val srcW = sourceBitmap.width.toFloat()
            val srcH = sourceBitmap.height.toFloat()
            val scale = minOf(TRAY_SIZE_PX / srcW, TRAY_SIZE_PX / srcH)

            val matrix = Matrix().apply {
                postTranslate(-srcW / 2f, -srcH / 2f)
                postScale(scale, scale)
                postTranslate(TRAY_SIZE_PX / 2f, TRAY_SIZE_PX / 2f)
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(sourceBitmap, matrix, paint)

            val trayDir = File(context.filesDir, "tray_icons").apply {
                if (!exists()) mkdirs()
            }
            val trayFile = File(trayDir, "tray_${packIdentifier}.png")
            FileOutputStream(trayFile).use { fos ->
                trayBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            trayBitmap.recycle()
            trayFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }

    /**
     * Transforma un Bitmap fuente a un lienzo 512x512 transparente.
     * Utiliza minOf (FIT) para preservar bordes transparentes y no cortar imagenes no cuadradas.
     */
    fun processBitmap(
        sourceBitmap: Bitmap,
        cropParameters: CropParameters
    ): Pair<Bitmap, ByteArray> {
        val outputBitmap = Bitmap.createBitmap(
            TARGET_SIZE_PX,
            TARGET_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(outputBitmap)

        val matrix = Matrix()
        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()

        // Escala base: Ajuste completo (FIT) dentro del cuadro 512x512
        // Permite bordes transparentes arriba/abajo o laterales si la imagen no es 1:1
        val baseScale = minOf(TARGET_SIZE_PX / srcW, TARGET_SIZE_PX / srcH)
        val effectiveScale = baseScale * cropParameters.scale

        val scaleRatio = if (cropParameters.viewportSizePx > 0f) {
            TARGET_SIZE_PX / cropParameters.viewportSizePx
        } else {
            1f
        }
        val tx = cropParameters.offsetX * scaleRatio
        val ty = cropParameters.offsetY * scaleRatio

        // 1. Trasladar origen al centro de la imagen fuente
        matrix.postTranslate(-srcW / 2f, -srcH / 2f)
        // 2. Aplicar escala efectiva
        matrix.postScale(effectiveScale, effectiveScale)
        // 3. Aplicar rotacion alrededor del centro
        if (cropParameters.rotationDegrees != 0f) {
            matrix.postRotate(cropParameters.rotationDegrees)
        }
        // 4. Trasladar al centro del lienzo 512x512 mas el desplazamiento del usuario
        matrix.postTranslate(TARGET_SIZE_PX / 2f + tx, TARGET_SIZE_PX / 2f + ty)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)

        // Compresion adaptativa WebP
        val webpBytes = compressToWebpUnderLimit(outputBitmap, SAFETY_MARGIN_BYTES)

        return Pair(outputBitmap, webpBytes)
    }

    /**
     * Comprime el Bitmap a formato WebP ajustando progresivamente la calidad
     * para asegurar que el tamano final no supere maxBytes.
     */
    fun compressToWebpUnderLimit(bitmap: Bitmap, maxBytes: Int): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        var quality = 95
        var outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality, outputStream)

        while (outputStream.size() > maxBytes && quality > 15) {
            quality -= 10
            outputStream = ByteArrayOutputStream()
            bitmap.compress(format, quality, outputStream)
        }

        if (outputStream.size() > maxBytes && quality > 5) {
            quality = 5
            outputStream = ByteArrayOutputStream()
            bitmap.compress(format, quality, outputStream)
        }

        return outputStream.toByteArray()
    }

    private fun loadSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null

        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxDimension || height / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }
}
