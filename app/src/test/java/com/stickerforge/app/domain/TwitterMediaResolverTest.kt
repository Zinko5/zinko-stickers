package com.stickerforge.app.domain

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class TwitterMediaResolverTest {

    @Test
    fun testInvalidUrlSchemeThrowsException() {
        val mockContext = mock(Context::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            TwitterMediaResolver.resolveAndDownload(mockContext, "ftp://twitter.com/test/status/12345")
        }
    }

    @Test
    fun testRealTweetResolution() {
        val mockContext = mock(Context::class.java)
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        org.mockito.Mockito.`when`(mockContext.cacheDir).thenReturn(tempDir)

        val resGif = TwitterMediaResolver.resolveAndDownload(mockContext, "https://x.com/gifskeepsgiving/status/2109493186540532100/photo/1")
        println("TEST GIF RESULT: file=" + resGif.file.absolutePath + ", size=" + resGif.file.length() + ", isVideo=" + resGif.isVideo + ", username=" + resGif.username)
        assertTrue(resGif.file.exists())
        assertTrue(resGif.file.length() > 0)

        val resVideo = TwitterMediaResolver.resolveAndDownload(mockContext, "https://x.com/GirlBossVibess/status/2109596077607264269/video/1")
        println("TEST VIDEO RESULT: file=" + resVideo.file.absolutePath + ", size=" + resVideo.file.length() + ", isVideo=" + resVideo.isVideo + ", username=" + resVideo.username)
        assertTrue(resVideo.file.exists())
        assertTrue("El video no debe ser una playlist m3u8 pequena", resVideo.file.length() > 50000)

        val urls = listOf(
            "https://x.com/majidawan764334/status/2103390323204301287/photo/1",
            "https://x.com/b1tesnails/status/2096773717753827603/photo/1",
            "https://x.com/npcnaia/status/2109465257563627830/photo/1"
        )
        for (u in urls) {
            val r = TwitterMediaResolver.resolveAndDownload(mockContext, u)
            println("USER URL TEST: url=$u -> file=${r.file.name}, size=${r.file.length()}, isVideo=${r.isVideo}, user=${r.username}")
            assertTrue("File for $u should exist", r.file.exists())
            assertTrue("File for $u should not be empty", r.file.length() > 0)
        }
    }

    @Test
    fun testNonTweetUrlThrowsException() {
        val mockContext = mock(Context::class.java)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TwitterMediaResolver.resolveAndDownload(mockContext, "https://x.com/home")
        }
        assertTrue(ex.message?.contains("ID de tweet") == true)
    }

    @Test
    fun testIsPlayableMediaUrl() {
        assertTrue(TwitterMediaResolver.isPlayableMediaUrl("https://video.twimg.com/tweet_video/12345.mp4"))
        assertTrue(TwitterMediaResolver.isPlayableMediaUrl("https://example.com/animation.gif"))
        assertTrue(TwitterMediaResolver.isPlayableMediaUrl("https://example.com/clip.webm"))
        assertTrue(TwitterMediaResolver.isPlayableMediaUrl("https://video.twimg.com/ext_tw_video/123/pu/vid/720x1280/abc.mp4?tag=12"))

        assertFalse(TwitterMediaResolver.isPlayableMediaUrl("https://video.twimg.com/amplify_video/123/pl/test.m3u8?tag=29"))
        assertFalse(TwitterMediaResolver.isPlayableMediaUrl("https://pbs.twimg.com/media/abc.jpg"))
        assertFalse(TwitterMediaResolver.isPlayableMediaUrl("https://pbs.twimg.com/media/abc.png?name=orig"))
        assertFalse(TwitterMediaResolver.isPlayableMediaUrl(""))
    }

    @Test
    fun testExtractPlayableMediaUrl_withTwitterGifInAll() {
        val tweetJson = JSONObject("""
            {
                "media": {
                    "all": [
                        {
                            "type": "gif",
                            "url": "https://pbs.twimg.com/tweet_video_thumb/HUUclc5bEAAtV-0.jpg"
                        }
                    ]
                }
            }
        """.trimIndent())

        val resolved = TwitterMediaResolver.extractPlayableMediaUrlFromTweetJson(tweetJson)
        assertEquals("https://video.twimg.com/tweet_video/HUUclc5bEAAtV-0.mp4", resolved)
    }

    @Test
    fun testExtractPlayableMediaUrl_withVideoVariants() {
        val tweetJson = JSONObject("""
            {
                "media": {
                    "videos": [
                        {
                            "type": "video",
                            "url": "https://video.twimg.com/ext_tw_video/123/vid/720x720/sample.mp4",
                            "formats": [
                                {
                                    "url": "https://video.twimg.com/ext_tw_video/123/vid/720x720/sample.mp4"
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent())

        val resolved = TwitterMediaResolver.extractPlayableMediaUrlFromTweetJson(tweetJson)
        assertEquals("https://video.twimg.com/ext_tw_video/123/vid/720x720/sample.mp4", resolved)
    }

    @Test
    fun testExtractPlayableMediaUrl_withPhotosOnlyReturnsNull() {
        val tweetJson = JSONObject("""
            {
                "media": {
                    "photos": [
                        {
                            "type": "photo",
                            "url": "https://pbs.twimg.com/media/static_picture.jpg"
                        }
                    ]
                }
            }
        """.trimIndent())

        val resolved = TwitterMediaResolver.extractPlayableMediaUrlFromTweetJson(tweetJson)
        assertNull(resolved)
    }

    @Test
    fun testNormalizeMp4Brands_convertsIso5ToIsomAndIso6ToMp42() {
        val tempFile = java.io.File.createTempFile("test_iso5_", ".mp4")
        tempFile.deleteOnExit()

        val data = ByteArray(24)
        // box size = 24
        data[0] = 0; data[1] = 0; data[2] = 0; data[3] = 24
        // 'ftyp'
        data[4] = 'f'.code.toByte(); data[5] = 't'.code.toByte(); data[6] = 'y'.code.toByte(); data[7] = 'p'.code.toByte()
        // major_brand = 'iso5'
        data[8] = 'i'.code.toByte(); data[9] = 's'.code.toByte(); data[10] = 'o'.code.toByte(); data[11] = '5'.code.toByte()
        // minor_version = 0x0200
        data[12] = 0; data[13] = 0; data[14] = 2; data[15] = 0
        // comp_brand1 = 'iso6'
        data[16] = 'i'.code.toByte(); data[17] = 's'.code.toByte(); data[18] = 'o'.code.toByte(); data[19] = '6'.code.toByte()
        // comp_brand2 = 'mp41'
        data[20] = 'm'.code.toByte(); data[21] = 'p'.code.toByte(); data[22] = '4'.code.toByte(); data[23] = '1'.code.toByte()

        tempFile.writeBytes(data)

        TwitterMediaResolver.normalizeMp4Brands(tempFile)

        val result = tempFile.readBytes()
        val majorResult = String(result, 8, 4, Charsets.US_ASCII)
        val compResult = String(result, 16, 4, Charsets.US_ASCII)

        assertEquals("isom", majorResult)
        assertEquals("mp42", compResult)
    }

    @Test
    fun testNormalizeMp4Brands_leavesStandardBrandsIntact() {
        val tempFile = java.io.File.createTempFile("test_standard_", ".mp4")
        tempFile.deleteOnExit()

        val data = ByteArray(24)
        data[0] = 0; data[1] = 0; data[2] = 0; data[3] = 24
        data[4] = 'f'.code.toByte(); data[5] = 't'.code.toByte(); data[6] = 'y'.code.toByte(); data[7] = 'p'.code.toByte()
        data[8] = 'i'.code.toByte(); data[9] = 's'.code.toByte(); data[10] = 'o'.code.toByte(); data[11] = 'm'.code.toByte()
        data[12] = 0; data[13] = 0; data[14] = 0; data[15] = 1
        data[16] = 'm'.code.toByte(); data[17] = 'p'.code.toByte(); data[18] = '4'.code.toByte(); data[19] = '2'.code.toByte()
        data[20] = 'm'.code.toByte(); data[21] = 'p'.code.toByte(); data[22] = '4'.code.toByte(); data[23] = '1'.code.toByte()

        tempFile.writeBytes(data)

        TwitterMediaResolver.normalizeMp4Brands(tempFile)

        val result = tempFile.readBytes()
        val majorResult = String(result, 8, 4, Charsets.US_ASCII)
        val compResult = String(result, 16, 4, Charsets.US_ASCII)

        assertEquals("isom", majorResult)
        assertEquals("mp42", compResult)
    }

    @Test
    fun testNormalizeMp4_patchesCttsFlagsToZero() {
        val tempFile = java.io.File.createTempFile("test_ctts_", ".mp4")
        tempFile.deleteOnExit()

        // Crear una cabecera ftyp minima + atomo ctts con flags=1 (como Twitter vork muxer)
        val data = ByteArray(40)
        // ftyp (24 bytes)
        data[0] = 0; data[1] = 0; data[2] = 0; data[3] = 24
        data[4] = 'f'.code.toByte(); data[5] = 't'.code.toByte(); data[6] = 'y'.code.toByte(); data[7] = 'p'.code.toByte()
        data[8] = 'i'.code.toByte(); data[9] = 's'.code.toByte(); data[10] = 'o'.code.toByte(); data[11] = '5'.code.toByte()
        data[12] = 0; data[13] = 0; data[14] = 2; data[15] = 0
        data[16] = 'i'.code.toByte(); data[17] = 's'.code.toByte(); data[18] = 'o'.code.toByte(); data[19] = '6'.code.toByte()
        data[20] = 'm'.code.toByte(); data[21] = 'p'.code.toByte(); data[22] = '4'.code.toByte(); data[23] = '1'.code.toByte()

        // ctts (16 bytes en offset 24): size=16, type='ctts', ver=0, flags=1 (0x00000001), entry_count=1
        data[24] = 0; data[25] = 0; data[26] = 0; data[27] = 16
        data[28] = 'c'.code.toByte(); data[29] = 't'.code.toByte(); data[30] = 't'.code.toByte(); data[31] = 's'.code.toByte()
        data[32] = 0 // ver=0
        data[33] = 0; data[34] = 0; data[35] = 1 // flags=1 (problema Stagefright)
        data[36] = 0; data[37] = 0; data[38] = 0; data[39] = 1 // entry_count=1

        tempFile.writeBytes(data)

        TwitterMediaResolver.normalizeMp4Brands(tempFile)

        val result = tempFile.readBytes()
        // Verificar ftyp parcheado
        assertEquals("isom", String(result, 8, 4, Charsets.US_ASCII))
        assertEquals("mp42", String(result, 16, 4, Charsets.US_ASCII))

        // Verificar ctts parcheado: ver y flags deben ser estrictamente 0x00000000
        assertEquals(0.toByte(), result[32]) // ver
        assertEquals(0.toByte(), result[33]) // flag byte 1
        assertEquals(0.toByte(), result[34]) // flag byte 2
        assertEquals(0.toByte(), result[35]) // flag byte 3
        assertEquals(1.toByte(), result[39]) // entry_count intacto
    }
}

