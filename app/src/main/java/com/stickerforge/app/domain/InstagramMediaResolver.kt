package com.stickerforge.app.domain

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.regex.Pattern

data class InstagramMediaResult(
    val file: File,
    val username: String?,
    val title: String?,
    val isVideo: Boolean,
    val isPhotoCollection: Boolean
)

object InstagramMediaResolver {

    private const val MAX_DOWNLOAD_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val FB_CRAWLER_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    private val INSTAGRAM_PATTERN = Pattern.compile(
        "https?://(?:www\\.|m\\.)?(?:instagram\\.com|instagr\\.am)/(?:p|reel|tv)/([a-zA-Z0-9_-]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun isInstagramUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                (trimmed.contains("instagram.com", ignoreCase = true) || trimmed.contains("instagr.am", ignoreCase = true))
    }

    fun extractShortcode(url: String): String? {
        val trimmed = url.trim()
        val matcher = INSTAGRAM_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val genericMatch = Pattern.compile("/(?:p|reel|tv)/([a-zA-Z0-9_-]+)").matcher(trimmed)
        if (genericMatch.find()) {
            return genericMatch.group(1)
        }
        return null
    }

    /**
     * Resuelve y descarga el video (Reel) o la primera foto (post o carrusel) de Instagram.
     */
    fun resolveAndDownload(context: Context, inputUrl: String): InstagramMediaResult {
        val trimmed = inputUrl.trim()
        DiagnosticLogger.log("InstagramResolver", "resolveAndDownload iniciado para: $trimmed")

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("La URL debe comenzar con http:// o https://")
        }

        if (!isInstagramUrl(trimmed)) {
            DiagnosticLogger.logError("InstagramResolver", "URL no reconocida como enlace de Instagram: $trimmed")
            throw IllegalArgumentException("La URL proporcionada no parece ser un enlace valido de Instagram.")
        }

        val shortcode = extractShortcode(trimmed)
        if (shortcode.isNullOrBlank()) {
            DiagnosticLogger.logError("InstagramResolver", "No se pudo extraer el shortcode de Instagram: $trimmed")
            throw IllegalArgumentException("No se encontro un codigo de publicacion o Reel de Instagram en el enlace.")
        }

        DiagnosticLogger.log("InstagramResolver", "Shortcode extraido: $shortcode")

        var resolvedMediaUrl: String? = null
        var isVideo = trimmed.contains("/reel/", ignoreCase = true)
        var isPhotoCollection = false
        var authorUsername: String? = null
        var postTitle: String? = null

        // 1. Obtener metadatos oficiales via oEmbed si estan disponibles
        try {
            val oembedUrl = "https://api.instagram.com/oembed/?url=https://www.instagram.com/p/$shortcode/"
            val oembedJson = fetchHttpString(oembedUrl)
            if (!oembedJson.isNullOrBlank()) {
                val oembed = JSONObject(oembedJson)
                authorUsername = oembed.optString("author_name").ifBlank { null }
                postTitle = oembed.optString("title").ifBlank { null }
                val thumb = oembed.optString("thumbnail_url").ifBlank { null }
                if (thumb != null && !isVideo && resolvedMediaUrl.isNullOrBlank()) {
                    resolvedMediaUrl = thumb
                    isPhotoCollection = true
                }
                DiagnosticLogger.log("InstagramResolver", "oEmbed exito: autor=$authorUsername, titulo=$postTitle")
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("InstagramResolver", "oEmbed no disponible: ${e.message}")
        }

        // 2. Estrategia A: SnapSave Extractor (Motor de alta precision para Reels, fotos y carruseles)
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                DiagnosticLogger.log("InstagramResolver", "Consultando SnapSave engine...")
                val snapCandidate = resolveViaSnapSave("https://www.instagram.com/p/$shortcode/")
                if (snapCandidate != null) {
                    resolvedMediaUrl = snapCandidate.url
                    isVideo = snapCandidate.isVideo
                    isPhotoCollection = snapCandidate.isPhoto
                    DiagnosticLogger.log("InstagramResolver", "SnapSave resolvio medio: $resolvedMediaUrl (isVideo=$isVideo, isPhoto=$isPhotoCollection)")
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("InstagramResolver", "SnapSave engine fallo: ${e.message}")
            }
        }

        // 3. Estrategia B: Cobalt v10 con instancias comunitarias sin auth (CobaltInstanceProvider)
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                DiagnosticLogger.log("InstagramResolver", "Consultando Cobalt v10...")
                val cobaltResult = CobaltInstanceProvider.resolveMedia("https://www.instagram.com/p/$shortcode/")
                if (cobaltResult != null) {
                    resolvedMediaUrl = cobaltResult.url
                    isVideo = cobaltResult.isVideo
                    isPhotoCollection = !cobaltResult.isVideo
                    DiagnosticLogger.log("InstagramResolver", "Cobalt v10 resolvio medio: $resolvedMediaUrl (isVideo=$isVideo)")
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("InstagramResolver", "Cobalt v10 fallo: ${e.message}")
            }
        }

        // 4. Estrategia C: Proxy fixup eeinstagram.com
        if (resolvedMediaUrl.isNullOrBlank()) {
            val fixupPaths = listOf("/reel/$shortcode/", "/p/$shortcode/")
            for (path in fixupPaths) {
                if (!resolvedMediaUrl.isNullOrBlank()) break
                try {
                    val eeUrl = "https://eeinstagram.com$path"
                    DiagnosticLogger.log("InstagramResolver", "Consultando eeinstagram: $eeUrl...")
                    val html = fetchHttpString(eeUrl, FB_CRAWLER_USER_AGENT)
                    if (!html.isNullOrBlank()) {
                        val videoMatch = Pattern.compile("property=\"og:video\" content=\"([^\"]+)\"").matcher(html)
                        if (videoMatch.find()) {
                            val vidPath = videoMatch.group(1) ?: ""
                            resolvedMediaUrl = if (vidPath.startsWith("http")) vidPath else "https://eeinstagram.com$vidPath"
                            isVideo = true
                            isPhotoCollection = false
                            DiagnosticLogger.log("InstagramResolver", "eeinstagram resolvio video: $resolvedMediaUrl")
                            break
                        }
                        val imgMatch = Pattern.compile("property=\"og:image\" content=\"([^\"]+)\"").matcher(html)
                        if (imgMatch.find()) {
                            resolvedMediaUrl = imgMatch.group(1)?.replace("&amp;", "&")
                            isVideo = false
                            isPhotoCollection = true
                            DiagnosticLogger.log("InstagramResolver", "eeinstagram resolvio foto: $resolvedMediaUrl")
                            break
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.log("InstagramResolver", "eeinstagram fallo: ${e.message}")
                }
            }
        }

        // 5. Estrategia D: Extraccion directa con crawler OpenGraph de Instagram
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                val directIgUrl = "https://www.instagram.com/p/$shortcode/"
                DiagnosticLogger.log("InstagramResolver", "Consultando OpenGraph crawler directo...")
                val igHtml = fetchHttpString(directIgUrl, FB_CRAWLER_USER_AGENT)
                if (!igHtml.isNullOrBlank()) {
                    if (postTitle == null) {
                        val titleMatch = Pattern.compile("property=\"og:title\" content=\"([^\"]+)\"").matcher(igHtml)
                        if (titleMatch.find()) {
                            postTitle = titleMatch.group(1)?.replace("&quot;", "\"")
                        }
                    }
                    val videoMatch = Pattern.compile("property=\"og:video\" content=\"([^\"]+)\"").matcher(igHtml)
                    if (videoMatch.find()) {
                        resolvedMediaUrl = videoMatch.group(1)?.replace("&amp;", "&")
                        isVideo = true
                        isPhotoCollection = false
                        DiagnosticLogger.log("InstagramResolver", "Crawler directo detecto video: $resolvedMediaUrl")
                    } else {
                        val imgMatch = Pattern.compile("property=\"og:image\" content=\"([^\"]+)\"").matcher(igHtml)
                        if (imgMatch.find()) {
                            resolvedMediaUrl = imgMatch.group(1)?.replace("&amp;", "&")
                            isVideo = false
                            isPhotoCollection = true
                            DiagnosticLogger.log("InstagramResolver", "Crawler directo detecto foto: $resolvedMediaUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("InstagramResolver", "Crawler directo fallo: ${e.message}")
            }
        }

        // 6. Estrategia E: Fallback para fotos via CDN directo
        if (resolvedMediaUrl.isNullOrBlank() && !isVideo) {
            val directCdnThumb = "https://instagram.com/p/$shortcode/media/?size=l"
            resolvedMediaUrl = directCdnThumb
            isVideo = false
            isPhotoCollection = true
            DiagnosticLogger.log("InstagramResolver", "Usando fallback de foto directa: $resolvedMediaUrl")
        }

        if (resolvedMediaUrl.isNullOrBlank()) {
            DiagnosticLogger.logError("InstagramResolver", "No se pudo extraer el medio de Instagram para: $trimmed")
            throw IllegalStateException("No se pudo obtener el medio de Instagram. Verifica que el Reel o publicacion sea publica.")
        }

        DiagnosticLogger.log("InstagramResolver", "Descargando medio desde: $resolvedMediaUrl (isVideo=$isVideo)")
        val downloadedFile = downloadFile(context, resolvedMediaUrl, isVideo)
        DiagnosticLogger.log("InstagramResolver", "Descarga completada: ${downloadedFile.name}, tamano=${downloadedFile.length()} bytes")

        if (isVideo) {
            normalizeMp4Brands(downloadedFile)
        }

        return InstagramMediaResult(
            file = downloadedFile,
            username = authorUsername,
            title = postTitle,
            isVideo = isVideo,
            isPhotoCollection = isPhotoCollection
        )
    }

    private data class SnapSaveCandidate(
        val url: String,
        val isVideo: Boolean,
        val isPhoto: Boolean
    )

    private fun resolveViaSnapSave(targetUrl: String): SnapSaveCandidate? {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = "https://snapsave.app/action.php"
            val postData = "url=" + URLEncoder.encode(targetUrl, "UTF-8")
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Origin", "https://snapsave.app")
                setRequestProperty("Referer", "https://snapsave.app/")
                instanceFollowRedirects = true
            }

            conn.outputStream.bufferedWriter().use { it.write(postData) }

            if (conn.responseCode in 200..299) {
                val jsResponse = conn.inputStream.bufferedReader().use { it.readText() }
                val paramMatcher = Pattern.compile("\\(\"([^\"]+)\",\\s*(\\d+),\\s*\"([^\"]+)\",\\s*(\\d+),\\s*(\\d+)").matcher(jsResponse)
                if (paramMatcher.find()) {
                    val h = paramMatcher.group(1) ?: return null
                    val u = paramMatcher.group(2)?.toIntOrNull() ?: return null
                    val n = paramMatcher.group(3) ?: return null
                    val t = paramMatcher.group(4)?.toIntOrNull() ?: return null
                    val e = paramMatcher.group(5)?.toIntOrNull() ?: return null

                    val decodedHtml = decodeSnapSave(h, u, n, t, e)
                    if (decodedHtml.isNotBlank()) {
                        val isVideo = decodedHtml.contains("icon-dlvideo") || decodedHtml.contains(".mp4") || decodedHtml.contains("Download video")
                        val isPhoto = decodedHtml.contains("icon-dlimage") || decodedHtml.contains("Download Photo") || !isVideo

                        // Extraer URL de descarga directa
                        var mediaUrl: String? = null
                        val rawHrefMatcher = Pattern.compile("href=\\\\?\"(https://[^\"]+?)\\\\?\"").matcher(decodedHtml)
                        if (rawHrefMatcher.find()) {
                            mediaUrl = rawHrefMatcher.group(1)?.replace("\\", "")
                        } else {
                            val generalHref = Pattern.compile("href=\"(https://[^\"]+)\"").matcher(decodedHtml.replace("\\\"", "\""))
                            if (generalHref.find()) {
                                mediaUrl = generalHref.group(1)
                            }
                        }

                        val validMediaUrl = mediaUrl
                        if (!validMediaUrl.isNullOrBlank()) {
                            var finalMediaUrl: String = validMediaUrl
                            // Si contiene un JWT con token=, extraer la URL directa de Instagram CDN
                            if (validMediaUrl.contains("token=")) {
                                try {
                                    val tokenStr = validMediaUrl.substringAfter("token=").substringBefore("&")
                                    val parts = tokenStr.split(".")
                                    if (parts.size >= 2) {
                                        val payloadBase64 = parts[1]
                                        val padded = payloadBase64 + "=".repeat((4 - (payloadBase64.length % 4)) % 4)
                                        val decodedBytes = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
                                        val jwtJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
                                        val directCdnUrl = jwtJson.optString("url")
                                        if (directCdnUrl.isNotBlank()) {
                                            finalMediaUrl = directCdnUrl
                                        }
                                    }
                                } catch (e: Exception) {
                                    DiagnosticLogger.log("InstagramResolver", "Aviso: no se pudo decodificar JWT, usando URL base de descarga")
                                }
                            }

                            SnapSaveCandidate(
                                url = finalMediaUrl,
                                isVideo = isVideo,
                                isPhoto = isPhoto
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("InstagramResolver", "SnapSave exception: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Decodificador nativo en Kotlin para el script empaquetado de SnapSave.
     */
    fun decodeSnapSave(h: String, @Suppress("UNUSED_PARAMETER") u: Int, n: String, t: Int, e: Int): String {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
        val hDigits = digits.take(e)
        if (e >= n.length) return ""
        val delimiter = n[e]
        val result = StringBuilder()
        var i = 0
        val len = h.length

        while (i < len) {
            val s = StringBuilder()
            while (i < len && h[i] != delimiter) {
                s.append(h[i])
                i++
            }
            i++ // Omitir delimitador
            if (s.isEmpty()) continue

            var sStr = s.toString()
            for (j in n.indices) {
                sStr = sStr.replace(n[j].toString(), j.toString())
            }

            // Conversion de base e a base 10
            var value = 0L
            var power = 1L
            val reversed = sStr.reversed()
            for (ch in reversed) {
                val digitIdx = hDigits.indexOf(ch)
                if (digitIdx != -1) {
                    value += digitIdx * power
                }
                power *= e
            }

            val charCode = (value - t).toInt()
            result.append(charCode.toChar())
        }

        return result.toString()
    }

    private fun downloadFile(context: Context, downloadUrl: String, isVideo: Boolean): File {
        var conn: HttpURLConnection? = null
        var inStream: InputStream? = null
        var outStream: FileOutputStream? = null

        val ext = if (isVideo) "mp4" else "jpg"
        val tempFile = File(context.cacheDir, "instagram_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")

        try {
            val url = URL(downloadUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Range", "bytes=0-${MAX_DOWNLOAD_SIZE_BYTES - 1}")
                instanceFollowRedirects = true
            }

            var status = conn.responseCode
            if (status == 416) {
                // Si el servidor rechaza Range, reconectar sin cabecera Range
                conn.disconnect()
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                }
                status = conn.responseCode
            }

            if (status !in 200..299 && status != 206) {
                throw IllegalStateException("Servidor respondio con HTTP $status al intentar descargar el medio de Instagram.")
            }

            inStream = conn.inputStream
            outStream = FileOutputStream(tempFile)

            val buffer = ByteArray(16 * 1024)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                outStream.write(buffer, 0, bytesRead)
                if (totalBytesRead >= MAX_DOWNLOAD_SIZE_BYTES) {
                    DiagnosticLogger.log("InstagramResolver", "Limite seguro de 50 MB alcanzado, finalizando stream para procesar los primeros 9.6s.")
                    break
                }
            }
            outStream.flush()

            if (tempFile.length() == 0L) {
                throw IllegalStateException("El archivo de Instagram descargado esta vacio.")
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

    private fun fetchHttpString(urlStr: String, userAgent: String = USER_AGENT): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json, text/html, */*")
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

                    if (boxType == "mdat") {
                        if (offset + actualBoxSize > fileLength) {
                            val remainingBytes = fileLength - offset
                            if (remainingBytes in 8..0x7FFFFFFFL) {
                                raf.seek(offset)
                                raf.writeInt(remainingBytes.toInt())
                                DiagnosticLogger.log("InstagramResolver", "Parcheado atomo mdat a $remainingBytes bytes en ${file.name}")
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
            DiagnosticLogger.log("InstagramResolver", "Aviso en normalizacion MP4: ${e.message}")
        }
    }
}
