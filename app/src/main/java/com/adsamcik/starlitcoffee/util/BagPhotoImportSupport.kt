package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BagPhotoImportSupport {
    private const val TAG = "BagPhotoImportSupport"
    private const val DEFAULT_EXTENSION = "jpg"
    private const val MAX_IMPORTED_PHOTOS = 2
    private val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    fun <T> limitImportedSelection(
        selectedItems: List<T>,
        maxPhotos: Int = MAX_IMPORTED_PHOTOS,
    ): List<T> = selectedItems.take(maxPhotos)

    fun buildImportedPhotoName(
        sourceName: String?,
        mimeType: String?,
        timestampMs: Long,
        index: Int = 0,
        originalExtension: String? = null,
    ): String {
        val sanitizedSourceName = sourceName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?.replace(Regex("_+"), "_")
            ?.trim('_')
            .orEmpty()
            .ifBlank { "selected_photo" }
        val extension = resolveExtension(
            mimeType = mimeType,
            originalExtension = originalExtension,
        )
        return "coffee_label_${timestampMs}_${index + 1}_${sanitizedSourceName}.${extension}"
    }

    suspend fun importPhotosToCache(
        context: Context,
        sourceUris: List<Uri>,
        maxPhotos: Int = MAX_IMPORTED_PHOTOS,
    ): List<Uri> = withContext(Dispatchers.IO) {
        val timestampMs = System.currentTimeMillis()
        limitImportedSelection(sourceUris, maxPhotos).mapIndexedNotNull { index, sourceUri ->
            try {
                val sourceName = sourceUri.lastPathSegment?.substringAfterLast('/')
                val originalExtension = sourceName
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
                val importedPhoto = File(
                    context.cacheDir,
                    buildImportedPhotoName(
                        sourceName = sourceName,
                        mimeType = context.contentResolver.getType(sourceUri),
                        timestampMs = timestampMs,
                        index = index,
                        originalExtension = originalExtension,
                    ),
                )
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    importedPhoto.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@mapIndexedNotNull null
                importedPhoto.toUri()
            } catch (error: Exception) {
                Log.w(TAG, "Failed to import gallery photo: $sourceUri", error)
                null
            }
        }
    }

    private fun resolveExtension(
        mimeType: String?,
        originalExtension: String?,
    ): String {
        return extensionFromMimeType(mimeType)
            ?: originalExtension
                ?.lowercase(Locale.US)
                ?.takeIf { it in KNOWN_IMAGE_EXTENSIONS }
            ?: DEFAULT_EXTENSION
    }

    private fun extensionFromMimeType(mimeType: String?): String? {
        return when (mimeType?.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> null
        }
    }
}
