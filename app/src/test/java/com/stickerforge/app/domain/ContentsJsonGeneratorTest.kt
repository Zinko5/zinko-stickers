package com.stickerforge.app.domain

import android.net.Uri
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class ContentsJsonGeneratorTest {

    @Mock
    lateinit var mockUri: Uri

    @Mock
    lateinit var mockFile: File

    private fun createDummySticker(
        fileName: String,
        emojis: List<String> = listOf("😀"),
        accessibilityText: String = "Test sticker"
    ): PackStickerItem {
        val file = File("/dummy/$fileName")
        return PackStickerItem(
            sourceUri = mockUri,
            processedSticker = ProcessedSticker(
                file = file,
                uri = mockUri,
                width = 512,
                height = 512,
                sizeBytes = 40 * 1024
            ),
            cropParams = CropParameters(),
            name = "Sticker $fileName",
            emojis = emojis,
            accessibilityText = accessibilityText
        )
    }

    @Test
    fun testContentsJsonGeneration() {
        val stickers = listOf(
            createDummySticker("sticker_1.webp", listOf("🤔", "😂"), "Trump pensativo"),
            createDummySticker("sticker_2.webp", listOf("🎉"), "Celebracion fiesta"),
            createDummySticker("sticker_3.webp", listOf("❤️", "🔥", "✨"), "Amor y fuego")
        )

        val pack = StickerPack(
            id = "test_uuid_123",
            identifier = "zinko_pack_test",
            name = "Pack Oficial",
            publisher = "Zinko Stickers",
            stickers = stickers
        )

        val json = ContentsJsonGenerator.generateContentsJson(pack)

        // Verificaciones de estructura
        assertTrue("Debe contener identifier", json.contains("\"identifier\": \"zinko_pack_test\""))
        assertFalse("No debe contener puntos en identifier", json.contains("\"identifier\": \"zinko.pack"))
        assertTrue("Debe contener name", json.contains("\"name\": \"Pack Oficial\""))
        assertTrue("Debe contener publisher", json.contains("\"publisher\": \"Zinko Stickers\""))
        assertTrue("Debe contener tray_image_file", json.contains("\"tray_image_file\": \"tray_zinko_pack_test.png\""))

        // Verificaciones de stickers y metadatos
        assertTrue("Debe incluir sticker_1", json.contains("\"image_file\": \"sticker_1.webp\""))
        assertTrue("Debe incluir emojis de sticker_1", json.contains("\"emojis\": [\"🤔\", \"😂\"]"))
        assertTrue("Debe incluir accessibility_text", json.contains("\"accessibility_text\": \"Trump pensativo\""))

        // Verificacion de array stickers
        assertTrue("Debe incluir sticker_3", json.contains("\"image_file\": \"sticker_3.webp\""))
        assertTrue("Debe incluir maximo 3 emojis", json.contains("\"emojis\": [\"❤️\", \"🔥\", \"✨\"]"))
    }

    @Test
    fun testIdentifierSanitization() {
        val sanitizedWithDots = StickerPack.sanitizeIdentifier("pack.con.puntos")
        assertFalse("No debe contener puntos", sanitizedWithDots.contains("."))

        val sanitizedWithSpaces = StickerPack.sanitizeIdentifier("pack con espacios y mayusculas")
        assertFalse("No debe contener espacios", sanitizedWithSpaces.contains(" "))

        assertTrue("Debe iniciar con zinko_pack_", sanitizedWithDots.startsWith("zinko_pack_"))
    }

    @Test
    fun testValidationChecklistRules() {
        val stickersWithoutEmojis = listOf(
            createDummySticker("sticker_1.webp", emptyList()),
            createDummySticker("sticker_2.webp", listOf("😀")),
            createDummySticker("sticker_3.webp", listOf("🔥"))
        )

        val pack = StickerPack(
            identifier = "zinko_pack_valid",
            stickers = stickersWithoutEmojis
        )

        assertFalse("No todos los stickers tienen emojis", pack.allStickersHaveEmojis)
        assertTrue("El conteo es valido (3)", pack.canExportCount)
        assertTrue("El identificador es valido", pack.hasValidIdentifier)
        assertFalse("No esta listo para exportar sin tray icon o sin emojis", pack.isReadyForWhatsAppExport)
    }
}
