package com.stickerforge.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.stickerforge.app.model.CropParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AnimatedWebpEncoder {

    const val TARGET_SIZE_PX = 512
    const val MAX_ANIMATED_SIZE_BYTES = 500 * 1024 // 500 KB (WhatsApp spec)
    private const val SAFETY_MARGIN_BYTES = 480 * 1024 // 480 KB
    private const val MAX_DURATION_MS = 6000L // Max 6 segundos para garantizar <500 KB
    private const val DEFAULT_FPS = 12 // 12 frames por segundo (balance ideal fluidez/tamano)

    /**
     * Extrae fotogramas de un video o GIF animado y genera un archivo WebP animado oficial de 512x512 px (<500 KB).
     */
    fun processAnimatedMedia(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        cropParams: CropParameters = CropParameters()
    ): Boolean {
        val frames = extractFrames(context, sourceUri, maxFrames = 24, fps = DEFAULT_FPS)
        if (frames.isEmpty()) return false

        return try {
            val frameDurationMs = 1000 / DEFAULT_FPS // ~83 ms por cuadro

            // 1. Ajustar cada fotograma a 512x512 transparente con recorte/escala
            val processedBitmaps = frames.map { rawBitmap ->
                val canvasBitmap = Bitmap.createBitmap(TARGET_SIZE_PX, TARGET_SIZE_PX, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canvasBitmap)

                val srcW = rawBitmap.width.toFloat()
                val srcH = rawBitmap.height.toFloat()
                val baseScale = minOf(TARGET_SIZE_PX / srcW, TARGET_SIZE_PX / srcH)
                val effectiveScale = baseScale * cropParams.scale

                val scaleRatio = if (cropParams.viewportSizePx > 0f) TARGET_SIZE_PX / cropParams.viewportSizePx else 1f
                val tx = cropParams.offsetX * scaleRatio
                val ty = cropParams.offsetY * scaleRatio

                val matrix = Matrix().apply {
                    postTranslate(-srcW / 2f, -srcH / 2f)
                    postScale(effectiveScale, effectiveScale)
                    if (cropParams.rotationDegrees != 0f) {
                        postRotate(cropParams.rotationDegrees)
                    }
                    postTranslate(TARGET_SIZE_PX / 2f + tx, TARGET_SIZE_PX / 2f + ty)
                }

                canvas.drawBitmap(rawBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                rawBitmap.recycle()
                canvasBitmap
            }

            // 2. Comprimir fotogramas en WebP animado ajustando la calidad para no superar 500 KB
            var quality = 80
            var webpBytes = encodeToAnimatedWebp(processedBitmaps, frameDurationMs, quality)

            while (webpBytes.size > SAFETY_MARGIN_BYTES && quality > 20) {
                quality -= 15
                webpBytes = encodeToAnimatedWebp(processedBitmaps, frameDurationMs, quality)
            }

            // Liberar bitmaps procesados
            processedBitmaps.forEach { it.recycle() }

            if (webpBytes.isEmpty()) return false

            FileOutputStream(outputFile).use { fos ->
                fos.write(webpBytes)
                fos.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extrae una secuencia de fotogramas equidistantes desde un GIF o video.
     */
    @Suppress("DEPRECATION")
    fun extractFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 24,
        fps: Int = 12
    ): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""

        // 1. Si es un archivo GIF animado, extraer los fotogramas con android.graphics.Movie
        if (mimeType == "image/gif" || uri.toString().endsWith(".gif", ignoreCase = true)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val movie = android.graphics.Movie.decodeStream(stream)
                    if (movie != null && movie.duration() > 0) {
                        val durationMs = minOf(movie.duration().toLong(), MAX_DURATION_MS)
                        val frameIntervalMs = 1000L / fps
                        val frameCount = minOf((durationMs / frameIntervalMs).toInt(), maxFrames).coerceAtLeast(1)
                        val width = movie.width().coerceAtLeast(1)
                        val height = movie.height().coerceAtLeast(1)

                        for (i in 0 until frameCount) {
                            val timeMs = (i * frameIntervalMs).toInt()
                            movie.setTime(timeMs)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            movie.draw(canvas, 0f, 0f)
                            frames.add(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Si no es GIF o no se extrajeron fotogramas, probar como video mediante MediaMetadataRetriever
        if (frames.isEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val totalDurationMs = durationStr?.toLongOrNull() ?: 3000L
                val effectiveDurationMs = minOf(totalDurationMs, MAX_DURATION_MS)

                val frameIntervalMs = 1000L / fps
                val frameCount = minOf((effectiveDurationMs / frameIntervalMs).toInt(), maxFrames).coerceAtLeast(1)

                for (i in 0 until frameCount) {
                    val timeUs = (i * frameIntervalMs) * 1000L
                    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            TARGET_SIZE_PX,
                            TARGET_SIZE_PX
                        ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }

                    if (frame != null) {
                        frames.add(frame)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // 3. Fallback si no se obtuvieron cuadros (ej. imagen estatica cargada en pack animado)
        if (frames.isEmpty()) {
            val fallback = StickerProcessor.loadSampledBitmap(context, uri, TARGET_SIZE_PX)
            if (fallback != null) {
                frames.add(fallback)
                val copy = fallback.copy(Bitmap.Config.ARGB_8888, true)
                if (copy != null) {
                    frames.add(copy)
                }
            }
        }

        return frames
    }

    /**
     * Muxea una lista de Bitmaps en un archivo Animated WebP estandar compatible con WhatsApp.
     */
    fun encodeToAnimatedWebp(
        bitmaps: List<Bitmap>,
        frameDurationMs: Int,
        quality: Int = 75
    ): ByteArray {
        if (bitmaps.isEmpty()) return ByteArray(0)

        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        // 1. Comprimir cada bitmap individualmente y extraer sub-chunks
        val encodedFrames = mutableListOf<ByteArray>()
        for (bitmap in bitmaps) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(format, quality, baos)
            val singleWebp = baos.toByteArray()
            val subchunks = extractPayloadSubchunks(singleWebp)
            if (subchunks.isNotEmpty()) {
                encodedFrames.add(subchunks)
            }
        }

        if (encodedFrames.isEmpty()) return ByteArray(0)

        // 2. Construir contenedor RIFF WebP Animado
        val output = ByteArrayOutputStream()

        // Placeholder para cabecera RIFF (12 bytes: 'RIFF', 4 bytes tamano, 'WEBP')
        output.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        output.write(byteArrayOf(0, 0, 0, 0)) // Tamano se calculara al final
        output.write(byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()))

        // Chunk VP8X (Extended WebP con flag de animacion 0x02 y alpha 0x10)
        output.write(byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte()))
        writeUInt32LE(output, 10) // Longitud de datos de VP8X
        output.write(0x12) // Flags: Animation (0x02) | Alpha (0x10)
        output.write(0x00) // Reserved
        output.write(0x00) // Reserved
        output.write(0x00) // Reserved
        writeUInt24LE(output, TARGET_SIZE_PX - 1) // Canvas width - 1 (511)
        writeUInt24LE(output, TARGET_SIZE_PX - 1) // Canvas height - 1 (511)

        // Chunk ANIM (Configuracion global de animacion: fondo transparente, loop infinito)
        output.write(byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'M'.code.toByte()))
        writeUInt32LE(output, 6) // Longitud de datos de ANIM
        output.write(byteArrayOf(0, 0, 0, 0)) // Background color RGBA transparente (0)
        writeUInt16LE(output, 0) // Loop count = 0 (loop infinito para WhatsApp)

        // Chunks ANMF (Un chunk por cada fotograma)
        for (subchunkData in encodedFrames) {
            output.write(byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'M'.code.toByte(), 'F'.code.toByte()))

            val anmfDataLength = 16 + subchunkData.size
            writeUInt32LE(output, anmfDataLength)

            writeUInt24LE(output, 0) // Frame X offset (0)
            writeUInt24LE(output, 0) // Frame Y offset (0)
            writeUInt24LE(output, TARGET_SIZE_PX - 1) // Frame Width - 1 (511)
            writeUInt24LE(output, TARGET_SIZE_PX - 1) // Frame Height - 1 (511)
            writeUInt24LE(output, frameDurationMs.coerceAtLeast(10)) // Duracion en ms
            output.write(0x02) // Flags: blend method (alpha blend = 0, dispose = no dispose)

            output.write(subchunkData)

            // Padding si la longitud es impar
            if (anmfDataLength % 2 != 0) {
                output.write(0x00)
            }
        }

        val resultBytes = output.toByteArray()

        // Actualizar el tamano total del archivo en la cabecera RIFF (posicion 4..7 = fileSize - 8)
        val totalSizeMinus8 = resultBytes.size - 8
        val sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        sizeBuffer.putInt(totalSizeMinus8)
        System.arraycopy(sizeBuffer.array(), 0, resultBytes, 4, 4)

        return resultBytes
    }

    /**
     * Extrae los subchunks de imagen (ALPH, VP8, VP8L) de un archivo WebP simple ignorando cabeceras RIFF y VP8X globales.
     */
    private fun extractPayloadSubchunks(webpBytes: ByteArray): ByteArray {
        if (webpBytes.size < 12) return ByteArray(0)

        // Verificar 'RIFF' y 'WEBP'
        val isRiff = webpBytes[0] == 'R'.code.toByte() && webpBytes[1] == 'I'.code.toByte() &&
                webpBytes[2] == 'F'.code.toByte() && webpBytes[3] == 'F'.code.toByte()
        val isWebp = webpBytes[8] == 'W'.code.toByte() && webpBytes[9] == 'E'.code.toByte() &&
                webpBytes[10] == 'B'.code.toByte() && webpBytes[11] == 'P'.code.toByte()

        if (!isRiff || !isWebp) return ByteArray(0)

        val out = ByteArrayOutputStream()
        var offset = 12

        while (offset + 8 <= webpBytes.size) {
            val chunkHeader = String(webpBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(webpBytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val totalChunkSize = 8 + chunkSize + (if (chunkSize % 2 != 0) 1 else 0)

            if (offset + totalChunkSize > webpBytes.size) break

            // Solo incluir chunks validos de imagen dentro de ANMF (ALPH, VP8, VP8L)
            if (chunkHeader == "ALPH" || chunkHeader == "VP8 " || chunkHeader == "VP8L") {
                out.write(webpBytes, offset, totalChunkSize)
            }

            offset += totalChunkSize
        }

        return out.toByteArray()
    }

    private fun writeUInt16LE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }

    private fun writeUInt24LE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
    }

    private fun writeUInt32LE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }
}
