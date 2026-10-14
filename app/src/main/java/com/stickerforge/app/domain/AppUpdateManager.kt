package com.stickerforge.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionName: String,
    val versionTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

object AppUpdateManager {
    const val GITHUB_OWNER = "Zinko5"
    const val GITHUB_REPO = "zinko-stickers"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val TIMEOUT_MS = 15000
    private const val MAX_REDIRECTS = 5

    /**
     * Compara dos versiones semánticas (ej. "1.2.0" vs "1.1.0" o "v1.2.0" vs "1.1.0").
     * Retorna true si [remoteVersion] es estrictamente mayor que [currentVersion].
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionComponents(remoteVersion)
        val currentParts = parseVersionComponents(currentVersion)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val remote = remoteParts.getOrElse(i) { 0 }
            val current = currentParts.getOrElse(i) { 0 }
            if (remote > current) return true
            if (remote < current) return false
        }
        return false
    }

    private fun parseVersionComponents(version: String): List<Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-")[0] // Ignora sufijos como -beta, -rc

        return cleaned.split(".")
            .mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
    }

    /**
     * Parsea la respuesta JSON de GitHub Releases y extrae la información de actualización
     * si la versión es más reciente que [currentVersionName].
     */
    fun parseReleaseJson(jsonString: String, currentVersionName: String): AppUpdateInfo? {
        val root = JSONObject(jsonString)
        val tagName = root.optString("tag_name", "").trim()
        val releaseTitle = root.optString("name", tagName).trim()
        val releaseNotes = root.optString("body", "").trim()
        val htmlUrl = root.optString("html_url", "").trim()

        val parsedVersionName = tagName.removePrefix("v").removePrefix("V").trim()

        if (!isNewerVersion(tagName, currentVersionName)) {
            return null
        }

        val assetsArray = root.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        var apkSize: Long = 0L

        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url", "")
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        if (apkUrl.isNullOrBlank()) {
            return null
        }

        return AppUpdateInfo(
            versionName = parsedVersionName.ifBlank { tagName },
            versionTag = tagName,
            releaseTitle = releaseTitle.ifBlank { "Actualización $tagName" },
            releaseNotes = releaseNotes,
            downloadUrl = apkUrl,
            apkSizeBytes = apkSize,
            htmlUrl = htmlUrl
        )
    }

    /**
     * Consulta la API pública de GitHub para obtener la última versión lanzada.
     */
    fun checkLatestRelease(currentVersionName: String): Result<AppUpdateInfo?> {
        return try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ZinkoStickers-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return Result.success(null)
            }
            if (responseCode !in 200..299) {
                return Result.failure(Exception("HTTP error $responseCode al consultar GitHub API"))
            }

            val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
            val updateInfo = parseReleaseJson(jsonResponse, currentVersionName)
            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Descarga el archivo APK en una ruta segura dentro de la caché de la aplicación.
     * Soporta redirecciones HTTP hacia la CDN de GitHub / AWS S3.
     */
    fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        return try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0

            while (true) {
                val url = URL(currentUrl)
                if (url.protocol != "https") {
                    return Result.failure(SecurityException("Solo se permiten descargas seguras HTTPS"))
                }

                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS * 2
                connection.setRequestProperty("User-Agent", "ZinkoStickers-Android-App")
                connection.instanceFollowRedirects = false

                val status = connection.responseCode
                if (status in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    )
                ) {
                    redirectCount++
                    if (redirectCount > MAX_REDIRECTS) {
                        return Result.failure(Exception("Demasiadas redirecciones al descargar APK"))
                    }
                    currentUrl = connection.getHeaderField("Location")
                        ?: return Result.failure(Exception("Cabecera Location no encontrada en redirección"))
                    connection.disconnect()
                    continue
                }

                if (status !in 200..299) {
                    return Result.failure(Exception("Error HTTP $status al descargar archivo APK"))
                }
                break
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            destinationFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Construye el Intent oficial para abrir el instalador de paquetes de Android.
     */
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
