package com.stickerforge.app.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickerforge.app.BuildConfig
import com.stickerforge.app.domain.StickerProcessor
import com.stickerforge.app.model.StickerPack
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider oficial para integracion con WhatsApp Stickers API.
 * Expone metadatos de paquetes, listas de stickers y descriptores de archivos WebP/PNG.
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH)
    }

    private fun getAuthority(): String {
        return context?.packageName?.let { "$it.stickercontentprovider" }
            ?: try {
                BuildConfig.CONTENT_PROVIDER_AUTHORITY
            } catch (e: Throwable) {
                StickerContentProviderConstants.DEFAULT_AUTHORITY
            }
    }

    override fun onCreate(): Boolean {
        val authority = getAuthority()
        MATCHER.addURI(authority, StickerContentProviderConstants.METADATA, METADATA_CODE)
        MATCHER.addURI(authority, "${StickerContentProviderConstants.METADATA}/*", METADATA_CODE_FOR_SINGLE_PACK)
        MATCHER.addURI(authority, "${StickerContentProviderConstants.STICKERS}/*", STICKERS_CODE)
        MATCHER.addURI(authority, "${StickerContentProviderConstants.STICKERS_ASSET}/*/*", STICKERS_ASSET_CODE)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (MATCHER.match(uri)) {
            METADATA_CODE -> {
                val packs = StickerPackStorage.getAllPacks(ctx)
                createPacksCursor(uri, packs)
            }
            METADATA_CODE_FOR_SINGLE_PACK -> {
                val identifier = uri.lastPathSegment ?: ""
                val pack = StickerPackStorage.getPack(ctx, identifier)
                val list = if (pack != null) listOf(pack) else emptyList()
                createPacksCursor(uri, list)
            }
            STICKERS_CODE -> {
                val identifier = uri.lastPathSegment ?: ""
                val pack = StickerPackStorage.getPack(ctx, identifier)
                createStickersCursor(uri, pack)
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String? {
        val authority = getAuthority()
        return when (MATCHER.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$authority.${StickerContentProviderConstants.METADATA}"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$authority.${StickerContentProviderConstants.METADATA}"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$authority.${StickerContentProviderConstants.STICKERS}"
            STICKERS_ASSET_CODE -> {
                val fileName = uri.lastPathSegment ?: ""
                if (fileName.endsWith(".webp", ignoreCase = true)) {
                    "image/webp"
                } else if (fileName.endsWith(".png", ignoreCase = true)) {
                    "image/png"
                } else {
                    "application/octet-stream"
                }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: throw IllegalStateException("Context is null")
        val matchCode = MATCHER.match(uri)
        if (matchCode == STICKERS_ASSET_CODE) {
            val segments = uri.pathSegments
            if (segments.size < 3) {
                throw IllegalArgumentException("Expected 3 path segments (stickers_asset/identifier/file), got: $uri")
            }
            val identifier = segments[1]
            val fileName = segments[2]

            // Validacion de seguridad contra Path Traversal
            if (identifier.contains("..") || fileName.contains("..") || identifier.contains("/") || fileName.contains("/")) {
                throw SecurityException("Invalid path segments in URI: $uri")
            }

            // 1. Buscar en directorio del paquete: files/packs/<identifier>/<fileName>
            val packFile = File(ctx.filesDir, "packs/$identifier/$fileName")
            if (packFile.exists()) {
                validateCanonicalPath(ctx, packFile)
                val pfd = ParcelFileDescriptor.open(packFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }

            // 2. Buscar en directorio de tray icons: files/tray_icons/<fileName>
            val trayFile = File(ctx.filesDir, "tray_icons/$fileName")
            if (trayFile.exists()) {
                validateCanonicalPath(ctx, trayFile)
                val pfd = ParcelFileDescriptor.open(trayFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }

            // 3. Buscar en directorio de stickers: files/stickers/<fileName>
            val stickerFile = File(ctx.filesDir, "stickers/$fileName")
            if (stickerFile.exists()) {
                validateCanonicalPath(ctx, stickerFile)
                val pfd = ParcelFileDescriptor.open(stickerFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }

            // 4. Fallback: Asset predeterminado de Pepe Smolder si es solicitado como icono
            if (fileName.contains("pepe", ignoreCase = true) || fileName.contains("tray", ignoreCase = true)) {
                try {
                    return ctx.assets.openFd(StickerProcessor.DEFAULT_TRAY_ASSET_NAME)
                } catch (e: Exception) {
                    // Ignorar y lanzar FileNotFoundException abajo
                }
            }

            throw FileNotFoundException("File not found for URI: $uri")
        }

        return super.openAssetFile(uri, mode)
    }

    private fun validateCanonicalPath(context: android.content.Context, file: File) {
        val baseDirCanonical = context.filesDir.canonicalPath
        val fileCanonical = file.canonicalPath
        if (!fileCanonical.startsWith(baseDirCanonical)) {
            throw SecurityException("Path traversal attempt detected: $fileCanonical")
        }
    }

    private fun createPacksCursor(uri: Uri, packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(StickerContentProviderConstants.METADATA_COLUMNS)
        for (pack in packs) {
            val trayName = pack.trayImageFile?.name ?: "tray_${pack.identifier}.png"
            val row = arrayOf<Any?>(
                pack.identifier,
                pack.name,
                pack.publisher,
                trayName,
                "", // android_play_store_link
                "", // ios_app_download_link
                pack.publisherEmail,
                pack.publisherWebsite,
                pack.privacyPolicyWebsite,
                pack.licenseAgreementWebsite,
                "1", // image_data_version
                0, // avoid_cache
                if (pack.isAnimated) 1 else 0 // animated_sticker_pack
            )
            cursor.addRow(row)
        }
        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    private fun createStickersCursor(uri: Uri, pack: StickerPack?): Cursor {
        val cursor = MatrixCursor(StickerContentProviderConstants.STICKERS_COLUMNS)
        if (pack != null) {
            for (sticker in pack.stickers) {
                val fileName = sticker.processedSticker.file.name
                val emojisString = sticker.emojis.take(3).joinToString(",")
                val accessibilityText = sticker.effectiveSearchKeywords
                val row = arrayOf<Any?>(fileName, emojisString, accessibilityText)
                cursor.addRow(row)
            }
        }
        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete operation not supported")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert operation not supported")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Update operation not supported")
    }
}
