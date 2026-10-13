package com.stickerforge.app.domain

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.regex.Pattern

data class YouTubeMediaResult(
    val file: File,
    val author: String?,
    val title: String?,
    val isVideo: Boolean
)

object YouTubeMediaResolver {

    // Limite maximo de descarga optimizado para videos largos de YouTube (10 MB cubre holgadamente >30-60s a 720p/1080p y es el limite maximo admitido por peticion de buffer en la CDN de Google)
    const val MAX_VIDEO_DOWNLOAD_BYTES = 10 * 1024 * 1024L // 10 MB
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val IOS_USER_AGENT = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

    private val YOUTUBE_WATCH_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/watch\\?.*?v=([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    private val YOUTUBE_SHORTS_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    private val YOUTUBE_SHORT_URL_PATTERN = Pattern.compile(
        "(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    private val YOUTUBE_EMBED_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    fun isYouTubeUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                (trimmed.contains("youtube.com", ignoreCase = true) || trimmed.contains("youtu.be", ignoreCase = true))
    }

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()

        val shortsMatcher = YOUTUBE_SHORTS_PATTERN.matcher(trimmed)
        if (shortsMatcher.find()) return shortsMatcher.group(1)

        val watchMatcher = YOUTUBE_WATCH_PATTERN.matcher(trimmed)
        if (watchMatcher.find()) return watchMatcher.group(1)

        val shortUrlMatcher = YOUTUBE_SHORT_URL_PATTERN.matcher(trimmed)
        if (shortUrlMatcher.find()) return shortUrlMatcher.group(1)

        val embedMatcher = YOUTUBE_EMBED_PATTERN.matcher(trimmed)
        if (embedMatcher.find()) return embedMatcher.group(1)

        // Busqueda generica de parametro v= o codigo de 11 caracteres
        val genericV = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})").matcher(trimmed)
        if (genericV.find()) return genericV.group(1)

        return null
    }

    /**
     * Resuelve y descarga los primeros segundos (optimizados a <= 15 MB) de un video o Short de YouTube.
     */
    fun resolveAndDownload(context: Context, inputUrl: String): YouTubeMediaResult {
        val trimmed = inputUrl.trim()
        DiagnosticLogger.log("YouTubeResolver", "resolveAndDownload iniciado para: $trimmed")

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("La URL debe comenzar con http:// o https://")
        }

        if (!isYouTubeUrl(trimmed)) {
            DiagnosticLogger.logError("YouTubeResolver", "URL no reconocida como enlace de YouTube: $trimmed")
            throw IllegalArgumentException("La URL proporcionada no parece ser un enlace valido de YouTube.")
        }

        val videoId = extractVideoId(trimmed)
        if (videoId.isNullOrBlank()) {
            DiagnosticLogger.logError("YouTubeResolver", "No se pudo extraer el ID de video de YouTube: $trimmed")
            throw IllegalArgumentException("No se encontro un ID de video de YouTube valido en el enlace.")
        }

        DiagnosticLogger.log("YouTubeResolver", "ID de video extraido: $videoId")

        var resolvedMediaUrl: String? = null
        var videoTitle: String? = null
        var authorName: String? = null

        // 1. Obtener metadatos publicos oficiales via oEmbed
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val oembedJson = fetchHttpString(oembedUrl)
            if (!oembedJson.isNullOrBlank()) {
                val oembed = JSONObject(oembedJson)
                videoTitle = oembed.optString("title").ifBlank { null }
                authorName = oembed.optString("author_name").ifBlank { null }
                DiagnosticLogger.log("YouTubeResolver", "oEmbed exito: titulo=$videoTitle, autor=$authorName")
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("YouTubeResolver", "oEmbed no disponible: ${e.message}")
        }

        // 2. Estrategia A (Principal): YouTube Innertube iOS Player API (Directo a googlevideo.com sin intermediarios ni login)
        try {
            DiagnosticLogger.log("YouTubeResolver", "Consultando YouTube Innertube iOS API...")
            val innertubeResult = resolveViaInnertubeIos(videoId)
            if (innertubeResult != null && innertubeResult.url.isNotBlank()) {
                resolvedMediaUrl = innertubeResult.url
                if (videoTitle == null) videoTitle = innertubeResult.title
                if (authorName == null) authorName = innertubeResult.author
                DiagnosticLogger.log("YouTubeResolver", "Innertube iOS resolvio exitosamente stream directo")
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("YouTubeResolver", "Innertube iOS excepcion: ${e.message}")
        }

        // 3. Estrategia B (Fallback): Cobalt v10 con instancias sin auth (CobaltInstanceProvider)
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                DiagnosticLogger.log("YouTubeResolver", "Consultando Cobalt v10...")
                val standardYoutubeUrl = "https://www.youtube.com/watch?v=$videoId"
                val cobaltResult = CobaltInstanceProvider.resolveMedia(standardYoutubeUrl, videoQuality = "720")
                if (cobaltResult != null && cobaltResult.url.isNotBlank()) {
                    resolvedMediaUrl = cobaltResult.url
                    DiagnosticLogger.log("YouTubeResolver", "Cobalt v10 resolvio stream: $resolvedMediaUrl")
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("YouTubeResolver", "Cobalt v10 fallo: ${e.message}")
            }
        }

        // 4. Estrategia C (Fallback): Invidious API instances
        if (resolvedMediaUrl.isNullOrBlank()) {
            val invidiousInstances = listOf(
                "https://inv.nadeko.net",
                "https://invidious.nerdvpn.de",
                "https://invidious.jing.rocks",
                "https://invidious.f5.si"
            )

            for (instance in invidiousInstances) {
                if (!resolvedMediaUrl.isNullOrBlank()) break
                try {
                    val apiUrl = "$instance/api/v1/videos/$videoId"
                    DiagnosticLogger.log("YouTubeResolver", "Consultando Invidious: $apiUrl...")
                    val jsonStr = fetchHttpString(apiUrl)
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        if (videoTitle == null) {
                            videoTitle = root.optString("title").ifBlank { null }
                        }
                        if (authorName == null) {
                            authorName = root.optString("author").ifBlank { null }
                        }

                        val formatStreams = root.optJSONArray("formatStreams")
                        if (formatStreams != null && formatStreams.length() > 0) {
                            for (i in 0 until formatStreams.length()) {
                                val streamObj = formatStreams.getJSONObject(i)
                                val sUrl = streamObj.optString("url")
                                val container = streamObj.optString("container")
                                if (sUrl.isNotBlank() && (container.contains("mp4", ignoreCase = true) || sUrl.contains(".mp4"))) {
                                    resolvedMediaUrl = sUrl
                                    DiagnosticLogger.log("YouTubeResolver", "Invidious formatStreams resolvio URL: $resolvedMediaUrl")
                                    break
                                }
                            }
                        }

                        if (resolvedMediaUrl.isNullOrBlank()) {
                            val adaptiveFormats = root.optJSONArray("adaptiveFormats")
                            if (adaptiveFormats != null) {
                                for (i in 0 until adaptiveFormats.length()) {
                                    val streamObj = adaptiveFormats.getJSONObject(i)
                                    val sUrl = streamObj.optString("url")
                                    val type = streamObj.optString("type")
                                    if (sUrl.isNotBlank() && type.startsWith("video/mp4", ignoreCase = true)) {
                                        resolvedMediaUrl = sUrl
                                        DiagnosticLogger.log("YouTubeResolver", "Invidious adaptiveFormats resolvio URL: $resolvedMediaUrl")
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.log("YouTubeResolver", "Invidious instance $instance fallo: ${e.message}")
                }
            }
        }

        if (resolvedMediaUrl.isNullOrBlank()) {
            DiagnosticLogger.logError("YouTubeResolver", "No se pudo extraer la pista de video de YouTube para: $trimmed")
            throw IllegalStateException("No se pudo obtener el video de YouTube. Verifica que el video o Short sea publico.")
        }

        DiagnosticLogger.log("YouTubeResolver", "Descargando video optimizado (limite maximo ${MAX_VIDEO_DOWNLOAD_BYTES / (1024 * 1024)} MB)...")
        val downloadedFile = downloadVideoWithSafetyLimit(context, resolvedMediaUrl)
        DiagnosticLogger.log("YouTubeResolver", "Descarga completada: ${downloadedFile.name}, tamano=${downloadedFile.length()} bytes")

        // Normalizar marcas MP4 y ajustar atomos truncados para compatibilidad perfecta con MediaCodec/Stagefright
        normalizeMp4Brands(downloadedFile)

        return YouTubeMediaResult(
            file = downloadedFile,
            author = authorName,
            title = videoTitle,
            isVideo = true
        )
    }

    private fun openVideoConnection(downloadUrl: String, rangeLimitBytes: Long?): HttpURLConnection {
        val url = URL(downloadUrl)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            val isGoogleVideo = downloadUrl.contains("googlevideo.com", ignoreCase = true)
            setRequestProperty("User-Agent", if (isGoogleVideo) IOS_USER_AGENT else USER_AGENT)
            setRequestProperty("Accept", "*/*")
            if (rangeLimitBytes != null && rangeLimitBytes > 0) {
                setRequestProperty("Range", "bytes=0-${rangeLimitBytes - 1}")
            }
            instanceFollowRedirects = true
        }
    }

    /**
     * Descarga el video utilizando un limite seguro de bytes (10 MB).
     * Evita descargar videos largos enteros (de 20 min o varias horas) cuando solo se requieren los primeros 9.6 segundos.
     */
    private fun downloadVideoWithSafetyLimit(context: Context, downloadUrl: String): File {
        var conn: HttpURLConnection? = null
        var inStream: InputStream? = null
        var outStream: FileOutputStream? = null

        val tempFile = File(context.cacheDir, "youtube_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4")

        try {
            var activeLimitBytes = MAX_VIDEO_DOWNLOAD_BYTES
            conn = openVideoConnection(downloadUrl, activeLimitBytes)
            var status = conn.responseCode

            // Si GoogleVideo devuelve 403 por exceder el buffer inicial en la pista seleccionada, reintentar con 8 MB y 5 MB
            if (status == 403 && downloadUrl.contains("googlevideo.com", ignoreCase = true)) {
                conn.disconnect()
                DiagnosticLogger.log("YouTubeResolver", "HTTP 403 con buffer de 10 MB, reintentando con rango de 8 MB...")
                activeLimitBytes = 8 * 1024 * 1024L
                conn = openVideoConnection(downloadUrl, activeLimitBytes)
                status = conn.responseCode
                if (status == 403) {
                    conn.disconnect()
                    DiagnosticLogger.log("YouTubeResolver", "HTTP 403 con buffer de 8 MB, reintentando con rango de 5 MB...")
                    activeLimitBytes = 5 * 1024 * 1024L
                    conn = openVideoConnection(downloadUrl, activeLimitBytes)
                    status = conn.responseCode
                }
            }

            if (status == 416) {
                // Si el servidor rechaza el header de rango (ej. Short mas corto que el limite), reconectar en streaming continuo
                conn.disconnect()
                DiagnosticLogger.log("YouTubeResolver", "HTTP 416 (rango fuera de limite), reconectando en streaming regular...")
                activeLimitBytes = MAX_VIDEO_DOWNLOAD_BYTES
                conn = openVideoConnection(downloadUrl, null)
                status = conn.responseCode
            }

            if (status !in 200..299 && status != 206) {
                throw IllegalStateException("Servidor respondio con HTTP $status al intentar descargar el video de YouTube.")
            }

            inStream = conn.inputStream
            outStream = FileOutputStream(tempFile)

            val buffer = ByteArray(16 * 1024)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                outStream.write(buffer, 0, bytesRead)
                if (totalBytesRead >= activeLimitBytes) {
                    DiagnosticLogger.log("YouTubeResolver", "Limite seguro de ${activeLimitBytes / (1024 * 1024)} MB alcanzado, finalizando stream para procesar el clip de 9.6s.")
                    break
                }
            }
            outStream.flush()

            if (tempFile.length() == 0L) {
                throw IllegalStateException("El archivo de video descargado esta vacio.")
            }

            return tempFile
        } catch (e: Exception) {
            try { tempFile.delete() } catch (ignored: Exception) {}
            throw e
        } finally {
            try { inStream?.close() } catch (ignored: Exception) {}
            try { outStream?.close() } catch (ignored: Exception) {}
            conn?.disconnect()
        }
    }

    private fun fetchHttpString(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    data class InnertubeResult(
        val url: String,
        val title: String?,
        val author: String?
    )

    /**
     * Resuelve el enlace directo al stream multimedia de YouTube usando el cliente nativo iOS de Innertube.
     * No requiere autenticacion, ni cookies, ni proxies intermediarios, y entrega streams H.264 MP4 directos
     * desde la CDN oficial de Google (googlevideo.com).
     */
    fun resolveViaInnertubeIos(videoId: String): InnertubeResult? {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = "https://www.youtube.com/youtubei/v1/player"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "20.10.4")
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPhone16,2")
                        put("userAgent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
                        put("osName", "iPhone")
                        put("osVersion", "18.3.2")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }.toString()

            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
                setRequestProperty("X-YouTube-Client-Name", "5")
                setRequestProperty("X-YouTube-Client-Version", "20.10.4")
                instanceFollowRedirects = true
            }

            conn.outputStream.bufferedWriter().use { it.write(payload) }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val status = root.optJSONObject("playabilityStatus")?.optString("status")
                if (status != "OK") {
                    DiagnosticLogger.log("YouTubeResolver", "Innertube iOS playabilityStatus: $status")
                    return null
                }

                val details = root.optJSONObject("videoDetails")
                val title = details?.optString("title")?.ifBlank { null }
                val author = details?.optString("author")?.ifBlank { null }

                val streamingData = root.optJSONObject("streamingData") ?: return null

                // 1. Buscar en muxed formats
                val formats = streamingData.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.optJSONObject(i) ?: continue
                        val sUrl = f.optString("url")
                        val mime = f.optString("mimeType")
                        if (sUrl.isNotBlank() && mime.contains("video/mp4", ignoreCase = true)) {
                            DiagnosticLogger.log("YouTubeResolver", "Innertube iOS resolvio formato muxed: ${f.optString("qualityLabel")}")
                            return InnertubeResult(sUrl, title, author)
                        }
                    }
                }

                // 2. Buscar en adaptiveFormats (preferir 720p, luego 480p, 360p, 1080p)
                val adaptive = streamingData.optJSONArray("adaptiveFormats")
                if (adaptive != null && adaptive.length() > 0) {
                    val candidates = mutableListOf<JSONObject>()
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.optJSONObject(i) ?: continue
                        val sUrl = f.optString("url")
                        val mime = f.optString("mimeType")
                        if (sUrl.isNotBlank() && mime.contains("video/mp4", ignoreCase = true)) {
                            candidates.add(f)
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        // Priorizar codecs H.264 (avc1) para compatibilidad universal con decodificadores hardware de Android
                        val avcCandidates = candidates.filter { it.optString("mimeType").contains("avc1", ignoreCase = true) }
                        val pool = if (avcCandidates.isNotEmpty()) avcCandidates else candidates

                        val preferred = pool.firstOrNull { it.optString("qualityLabel").startsWith("720") }
                            ?: pool.firstOrNull { it.optString("qualityLabel").startsWith("480") }
                            ?: pool.firstOrNull { it.optString("qualityLabel").startsWith("360") }
                            ?: pool.firstOrNull { it.optString("qualityLabel").startsWith("1080") }
                            ?: pool.first()

                        val sUrl = preferred.optString("url")
                        DiagnosticLogger.log("YouTubeResolver", "Innertube iOS resolvio stream adaptativo: ${preferred.optString("qualityLabel")} (${preferred.optString("mimeType")})")
                        return InnertubeResult(sUrl, title, author)
                    }
                }
            } else {
                DiagnosticLogger.log("YouTubeResolver", "Innertube iOS respondio con HTTP ${conn.responseCode}")
            }
            null
        } catch (e: Exception) {
            DiagnosticLogger.log("YouTubeResolver", "Innertube iOS fallo: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun normalizeMp4Brands(file: File) {
        if (!file.exists() || file.length() < 16) return
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val fileLength = raf.length()
                val header = ByteArray(8)
                var offset = 0L

                while (offset + 8 <= fileLength) {
                    raf.seek(offset)
                    raf.readFully(header)
                    val size = ((header[0].toLong() and 0xFF) shl 24) or
                            ((header[1].toLong() and 0xFF) shl 16) or
                            ((header[2].toLong() and 0xFF) shl 8) or
                            (header[3].toLong() and 0xFF)
                    val boxType = String(header, 4, 4, Charsets.US_ASCII)

                    val actualBoxSize = if (size == 1L) {
                        if (offset + 16 > fileLength) break
                        raf.readLong()
                    } else if (size == 0L) {
                        fileLength - offset
                    } else {
                        size
                    }

                    if (actualBoxSize < 8) break

                    if (boxType == "ftyp") {
                        if (offset + 16 <= fileLength) {
                            raf.seek(offset + 8)
                            val brandBytes = ByteArray(4)
                            raf.readFully(brandBytes)
                            val majorBrand = String(brandBytes, Charsets.US_ASCII)
                            if (majorBrand.startsWith("iso5") || majorBrand.startsWith("iso6") || majorBrand.startsWith("dash")) {
                                raf.seek(offset + 8)
                                raf.write("isom".toByteArray(Charsets.US_ASCII))
                            }
                        }
                    }

                    if (boxType == "mdat") {
                        if (offset + actualBoxSize > fileLength) {
                            val remainingBytes = fileLength - offset
                            if (remainingBytes in 8..0x7FFFFFFFL) {
                                raf.seek(offset)
                                raf.writeInt(remainingBytes.toInt())
                            }
                        }
                    }

                    if (boxType == "ctts") {
                        val cttsPayloadOffset = if (size == 1L) offset + 16 else offset + 8
                        if (cttsPayloadOffset + 4 <= fileLength) {
                            raf.seek(cttsPayloadOffset)
                            val verFlags = raf.readInt()
                            if (verFlags != 0) {
                                raf.seek(cttsPayloadOffset)
                                raf.writeInt(0)
                            }
                        }
                    }

                    if (boxType == "moov" || boxType == "trak" || boxType == "mdia" || boxType == "minf" || boxType == "stbl") {
                        offset += if (size == 1L) 16 else 8
                    } else {
                        offset += actualBoxSize
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("YouTubeResolver", "Aviso en normalizacion MP4: ${e.message}")
        }
    }
}
