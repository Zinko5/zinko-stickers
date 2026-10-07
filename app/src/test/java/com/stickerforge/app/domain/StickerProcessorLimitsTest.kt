package com.stickerforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerProcessorLimitsTest {

    @Test
    fun testTargetDimensionsMatchWhatsAppSpecification() {
        assertEquals(512, StickerProcessor.TARGET_SIZE_PX)
    }

    @Test
    fun testMaxFileSizeCompliesWithWhatsApp100KBLimit() {
        assertEquals(102400, StickerProcessor.MAX_FILE_SIZE_BYTES)
        assertTrue(StickerProcessor.MAX_FILE_SIZE_BYTES <= 100 * 1024)
    }
}
