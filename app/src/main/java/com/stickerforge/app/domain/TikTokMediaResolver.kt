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

data class TikTokMediaResult(
    val file: File,
    val username: String?,
    val title: String?,
    val isVideo: Boolean,
    val isPhotoCollection: Boolean
)

object TikTokMediaResolver {

    private const val MAX_DOWNLOAD_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val TIKTOK_PATTERN = Pattern.compile(
        "https?://(?:www\\.|m\\.|vm\\.|vt\\.)?tiktok\\.com/(?:@[a-zA-Z0-9_.-]+/video/([0-9]+)|@[a-zA-Z0-9_.-]+/photo/([0-9]+)|v/([0-9]+)|t/[a-zA-Z0-9_-]+|[a-zA-Z0-9_-]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun isTikTokUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                (trimmed.contains("tiktok.com", ignoreCase = true) || trimmed.contains("vt.tiktok", ignoreCase = true) || trimmed.contains("vm.tiktok", ignoreCase = true))
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.contains("mime_type=video_mp4")
    }

    /**
     * Resuelve y descarga el video o la primera foto de un TikTok a un archivo temporal en cache.
     */
    fun resolveAndDownload(context: Context, inputUrl: String): TikTokMediaResult {
        val trimmed = inputUrl.trim()
        DiagnosticLogger.log("TikTokResolver", "resolveAndDownload iniciado para: $trimmed")

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("La URL debe comenzar con http:// o https://")
        }

        // 1. Enlace directo de video o imagen
        if (isDirectMediaUrl(trimmed)) {
            DiagnosticLogger.log("TikTokResolver", "Enlace directo de medio detectado: $trimmed")
            val downloadedFile = downloadFile(context, trimmed)
            val isVideo = downloadedFile.name.endsWith(".mp4", ignoreCase = true) ||
                    downloadedFile.name.endsWith(".webm", ignoreCase = true) ||
                    trimmed.contains("video_mp4", ignoreCase = true)
            if (isVideo) {
                normalizeMp4Brands(downloadedFile)
            }
            return TikTokMediaResult(
                file = downloadedFile,
                username = null,
                title = null,
                isVideo = isVideo,
                isPhotoCollection = !isVideo
            )
        }

        if (!isTikTokUrl(trimmed)) {
            DiagnosticLogger.logError("TikTokResolver", "URL no reconocida como enlace de TikTok: $trimmed")
            throw IllegalArgumentException("La URL proporcionada no parece ser un enlace valido de TikTok.")
        }

        var resolvedMediaUrl: String? = null
        var isPhotoCollection = false
        var authorName: String? = null
        var videoTitle: String? = null

        // 2. Consultar TikWM API
        val apiEndpoints = listOf(
            "https://www.tikwm.com/api/?url=",
            "https://tikwm.com/api/?url="
        )

        for (endpoint in apiEndpoints) {
            try {
                val fullApiUrl = endpoint + java.net.URLEncoder.encode(trimmed, "UTF-8")
                DiagnosticLogger.log("TikTokResolver", "Consultando API: $endpoint...")
                val jsonStr = fetchHttpString(fullApiUrl)

                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)
                    val code = root.optInt("code", -1)
                    if (code == 0) {
                        val dataObj = root.optJSONObject("data")
                        if (dataObj != null) {
                            videoTitle = dataObj.optString("title").ifBlank { null }

                            val authorObj = dataObj.optJSONObject("author")
                            val uId = authorObj?.optString("unique_id")
                            if (!uId.isNullOrBlank()) {
                                authorName = uId
                            }

                            // Comprobar si es coleccion de fotos
                            val imagesArray = dataObj.optJSONArray("images")
                            if (imagesArray != null && imagesArray.length() > 0) {
                                isPhotoCollection = true
                                resolvedMediaUrl = imagesArray.getString(0)
                                DiagnosticLogger.log("TikTokResolver", "Coleccion de fotos detectada (${imagesArray.length()} imagenes). Primera imagen: $resolvedMediaUrl")
                                break
                            }

                            // Si es video, extraer 'play' (sin marca de agua) o 'wmplay'
                            val playUrl = dataObj.optString("play").ifBlank {
                                dataObj.optString("wmplay").ifBlank { null }
                            }

                            if (!playUrl.isNullOrBlank()) {
                                resolvedMediaUrl = playUrl
                                isPhotoCollection = false
                                DiagnosticLogger.log("TikTokResolver", "Video detectado: $resolvedMediaUrl")
                                break
                            }
                        }
                    } else {
                        val msg = root.optString("msg")
                        DiagnosticLogger.log("TikTokResolver", "TikWM respondio code=$code, msg=$msg")
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.logError("TikTokResolver", "Error al consultar endpoint $endpoint: ${e.message}", e)
            }
        }

        // 3. Si fallo con la URL original y era una URL corta, intentar resolver la redireccion y reintentar
        if (resolvedMediaUrl.isNullOrBlank() && (trimmed.contains("vt.tiktok", ignoreCase = true) || trimmed.contains("vm.tiktok", ignoreCase = true))) {
            try {
                DiagnosticLogger.log("TikTokResolver", "Intentando resolver redireccion de URL corta...")
                val redirectedUrl = resolveRedirectUrl(trimmed)
                if (!redirectedUrl.isNullOrBlank() && redirectedUrl != trimmed) {
                    DiagnosticLogger.log("TikTokResolver", "URL redirigida a: $redirectedUrl. Reintentando API...")
                    val fullApiUrl = "https://www.tikwm.com/api/?url=" + java.net.URLEncoder.encode(redirectedUrl, "UTF-8")
                    val jsonStr = fetchHttpString(fullApiUrl)
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        if (root.optInt("code", -1) == 0) {
                            val dataObj = root.optJSONObject("data")
                            if (dataObj != null) {
                                videoTitle = dataObj.optString("title").ifBlank { null }
                                val authorObj = dataObj.optJSONObject("author")
                                val uId = authorObj?.optString("unique_id")
                                if (!uId.isNullOrBlank()) authorName = uId

                                val imagesArray = dataObj.optJSONArray("images")
                                if (imagesArray != null && imagesArray.length() > 0) {
                                    isPhotoCollection = true
                                    resolvedMediaUrl = imagesArray.getString(0)
                                } else {
                                    val playUrl = dataObj.optString("play").ifBlank {
                                        dataObj.optString("wmplay").ifBlank { null }
                                    }
                                    if (!playUrl.isNullOrBlank()) {
                                        resolvedMediaUrl = playUrl
                                        isPhotoCollection = false
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.logError("TikTokResolver", "Error resolviendo redireccion: ${e.message}", e)
            }
        }

        if (resolvedMediaUrl.isNullOrBlank()) {
            DiagnosticLogger.logError("TikTokResolver", "No se pudo extraer el medio del TikTok: $trimmed")
            throw IllegalStateException("No se pudo obtener el medio de TikTok. Verifica que el video o publicacion sea publica.")
        }

        DiagnosticLogger.log("TikTokResolver", "Descargando medio desde: $resolvedMediaUrl")
        val downloadedFile = downloadFile(context, resolvedMediaUrl)
        val isVideo = !isPhotoCollection && (
                downloadedFile.name.endsWith(".mp4", ignoreCase = true) ||
                        downloadedFile.name.endsWith(".webm", ignoreCase = true) ||
                        resolvedMediaUrl.contains(".mp4", ignoreCase = true) ||
                        resolvedMediaUrl.contains("video", ignoreCase = true)
                )

        if (isVideo) {
            normalizeMp4Brands(downloadedFile)
        }

        DiagnosticLogger.log(
            "TikTokResolver",
            "Descarga completada: ${downloadedFile.name}, bytes=${downloadedFile.length()}, isVideo=$isVideo, isPhotoCollection=$isPhotoCollection, user=$authorName"
        )

        return TikTokMediaResult(
            file = downloadedFile,
            username = authorName,
            title = videoTitle,
            isVideo = isVideo,
            isPhotoCollection = isPhotoCollection
        )
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

            val status = conn.responseCode
            if (status in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                DiagnosticLogger.log("TikTokResolver", "HTTP status no exitoso: $status para $urlStr")
                null
            }
        } catch (e: Exception) {
            DiagnosticLogger.logError("TikTokResolver", "Error en fetchHttpString: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun resolveRedirectUrl(urlStr: String): String? {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    instanceFollowRedirects = false
                }
                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        redirects++
                        continue
                    }
                }
                return currentUrl
            } catch (e: Exception) {
                return currentUrl
            } finally {
                conn?.disconnect()
            }
        }
        return currentUrl
    }

    private fun downloadFile(context: Context, downloadUrl: String): File {
        var conn: HttpURLConnection? = null
        var inStream: InputStream? = null
        var outStream: FileOutputStream? = null

        val isVideoUrl = downloadUrl.contains(".mp4", ignoreCase = true) ||
                downloadUrl.contains("video", ignoreCase = true) ||
                downloadUrl.contains("mime_type=video_mp4", ignoreCase = true)

        val ext = if (isVideoUrl) "mp4" else "jpg"
        val tempFile = File(context.cacheDir, "tiktok_media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")

        try {
            val url = URL(downloadUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                instanceFollowRedirects = true
            }

            val status = conn.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Servidor respondio con HTTP $status al intentar descargar el medio.")
            }

            inStream = conn.inputStream
            outStream = FileOutputStream(tempFile)

            val buffer = ByteArray(16 * 1024)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                if (totalBytesRead > MAX_DOWNLOAD_SIZE_BYTES) {
                    throw IllegalStateException("El archivo supera el limite maximo de descarga (50 MB).")
                }
                outStream.write(buffer, 0, bytesRead)
            }
            outStream.flush()

            if (tempFile.length() == 0L) {
                throw IllegalStateException("El archivo descargado esta vacio.")
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

    /**
     * Normaliza los encabezados del archivo MP4 asegurando que marcas no estandar
     * y atomos ctts se ajusten para maxima compatibilidad con Android Stagefright.
     */
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
                            if (majorBrand.startsWith("iso5") || majorBrand.startsWith("iso6")) {
                                raf.seek(offset + 8)
                                raf.write("isom".toByteArray(Charsets.US_ASCII))
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
            DiagnosticLogger.log("TikTokResolver", "Aviso en normalizacion MP4: ${e.message}")
        }
    }
}
