package com.adsamcik.starlitcoffee.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoStorageInstrumentedTest {

    @Test
    fun permanentPhotoAppliesExifBoundsLongestEdgeAndEncodesWebp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "photo_storage_source.jpg")
        val sourceBitmap = Bitmap.createBitmap(1200, 2400, Bitmap.Config.ARGB_8888)
        source.outputStream().use { output ->
            assertTrue(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        sourceBitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }

        var output: File? = null
        var stagedUris = emptyList<String>()
        try {
            stagedUris = requireNotNull(
                ScanPhotoStorage.copyPhotosToCache(
                    context = context,
                    sourceUris = listOf(source.toUri().toString()),
                ),
            )
            val storedUris = ScanPhotoStorage.promoteStagedPhotosToPermanentStorage(
                context = context,
                stagedUris = stagedUris.single(),
                storageKey = ScanPhotoStorage.storageKeyForSession("photo-storage-test"),
            )
            val outputUri = BagPhotoReviewUris.parse(storedUris).single()
            output = File(requireNotNull(outputUri.toUri().path))

            assertEquals("webp", output.extension)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, bounds)
            assertTrue(bounds.outWidth > bounds.outHeight)
            assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= PhotoStoragePolicy.AI_MAX_LONG_EDGE_PX)
            assertEquals(2f, bounds.outWidth.toFloat() / bounds.outHeight, 0.05f)
            assertEquals("WEBP", output.readBytes().copyOfRange(8, 12).decodeToString())

            ScanPhotoStorage.markSavePending(context, "photo-storage-test")
            assertTrue("photo-storage-test" in ScanPhotoStorage.pendingSaveSessions(context))
            assertTrue(
                ScanPhotoStorage.deletePermanentPhotosForSession(context, "photo-storage-test"),
            )
            assertFalse(output.exists())
            ScanPhotoStorage.clearPendingSave(context, "photo-storage-test")
            assertFalse("photo-storage-test" in ScanPhotoStorage.pendingSaveSessions(context))
        } finally {
            source.delete()
            ScanPhotoStorage.deleteStagedCaptures(
                context,
                stagedUris.joinToString(","),
            )
            output?.delete()
        }
    }

    @Test
    fun thumbnailLoaderReturnsTrueMaximumSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "thumbnail_loader_source.jpg")
        val sourceBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        source.outputStream().use { output ->
            assertTrue(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        sourceBitmap.recycle()

        try {
            val thumbnail = requireNotNull(
                ThumbnailLoader.loadThumbnail(
                    filePath = source.absolutePath,
                    targetSizePx = PhotoStoragePolicy.THUMBNAIL_MAX_LONG_EDGE_PX,
                ),
            )
            try {
                assertTrue(
                    maxOf(thumbnail.width, thumbnail.height) <=
                        PhotoStoragePolicy.THUMBNAIL_MAX_LONG_EDGE_PX,
                )
                assertEquals(2f, thumbnail.width.toFloat() / thumbnail.height, 0.05f)
            } finally {
                thumbnail.recycle()
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun saveJournalSerializesConcurrentMarkAndClearOperations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessions = (0 until 40).map { "journal-session-$it" }
        sessions.forEach { ScanPhotoStorage.clearPendingSave(context, it) }
        val executor = Executors.newFixedThreadPool(8)
        try {
            sessions.forEach { session ->
                executor.submit { ScanPhotoStorage.markSavePending(context, session) }
            }
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

            assertTrue(ScanPhotoStorage.pendingSaveSessions(context).containsAll(sessions))
            sessions.filterIndexed { index, _ -> index % 2 == 0 }
                .forEach { ScanPhotoStorage.clearPendingSave(context, it) }
            assertEquals(
                sessions.filterIndexed { index, _ -> index % 2 != 0 }.toSet(),
                ScanPhotoStorage.pendingSaveSessions(context).filter { it.startsWith("journal-session-") }.toSet(),
            )
        } finally {
            sessions.forEach { ScanPhotoStorage.clearPendingSave(context, it) }
            executor.shutdownNow()
        }
    }

    @Test
    fun temporaryCaptureUsesDurableNoBackupStaging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "photo".encodeToByteArray()
        val uri = requireNotNull(ScanPhotoStorage.writeCaptureToCache(context, bytes))
        val file = File(requireNotNull(uri.toUri().path))
        try {
            assertTrue(file.path.startsWith(context.noBackupFilesDir.path))
            assertEquals("photo", file.readText())
            assertTrue(file.parentFile?.listFiles()?.none { it.name.endsWith(".tmp") } == true)
        } finally {
            file.delete()
        }
    }

    @Test
    fun captureStagingRejectsSymlinkDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stagingDir = File(context.noBackupFilesDir, "bag_scan_captures").apply { mkdirs() }
        val outside = File(context.cacheDir, "capture_symlink_target.jpg").apply { writeText("keep") }
        val link = File(stagingDir, "capture_symlink.jpg")
        try {
            val linkCreated = runCatching {
                Os.symlink(outside.absolutePath, link.absolutePath)
            }.isSuccess
            assumeTrue("Symbolic links are unavailable on this device", linkCreated)

            val result = runCatching {
                ScanPhotoStorage.writeCaptureBytesToStorage(
                    storageDir = stagingDir,
                    fileName = link.name,
                    bytes = "replace".encodeToByteArray(),
                )
            }

            assertTrue(result.isFailure)
            assertEquals("keep", outside.readText())
        } finally {
            link.delete()
            outside.delete()
        }
    }

    @Test
    fun permanentPromotionRejectsReadableFileOutsideStaging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "outside_staging_source.jpg")
        val sourceBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        source.outputStream().use { output ->
            assertTrue(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        sourceBitmap.recycle()
        val sessionId = "outside-staging-promotion-test"
        try {
            val result = ScanPhotoStorage.promoteStagedPhotosToPermanentStorage(
                context = context,
                stagedUris = source.toUri().toString(),
                storageKey = ScanPhotoStorage.storageKeyForSession(sessionId),
            )

            assertEquals(null, result)
            assertTrue(source.exists())
        } finally {
            source.delete()
            ScanPhotoStorage.deletePermanentPhotosForSession(context, sessionId)
        }
    }
}
