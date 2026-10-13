package com.stickerforge.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class InstagramMediaResolverTest {

    @Test
    fun testIsInstagramUrl() {
        assertTrue(InstagramMediaResolver.isInstagramUrl("https://www.instagram.com/reel/DecM8Afv_9Q/?xtok=MWRhdndtNW9oM2dveQ=="))
        assertTrue(InstagramMediaResolver.isInstagramUrl("https://instagram.com/reel/DecM8Afv_9Q/"))
        assertTrue(InstagramMediaResolver.isInstagramUrl("https://www.instagram.com/p/DB12345/"))
        assertTrue(InstagramMediaResolver.isInstagramUrl("https://www.instagram.com/tv/DB12345/"))
        assertTrue(InstagramMediaResolver.isInstagramUrl("https://instagr.am/reel/DecM8Afv_9Q/"))

        assertFalse(InstagramMediaResolver.isInstagramUrl("https://www.youtube.com/watch?v=3IqYxjdB8DY"))
        assertFalse(InstagramMediaResolver.isInstagramUrl("https://vt.tiktok.com/ZSbKW3Rpb/"))
        assertFalse(InstagramMediaResolver.isInstagramUrl("https://x.com/user/status/123"))
        assertFalse(InstagramMediaResolver.isInstagramUrl("https://google.com"))
        assertFalse(InstagramMediaResolver.isInstagramUrl(""))
    }

    @Test
    fun testExtractShortcode() {
        assertEquals("DecM8Afv_9Q", InstagramMediaResolver.extractShortcode("https://www.instagram.com/reel/DecM8Afv_9Q/?xtok=MWRhdndtNW9oM2dveQ=="))
        assertEquals("DecM8Afv_9Q", InstagramMediaResolver.extractShortcode("https://instagram.com/reel/DecM8Afv_9Q/"))
        assertEquals("DB12345", InstagramMediaResolver.extractShortcode("https://www.instagram.com/p/DB12345/"))
        assertEquals("DB12345", InstagramMediaResolver.extractShortcode("https://www.instagram.com/tv/DB12345/?utm_source=ig_web"))
    }

    @Test
    fun testInvalidUrlSchemeThrowsException() {
        val mockContext = mock(Context::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            InstagramMediaResolver.resolveAndDownload(mockContext, "ftp://instagram.com/reel/DecM8Afv_9Q/")
        }
    }

    @Test
    fun testNonInstagramUrlThrowsException() {
        val mockContext = mock(Context::class.java)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            InstagramMediaResolver.resolveAndDownload(mockContext, "https://example.com/not-instagram")
        }
        assertTrue(ex.message?.contains("Instagram") == true)
    }

    @Test
    fun testDecodeSnapSaveUnpacker() {
        // Parametros reales capturados de prueba
        val h = "PdVCPTdCTZVCTZVCdTdCPVdCVZZTCPdZCTVTCVZZZCPPTCVZZdCPPVCPTVCTZVCTZPCPPVCPTTCVZVZCVddCdZPCPVdCVZZTCPTTCTZVCTZTCTVdCTTZCTVPCTVPCTVPCTZTCTZTCVddCTPVCVddCTTZCTdVCTddCTTZCTddCTTVCTdTCTTZCTTVCTVPCTZTCVZVPCPdVCPTdCTZVCVZVZCPdVCPPVCPTVCPPTCVZVZCTVTCPdPCPPTC"
        val u = 33
        val n = "ZVTdPCtYX"
        val t = 11
        val e = 5

        val decoded = InstagramMediaResolver.decodeSnapSave(h, u, n, t, e)
        assertTrue(decoded.isNotEmpty())
        assertTrue(decoded.startsWith("if((Math.round(+new Date()/1000))"))
    }
}
