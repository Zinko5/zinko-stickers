package com.stickerforge.app.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class ProcessedStickerTest {

    @Mock
    lateinit var mockUri: Uri

    @Mock
    lateinit var mockFile: File

    @Test
    fun testProcessedStickerSizeLimits() {
        val validSticker = ProcessedSticker(
            file = mockFile,
            uri = mockUri,
            width = 512,
            height = 512,
            sizeBytes = 85 * 1024
        )
        assertTrue(validSticker.isUnder100KB)
        assertEquals("85.0 KB", validSticker.formattedSize)
        assertEquals(512, validSticker.width)
        assertEquals(512, validSticker.height)

        val oversizedSticker = ProcessedSticker(
            file = mockFile,
            uri = mockUri,
            width = 512,
            height = 512,
            sizeBytes = 105 * 1024
        )
        assertFalse(oversizedSticker.isUnder100KB)
        assertEquals("105.0 KB", oversizedSticker.formattedSize)
    }

    @Test
    fun testExactWhatsAppBounds() {
        val sticker = ProcessedSticker(
            file = mockFile,
            uri = mockUri,
            width = 512,
            height = 512,
            sizeBytes = 50 * 1024
        )
        assertEquals(512, sticker.width)
        assertEquals(512, sticker.height)
    }
}
