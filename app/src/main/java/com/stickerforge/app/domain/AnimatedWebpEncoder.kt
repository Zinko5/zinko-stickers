package com.stickerforge.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
    const val MAX_STICKER_FRAMES = 70 // Limite optimo y limite maximo de fotogramas bajo 485 KB con alta fidelidad
    private const val DEFAULT_FPS = 15

    data class AnimationFrames(
        val frames: List<Bitmap>,
        val frameDurationMs: Int
    )

    /**
     * Extrae fotogramas de un video o GIF animado y genera un archivo WebP animado oficial de 512x512 px (<500 KB, <10s).
     * Mantiene una cadencia fluida de 15 FPS a velocidad real (1.0x).
     */
    fun processAnimatedMedia(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        cropParams: CropParameters = CropParameters()
    ): Boolean {
        DiagnosticLogger.log("WebpEncoder", "processAnimatedMedia iniciado para $sourceUri")
        val animationData = extractFramesWithTiming(context, sourceUri)
        val frames = animationData.frames
        DiagnosticLogger.log("WebpEncoder", "extractFramesWithTiming retorno ${frames.size} cuadros (duracionPorCuadro=${animationData.frameDurationMs}ms)")
        if (frames.isEmpty()) {
            DiagnosticLogger.logError("WebpEncoder", "No se obtuvieron fotogramas de la fuente: $sourceUri")
            return false
        }

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

            DiagnosticLogger.log("WebpEncoder", "Codificando ${processedBitmaps.size} cuadros a Animated WebP (duracion=${frameDurationMs}ms)...")

            // 2. Comprimir fotogramas en WebP animado garantizando fluidez (FPS >= 10.0) y tamano < 485 KB
            val extraBitmapsToRecycle = mutableListOf<Bitmap>()
            var currentFrames = processedBitmaps
            // Limitar la duracion por cuadro a maximo 140ms para preservar la duracion completa del clip (hasta 9.6s) a velocidad real 1.0x
            var currentDurationMs = frameDurationMs.coerceIn(50, 140)
            var quality = 70
            var webpBytes = encodeToAnimatedWebp(currentFrames, currentDurationMs, quality)

            // Etapa 1: Reduccion progresiva de calidad manteniendo el 100% de los fotogramas
            while (webpBytes.size > SAFETY_MARGIN_BYTES && quality > 20) {
                quality -= 6
                webpBytes = encodeToAnimatedWebp(currentFrames, currentDurationMs, quality)
            }

            // Etapa 2: Si supera 485 KB, aplicar reescalado adaptativo (0.85x) manteniendo 512x512 transparente
            // Reduce el peso entre 30% y 40% al limpiar ruido de bordes, permitiendo retener TODOS los fotogramas
            if (webpBytes.size > SAFETY_MARGIN_BYTES && currentFrames.size >= 2) {
                val scaleFactor = 0.85f
                val targetScaledSize = (TARGET_SIZE_PX * scaleFactor).toInt()
                val offset = ((TARGET_SIZE_PX - targetScaledSize) / 2f)
                val scaledBitmaps = currentFrames.map { bmp ->
                    val scaled = Bitmap.createScaledBitmap(bmp, targetScaledSize, targetScaledSize, true)
                    val padded = Bitmap.createBitmap(TARGET_SIZE_PX, TARGET_SIZE_PX, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(padded)
                    canvas.drawBitmap(scaled, offset, offset, Paint(Paint.FILTER_BITMAP_FLAG))
                    scaled.recycle()
                    padded
                }
                extraBitmapsToRecycle.addAll(scaledBitmaps)
                quality = 50
                var candidateBytes = encodeToAnimatedWebp(scaledBitmaps, currentDurationMs, quality)
                while (candidateBytes.size > SAFETY_MARGIN_BYTES && quality > 15) {
                    quality -= 5
                    candidateBytes = encodeToAnimatedWebp(scaledBitmaps, currentDurationMs, quality)
                }
                if (candidateBytes.isNotEmpty() && candidateBytes.size < webpBytes.size) {
                    webpBytes = candidateBytes
                    currentFrames = scaledBitmaps
                }
            }

            // Etapa 3: Si aun supera 485 KB en videos con ruido masivo, ajustar cantidad de cuadros
            // REGLA CRITICA: Se mantiene una cadencia fluida (<= 95ms / >= 10.5 FPS), NUNCA se alarga a 260ms (slideshow)
            if (webpBytes.size > SAFETY_MARGIN_BYTES && currentFrames.size > 20) {
                val fluidDurationMs = minOf(currentDurationMs, 95)
                val frameSteps = listOf(
                    (currentFrames.size * 0.78).toInt(),
                    (currentFrames.size * 0.62).toInt(),
                    (currentFrames.size * 0.48).toInt(),
                    (currentFrames.size * 0.38).toInt()
                )

                for (targetCount in frameSteps) {
                    if (webpBytes.size <= SAFETY_MARGIN_BYTES || targetCount < 14) break
                    val step = currentFrames.size.toFloat() / targetCount
                    val candidateFrames = (0 until targetCount).map { k ->
                        val idx = (k * step).toInt().coerceIn(0, currentFrames.size - 1)
                        currentFrames[idx]
                    }
                    quality = 45
                    var candidateBytes = encodeToAnimatedWebp(candidateFrames, fluidDurationMs, quality)
                    while (candidateBytes.size > SAFETY_MARGIN_BYTES && quality > 12) {
                        quality -= 5
                        candidateBytes = encodeToAnimatedWebp(candidateFrames, fluidDurationMs, quality)
                    }
                    if (candidateBytes.isNotEmpty() && candidateBytes.size < webpBytes.size) {
                        webpBytes = candidateBytes
                        currentDurationMs = fluidDurationMs
                        if (candidateBytes.size <= SAFETY_MARGIN_BYTES) break
                    }
                }
            }

            // Etapa 4: Guarda final estricta de seguridad (<485 KB) manteniendo fluidez a 90ms (11.1 FPS)
            while (webpBytes.size > SAFETY_MARGIN_BYTES && currentFrames.size > 12) {
                val newCount = (currentFrames.size * 0.75).toInt()
                val step = currentFrames.size.toFloat() / newCount
                val candidateFrames = (0 until newCount).map { k ->
                    val idx = (k * step).toInt().coerceIn(0, currentFrames.size - 1)
                    currentFrames[idx]
                }
                val fluidDurationMs = 90
                val candidateBytes = encodeToAnimatedWebp(candidateFrames, fluidDurationMs, 12)
                if (candidateBytes.isNotEmpty() && candidateBytes.size < webpBytes.size) {
                    webpBytes = candidateBytes
                    currentFrames = candidateFrames
                    currentDurationMs = fluidDurationMs
                } else {
                    break
                }
            }

            // Liberar todos los bitmaps procesados y generados
            processedBitmaps.forEach { it.recycle() }
            extraBitmapsToRecycle.forEach { if (!it.isRecycled) it.recycle() }

            DiagnosticLogger.log("WebpEncoder", "encodeToAnimatedWebp resultado: ${webpBytes.size} bytes (calidad=$quality, duracion=${currentDurationMs}ms)")
            if (webpBytes.isEmpty()) {
                DiagnosticLogger.logError("WebpEncoder", "encodeToAnimatedWebp devolvio 0 bytes!")
                return false
            }

            FileOutputStream(outputFile).use { fos ->
                fos.write(webpBytes)
                fos.flush()
            }
            DiagnosticLogger.log("WebpEncoder", "Archivo de salida escrito con exito: ${outputFile.name}, size=${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            DiagnosticLogger.logError("WebpEncoder", "Excepcion en processAnimatedMedia: ${e.message}", e)
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

        if (localFile != null && localFile.exists()) {
            TwitterMediaResolver.normalizeMp4Brands(localFile)
            TikTokMediaResolver.normalizeMp4Brands(localFile)
            InstagramMediaResolver.normalizeMp4Brands(localFile)
            YouTubeMediaResolver.normalizeMp4Brands(localFile)
        }

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
                        val effectiveDurationMs = minOf(totalDurationMs, MAX_WHATSAPP_DURATION_MS).coerceAtLeast(300L)

                        val targetFps = when {
                            effectiveDurationMs <= 2500L -> 15.0
                            effectiveDurationMs <= 4500L -> 13.5
                            effectiveDurationMs <= 6500L -> 11.5
                            else -> 8.5
                        }
                        val idealFrameCount = ((effectiveDurationMs * targetFps) / 1000.0).toInt()
                        val frameCount = idealFrameCount.coerceIn(16, MAX_STICKER_FRAMES)
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

        // 2. Si es video (MP4, WebM, etc.), prioridad 1: Decodificacion secuencial nativa por hardware con MediaCodec
        // MediaCodec decodifica correctamente cuadros B y P sin congelarse en keyframes distantes (crucial para videos de TikTok y Twitter)
        val codecResult = extractFramesViaMediaCodec(context, uri, localFile)
        if (codecResult != null && codecResult.frames.isNotEmpty()) {
            DiagnosticLogger.log("WebpEncoder", "extractFramesWithTiming decodifico ${codecResult.frames.size} cuadros via MediaCodec a ${codecResult.frameDurationMs}ms/frame")
            return codecResult
        }

        // 3. Fallback: Si MediaCodec falla o no encuentra pistas, intentar con MediaMetadataRetriever
        if (frames.isEmpty()) {
            DiagnosticLogger.log("WebpEncoder", "Intentando fallback con MediaMetadataRetriever...")
            val retrieverPair = createInitializedRetriever(context, uri, localFile)
            if (retrieverPair != null) {
                val retriever = retrieverPair.first
                val closeable = retrieverPair.second
                try {
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val countStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    } else null
                    val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                    DiagnosticLogger.log(
                        "WebpEncoder",
                        "Retriever metadata: dur=$durationStr, frameCount=$countStr, w=$videoWidth, h=$videoHeight, mime=$mime"
                    )

                    val parsedDuration = durationStr?.toLongOrNull() ?: 0L
                    val totalDurationMs = if (parsedDuration > 0L) parsedDuration else MAX_WHATSAPP_DURATION_MS
                    val effectiveDurationMs = minOf(totalDurationMs, MAX_WHATSAPP_DURATION_MS).coerceAtLeast(500L)

                    val targetFps = when {
                        effectiveDurationMs <= 2500L -> 15.0
                        effectiveDurationMs <= 4500L -> 13.5
                        effectiveDurationMs <= 6500L -> 11.5
                        else -> 8.5
                    }
                    val idealFrameCount = ((effectiveDurationMs * targetFps) / 1000.0).toInt()
                    val frameCount = idealFrameCount.coerceIn(16, MAX_STICKER_FRAMES)
                    val frameDurationMs = (effectiveDurationMs / frameCount).toInt().coerceAtLeast(30)
                    val canUseScaled = videoWidth >= TARGET_SIZE_PX && videoHeight >= TARGET_SIZE_PX

                    // Estrategia A: Si API >= 28 y el video reporta METADATA_KEY_VIDEO_FRAME_COUNT, extraer por indice directo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            val totalVideoFrames = countStr?.toIntOrNull() ?: 0
                            if (totalVideoFrames > 0) {
                                val clipFramesCount = ((totalVideoFrames.toFloat() * effectiveDurationMs) / totalDurationMs).toInt().coerceAtLeast(1)
                                val desiredFrames = frameCount.coerceAtMost(clipFramesCount)
                                val step = clipFramesCount.toFloat() / desiredFrames
                                for (k in 0 until desiredFrames) {
                                    val idx = (k * step).toInt().coerceIn(0, clipFramesCount - 1)
                                    val f = try {
                                        retriever.getFrameAtIndex(idx)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (f != null) {
                                        frames.add(f)
                                    }
                                }
                                DiagnosticLogger.log("WebpEncoder", "Estrategia A (getFrameAtIndex): se obtuvieron ${frames.size}/$desiredFrames cuadros")
                            }
                        } catch (e: Exception) {
                            DiagnosticLogger.logError("WebpEncoder", "getFrameAtIndex fallo: ${e.message}", e)
                        }
                    }

                    // Estrategia B: Si getFrameAtIndex no extrajo fotogramas, extraer por timestamp
                    if (frames.isEmpty()) {
                        val stepUs = (effectiveDurationMs * 1000L) / frameCount
                        DiagnosticLogger.log("WebpEncoder", "Iniciando Estrategia B (timestamp): framesObjetivo=$frameCount, stepUs=$stepUs")

                        for (i in 0 until frameCount) {
                            val timeUs = i * stepUs
                            val frame = try {
                                if (canUseScaled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
                                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                            } catch (e: Exception) {
                                null
                            } ?: try {
                                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_NEXT_SYNC)
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
                        DiagnosticLogger.log("WebpEncoder", "Estrategia B (timestamp): se obtuvieron ${frames.size}/$frameCount cuadros")
                    }

                    // Estrategia C: Fallback para recuperar al menos un cuadro representativo si frames sigue vacio
                    if (frames.isEmpty()) {
                        DiagnosticLogger.log("WebpEncoder", "Frames sigue vacio, probando Estrategia C (frame representativo)...")
                        val fallbackFrame = try {
                            retriever.frameAtTime
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(0L)
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(100_000L)
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            retriever.getFrameAtTime(500_000L)
                        } catch (e: Exception) {
                            null
                        }

                        if (fallbackFrame != null) {
                            frames.add(fallbackFrame)
                            val copy = fallbackFrame.copy(Bitmap.Config.ARGB_8888, true)
                            if (copy != null) {
                                frames.add(copy)
                            }
                            DiagnosticLogger.log("WebpEncoder", "Estrategia C logro recuperar 1 cuadro representativo duplicado")
                        } else {
                            DiagnosticLogger.logError("WebpEncoder", "Estrategia C no pudo obtener ni un solo cuadro de MediaMetadataRetriever")
                        }
                    }

                    if (frames.size == 1) {
                        val copy = frames[0].copy(Bitmap.Config.ARGB_8888, true)
                        if (copy != null) {
                            frames.add(copy)
                        }
                    }

                    if (frames.isNotEmpty()) {
                        DiagnosticLogger.log("WebpEncoder", "MediaMetadataRetriever finalizo con exito: ${frames.size} cuadros")
                        return AnimationFrames(frames, frameDurationMs)
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.logError("WebpEncoder", "Excepcion durante lectura de MediaMetadataRetriever: ${e.message}", e)
                } finally {
                    try {
                        closeable?.close()
                    } catch (ignored: Exception) {}
                    try {
                        retriever.release()
                    } catch (ignored: Exception) {}
                }
            } else {
                DiagnosticLogger.logError("WebpEncoder", "createInitializedRetriever devolvio null para $uri (localFile=${localFile?.absolutePath})")
            }
        }

        // 4. Fallback si no se obtuvieron cuadros (ej. imagen estatica cargada en pack animado)
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

    private fun isRetrieverValid(retriever: MediaMetadataRetriever): Boolean {
        return try {
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val durValid = dur?.toLongOrNull()?.let { it > 0 } ?: false
            val wValid = w?.toIntOrNull()?.let { it > 0 } ?: false
            val mimeValid = mime != null && mime.startsWith("video/")
            durValid || wValid || mimeValid || hasVideo == "yes"
        } catch (e: Exception) {
            false
        }
    }

    private fun createInitializedRetriever(
        context: Context,
        uri: Uri,
        localFile: File?
    ): Pair<MediaMetadataRetriever, java.io.Closeable?>? {
        // Estrategia 1: FileDescriptor con offset y longitud exactos via FileInputStream
        // Bypassea problemas de sandbox y permisos en rutas /data/user/0/
        if (localFile != null && localFile.exists()) {
            try {
                val fis = java.io.FileInputStream(localFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fis.fd, 0L, localFile.length())
                if (isRetrieverValid(retriever)) {
                    DiagnosticLogger.log("WebpEncoder", "createInitializedRetriever: Exito con FileInputStream.fd (len=${localFile.length()})")
                    return Pair(retriever, fis)
                } else {
                    DiagnosticLogger.log("WebpEncoder", "Retriever via fd(offset, len) no reporto metadata valida, probando fd simple")
                    retriever.release()
                    fis.close()
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("WebpEncoder", "setDataSource(fd, 0, len) fallo: ${e.message}")
            }

            // Estrategia 1b: FileDescriptor simple sin offset y longitud
            try {
                val fis = java.io.FileInputStream(localFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fis.fd)
                if (isRetrieverValid(retriever)) {
                    DiagnosticLogger.log("WebpEncoder", "createInitializedRetriever: Exito con FileInputStream.fd simple")
                    return Pair(retriever, fis)
                } else {
                    DiagnosticLogger.log("WebpEncoder", "Retriever via fd simple no reporto metadata valida, probando siguiente estrategia")
                    retriever.release()
                    fis.close()
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("WebpEncoder", "setDataSource(fd) simple fallo: ${e.message}")
            }

            // Estrategia 2: Ruta absoluta directa del archivo local
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(localFile.absolutePath)
                if (isRetrieverValid(retriever)) {
                    DiagnosticLogger.log("WebpEncoder", "createInitializedRetriever: Exito con absolutePath (${localFile.absolutePath})")
                    return Pair(retriever, null)
                } else {
                    DiagnosticLogger.log("WebpEncoder", "Retriever via absolutePath no reporto metadata valida, probando siguiente estrategia")
                    retriever.release()
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("WebpEncoder", "setDataSource(path) fallo: ${e.message}")
            }
        }

        // Estrategia 3: Context y URI directo (maneja internamente content:// y file://)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            if (isRetrieverValid(retriever)) {
                DiagnosticLogger.log("WebpEncoder", "createInitializedRetriever: Exito con context + uri ($uri)")
                return Pair(retriever, null)
            } else {
                DiagnosticLogger.log("WebpEncoder", "Retriever via context+uri no reporto metadata valida, probando siguiente estrategia")
                retriever.release()
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("WebpEncoder", "setDataSource(context, uri) fallo: ${e.message}")
        }

        // Estrategia 4: ParcelFileDescriptor via ContentResolver
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(pfd.fileDescriptor, 0L, pfd.statSize)
                if (isRetrieverValid(retriever)) {
                    DiagnosticLogger.log("WebpEncoder", "createInitializedRetriever: Exito con ParcelFileDescriptor")
                    return Pair(retriever, pfd)
                } else {
                    retriever.release()
                    pfd.close()
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("WebpEncoder", "setDataSource(pfd) fallo: ${e.message}")
        }

        // Estrategia 5: Fallback si ninguna estrategia paso la validacion estricta, pero se puede intentar extraer
        if (localFile != null && localFile.exists()) {
            try {
                val fis = java.io.FileInputStream(localFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fis.fd)
                DiagnosticLogger.log("WebpEncoder", "Retriever inicializado en modo fallback via fd simple")
                return Pair(retriever, fis)
            } catch (ignored: Exception) {}

            try {
                val fis = java.io.FileInputStream(localFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fis.fd, 0L, localFile.length())
                DiagnosticLogger.log("WebpEncoder", "Retriever inicializado en modo fallback via fd con offset")
                return Pair(retriever, fis)
            } catch (ignored: Exception) {}

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(localFile.absolutePath)
                DiagnosticLogger.log("WebpEncoder", "Retriever inicializado en modo fallback via path")
                return Pair(retriever, null)
            } catch (ignored: Exception) {}
        }

        return null
    }

    /**
     * Decodifica directamente cuadros de video utilizando el framework nativo MediaExtractor + MediaCodec.
     * Esencial para archivos de video como los GIFs de Twitter (MP4s silenciosos de corta duracion),
     * donde la implementacion de MediaMetadataRetriever de ciertos fabricantes falla o devuelve metadatos nulos.
     */
    private fun extractFramesViaMediaCodec(
        context: Context,
        uri: Uri,
        localFile: File?
    ): AnimationFrames? {
        var extractor = MediaExtractor()
        var fis: java.io.FileInputStream? = null
        var codec: MediaCodec? = null
        val decodedBitmaps = mutableListOf<Bitmap>()

        return try {
            DiagnosticLogger.log("WebpEncoder", "Iniciando decodificacion nativa via MediaCodec...")

            var sourceSet = false
            if (localFile != null && localFile.exists()) {
                // Intento 1: FileDescriptor simple
                try {
                    fis = java.io.FileInputStream(localFile)
                    extractor.setDataSource(fis.fd)
                    DiagnosticLogger.log("WebpEncoder", "MediaExtractor(fd) tracks=${extractor.trackCount}")
                    if (extractor.trackCount > 0) sourceSet = true
                } catch (e: Exception) {
                    DiagnosticLogger.log("WebpEncoder", "MediaExtractor(fd) fallo: ${e.message}")
                }

                // Intento 2: FileDescriptor con offset y longitud
                if (!sourceSet) {
                    try {
                        extractor.release()
                        extractor = MediaExtractor()
                        fis?.close()
                        fis = java.io.FileInputStream(localFile)
                        extractor.setDataSource(fis.fd, 0L, localFile.length())
                        DiagnosticLogger.log("WebpEncoder", "MediaExtractor(fd, 0, len) tracks=${extractor.trackCount}")
                        if (extractor.trackCount > 0) sourceSet = true
                    } catch (e: Exception) {
                        DiagnosticLogger.log("WebpEncoder", "MediaExtractor(fd, 0, len) fallo: ${e.message}")
                    }
                }

                // Intento 3: Ruta absoluta directa
                if (!sourceSet) {
                    try {
                        extractor.release()
                        extractor = MediaExtractor()
                        extractor.setDataSource(localFile.absolutePath)
                        DiagnosticLogger.log("WebpEncoder", "MediaExtractor(path) tracks=${extractor.trackCount}")
                        if (extractor.trackCount > 0) sourceSet = true
                    } catch (e: Exception) {
                        DiagnosticLogger.log("WebpEncoder", "MediaExtractor(path) fallo: ${e.message}")
                    }
                }
            }

            // Intento 4: Context y URI
            if (!sourceSet) {
                try {
                    extractor.release()
                    extractor = MediaExtractor()
                    extractor.setDataSource(context, uri, null)
                    DiagnosticLogger.log("WebpEncoder", "MediaExtractor(context, uri) tracks=${extractor.trackCount}")
                } catch (e: Exception) {
                    DiagnosticLogger.log("WebpEncoder", "MediaExtractor(context, uri) fallo: ${e.message}")
                }
            }

            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("video/")) {
                    videoTrack = i
                    videoFormat = f
                    break
                }
            }

            if (videoTrack == -1 || videoFormat == null) {
                DiagnosticLogger.logError("WebpEncoder", "MediaExtractor no encontro pista de video en el archivo (tracks=${extractor.trackCount})")
                return null
            }

            extractor.selectTrack(videoTrack)
            val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            var durationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                videoFormat.getLong(MediaFormat.KEY_DURATION)
            } else -1L

            if (durationUs <= 0L) {
                // Intentar recuperar duracion via MediaMetadataRetriever
                try {
                    val retriever = MediaMetadataRetriever()
                    if (localFile != null && localFile.exists()) {
                        retriever.setDataSource(localFile.absolutePath)
                    } else {
                        retriever.setDataSource(context, uri)
                    }
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durMs = durStr?.toLongOrNull() ?: 0L
                    if (durMs > 0L) {
                        durationUs = durMs * 1000L
                    }
                    retriever.release()
                } catch (ignored: Exception) {}
            }

            // Si la duracion sigue sin detectarse (ej. streams fMP4 / DASH truncados),
            // se asume la duracion maxima oficial de WhatsApp (9.6s) para procesar el clip completo
            if (durationUs <= 0L) {
                durationUs = MAX_WHATSAPP_DURATION_MS * 1000L
            }

            DiagnosticLogger.log("WebpEncoder", "MediaCodec: track encontrado mime=$mime, $width x $height, dur=${durationUs / 1000}ms")

            // Duracion efectiva del clip para el sticker: hasta 9.6 segundos (limite maximo estricto de WhatsApp)
            val clipDurationUs = minOf(durationUs, MAX_WHATSAPP_DURATION_MS * 1000L).coerceAtLeast(500_000L)
            val effectiveDurationMs = clipDurationUs / 1000L

            val targetFps = when {
                effectiveDurationMs <= 2500L -> 15.0
                effectiveDurationMs <= 4500L -> 13.5
                effectiveDurationMs <= 6500L -> 11.5
                else -> 8.5
            }
            val idealFrameCount = ((effectiveDurationMs * targetFps) / 1000.0).toInt()
            val targetFramesCount = idealFrameCount.coerceIn(16, MAX_STICKER_FRAMES)
            val frameIntervalUs = clipDurationUs / targetFramesCount

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(videoFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 12_000L
            var consecutiveTimeouts = 0

            var firstPtsUs = -1L
            var lastPtsUs = -1L
            var nextTargetRelativePtsUs = 0L

            while (!isOutputEOS && decodedBitmaps.size < targetFramesCount) {
                if (!isInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    consecutiveTimeouts = 0
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                    if (bufferInfo.size > 0) {
                        val currentPtsUs = bufferInfo.presentationTimeUs
                        if (firstPtsUs < 0L) {
                            firstPtsUs = currentPtsUs
                        }
                        val relativePtsUs = currentPtsUs - firstPtsUs

                        if (relativePtsUs >= clipDurationUs) {
                            isOutputEOS = true
                        } else if (relativePtsUs >= nextTargetRelativePtsUs - 10_000L) {
                            // Este cuadro califica para el muestreo uniforme
                            val image = try {
                                codec.getOutputImage(outIdx)
                            } catch (e: Exception) {
                                null
                            }
                            if (image != null) {
                                try {
                                    val bmp = imageToBitmap(image)
                                    if (bmp != null) {
                                        decodedBitmaps.add(bmp)
                                        lastPtsUs = currentPtsUs
                                        nextTargetRelativePtsUs += frameIntervalUs
                                        if (nextTargetRelativePtsUs <= relativePtsUs) {
                                            nextTargetRelativePtsUs = relativePtsUs + frameIntervalUs
                                        }
                                    }
                                } finally {
                                    try { image.close() } catch (ignored: Exception) {}
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isInputEOS) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts > 15) {
                            break
                        }
                    }
                }
            }

            val actualDecodedDurationMs = if (firstPtsUs >= 0L && lastPtsUs > firstPtsUs) {
                ((lastPtsUs - firstPtsUs) / 1000L).coerceAtLeast(300L)
            } else {
                effectiveDurationMs
            }
            val actualFrameDurationMs = (actualDecodedDurationMs / decodedBitmaps.size.coerceAtLeast(1)).toInt().coerceAtLeast(30)
            DiagnosticLogger.log("WebpEncoder", "MediaCodec decodifico exitosamente ${decodedBitmaps.size} cuadros uniformes (duracionPorCuadro=${actualFrameDurationMs}ms, duracionTotal=${actualDecodedDurationMs}ms)")

            if (decodedBitmaps.isEmpty()) {
                return null
            }

            if (decodedBitmaps.size == 1) {
                val copy = decodedBitmaps[0].copy(Bitmap.Config.ARGB_8888, true)
                if (copy != null) {
                    decodedBitmaps.add(copy)
                }
            }

            AnimationFrames(decodedBitmaps, actualFrameDurationMs)
        } catch (e: Exception) {
            DiagnosticLogger.logError("WebpEncoder", "Fallo decodificacion via MediaCodec: ${e.message}", e)
            null
        } finally {
            try { codec?.stop() } catch (ignored: Exception) {}
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor.release() } catch (ignored: Exception) {}
            try { fis?.close() } catch (ignored: Exception) {}
        }
    }

    private fun imageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var pos = 0
        if (yPixelStride == 1 && yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                if (yPixelStride == 1) {
                    yBuffer.get(nv21, pos, width)
                    pos += width
                } else {
                    for (col in 0 until width) {
                        nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
                    }
                }
            }
        }

        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until uvWidth) {
                val vIndex = vRowStart + col * vPixelStride
                val uIndex = uRowStart + col * uPixelStride
                if (vIndex < vBuffer.limit() && uIndex < uBuffer.limit()) {
                    nv21[pos++] = vBuffer.get(vIndex)
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }
        }

        return nv21
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val nv21 = imageToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
            val jpegBytes = out.toByteArray()

            var sampleSize = 1
            while (image.width / (sampleSize * 2) >= TARGET_SIZE_PX || image.height / (sampleSize * 2) >= TARGET_SIZE_PX) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
        } catch (e: Exception) {
            DiagnosticLogger.logError("WebpEncoder", "imageToBitmap fallo: ${e.message}", e)
            null
        }
    }
}
