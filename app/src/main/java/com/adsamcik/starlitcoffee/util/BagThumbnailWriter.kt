package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.net.toUri
import java.io.File

/**
 * Renders a focused, square WebP thumbnail for a coffee bag by cropping the
 * front photo to the label region the scan detected (see [BagThumbnailFocus]).
 * This produces the small `photoUri` the list card displays.
 */
object BagThumbnailWriter {

    private const val TAG = "BagThumbnailWriter"
    private const val MAX_DECODE_PX = 2048

    /**
     * Crops the photo at [sourceUri] to a square thumbnail around [focus] and
     * writes it next to the other bag photos. Returns the new file URI, or null
     * on any failure so the caller can fall back to the full photo.
     *
     * Must be called off the main thread (decodes a bitmap + writes a file).
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun createFocusedThumbnail(
        context: Context,
        sourceUri: String,
        focus: BagPhotoRect,
        targetSizePx: Int,
        storageKey: String,
    ): String? {
        return try {
            val source = ScanPhotoStorage.resolvePermanentPhoto(context, sourceUri) ?: return null

            // Decode at enough resolution that the (possibly small) crop still
            // has roughly targetSizePx on its short side, capped so a 12 MP
            // frame can't be decoded at full size for a tiny label region.
            val focusWidth = (focus.rightFraction - focus.leftFraction).coerceAtLeast(0.05f)
            val focusHeight = (focus.bottomFraction - focus.topFraction).coerceAtLeast(0.05f)
            val decodeTarget = (targetSizePx / minOf(focusWidth, focusHeight))
                .toInt()
                .coerceIn(targetSizePx, MAX_DECODE_PX)

            val bitmap = ThumbnailLoader.loadThumbnail(source.path, decodeTarget) ?: return null
            try {
                val crop = BagThumbnailFocus.squareCrop(focus, bitmap.width, bitmap.height)

                val left = (bitmap.width * crop.leftFraction).toInt().coerceIn(0, bitmap.width - 1)
                val top = (bitmap.height * crop.topFraction).toInt().coerceIn(0, bitmap.height - 1)
                val right = (bitmap.width * crop.rightFraction).toInt().coerceIn(left + 1, bitmap.width)
                val bottom = (bitmap.height * crop.bottomFraction).toInt().coerceIn(top + 1, bitmap.height)
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

                val dir = File(context.filesDir, "bag_photos").apply { mkdirs() }
                val outFile = File(dir, ScanPhotoStorage.focusedThumbnailFileName(storageKey))
                ScanPhotoStorage.writeOptimizedWebp(
                    source = cropped,
                    destination = outFile,
                    maxLongEdgePx = targetSizePx.coerceAtMost(
                        PhotoStoragePolicy.THUMBNAIL_MAX_LONG_EDGE_PX,
                    ),
                    quality = PhotoStoragePolicy.THUMBNAIL_WEBP_QUALITY,
                )
                outFile.toUri().toString()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create focused thumbnail", e)
            null
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Insufficient memory to create focused thumbnail", error)
            null
        }
    }
}
