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
class StickerPackTest {

    @Mock
    lateinit var mockUri: Uri

    @Mock
    lateinit var mockFile: File

    private fun createDummyStickerItem(isAnimated: Boolean = false): PackStickerItem {
        return PackStickerItem(
            sourceUri = mockUri,
            processedSticker = ProcessedSticker(
                file = mockFile,
                uri = mockUri,
                width = 512,
                height = 512,
                sizeBytes = 50 * 1024
            ),
            isAnimated = isAnimated
        )
    }

    @Test
    fun testPackExportLimits() {
        val emptyPack = StickerPack(name = "Test Pack")
        assertFalse("Pack vacio no debe poder exportarse", emptyPack.canExport)
        assertEquals(3, emptyPack.remainingToMin)

        val twoStickersPack = emptyPack.copy(
            stickers = listOf(createDummyStickerItem(), createDummyStickerItem())
        )
        assertFalse("Pack con 2 stickers no debe poder exportarse", twoStickersPack.canExport)
        assertEquals(1, twoStickersPack.remainingToMin)

        val validPack = emptyPack.copy(
            stickers = listOf(createDummyStickerItem(), createDummyStickerItem(), createDummyStickerItem())
        )
        assertTrue("Pack con 3 stickers debe ser valido para exportar", validPack.canExport)
        assertEquals(0, validPack.remainingToMin)

        // 30 stickers pack
        val fullList = (1..30).map { createDummyStickerItem() }
        val fullPack = emptyPack.copy(stickers = fullList)
        assertTrue("Pack con 30 stickers debe ser valido", fullPack.canExport)
        assertTrue("Pack debe estar lleno", fullPack.isFull)

        // >30 stickers pack
        val overPack = emptyPack.copy(stickers = fullList + createDummyStickerItem())
        assertFalse("Pack con mas de 30 stickers no debe poder exportarse", overPack.canExport)
    }

    @Test
    fun testAnimatedPackType() {
        val staticPack = StickerPack(isAnimated = false)
        assertFalse(staticPack.isAnimated)

        val animatedPack = StickerPack(isAnimated = true)
        assertTrue(animatedPack.isAnimated)
    }
}
