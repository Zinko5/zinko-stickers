package com.stickerforge.app.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.stickerforge.app.BuildConfig
import com.stickerforge.app.model.StickerPack
import com.stickerforge.app.provider.StickerContentProviderConstants

data class WhatsAppPackageInfo(
    val packageName: String,
    val title: String,
    val isBusiness: Boolean
)

sealed class ExportResult {
    data object Success : ExportResult()
    data class Cancelled(val validationError: String?) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Encapsula la logica de exportacion de paquetes de stickers a WhatsApp mediante el Intent oficial.
 */
object WhatsAppExporter {
    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"
    const val EXTRA_VALIDATION_ERROR = "validation_error"

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Comprueba si una aplicacion especifica de WhatsApp esta instalada en el dispositivo.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retorna la lista de aplicaciones de WhatsApp instaladas (WhatsApp estandar y/o Business).
     */
    fun getInstalledWhatsAppPackages(context: Context): List<WhatsAppPackageInfo> {
        val list = mutableListOf<WhatsAppPackageInfo>()
        if (isPackageInstalled(context, PACKAGE_WHATSAPP)) {
            list.add(WhatsAppPackageInfo(PACKAGE_WHATSAPP, "WhatsApp", isBusiness = false))
        }
        if (isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)) {
            list.add(WhatsAppPackageInfo(PACKAGE_WHATSAPP_BUSINESS, "WhatsApp Business", isBusiness = true))
        }
        return list
    }

    /**
     * Construye el Intent oficial de exportacion con los extras requeridos por WhatsApp.
     */
    fun createExportIntent(
        context: Context,
        pack: StickerPack,
        targetPackage: String? = null
    ): Intent {
        val authority = try {
            BuildConfig.CONTENT_PROVIDER_AUTHORITY
        } catch (e: Throwable) {
            "${context.packageName}.stickercontentprovider"
        }

        return Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
            putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    /**
     * Interpreta el resultado retornado tras completar el handshake con WhatsApp.
     */
    fun parseExportResult(resultCode: Int, data: Intent?): ExportResult {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                ExportResult.Success
            }
            Activity.RESULT_CANCELED -> {
                val validationError = data?.getStringExtra(EXTRA_VALIDATION_ERROR)
                ExportResult.Cancelled(validationError)
            }
            else -> {
                val validationError = data?.getStringExtra(EXTRA_VALIDATION_ERROR)
                ExportResult.Error(validationError ?: "Error desconocido durante la exportacion (codigo: $resultCode)")
            }
        }
    }
}
