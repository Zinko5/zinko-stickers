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
    private const val SAFETY_MARGIN_BYTES = 485 * 1024 // 485 KB para garantizar aceptacion estricta
    const val MAX_WHATSAPP_DURATION_MS = 9600L // WhatsApp spec: estrictamente < 10 segundos
    private const val DEFAULT_FPS = 12

    data class AnimationFrames(
        val frames: List<Bitmap>,
        val frameDurationMs: Int
    )

    /**
     * Extrae fotogramas de un video o GIF animado y genera un archivo WebP animado oficial de 512x512 px (<500 KB, <10s).
     * Muestrea a lo largo de toda la duracion del medio sin recortar prematuramente.
     */
    fun processAnimatedMedia(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        cropParams: CropParameters = CropParameters()
    ): Boolean {
        val animationData = extractFramesWithTiming(context, sourceUri)
        val frames = animationData.frames
        if (frames.isEmpty()) return false

        return try {
            val frameDurationMs = animationData.frameDurationMs

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
            var quality = 75
            var webpBytes = encodeToAnimatedWebp(processedBitmaps, frameDurationMs, quality)

            while (webpBytes.size > SAFETY_MARGIN_BYTES && quality > 15) {
                quality -= 8
                webpBytes = encodeToAnimatedWebp(processedBitmaps, frameDurationMs, quality)
            }

            // Si aun con calidad baja supera 485 KB, submuestrear a la mitad de fotogramas duplicando la duracion por cuadro
            if (webpBytes.size > SAFETY_MARGIN_BYTES && processedBitmaps.size > 20) {
                val halvedBitmaps = processedBitmaps.filterIndexed { index, _ -> index % 2 == 0 }
                val newFrameDurationMs = frameDurationMs * 2
                quality = 70
                webpBytes = encodeToAnimatedWebp(halvedBitmaps, newFrameDurationMs, quality)
                while (webpBytes.size > SAFETY_MARGIN_BYTES && quality > 15) {
                    quality -= 8
                    webpBytes = encodeToAnimatedWebp(halvedBitmaps, newFrameDurationMs, quality)
                }
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
     * Extrae fotogramas equidistantes distribuidos por toda la duracion del video o GIF (hasta 9.6s).
     * Calcula la duracion exacta por cuadro para mantener la velocidad real 1.0x sin cortes prematuros.
     */
    @Suppress("DEPRECATION")
    fun extractFramesWithTiming(
        context: Context,
        uri: Uri
    ): AnimationFrames {
        val frames = mutableListOf<Bitmap>()
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""

        val localFile = if (uri.scheme == "file" && uri.path != null) {
            java.io.File(uri.path!!)
        } else null

        // Detectar si el contenido es un GIF (por mime, extension o cabecera de bytes)
        var isGif = mimeType == "image/gif" ||
                uri.toString().contains(".gif", ignoreCase = true) ||
                (uri.path?.contains(".gif", ignoreCase = true) == true)

        if (localFile != null && localFile.exists() && localFile.length() >= 3) {
            try {
                java.io.FileInputStream(localFile).use { fis ->
                    val header = ByteArray(3)
                    if (fis.read(header) == 3 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
                        isGif = true
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 1. Si es un archivo GIF animado, extraer los fotogramas con android.graphics.Movie
        if (isGif) {
            try {
                val stream = if (localFile != null && localFile.exists()) {
                    java.io.FileInputStream(localFile)
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                stream?.use { inStream ->
                    val movie = android.graphics.Movie.decodeStream(inStream)
                    if (movie != null && movie.duration() > 0) {
                        val totalDurationMs = movie.duration().toLong()
                        val effectiveDurationMs = minOf(totalDurationMs, MAX_WHATSAPP_DURATION_MS)

                        val targetFps = when {
                            effectiveDurationMs <= 2500L -> 14.0
                            effectiveDurationMs <= 4500L -> 12.0
                            effectiveDurationMs <= 7200L -> 10.0
                            else -> 8.0
                        }
                        val idealFrameCount = ((effectiveDurationMs * targetFps) / 1000.0).toInt()
                        val frameCount = idealFrameCount.coerceIn(16, 70)
                        val frameDurationMs = (effectiveDurationMs / frameCount).toInt().coerceAtLeast(30)

                        val width = movie.width().coerceAtLeast(1)
                        val height = movie.height().coerceAtLeast(1)
                        val stepMs = effectiveDurationMs.toFloat() / frameCount

                        for (i in 0 until frameCount) {
                            val timeMs = (i * stepMs).toInt()
                            movie.setTime(timeMs)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            movie.draw(canvas, 0f, 0f)
                            frames.add(bitmap)
                        }
                        if (frames.isNotEmpty()) {
                            return AnimationFrames(frames, frameDurationMs)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Si es video (o Movie no pudo procesar el GIF/MP4), extraer con MediaMetadataRetriever
        if (frames.isEmpty()) {
            val retrieverPair = createInitializedRetriever(context, uri, localFile)
            if (retrieverPair != null) {
                val retriever = retrieverPair.first
                val closeable = retrieverPair.second
                try {
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val parsedDuration = durationStr?.toLongOrNull() ?: 0L
                    val totalDurationMs = if (parsedDuration > 0L) parsedDuration else 3000L
                    val effectiveDurationMs = minOf(totalDurationMs, MAX_WHATSAPP_DURATION_MS).coerceAtLeast(500L)

                    val targetFps = when {
                        effectiveDurationMs <= 2500L -> 14.0
                        effectiveDurationMs <= 4500L -> 12.0
                        effectiveDurationMs <= 7200L -> 10.0
                        else -> 8.0
                    }
                    val idealFrameCount = ((effectiveDurationMs * targetFps) / 1000.0).toInt()
                    val frameCount = idealFrameCount.coerceIn(16, 70)
                    val frameDurationMs = (effectiveDurationMs / frameCount).toInt().coerceAtLeast(30)

                    val stepUs = (effectiveDurationMs * 1000L) / frameCount

                    for (i in 0 until frameCount) {
                        val timeUs = i * stepUs
                        val frame = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                retriever.getScaledFrameAtTime(
                                    timeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST,
                                    TARGET_SIZE_PX,
                                    TARGET_SIZE_PX
                                )
                            } else null
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(timeUs)
                        } catch (e: Exception) {
                            null
                        }

                        if (frame != null) {
                            frames.add(frame)
                        }
                    }

                    if (frames.size == 1) {
                        val copy = frames[0].copy(Bitmap.Config.ARGB_8888, true)
                        if (copy != null) {
                            frames.add(copy)
                        }
                    }

                    if (frames.isNotEmpty()) {
                        return AnimationFrames(frames, frameDurationMs)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        closeable?.close()
                    } catch (ignored: Exception) {}
                    try {
                        retriever.release()
                    } catch (ignored: Exception) {}
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
                return AnimationFrames(frames, 500)
            }
        }

        return AnimationFrames(frames, 1000 / DEFAULT_FPS)
    }

    /**
     * Mantiene compatibilidad con llamadas existentes devolviendo la lista simple de cuadros.
     */
    @Suppress("UNUSED_PARAMETER")
    fun extractFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 36,
        fps: Int = 12
    ): List<Bitmap> {
        return extractFramesWithTiming(context, uri).frames.take(maxFrames)
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

    private fun createInitializedRetriever(
        context: Context,
        uri: Uri,
        localFile: File?
    ): Pair<MediaMetadataRetriever, java.io.Closeable?>? {
        // Estrategia 1: Archivo local con descriptor de FileInputStream (fd limpio sin offset ni longitud)
        if (localFile != null && localFile.exists()) {
            try {
                val fis = java.io.FileInputStream(localFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fis.fd)
                return Pair(retriever, fis)
            } catch (ignored: Exception) {}

            // Estrategia 2: Ruta absoluta directa del archivo local
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(localFile.absolutePath)
                return Pair(retriever, null)
            } catch (ignored: Exception) {}
        }

        // Estrategia 3: Context y URI directo (maneja internamente content:// y file://)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            return Pair(retriever, null)
        } catch (ignored: Exception) {}

        // Estrategia 4: ParcelFileDescriptor via ContentResolver
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(pfd.fileDescriptor)
                return Pair(retriever, pfd)
            }
        } catch (ignored: Exception) {}

        // Estrategia 5: Path del URI si existe
        if (uri.path != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(uri.path)
                return Pair(retriever, null)
            } catch (ignored: Exception) {}
        }

        return null
    }
}
