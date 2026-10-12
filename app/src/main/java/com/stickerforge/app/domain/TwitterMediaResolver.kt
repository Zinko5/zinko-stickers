package com.stickerforge.app.domain

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class TwitterMediaResult(
    val file: File,
    val username: String?,
    val tweetText: String?,
    val isVideo: Boolean
)

object TwitterMediaResolver {

    private const val MAX_DOWNLOAD_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val TWEET_PATTERN_WITH_USER = Pattern.compile(
        "https?://(?:www\\.|mobile\\.)?(?:twitter\\.com|x\\.com|fxtwitter\\.com|vxtwitter\\.com|fixupx\\.com)/([a-zA-Z0-9_]+)/status(?:es)?/([0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val TWEET_PATTERN_WITHOUT_USER = Pattern.compile(
        "https?://(?:www\\.|mobile\\.)?(?:twitter\\.com|x\\.com)/i/(?:web/)?status(?:es)?/([0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Resuelve y descarga el video o GIF de un tweet o enlace directo a un archivo temporal en cache.
     */
    fun resolveAndDownload(context: Context, inputUrl: String): TwitterMediaResult {
        val trimmed = inputUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("La URL debe comenzar con http:// o https://")
        }

        // 1. Si es un enlace directo de video o imagen twimg/mp4/gif
        if (isDirectMediaUrl(trimmed)) {
            val downloadedFile = downloadFile(context, trimmed)
            val isVideo = downloadedFile.name.endsWith(".mp4", ignoreCase = true) ||
                    downloadedFile.name.endsWith(".webm", ignoreCase = true)
            return TwitterMediaResult(
                file = downloadedFile,
                username = null,
                tweetText = null,
                isVideo = isVideo
            )
        }

        // 2. Extraer usuario y ID del tweet
        var username: String? = null
        var tweetId: String? = null

        val matcherWithUser = TWEET_PATTERN_WITH_USER.matcher(trimmed)
        if (matcherWithUser.find()) {
            username = matcherWithUser.group(1)
            tweetId = matcherWithUser.group(2)
        } else {
            val matcherWithoutUser = TWEET_PATTERN_WITHOUT_USER.matcher(trimmed)
            if (matcherWithoutUser.find()) {
                username = "i"
                tweetId = matcherWithoutUser.group(1)
            }
        }

        if (tweetId == null) {
            // Intentar buscar cualquier secuencia de dígitos de status
            val genericMatch = Pattern.compile("/status/([0-9]+)").matcher(trimmed)
            if (genericMatch.find()) {
                tweetId = genericMatch.group(1)
                username = "i"
            }
        }

        if (tweetId == null) {
            throw IllegalArgumentException("No se encontro un ID de tweet valido en la URL proporcionada.")
        }

        val effectiveUsername = username ?: "i"

        // 3. Estrategia A: FxTwitter JSON API
        var resolvedMediaUrl: String? = null
        var authorName: String? = if (effectiveUsername != "i") effectiveUsername else null
        var tweetText: String? = null

        try {
            // Intentar primero con usuario y luego endpoint directo /status/
            var jsonStr = fetchHttpString("https://api.fxtwitter.com/$effectiveUsername/status/$tweetId")
            if (jsonStr.isNullOrBlank()) {
                jsonStr = fetchHttpString("https://api.fxtwitter.com/status/$tweetId")
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                if (root.optInt("code") == 200 || root.optString("message") == "OK") {
                    val tweetObj = root.optJSONObject("tweet")
                    if (tweetObj != null) {
                        tweetText = tweetObj.optString("text").ifBlank { null }
                        val authorObj = tweetObj.optJSONObject("author")
                        val screenName = authorObj?.optString("screen_name")
                        if (!screenName.isNullOrBlank()) {
                            authorName = screenName
                        }

                        resolvedMediaUrl = extractPlayableMediaUrlFromTweetJson(tweetObj)
                    }
                }
            }
        } catch (e: Exception) {
            // Continuar con fallback
        }

        // 4. Estrategia B: Twitter Syndication CDN API
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                val token = calculateSyndicationToken(tweetId)
                val syndicationUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en&token=$token"
                val jsonStr = fetchHttpString(syndicationUrl)
                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)
                    val userObj = root.optJSONObject("user")
                    val screenName = userObj?.optString("screen_name")
                    if (!screenName.isNullOrBlank()) {
                        authorName = screenName
                    }
                    if (tweetText == null) {
                        tweetText = root.optString("text").ifBlank { null }
                    }

                    val mediaDetails = root.optJSONArray("mediaDetails")
                    if (mediaDetails != null) {
                        for (i in 0 until mediaDetails.length()) {
                            val media = mediaDetails.getJSONObject(i)
                            val type = media.optString("type")
                            if (type == "video" || type == "animated_gif") {
                                val videoInfo = media.optJSONObject("video_info")
                                val variants = videoInfo?.optJSONArray("variants")
                                if (variants != null) {
                                    var bestBitrate = -1
                                    var bestUrl: String? = null
                                    for (j in 0 until variants.length()) {
                                        val v = variants.getJSONObject(j)
                                        val vUrl = v.optString("url")
                                        val contentType = v.optString("content_type")
                                        if (contentType == "video/mp4" || vUrl.contains(".mp4") || vUrl.contains("tweet_video")) {
                                            val bitrate = v.optInt("bitrate", 0)
                                            if (bestUrl == null || bitrate >= bestBitrate) {
                                                bestBitrate = bitrate
                                                bestUrl = vUrl
                                            }
                                        }
                                    }
                                    if (bestUrl != null) {
                                        resolvedMediaUrl = bestUrl
                                        break
                                    }
                                }
                            }

                            // Si media_url_https contiene tweet_video_thumb
                            val mediaThumb = media.optString("media_url_https")
                            if (resolvedMediaUrl.isNullOrBlank() && mediaThumb.contains("tweet_video_thumb")) {
                                resolvedMediaUrl = mediaThumb
                                    .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                                    .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                                break
                            }
                        }
                    }

                    // Tambien revisar el objeto root "video" de syndication
                    if (resolvedMediaUrl.isNullOrBlank()) {
                        val videoObj = root.optJSONObject("video")
                        val variants = videoObj?.optJSONArray("variants")
                        if (variants != null) {
                            for (j in 0 until variants.length()) {
                                val v = variants.optJSONObject(j) ?: continue
                                val vSrc = v.optString("src").ifBlank { v.optString("url") }
                                if (isPlayableMediaUrl(vSrc)) {
                                    resolvedMediaUrl = vSrc
                                    break
                                }
                            }
                        }
                        if (resolvedMediaUrl.isNullOrBlank()) {
                            val poster = videoObj?.optString("poster") ?: ""
                            if (poster.contains("tweet_video_thumb")) {
                                resolvedMediaUrl = poster
                                    .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                                    .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Continuar con fallback
            }
        }

        // 5. Estrategia C: FxTwitter Direct Video Redirect
        if (resolvedMediaUrl.isNullOrBlank()) {
            try {
                val directSubdomainUrl = "https://d.fxtwitter.com/status/$tweetId"
                val finalUrl = resolveRedirectUrl(directSubdomainUrl)
                    ?: resolveRedirectUrl("https://d.fxtwitter.com/$effectiveUsername/status/$tweetId")
                if (finalUrl != null && (finalUrl.contains(".mp4") || finalUrl.contains("video.twimg.com") || finalUrl.contains("tweet_video"))) {
                    resolvedMediaUrl = finalUrl
                }
            } catch (e: Exception) {
                // Continuar
            }
        }

        if (resolvedMediaUrl.isNullOrBlank()) {
            throw IllegalStateException("No se pudo extraer ningun video o GIF del tweet. Verifica que el tweet sea publico y contenga un video o GIF.")
        }

        val downloadedFile = downloadFile(context, resolvedMediaUrl)
        val isVideo = downloadedFile.name.endsWith(".mp4", ignoreCase = true) ||
                downloadedFile.name.endsWith(".webm", ignoreCase = true) ||
                resolvedMediaUrl.contains(".mp4", ignoreCase = true) ||
                resolvedMediaUrl.contains("tweet_video", ignoreCase = true)

        return TwitterMediaResult(
            file = downloadedFile,
            username = authorName,
            tweetText = tweetText,
            isVideo = isVideo
        )
    }

    /**
     * Extrae de forma exhaustiva la URL del video o GIF animado evitando miniaturas estaticas JPG/PNG.
     */
    internal fun extractPlayableMediaUrlFromTweetJson(tweetObj: JSONObject): String? {
        val mediaObj = tweetObj.optJSONObject("media") ?: return null

        // 1. Revisar lista de videos
        val videosArr = mediaObj.optJSONArray("videos")
        if (videosArr != null && videosArr.length() > 0) {
            for (i in 0 until videosArr.length()) {
                val videoItem = videosArr.optJSONObject(i) ?: continue

                // Formatos internos: seleccionar el de mejor bitrate que sea reproducible
                val formats = videoItem.optJSONArray("formats")
                if (formats != null) {
                    var bestUrl: String? = null
                    var bestBitrate = -1
                    for (f in 0 until formats.length()) {
                        val formatObj = formats.optJSONObject(f) ?: continue
                        val fUrl = formatObj.optString("url")
                        if (isPlayableMediaUrl(fUrl)) {
                            val bitrate = formatObj.optInt("bitrate", 0)
                            if (bestUrl == null || bitrate >= bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = fUrl
                            }
                        }
                    }
                    if (bestUrl != null) {
                        return bestUrl
                    }
                }

                // Variantes internas: seleccionar el de mejor bitrate que sea reproducible
                val variants = videoItem.optJSONArray("variants")
                if (variants != null) {
                    var bestUrl: String? = null
                    var bestBitrate = -1
                    for (v in 0 until variants.length()) {
                        val varObj = variants.optJSONObject(v) ?: continue
                        val vUrl = varObj.optString("url")
                        if (isPlayableMediaUrl(vUrl)) {
                            val bitrate = varObj.optInt("bitrate", 0)
                            if (bestUrl == null || bitrate >= bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = vUrl
                            }
                        }
                    }
                    if (bestUrl != null) {
                        return bestUrl
                    }
                }

                val vUrl = videoItem.optString("url")
                if (isPlayableMediaUrl(vUrl)) {
                    return vUrl
                }

                val thumb = videoItem.optString("thumbnail_url")
                if (thumb.contains("tweet_video_thumb")) {
                    return thumb
                        .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                        .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                }
            }
        }

        // 2. Revisar lista 'all' (GIFs y videos mixtos)
        val allArr = mediaObj.optJSONArray("all")
        if (allArr != null && allArr.length() > 0) {
            for (i in 0 until allArr.length()) {
                val item = allArr.optJSONObject(i) ?: continue

                // Formatos internos
                val formats = item.optJSONArray("formats")
                if (formats != null) {
                    var bestUrl: String? = null
                    var bestBitrate = -1
                    for (f in 0 until formats.length()) {
                        val formatObj = formats.optJSONObject(f) ?: continue
                        val fUrl = formatObj.optString("url")
                        if (isPlayableMediaUrl(fUrl)) {
                            val bitrate = formatObj.optInt("bitrate", 0)
                            if (bestUrl == null || bitrate >= bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = fUrl
                            }
                        }
                    }
                    if (bestUrl != null) {
                        return bestUrl
                    }
                }

                // Variantes
                val variants = item.optJSONArray("variants")
                if (variants != null) {
                    var bestUrl: String? = null
                    var bestBitrate = -1
                    for (v in 0 until variants.length()) {
                        val varObj = variants.optJSONObject(v) ?: continue
                        val vUrl = varObj.optString("url")
                        if (isPlayableMediaUrl(vUrl)) {
                            val bitrate = varObj.optInt("bitrate", 0)
                            if (bestUrl == null || bitrate >= bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = vUrl
                            }
                        }
                    }
                    if (bestUrl != null) {
                        return bestUrl
                    }
                }

                val transcodeUrl = item.optString("transcode_url")
                if (isPlayableMediaUrl(transcodeUrl)) {
                    return transcodeUrl
                }

                val itemUrl = item.optString("url")
                if (isPlayableMediaUrl(itemUrl)) {
                    return itemUrl
                }

                // Si la URL apunta a tweet_video_thumb (ej: pbs.twimg.com/tweet_video_thumb/XYZ.jpg)
                if (itemUrl.contains("tweet_video_thumb")) {
                    return itemUrl
                        .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                        .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                }

                val thumb = item.optString("thumbnail_url")
                if (thumb.contains("tweet_video_thumb")) {
                    return thumb
                        .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                        .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                }
            }
        }

        // 3. Revisar lista de 'photos' por si contiene tweet_video_thumb o GIF real
        val photosArr = mediaObj.optJSONArray("photos")
        if (photosArr != null && photosArr.length() > 0) {
            for (i in 0 until photosArr.length()) {
                val photoItem = photosArr.optJSONObject(i) ?: continue
                val pUrl = photoItem.optString("url")
                if (isPlayableMediaUrl(pUrl)) {
                    return pUrl
                }
                if (pUrl.contains("tweet_video_thumb")) {
                    return pUrl
                        .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                        .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                }
                val pThumb = photoItem.optString("thumbnail_url")
                if (pThumb.contains("tweet_video_thumb")) {
                    return pThumb
                        .replace("pbs.twimg.com/tweet_video_thumb/", "video.twimg.com/tweet_video/")
                        .replace(Regex("\\.(jpg|jpeg|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE), ".mp4")
                }
            }
        }

        return null
    }

    internal fun isPlayableMediaUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.contains(".m3u8")) return false
        return lower.endsWith(".mp4") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".webm") ||
                lower.contains(".mp4?") ||
                lower.contains(".gif?") ||
                lower.contains(".webm?") ||
                lower.contains("/tweet_video/")
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        return isPlayableMediaUrl(url)
    }

    private fun calculateSyndicationToken(id: String): String {
        return try {
            val num = id.toDouble()
            val product = (num / 1e15) * Math.PI
            val intPart = product.toLong()
            val fracPart = product - intPart

            val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
            var res = ""
            var n = intPart
            while (n > 0) {
                res = chars[(n % 36).toInt()] + res
                n /= 36
            }
            var f = fracPart
            var fracRes = ""
            for (i in 0 until 12) {
                f *= 36
                val d = f.toInt()
                fracRes += chars[d]
                f -= d
            }
            (res + fracRes).replace("0", "").replace(".", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun fetchHttpString(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
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

    private fun resolveRedirectUrl(urlStr: String): String? {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(URL(currentUrl), location).toString()
                    }
                    redirects++
                } else if (code in 200..299) {
                    return currentUrl
                } else {
                    return null
                }
            } finally {
                conn?.disconnect()
            }
        }
        return currentUrl
    }

    private fun downloadFile(context: Context, downloadUrl: String): File {
        val extension = when {
            downloadUrl.contains(".gif", ignoreCase = true) -> ".gif"
            downloadUrl.contains(".webm", ignoreCase = true) -> ".webm"
            else -> ".mp4"
        }
        val targetFile = File(context.cacheDir, "twitter_temp_${System.currentTimeMillis()}$extension")

        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(downloadUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("El servidor devolvio el codigo de error ${conn.responseCode} al descargar el medio.")
            }

            val contentLength = conn.contentLengthLong
            if (contentLength > MAX_DOWNLOAD_SIZE_BYTES) {
                throw IllegalStateException("El archivo supera el limite maximo de descarga de 50 MB.")
            }

            inputStream = conn.inputStream
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var totalBytes = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                totalBytes += read
                if (totalBytes > MAX_DOWNLOAD_SIZE_BYTES) {
                    targetFile.delete()
                    throw IllegalStateException("La descarga supero el limite maximo de 50 MB.")
                }
                outputStream.write(buffer, 0, read)
            }

            outputStream.flush()
            return targetFile
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        } finally {
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
            conn?.disconnect()
        }
    }
}
