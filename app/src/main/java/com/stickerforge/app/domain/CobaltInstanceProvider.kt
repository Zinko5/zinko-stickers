package com.stickerforge.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CobaltMediaItem(
    val url: String,
    val type: String, // "video" or "photo"
    val thumb: String?
)

data class CobaltResolutionResult(
    val url: String,
    val filename: String?,
    val isVideo: Boolean,
    val items: List<CobaltMediaItem> = emptyList()
)

/**
 * Proveedor de instancias de Cobalt v10 con resolucion automatica de endpoints y fallback.
 * Utiliza instancias comunitarias activas sin autenticacion (auth: false),
 * como White-Cobalt (api.cobalt.liubquanti.click), y sincroniza dinamicamente
 * la lista oficial comunitaria desde GitHub.
 */
object CobaltInstanceProvider {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 25000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val OFFICIAL_SERVERS_URL = "https://raw.githubusercontent.com/liubquanti/White-Cobalt/refs/heads/main/files/servers.json"

    // Instancias predeterminadas de alta disponibilidad verificadas
    private val DEFAULT_INSTANCES = listOf(
        "https://api.cobalt.liubquanti.click"
    )

    @Volatile
    private var cachedInstances: List<String>? = null

    @Volatile
    private var lastFetchTimeMs: Long = 0L
    private const val CACHE_TTL_MS = 3600_000L // 1 hora

    fun getWorkingInstances(): List<String> {
        val currentCached = cachedInstances
        val now = System.currentTimeMillis()
        if (currentCached != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            return currentCached
        }

        val discovered = fetchRemoteServers()
        val combined = if (discovered.isNotEmpty()) {
            (discovered + DEFAULT_INSTANCES).distinct()
        } else {
            DEFAULT_INSTANCES
        }

        cachedInstances = combined
        lastFetchTimeMs = now
        return combined
    }

    private fun fetchRemoteServers(): List<String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(OFFICIAL_SERVERS_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                val results = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val requiresAuth = obj.optBoolean("auth", false)
                    val apiHost = obj.optString("api")
                    val protocol = obj.optString("protocol", "https")
                    if (!requiresAuth && apiHost.isNotBlank()) {
                        results.add("$protocol://$apiHost")
                    }
                }
                DiagnosticLogger.log("CobaltProvider", "Sincronizadas ${results.size} instancias activas sin auth")
                results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("CobaltProvider", "Aviso: no se pudo sincronizar lista remota de servers (${e.message}), usando predeterminados.")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Resuelve un enlace multimedia utilizando las instancias de Cobalt v10.
     * Retorna CobaltResolutionResult si tiene exito, o null si todas las instancias fallan.
     */
    fun resolveMedia(targetUrl: String, videoQuality: String = "480"): CobaltResolutionResult? {
        val instances = getWorkingInstances()
        val payload = JSONObject().apply {
            put("url", targetUrl)
            put("videoQuality", videoQuality)
            put("downloadMode", "auto")
        }.toString()

        for (baseEndpoint in instances) {
            val endpoint = if (baseEndpoint.endsWith("/")) baseEndpoint else "$baseEndpoint/"
            try {
                DiagnosticLogger.log("CobaltProvider", "Consultando endpoint v10: $endpoint")
                val responseJson = postJson(endpoint, payload) ?: continue
                val root = JSONObject(responseJson)
                val status = root.optString("status")

                if (status == "tunnel" || status == "redirect") {
                    val mediaUrl = root.optString("url")
                    val filename = root.optString("filename").ifBlank { null }
                    if (mediaUrl.isNotBlank()) {
                        val isVideo = filename?.endsWith(".mp4", ignoreCase = true) == true ||
                                mediaUrl.contains(".mp4", ignoreCase = true) ||
                                filename?.endsWith(".webm", ignoreCase = true) == true
                        DiagnosticLogger.log("CobaltProvider", "Cobalt exito ($status): $mediaUrl (isVideo=$isVideo)")
                        return CobaltResolutionResult(
                            url = mediaUrl,
                            filename = filename,
                            isVideo = isVideo
                        )
                    }
                } else if (status == "picker") {
                    val pickerArray = root.optJSONArray("picker")
                    if (pickerArray != null && pickerArray.length() > 0) {
                        val items = mutableListOf<CobaltMediaItem>()
                        for (i in 0 until pickerArray.length()) {
                            val itemObj = pickerArray.optJSONObject(i) ?: continue
                            val itemUrl = itemObj.optString("url")
                            val itemType = itemObj.optString("type", "video")
                            val itemThumb = itemObj.optString("thumb").ifBlank { null }
                            if (itemUrl.isNotBlank()) {
                                items.add(CobaltMediaItem(url = itemUrl, type = itemType, thumb = itemThumb))
                            }
                        }

                        if (items.isNotEmpty()) {
                            val first = items.first()
                            DiagnosticLogger.log("CobaltProvider", "Cobalt picker resolvio ${items.size} elementos. Seleccionado primero (${first.type})")
                            return CobaltResolutionResult(
                                url = first.url,
                                filename = null,
                                isVideo = first.type == "video",
                                items = items
                            )
                        }
                    }
                } else {
                    val errorObj = root.optJSONObject("error")
                    val errCode = errorObj?.optString("code") ?: status
                    DiagnosticLogger.log("CobaltProvider", "Cobalt endpoint $endpoint devolvio status: $errCode")
                }
            } catch (e: Exception) {
                DiagnosticLogger.log("CobaltProvider", "Cobalt endpoint $endpoint error: ${e.message}")
            }
        }
        return null
    }

    private fun postJson(urlStr: String, jsonBody: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            conn.outputStream.bufferedWriter().use { it.write(jsonBody) }

            val status = conn.responseCode
            if (status in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }
                DiagnosticLogger.log("CobaltProvider", "HTTP $status desde $urlStr: $errText")
                null
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("CobaltProvider", "Fallo conexion POST a $urlStr: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
