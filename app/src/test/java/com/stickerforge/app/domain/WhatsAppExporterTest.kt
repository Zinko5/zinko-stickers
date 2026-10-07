package com.stickerforge.app.domain

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class WhatsAppExporterTest {

    @Test
    fun `test official action and extra constants match WhatsApp specification`() {
        assertEquals("com.whatsapp.intent.action.ENABLE_STICKER_PACK", WhatsAppExporter.ACTION_ENABLE_STICKER_PACK)
        assertEquals("sticker_pack_id", WhatsAppExporter.EXTRA_STICKER_PACK_ID)
        assertEquals("sticker_pack_authority", WhatsAppExporter.EXTRA_STICKER_PACK_AUTHORITY)
        assertEquals("sticker_pack_name", WhatsAppExporter.EXTRA_STICKER_PACK_NAME)
        assertEquals("validation_error", WhatsAppExporter.EXTRA_VALIDATION_ERROR)
        assertEquals("com.whatsapp", WhatsAppExporter.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.w4b", WhatsAppExporter.PACKAGE_WHATSAPP_BUSINESS)
    }

    @Test
    fun `test parseExportResult returns Success on RESULT_OK`() {
        val result = WhatsAppExporter.parseExportResult(Activity.RESULT_OK, null)
        assertTrue(result is ExportResult.Success)
    }

    @Test
    fun `test parseExportResult returns Cancelled with validation error on RESULT_CANCELED`() {
        val mockIntent = mock(Intent::class.java)
        `when`(mockIntent.getStringExtra("validation_error")).thenReturn("Stickers must be 512x512")

        val result = WhatsAppExporter.parseExportResult(Activity.RESULT_CANCELED, mockIntent)
        assertTrue(result is ExportResult.Cancelled)
        assertEquals("Stickers must be 512x512", (result as ExportResult.Cancelled).validationError)
    }

    @Test
    fun `test parseExportResult returns Cancelled with null when user cancels dialog`() {
        val result = WhatsAppExporter.parseExportResult(Activity.RESULT_CANCELED, null)
        assertTrue(result is ExportResult.Cancelled)
        assertEquals(null, (result as ExportResult.Cancelled).validationError)
    }
}
