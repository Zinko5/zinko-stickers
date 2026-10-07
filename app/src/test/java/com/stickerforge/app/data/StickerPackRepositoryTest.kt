package com.stickerforge.app.data

import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import android.net.Uri
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class StickerPackRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var mockUri: Uri

    private lateinit var repository: StickerPackRepository
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        repository = StickerPackRepository(filesDir = filesDir)
        Mockito.`when`(mockUri.toString()).thenReturn("content://media/test/image1")
        repository.uriParser = { mockUri }
    }

    @Test
    fun testSaveAndLoadPacksPersistsState() {
        // Crear archivo simulado de sticker en disco
        val stickerFile = File(filesDir, "sample_sticker.webp").apply {
            writeBytes(ByteArray(2048))
        }

        val stickerItem = PackStickerItem(
            id = "item_1",
            sourceUri = mockUri,
            processedSticker = ProcessedSticker(
                file = stickerFile,
                uri = mockUri,
                width = 512,
                height = 512,
                sizeBytes = stickerFile.length()
            ),
            cropParams = CropParameters(scale = 1.25f, offsetX = 10f, offsetY = -5f),
            isAnimated = false,
            name = "Sticker Test",
            emojis = listOf("😀", "🔥"),
            accessibilityText = "Sticker para prueba"
        )

        val pack1 = StickerPack(
            id = "pack_alpha",
            identifier = "zinko_pack_alpha",
            name = "Pack Alpha",
            publisher = "Zinko Artista",
            isAnimated = false,
            stickers = listOf(stickerItem)
        )

        val pack2 = StickerPack(
            id = "pack_beta",
            identifier = "zinko_pack_beta",
            name = "Pack Beta",
            publisher = "Zinko Artista",
            isAnimated = true,
            stickers = emptyList()
        )

        // Guardar
        val saveSuccess = repository.savePacks(listOf(pack1, pack2), activePackId = "pack_alpha")
        assertTrue("La persistencia en disco debe ser exitosa", saveSuccess)

        // Cargar
        val loadedState = repository.loadPacks()
        assertNotNull("El estado cargado no debe ser nulo", loadedState)
        assertEquals("pack_alpha", loadedState?.activePackId)
        assertEquals(2, loadedState?.packs?.size)

        val loadedPack1 = loadedState?.packs?.first { it.id == "pack_alpha" }
        assertNotNull(loadedPack1)
        assertEquals("Pack Alpha", loadedPack1?.name)
        assertEquals("Zinko Artista", loadedPack1?.publisher)
        assertEquals(1, loadedPack1?.stickers?.size)

        val loadedSticker = loadedPack1?.stickers?.first()
        assertEquals("item_1", loadedSticker?.id)
        assertEquals("Sticker Test", loadedSticker?.name)
        assertEquals(listOf("😀", "🔥"), loadedSticker?.emojis)
        assertEquals(1.25f, loadedSticker?.cropParams?.scale ?: 0f, 0.001f)
    }

    @Test
    fun testEmptyDatabaseReturnsNull() {
        val loaded = repository.loadPacks()
        assertEquals(null, loaded)
    }
}
