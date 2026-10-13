package com.stickerforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalMediaResolverTest {

    @Test
    fun testDetectPlatform() {
        assertEquals(
            SocialPlatform.YOUTUBE,
            UniversalMediaResolver.detectPlatform("https://www.youtube.com/watch?v=3IqYxjdB8DY")
        )
        assertEquals(
            SocialPlatform.YOUTUBE,
            UniversalMediaResolver.detectPlatform("https://youtu.be/3IqYxjdB8DY")
        )
        assertEquals(
            SocialPlatform.YOUTUBE,
            UniversalMediaResolver.detectPlatform("https://www.youtube.com/shorts/G5aj_yu5LBE")
        )

        assertEquals(
            SocialPlatform.INSTAGRAM,
            UniversalMediaResolver.detectPlatform("https://www.instagram.com/reel/DecM8Afv_9Q/?xtok=MWRhdndtNW9oM2dveQ==")
        )
        assertEquals(
            SocialPlatform.INSTAGRAM,
            UniversalMediaResolver.detectPlatform("https://instagram.com/p/DB12345/")
        )

        assertEquals(
            SocialPlatform.TIKTOK,
            UniversalMediaResolver.detectPlatform("https://vt.tiktok.com/ZSbKW3Rpb/")
        )
        assertEquals(
            SocialPlatform.TIKTOK,
            UniversalMediaResolver.detectPlatform("https://www.tiktok.com/@user/video/123456")
        )

        assertEquals(
            SocialPlatform.TWITTER,
            UniversalMediaResolver.detectPlatform("https://x.com/user/status/123456789")
        )
        assertEquals(
            SocialPlatform.TWITTER,
            UniversalMediaResolver.detectPlatform("https://twitter.com/user/status/123456789")
        )

        assertEquals(
            SocialPlatform.DIRECT_LINK,
            UniversalMediaResolver.detectPlatform("https://example.com/video.mp4")
        )
        assertEquals(
            SocialPlatform.DIRECT_LINK,
            UniversalMediaResolver.detectPlatform("https://example.com/image.webp")
        )

        assertEquals(
            SocialPlatform.UNKNOWN,
            UniversalMediaResolver.detectPlatform("https://wikipedia.org")
        )
    }

    @Test
    fun testDefaultEmojis() {
        assertEquals("▶️", SocialPlatform.YOUTUBE.defaultEmoji)
        assertEquals("📸", SocialPlatform.INSTAGRAM.defaultEmoji)
        assertEquals("🎵", SocialPlatform.TIKTOK.defaultEmoji)
        assertEquals("✖️", SocialPlatform.TWITTER.defaultEmoji)
        assertEquals("✨", SocialPlatform.DIRECT_LINK.defaultEmoji)
    }
}
