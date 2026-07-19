package com.adsamcik.starlitcoffee.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Off-main bitmap loader for bag photo thumbnails.
 *
 * Two responsibilities the call sites used to do synchronously inside
 * `remember { … }` during composition:
 *  * Decode the JPEG from disk (full-resolution!) with [BitmapFactory.decodeFile].
 *  * Run [ImagePreprocessor.applyExifRotation] which reads the EXIF tag and
 *    creates a rotated copy of the bitmap.
 *
 * Both steps are CPU + IO heavy — large bag photos can take 50–200 ms to
 * decode at full resolution, plus a Bitmap.createBitmap allocation. Doing
 * them on the main thread caused list-scroll jank and contributed to the
 * camera-preview freeze whenever a bag list was visible during inference.
 *
 * Three layers of protection beyond just "off the main thread":
 *  * **LruCache** — re-decoding the same `(path, sampleSize)` is cheap to
 *    avoid; an [LruCache] sized as a fraction of the runtime memory ceiling
 *    keeps recently-shown thumbnails in memory across scroll cycles.
 *  * **Bounded parallelism** — a `LazyColumn` first-paint can fan out 30+
 *    `produceState` decode coroutines simultaneously. We use a
 *    [Dispatchers.IO]-derived dispatcher with a small parallelism cap so
 *    decodes serialize through a small worker pool instead of spawning
 *    dozens of concurrent native decodes.
 *  * **Downsampling** — `BitmapFactory.Options.inSampleSize` to a power of
 *    two whose result still has the longest side ≥ `targetSizePx`.
 *
 * Two entry points:
 *  * [loadThumbnailFromUri] — `suspend`; the recommended entry point. Owns
 *    its own dispatcher so call sites (typically Compose `produceState`)
 *    don't need to think about which dispatcher to use.
 *  * [loadThumbnail] — synchronous; for callers that already hold a
 *    background dispatcher (e.g. ViewModel pipelines that need to chain
 *    crop / preprocess steps).
 */
object ThumbnailLoader {

    private const val TAG = "ThumbnailLoader"

    /**
     * Maximum decode parallelism. JPEG decoding is CPU-bound and not
     * particularly IO-heavy once the page cache is warm, so a small fixed
     * worker pool keeps things responsive without starving other IO. 4 was
     * chosen empirically: 1 leaves throughput on the table when many cards
     * are visible at once; >4 didn't reduce first-paint time on test
     * devices and increased memory churn.
     */
    private const val DECODE_PARALLELISM = 4

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(DECODE_PARALLELISM)

    /**
     * Cache budget: 1/8 of the runtime's max heap, clamped to [4 MiB, 32 MiB].
     * A single 1024-px ARGB_8888 thumbnail is ~4 MB, so even a 4 MiB cache
     * holds a row of thumbnails at the next-power-of-two downsample level.
     */
    private val cache: LruCache<CacheKey, Bitmap> by lazy {
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val budgetBytes = (maxHeapBytes / 8L)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
            .toInt()
        object : LruCache<CacheKey, Bitmap>(budgetBytes) {
            override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.allocationByteCount
        }
    }

    private const val MIN_CACHE_BYTES = 4L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 32L * 1024 * 1024

    /**
     * Decode + EXIF-rotate the photo at [uri] into a downsampled bitmap
     * suitable for display at approximately [targetSizePx] on its longest
     * side. Always runs the IO + decode work off the main thread, on a
     * shared bounded-parallelism dispatcher so a long list of thumbnails
     * can't fan out 60+ concurrent decodes.
     */
    suspend fun loadThumbnailFromUri(uri: String, targetSizePx: Int): Bitmap? =
        withContext(decodeDispatcher) {
            val path = uri.toUri().path ?: return@withContext null
            loadThumbnailCached(path, targetSizePx)
        }

    /**
     * Decode a bag photo into a downsampled bitmap suitable for display at
     * approximately [targetSizePx] on its longest side, then apply EXIF
     * rotation so the photo is oriented correctly.
     *
     * Synchronous — call from a background dispatcher. Returns `null` if the
     * file does not exist or cannot be decoded. Bypasses the in-memory
     * cache so callers performing post-processing (cropping, OCR) don't
     * pollute the cache with single-use intermediate bitmaps.
     */
    fun loadThumbnail(filePath: String, targetSizePx: Int): Bitmap? =
        decodeAndRotate(filePath, targetSizePx)

    private fun loadThumbnailCached(filePath: String, targetSizePx: Int): Bitmap? {
        val sampleSize = computeSampleSize(filePath, targetSizePx)
        val key = CacheKey(filePath, targetSizePx)
        cache.get(key)?.let { return it }
        val decoded = decodeAndRotate(filePath, targetSizePx, sampleSize) ?: return null
        cache.put(key, decoded)
        return decoded
    }

    private fun decodeAndRotate(
        filePath: String,
        targetSizePx: Int,
        precomputedSampleSize: Int? = null,
    ): Bitmap? {
        // Boundary catch: native JPEG decode can throw OutOfMemoryError or
        // broken-file IO exceptions. ExifInterface adds another set of
        // failure modes. A null return is the documented contract; logging
        // the cause is enough for diagnostics.
        @Suppress("TooGenericExceptionCaught")
        return try {
            val sampleSize = precomputedSampleSize ?: computeSampleSize(filePath, targetSizePx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(filePath, opts) ?: return null
            val oriented = ImagePreprocessor.applyExifRotation(raw, filePath)
            if (oriented !== raw && !raw.isRecycled) raw.recycle()

            val target = PhotoStoragePolicy.scaledDimensions(
                width = oriented.width,
                height = oriented.height,
                maxLongEdgePx = targetSizePx,
            )
            val resized = Bitmap.createScaledBitmap(
                oriented,
                target.width,
                target.height,
                true,
            )
            if (resized !== oriented && !oriented.isRecycled) oriented.recycle()
            resized
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Insufficient memory to load thumbnail for $filePath", error)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load thumbnail for $filePath", e)
            null
        }
    }

    /**
     * Read just the JPEG header to compute the largest power-of-two
     * downsample factor whose result still has its longest side ≥ [targetSizePx].
     * Mirrors the standard pattern from the Android docs.
     */
    private fun computeSampleSize(filePath: String, targetSizePx: Int): Int {
        if (targetSizePx <= 0) return 1
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        val srcLongest = maxOf(bounds.outWidth, bounds.outHeight)
        if (srcLongest <= 0) return 1
        var sample = 1
        while (srcLongest / (sample * 2) >= targetSizePx) {
            sample *= 2
        }
        return sample
    }

    private data class CacheKey(val filePath: String, val targetSizePx: Int)
}
