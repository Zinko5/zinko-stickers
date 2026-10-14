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

    @Test
    fun testExtractFramesFromAnimatedWebpRejectsInvalidOrNonWebpBytes() {
        org.junit.Assert.assertNull(AnimatedWebpEncoder.extractFramesFromAnimatedWebp(ByteArray(0)))
        org.junit.Assert.assertNull(AnimatedWebpEncoder.extractFramesFromAnimatedWebp(ByteArray(10)))
        val dummyBytes = "NOT_A_WEBP_FILE".toByteArray()
        org.junit.Assert.assertNull(AnimatedWebpEncoder.extractFramesFromAnimatedWebp(dummyBytes))
    }

    @Test
    fun testExtractFramesFromAnimatedWebpRejectsStaticWebp() {
        // Un archivo WebP estatico tiene RIFF...WEBP pero no tiene chunk VP8X animado ni ANMF
        val staticWebp = java.io.ByteArrayOutputStream()
        staticWebp.write("RIFF".toByteArray())
        staticWebp.write(byteArrayOf(20, 0, 0, 0)) // 20 bytes
        staticWebp.write("WEBP".toByteArray())
        staticWebp.write("VP8 ".toByteArray())
        staticWebp.write(byteArrayOf(4, 0, 0, 0)) // 4 bytes
        staticWebp.write(byteArrayOf(0, 0, 0, 0))
        val result = AnimatedWebpEncoder.extractFramesFromAnimatedWebp(staticWebp.toByteArray())
        org.junit.Assert.assertNull(result)
    }
}
