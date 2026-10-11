package com.stickerforge.app.domain

import android.content.Context
import android.net.Uri
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
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@RunWith(MockitoJUnitRunner.Silent::class)
class StickerPackBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var mockUri: Uri

    private lateinit var mockContext: Context
    private lateinit var testFilesDir: File
    private lateinit var testCacheDir: File

    @Before
    fun setUp() {
        testFilesDir = tempFolder.newFolder("files")
        testCacheDir = tempFolder.newFolder("cache")
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(testFilesDir)
        `when`(mockContext.cacheDir).thenReturn(testCacheDir)
        `when`(mockContext.packageName).thenReturn("com.stickerforge.app")
        StickerPackBackupManager.fileUriProvider = { _, _ -> mockUri }
    }

    @Test
    fun testExportAndImportBackupRoundTrip() {
        // Crear archivos de prueba
        val stickerFile = File(testFilesDir, "sticker_test_1.webp")
        FileOutputStream(stickerFile).use { it.write("fake_webp_bytes".toByteArray()) }

        val trayFile = File(testFilesDir, "tray_test.png")
        FileOutputStream(trayFile).use { it.write("fake_tray_bytes".toByteArray()) }

        val stickerItem = PackStickerItem(
            id = "test_sticker_1",
            sourceUri = mockUri,
            processedSticker = ProcessedSticker(
                file = stickerFile,
                uri = mockUri,
                width = 512,
                height = 512,
                sizeBytes = stickerFile.length()
            ),
            cropParams = CropParameters(scale = 1.25f, offsetX = 10f, offsetY = -5f, rotationDegrees = 45f),
            isAnimated = true,
            name = "Pepe Animated",
            emojis = listOf("\uD83D\uDE00", "\uD83D\uDD25"),
            accessibilityText = "pepe fuego"
        )

        val originalPack = StickerPack(
            id = "pack_123",
            identifier = "zinko_pack_test",
            name = "Pack de Prueba",
            publisher = "Zinko Studio",
            publisherEmail = "dev@zinko.com",
            publisherWebsite = "https://zinko.com",
            isAnimated = true,
            trayImageFile = trayFile,
            stickers = listOf(stickerItem)
        )

        // 1. Exportar a ZIP
        val outStream = ByteArrayOutputStream()
        val exportResult = StickerPackBackupManager.exportPacksToZip(
            packsToExport = listOf(originalPack),
            outputStream = outStream
        )

        assertTrue(exportResult is BackupExportResult.Success)
        val successExport = exportResult as BackupExportResult.Success
        assertEquals(1, successExport.packsCount)
        assertEquals(1, successExport.stickersCount)
        assertTrue(successExport.bytesWritten > 0)

        val zipBytes = outStream.toByteArray()

        // Verificar que el ZIP contiene manifest.json
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entryNames = mutableListOf<String>()
        var entry = zipIn.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        zipIn.close()

        assertTrue(entryNames.contains(StickerPackBackupManager.MANIFEST_FILE_NAME))
        assertTrue(entryNames.any { it.contains("tray_zinko_pack_test.png") })
        assertTrue(entryNames.any { it.contains("sticker_test_1.webp") })

        // 2. Importar desde el ZIP
        val inStream = ByteArrayInputStream(zipBytes)
        val importResult = StickerPackBackupManager.importPacksFromZip(
            context = mockContext,
            inputStream = inStream,
            existingIdentifiers = emptySet()
        )

        assertTrue(importResult is BackupImportResult.Success)
        val successImport = importResult as BackupImportResult.Success
        assertEquals(1, successImport.packsCount)
        assertEquals(1, successImport.stickersCount)

        val importedPack = successImport.importedPacks.first()
        assertEquals("Pack de Prueba", importedPack.name)
        assertEquals("Zinko Studio", importedPack.publisher)
        assertEquals("dev@zinko.com", importedPack.publisherEmail)
        assertEquals(true, importedPack.isAnimated)
        assertNotNull(importedPack.trayImageFile)
        assertTrue(importedPack.trayImageFile!!.exists())

        assertEquals(1, importedPack.stickers.size)
        val importedSticker = importedPack.stickers.first()
        assertEquals("Pepe Animated", importedSticker.name)
        assertEquals(listOf("\uD83D\uDE00", "\uD83D\uDD25"), importedSticker.emojis)
        assertEquals("pepe fuego", importedSticker.accessibilityText)
        assertEquals(true, importedSticker.isAnimated)
        assertEquals(1.25f, importedSticker.cropParams.scale, 0.01f)
        assertEquals(10f, importedSticker.cropParams.offsetX, 0.01f)
        assertEquals(-5f, importedSticker.cropParams.offsetY, 0.01f)
        assertEquals(45f, importedSticker.cropParams.rotationDegrees, 0.01f)
        assertTrue(importedSticker.processedSticker.file.exists())
    }

    @Test
    fun testExportEmptyListReturnsError() {
        val outStream = ByteArrayOutputStream()
        val result = StickerPackBackupManager.exportPacksToZip(
            packsToExport = emptyList(),
            outputStream = outStream
        )
        assertTrue(result is BackupExportResult.Error)
    }

    @Test
    fun testImportCorruptedStreamReturnsError() {
        val corruptedBytes = "not_a_valid_zip_content".toByteArray()
        val inStream = ByteArrayInputStream(corruptedBytes)
        val result = StickerPackBackupManager.importPacksFromZip(
            context = mockContext,
            inputStream = inStream
        )
        assertTrue(result is BackupImportResult.Error)
    }
}
