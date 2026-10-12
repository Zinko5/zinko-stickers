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
}
