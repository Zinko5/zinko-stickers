package com.stickerforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isNewerVersion_correctlyDetectsNewerVersions() {
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.10.0", "1.9.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.0-hotfix", "1.0.9"))
    }

    @Test
    fun isNewerVersion_correctlyDetectsEqualOrOlderVersions() {
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.9", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v0.9.0", "1.1.0"))
    }

    @Test
    fun parseReleaseJson_returnsInfoWhenNewerVersionWithApk() {
        val json = """
        {
            "tag_name": "v1.2.0",
            "name": "v1.2.0 - Nuevas funciones",
            "body": "- Mejora de rendimiento\n- Correcciones",
            "html_url": "https://github.com/Zinko5/zinko-stickers/releases/tag/v1.2.0",
            "assets": [
                {
                    "name": "app-release.apk",
                    "size": 12000000,
                    "browser_download_url": "https://github.com/Zinko5/zinko-stickers/releases/download/v1.2.0/app-release.apk"
                }
            ]
        }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(json, "1.1.0")
        assertNotNull(info)
        assertEquals("1.2.0", info?.versionName)
        assertEquals("v1.2.0", info?.versionTag)
        assertEquals("v1.2.0 - Nuevas funciones", info?.releaseTitle)
        assertEquals("https://github.com/Zinko5/zinko-stickers/releases/download/v1.2.0/app-release.apk", info?.downloadUrl)
        assertEquals(12000000L, info?.apkSizeBytes)
    }

    @Test
    fun parseReleaseJson_returnsNullWhenSameVersion() {
        val json = """
        {
            "tag_name": "v1.1.0",
            "name": "v1.1.0",
            "body": "Notas",
            "html_url": "https://github.com/Zinko5/zinko-stickers/releases/tag/v1.1.0",
            "assets": [
                {
                    "name": "app-release.apk",
                    "size": 12000000,
                    "browser_download_url": "https://github.com/Zinko5/zinko-stickers/releases/download/v1.1.0/app-release.apk"
                }
            ]
        }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(json, "1.1.0")
        assertNull(info)
    }

    @Test
    fun parseReleaseJson_returnsNullWhenNoApkAsset() {
        val json = """
        {
            "tag_name": "v1.2.0",
            "name": "v1.2.0",
            "body": "Notas",
            "html_url": "https://github.com/Zinko5/zinko-stickers/releases/tag/v1.2.0",
            "assets": [
                {
                    "name": "source.zip",
                    "size": 1000,
                    "browser_download_url": "https://github.com/..."
                }
            ]
        }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(json, "1.1.0")
        assertNull(info)
    }
}
