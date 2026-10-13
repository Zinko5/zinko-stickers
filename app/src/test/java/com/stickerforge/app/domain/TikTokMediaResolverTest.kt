package com.stickerforge.app.domain

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class TikTokMediaResolverTest {

    @Test
    fun testInvalidUrlSchemeThrowsException() {
        val mockContext = mock(Context::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            TikTokMediaResolver.resolveAndDownload(mockContext, "ftp://vt.tiktok.com/ZSbKW3Rpb/")
        }
    }

    @Test
    fun testNonTikTokUrlThrowsException() {
        val mockContext = mock(Context::class.java)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TikTokMediaResolver.resolveAndDownload(mockContext, "https://example.com/not-tiktok")
        }
        assertTrue(ex.message?.contains("TikTok") == true)
    }

    @Test
    fun testIsTikTokUrl() {
        assertTrue(TikTokMediaResolver.isTikTokUrl("https://vt.tiktok.com/ZSbKW3Rpb/"))
        assertTrue(TikTokMediaResolver.isTikTokUrl("https://vm.tiktok.com/ZSbKW3Rpb/"))
        assertTrue(TikTokMediaResolver.isTikTokUrl("https://www.tiktok.com/@noimtina/video/7695960468904496390"))
        assertTrue(TikTokMediaResolver.isTikTokUrl("https://www.tiktok.com/@euromericapatriot/photo/7695851521086410014"))
        assertTrue(TikTokMediaResolver.isTikTokUrl("https://tiktok.com/t/ZT8Rxbabc/"))

        assertFalse(TikTokMediaResolver.isTikTokUrl("https://twitter.com/user/status/123"))
        assertFalse(TikTokMediaResolver.isTikTokUrl("https://google.com"))
        assertFalse(TikTokMediaResolver.isTikTokUrl(""))
    }

    @Test
    fun testIsDirectMediaUrl() {
        assertTrue(TikTokMediaResolver.isDirectMediaUrl("https://cdn.example.com/video.mp4"))
        assertTrue(TikTokMediaResolver.isDirectMediaUrl("https://cdn.example.com/photo.jpg"))
        assertTrue(TikTokMediaResolver.isDirectMediaUrl("https://cdn.example.com/image.png"))
        assertTrue(TikTokMediaResolver.isDirectMediaUrl("https://cdn.example.com/stream?mime_type=video_mp4&token=123"))

        assertFalse(TikTokMediaResolver.isDirectMediaUrl("https://vt.tiktok.com/ZSbKW3Rpb/"))
        assertFalse(TikTokMediaResolver.isDirectMediaUrl("https://www.tiktok.com/@user/video/12345"))
    }

    @Test
    fun testRealTikTokVideoResolution() {
        val mockContext = mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        org.mockito.Mockito.`when`(mockContext.cacheDir).thenReturn(tempDir)

        val result = TikTokMediaResolver.resolveAndDownload(mockContext, "https://vt.tiktok.com/ZSbKW3Rpb/")
        try {
            assertTrue("El archivo descargado debe existir", result.file.exists())
            assertTrue("El archivo descargado debe tener contenido", result.file.length() > 10000)
            assertTrue("Debe ser detectado como video", result.isVideo)
            assertFalse("No debe ser coleccion de fotos", result.isPhotoCollection)
            assertNotNull("Debe extraer el nombre de usuario", result.username)
        } finally {
            try { result.file.delete() } catch (ignored: Exception) {}
        }
    }

    @Test
    fun testRealTikTokPhotoCollectionResolution() {
        val mockContext = mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        org.mockito.Mockito.`when`(mockContext.cacheDir).thenReturn(tempDir)

        val result = TikTokMediaResolver.resolveAndDownload(mockContext, "https://vt.tiktok.com/ZSbKWxjKE/")
        try {
            assertTrue("La foto descargada debe existir", result.file.exists())
            assertTrue("La foto debe tener contenido", result.file.length() > 5000)
            assertFalse("No debe ser video", result.isVideo)
            assertTrue("Debe ser detectado como coleccion de fotos", result.isPhotoCollection)
            assertNotNull("Debe extraer el nombre de usuario", result.username)
        } finally {
            try { result.file.delete() } catch (ignored: Exception) {}
        }
    }
}
