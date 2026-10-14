package com.stickerforge.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.io.FileOutputStream

class YouTubeMediaResolverTest {

    @Test
    fun testIsYouTubeUrl() {
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://www.youtube.com/watch?v=3IqYxjdB8DY"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://youtube.com/watch?v=3IqYxjdB8DY&t=10s"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://m.youtube.com/watch?v=3IqYxjdB8DY"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://youtu.be/3IqYxjdB8DY"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://www.youtube.com/shorts/G5aj_yu5LBE"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://youtube.com/shorts/G5aj_yu5LBE?feature=share"))
        assertTrue(YouTubeMediaResolver.isYouTubeUrl("https://www.youtube.com/embed/3IqYxjdB8DY"))

        assertFalse(YouTubeMediaResolver.isYouTubeUrl("https://instagram.com/reel/DecM8Afv_9Q/"))
        assertFalse(YouTubeMediaResolver.isYouTubeUrl("https://vt.tiktok.com/ZSbKW3Rpb/"))
        assertFalse(YouTubeMediaResolver.isYouTubeUrl("https://x.com/user/status/123"))
        assertFalse(YouTubeMediaResolver.isYouTubeUrl("https://google.com"))
        assertFalse(YouTubeMediaResolver.isYouTubeUrl(""))
    }

    @Test
    fun testExtractVideoId() {
        assertEquals("3IqYxjdB8DY", YouTubeMediaResolver.extractVideoId("https://www.youtube.com/watch?v=3IqYxjdB8DY"))
        assertEquals("3IqYxjdB8DY", YouTubeMediaResolver.extractVideoId("https://youtu.be/3IqYxjdB8DY"))
        assertEquals("FSHvN6N-f1Q", YouTubeMediaResolver.extractVideoId("https://youtu.be/FSHvN6N-f1Q"))
        assertEquals("FSHvN6N-f1Q", YouTubeMediaResolver.extractVideoId("https://youtu.be/FSHvN6N-f1Q?si=abc123xyz"))
        assertEquals("G5aj_yu5LBE", YouTubeMediaResolver.extractVideoId("https://www.youtube.com/shorts/G5aj_yu5LBE"))
        assertEquals("G5aj_yu5LBE", YouTubeMediaResolver.extractVideoId("https://youtube.com/shorts/G5aj_yu5LBE?feature=share"))
    }

    @Test
    fun testInvalidUrlSchemeThrowsException() {
        val mockContext = mock(Context::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            YouTubeMediaResolver.resolveAndDownload(mockContext, "ftp://youtube.com/watch?v=3IqYxjdB8DY")
        }
    }

    @Test
    fun testNonYouTubeUrlThrowsException() {
        val mockContext = mock(Context::class.java)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            YouTubeMediaResolver.resolveAndDownload(mockContext, "https://example.com/not-youtube")
        }
        assertTrue(ex.message?.contains("YouTube") == true)
    }

    @Test
    fun testNormalizeMp4BrandsPatchesMdatBox() {
        val tempFile = File.createTempFile("test_mdat", ".mp4")
        try {
            // Construye un header simulado de MP4 con ftyp y un mdat declarado grande
            val fos = FileOutputStream(tempFile)
            // ftyp box: 16 bytes (size: 16, type: 'ftyp', major: 'dash', minor: 0)
            fos.write(byteArrayOf(0, 0, 0, 16))
            fos.write("ftypdash".toByteArray(Charsets.US_ASCII))
            fos.write(byteArrayOf(0, 0, 0, 0))

            // mdat box: declaramos un tamaño de 1000 bytes, pero el archivo solo tendra 50 bytes en total
            val declaredMdatSize = 1000
            fos.write(byteArrayOf(
                ((declaredMdatSize shr 24) and 0xFF).toByte(),
                ((declaredMdatSize shr 16) and 0xFF).toByte(),
                ((declaredMdatSize shr 8) and 0xFF).toByte(),
                (declaredMdatSize and 0xFF).toByte()
            ))
            fos.write("mdat".toByteArray(Charsets.US_ASCII))
            // Escribimos 20 bytes de datos multimedia simulados
            val payload = ByteArray(20) { it.toByte() }
            fos.write(payload)
            fos.close()

            val initialLength = tempFile.length()
            assertEquals(16 + 8 + 20, initialLength) // 44 bytes

            // Ejecutamos la normalizacion
            YouTubeMediaResolver.normalizeMp4Brands(tempFile)

            // Verificamos que el archivo sigue existiendo y conserva el tamaño
            assertEquals(initialLength, tempFile.length())

            // Verificamos que el atom ftyp fue cambiado de 'dash' a 'isom'
            val bytes = tempFile.readBytes()
            val majorBrand = String(bytes, 8, 4, Charsets.US_ASCII)
            assertEquals("isom", majorBrand)

            // Verificamos que el atom mdat fue recalculado a los bytes restantes (44 - 16 = 28 bytes)
            val patchedMdatSize = (bytes[16].toInt() and 0xFF shl 24) or
                    (bytes[17].toInt() and 0xFF shl 16) or
                    (bytes[18].toInt() and 0xFF shl 8) or
                    (bytes[19].toInt() and 0xFF)
            assertEquals(28, patchedMdatSize)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testMaxVideoDownloadBytesIs10MBToEnsureFullWhatsAppDuration() {
        assertEquals(10 * 1024 * 1024L, YouTubeMediaResolver.MAX_VIDEO_DOWNLOAD_BYTES)
    }

    @Test
    fun testInnertubeDataStructures() {
        val candidate = YouTubeMediaResolver.InnertubeCandidate(
            url = "https://example.com/video.mp4",
            label = "Adaptive 720p (avc1)"
        )
        val response = YouTubeMediaResolver.InnertubeResponse(
            candidates = listOf(candidate),
            title = "Test Video",
            author = "Test Author"
        )
        assertEquals(1, response.candidates.size)
        assertEquals("Test Video", response.title)
        assertEquals("Adaptive 720p (avc1)", response.candidates.first().label)

        val legacyResult = YouTubeMediaResolver.InnertubeResult(
            url = "https://example.com/video.mp4",
            title = "Test Video",
            author = "Test Author"
        )
        assertEquals("https://example.com/video.mp4", legacyResult.url)
    }
}
