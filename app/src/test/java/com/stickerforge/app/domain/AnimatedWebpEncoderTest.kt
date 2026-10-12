package com.stickerforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedWebpEncoderTest {

    @Test
    fun testAnimatedTargetDimensionsMatchWhatsAppSpecification() {
        assertEquals(512, AnimatedWebpEncoder.TARGET_SIZE_PX)
    }

    @Test
    fun testAnimatedMaxFileSizeCompliesWithWhatsApp500KBLimit() {
        assertEquals(500 * 1024, AnimatedWebpEncoder.MAX_ANIMATED_SIZE_BYTES)
        assertTrue(AnimatedWebpEncoder.MAX_ANIMATED_SIZE_BYTES <= 500 * 1024)
    }

    @Test
    fun testAnimatedMaxDurationIsStrictlyUnderWhatsAppTenSecondsRequirement() {
        assertTrue("La duracion maxima debe ser estrictamente menor a 10 segundos", AnimatedWebpEncoder.MAX_WHATSAPP_DURATION_MS < 10000L)
        assertEquals(9600L, AnimatedWebpEncoder.MAX_WHATSAPP_DURATION_MS)
    }
}
