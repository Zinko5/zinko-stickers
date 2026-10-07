package com.stickerforge.app.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerContentProviderConstantsTest {

    @Test
    fun `test official WhatsApp metadata column constants`() {
        assertEquals("sticker_pack_identifier", StickerContentProviderConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)
        assertEquals("sticker_pack_name", StickerContentProviderConstants.STICKER_PACK_NAME_IN_QUERY)
        assertEquals("sticker_pack_publisher", StickerContentProviderConstants.STICKER_PACK_PUBLISHER_IN_QUERY)
        assertEquals("sticker_pack_icon", StickerContentProviderConstants.STICKER_PACK_ICON_IN_QUERY)
        assertEquals("android_play_store_link", StickerContentProviderConstants.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY)
        assertEquals("ios_app_download_link", StickerContentProviderConstants.IOS_APP_DOWNLOAD_LINK_IN_QUERY)
        assertEquals("sticker_pack_publisher_email", StickerContentProviderConstants.PUBLISHER_EMAIL)
        assertEquals("sticker_pack_publisher_website", StickerContentProviderConstants.PUBLISHER_WEBSITE)
        assertEquals("sticker_pack_privacy_policy_website", StickerContentProviderConstants.PRIVACY_POLICY_WEBSITE)
        assertEquals("sticker_pack_license_agreement_website", StickerContentProviderConstants.LICENSE_AGREEMENT_WEBSITE)
        assertEquals("image_data_version", StickerContentProviderConstants.IMAGE_DATA_VERSION)
        assertEquals("whatsapp_will_not_cache_stickers", StickerContentProviderConstants.AVOID_CACHE)
        assertEquals("animated_sticker_pack", StickerContentProviderConstants.ANIMATED_STICKER_PACK)
    }

    @Test
    fun `test official WhatsApp sticker items column constants`() {
        assertEquals("sticker_file_name", StickerContentProviderConstants.STICKER_FILE_NAME_IN_QUERY)
        assertEquals("sticker_emoji", StickerContentProviderConstants.STICKER_FILE_EMOJI_IN_QUERY)
        assertEquals("sticker_accessibility_text", StickerContentProviderConstants.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY)
    }

    @Test
    fun `test columns array contains all 13 metadata keys and 3 sticker keys`() {
        assertEquals(13, StickerContentProviderConstants.METADATA_COLUMNS.size)
        assertTrue(StickerContentProviderConstants.METADATA_COLUMNS.contains("sticker_pack_identifier"))
        assertTrue(StickerContentProviderConstants.METADATA_COLUMNS.contains("sticker_pack_name"))
        assertTrue(StickerContentProviderConstants.METADATA_COLUMNS.contains("animated_sticker_pack"))

        assertEquals(3, StickerContentProviderConstants.STICKERS_COLUMNS.size)
        assertArrayEquals(
            arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"),
            StickerContentProviderConstants.STICKERS_COLUMNS
        )
    }
}
