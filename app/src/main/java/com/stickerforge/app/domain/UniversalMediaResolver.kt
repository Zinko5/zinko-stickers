package com.stickerforge.app.domain

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class SocialPlatform(
    val displayName: String,
    val defaultEmoji: String
) {
    YOUTUBE("YouTube", "▶️"),
    INSTAGRAM("Instagram", "📸"),
    TIKTOK("TikTok", "🎵"),
    TWITTER("Twitter / X", "✖️"),
    DIRECT_LINK("Enlace directo", "✨"),
    UNKNOWN("Desconocido", "🔗")
}

data class UniversalMediaResult(
    val file: File,
    val platform: SocialPlatform,
    val username: String?,
    val title: String?,
    val defaultEmoji: String,
    val isVideo: Boolean,
    val isPhotoCollection: Boolean
)

object UniversalMediaResolver {

    fun detectPlatform(url: String): SocialPlatform {
        val trimmed = url.trim()
        return when {
            YouTubeMediaResolver.isYouTubeUrl(trimmed) -> SocialPlatform.YOUTUBE
            InstagramMediaResolver.isInstagramUrl(trimmed) -> SocialPlatform.INSTAGRAM
            TikTokMediaResolver.isTikTokUrl(trimmed) -> SocialPlatform.TIKTOK
            TwitterMediaResolver.isTweetUrl(trimmed) -> SocialPlatform.TWITTER
            isDirectMediaUrl(trimmed) -> SocialPlatform.DIRECT_LINK
            else -> SocialPlatform.UNKNOWN
        }
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.contains("mime_type=video_mp4")
    }

    /**
     * Resuelve y descarga cualquier URL admitida (YouTube, Instagram, TikTok, Twitter/X o enlace directo).
     */
    fun resolveAndDownload(context: Context, inputUrl: String): UniversalMediaResult {
        val trimmed = inputUrl.trim()
        DiagnosticLogger.log("UniversalResolver", "resolveAndDownload iniciado para: $trimmed")

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("La URL debe comenzar con http:// o https://")
        }

        // 1. YouTube
        if (YouTubeMediaResolver.isYouTubeUrl(trimmed)) {
            DiagnosticLogger.log("UniversalResolver", "Plataforma detectada: YouTube")
            val ytResult = YouTubeMediaResolver.resolveAndDownload(context, trimmed)
            return UniversalMediaResult(
                file = ytResult.file,
                platform = SocialPlatform.YOUTUBE,
                username = ytResult.author,
                title = ytResult.title,
                defaultEmoji = SocialPlatform.YOUTUBE.defaultEmoji,
                isVideo = ytResult.isVideo,
                isPhotoCollection = false
            )
        }

        // 2. Instagram
        if (InstagramMediaResolver.isInstagramUrl(trimmed)) {
            DiagnosticLogger.log("UniversalResolver", "Plataforma detectada: Instagram")
            val igResult = InstagramMediaResolver.resolveAndDownload(context, trimmed)
            return UniversalMediaResult(
                file = igResult.file,
                platform = SocialPlatform.INSTAGRAM,
                username = igResult.username,
                title = igResult.title,
                defaultEmoji = SocialPlatform.INSTAGRAM.defaultEmoji,
                isVideo = igResult.isVideo,
                isPhotoCollection = igResult.isPhotoCollection
            )
        }

        // 3. TikTok
        if (TikTokMediaResolver.isTikTokUrl(trimmed)) {
            DiagnosticLogger.log("UniversalResolver", "Plataforma detectada: TikTok")
            val ttResult = TikTokMediaResolver.resolveAndDownload(context, trimmed)
            return UniversalMediaResult(
                file = ttResult.file,
                platform = SocialPlatform.TIKTOK,
                username = ttResult.username,
                title = ttResult.title,
                defaultEmoji = SocialPlatform.TIKTOK.defaultEmoji,
                isVideo = ttResult.isVideo,
                isPhotoCollection = ttResult.isPhotoCollection
            )
        }

        // 4. Twitter / X
        if (TwitterMediaResolver.isTweetUrl(trimmed)) {
            DiagnosticLogger.log("UniversalResolver", "Plataforma detectada: Twitter / X")
            val twResult = TwitterMediaResolver.resolveAndDownload(context, trimmed)
            return UniversalMediaResult(
                file = twResult.file,
                platform = SocialPlatform.TWITTER,
                username = twResult.username,
                title = twResult.tweetText,
                defaultEmoji = SocialPlatform.TWITTER.defaultEmoji,
                isVideo = twResult.isVideo,
                isPhotoCollection = false
            )
        }

        // 5. Enlace directo de medio
        if (isDirectMediaUrl(trimmed) || TikTokMediaResolver.isDirectMediaUrl(trimmed) || TwitterMediaResolver.isDirectMediaUrl(trimmed)) {
            DiagnosticLogger.log("UniversalResolver", "Enlace directo de medio detectado: $trimmed")
            val downloadedFile = downloadDirectMediaFile(context, trimmed)
            val isVideo = downloadedFile.name.endsWith(".mp4", ignoreCase = true) ||
                    downloadedFile.name.endsWith(".webm", ignoreCase = true)
            if (isVideo) {
                TikTokMediaResolver.normalizeMp4Brands(downloadedFile)
            }
            return UniversalMediaResult(
                file = downloadedFile,
                platform = SocialPlatform.DIRECT_LINK,
                username = null,
                title = null,
                defaultEmoji = SocialPlatform.DIRECT_LINK.defaultEmoji,
                isVideo = isVideo,
                isPhotoCollection = !isVideo
            )
        }

        throw IllegalArgumentException("La URL no corresponde a una plataforma admitida (YouTube, Instagram, TikTok, Twitter/X) ni es un enlace directo de medio.")
    }

    private fun downloadDirectMediaFile(context: Context, downloadUrl: String): File {
        var conn: HttpURLConnection? = null
        var inStream: InputStream? = null
        var outStream: FileOutputStream? = null

        val isVideoUrl = downloadUrl.contains(".mp4", ignoreCase = true) ||
                downloadUrl.contains(".webm", ignoreCase = true)
        val ext = if (isVideoUrl) "mp4" else "jpg"
        val tempFile = File(context.cacheDir, "direct_media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")

        try {
            val url = URL(downloadUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
                setRequestProperty("Accept", "*/*")
                instanceFollowRedirects = true
            }

            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Servidor respondio con HTTP ${conn.responseCode}")
            }

            inStream = conn.inputStream
            outStream = FileOutputStream(tempFile)

            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalRead = 0L

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > 50 * 1024 * 1024) {
                    throw IllegalStateException("El archivo supera el limite maximo de descarga (50 MB).")
                }
                outStream.write(buffer, 0, bytesRead)
            }
            outStream.flush()
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
}
