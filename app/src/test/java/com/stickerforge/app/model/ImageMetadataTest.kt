package com.stickerforge.app.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ImageMetadataTest {

    @Mock
    lateinit var mockUri: Uri

    @Test
    fun testFormattedSizeFormatting() {
        val metaBytes = ImageMetadata(
            uri = mockUri,
            fileName = "test.png",
            width = 512,
            height = 512,
            sizeBytes = 500,
            mimeType = "image/png"
        )
        assertEquals("500 B", metaBytes.formattedSize)

        val metaKb = ImageMetadata(
            uri = mockUri,
            fileName = "test.png",
            width = 512,
            height = 512,
            sizeBytes = 50 * 1024,
            mimeType = "image/png"
        )
        assertEquals("50.0 KB", metaKb.formattedSize)

        val metaMb = ImageMetadata(
            uri = mockUri,
            fileName = "test.png",
            width = 1080,
            height = 1080,
            sizeBytes = 12 * 1024 * 1024,
            mimeType = "image/png"
        )
        assertEquals("12.00 MB", metaMb.formattedSize)
        assertTrue(metaMb.isLargerThan10MB)
    }

    @Test
    fun testSquareDimensions() {
        val squareMeta = ImageMetadata(
            uri = mockUri,
            fileName = "square.jpg",
            width = 512,
            height = 512,
            sizeBytes = 2048,
            mimeType = "image/jpeg"
        )
        assertTrue(squareMeta.isSquare)

        val rectMeta = ImageMetadata(
            uri = mockUri,
            fileName = "rect.jpg",
            width = 1080,
            height = 1920,
            sizeBytes = 2048,
            mimeType = "image/jpeg"
        )
        assertFalse(rectMeta.isSquare)
    }
}
