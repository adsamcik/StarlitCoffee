package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import java.io.File

/**
 * Filesystem helpers for the guided bag-scan flow: writing freshly captured
 * camera frames to the cache, and promoting kept photos to permanent storage
 * when a bag is saved.
 *
 * Cache captures use the `file://` scheme so the existing OCR pipeline
 * ([com.adsamcik.starlitcoffee.viewmodel.BrewViewModel] `decodeBagPhotoBitmap`)
 * can decode them via `contentResolver.openInputStream`.
 */
object ScanPhotoStorage {
    private const val TAG = "ScanPhotoStorage"
    private const val CACHE_DIR = "bag_scan_captures"
    private const val PERMANENT_DIR = "bag_photos"

    /**
     * Write JPEG [bytes] to a new cache file and return its `file://` URI
     * string, or null on failure.
     */
    fun writeCaptureToCache(context: Context, bytes: ByteArray): String? = try {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}_${bytes.size}.jpg")
        file.outputStream().use { it.write(bytes) }
        file.toUri().toString()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to write capture to cache", e)
        null
    }

    /** Best-effort deletion of a cache-file capture (e.g. user removed a thumbnail). */
    fun deleteCapture(uriStr: String) {
        try {
            val uri = uriStr.toUri()
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete capture $uriStr", e)
        }
    }

    /**
     * Copies photos from cache to permanent storage (`filesDir/bag_photos/`).
     * Returns a comma-separated list of permanent `file://` URI strings.
     * `openInputStream` resolves both `file://` (camera capture) and `content://`
     * (gallery picker / SAF) URIs.
     */
    fun copyPhotosToPermanentStorage(context: Context, cacheUris: String): String {
        val bagPhotosDir = File(context.filesDir, PERMANENT_DIR).apply { mkdirs() }
        return cacheUris.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexedNotNull { index, uriStr ->
                try {
                    val uri = uriStr.toUri()
                    val destFile = File(bagPhotosDir, "bag_${System.currentTimeMillis()}_$index.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: run {
                        Log.w(TAG, "Could not open photo stream for $uriStr")
                        return@mapIndexedNotNull null
                    }
                    destFile.toUri().toString()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy photo to permanent storage", e)
                    null
                }
            }
            .joinToString(",")
    }
}
