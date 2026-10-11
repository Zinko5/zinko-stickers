package com.stickerforge.app.util

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.stickerforge.app.model.ImageMetadata

object ImageUtils {

    fun extractMetadata(contentResolver: ContentResolver, uri: Uri): ImageMetadata? {
        return try {
            var fileName = "imagen_seleccionada"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val mimeType = contentResolver.getType(uri) ?: "image/*"

            // Detectar si el medio es animado (Video, GIF o WebP animado)
            val isAnimated = isAnimatedImage(contentResolver, uri, mimeType)

            var width = 0
            var height = 0

            if (mimeType.startsWith("video/")) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        val wStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val hStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        width = wStr?.toIntOrNull() ?: 512
                        height = hStr?.toIntOrNull() ?: 512
                    }
                } catch (e: Exception) {
                    width = 512
                    height = 512
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
            } else {
                // Read image dimensions without allocating full bitmap in memory (prevents OOM on large >10MB images)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                width = if (options.outWidth > 0) options.outWidth else 0
                height = if (options.outHeight > 0) options.outHeight else 0
            }

            ImageMetadata(
                uri = uri,
                fileName = fileName,
                width = width,
                height = height,
                sizeBytes = fileSize,
                mimeType = mimeType,
                isAnimated = isAnimated
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isAnimatedImage(contentResolver: ContentResolver, uri: Uri, mimeType: String? = null): Boolean {
        val resolvedMime = (mimeType ?: contentResolver.getType(uri))?.lowercase() ?: ""
        if (resolvedMime.startsWith("video/")) return true
        if (resolvedMime == "image/gif") return true
        if (resolvedMime == "image/webp") {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(64)
                    val read = stream.read(header)
                    if (read >= 30) {
                        val headerStr = String(header, 0, read)
                        if (headerStr.contains("ANIM")) return true
                        if (header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                            header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()) {
                            val flags = header[20].toInt()
                            if ((flags and 0x02) != 0) return true
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and treat as static
            }
        }
        return false
    }
}
