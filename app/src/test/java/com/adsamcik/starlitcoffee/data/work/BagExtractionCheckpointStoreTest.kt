package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagPhotoAnalysis
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagExtractionCheckpointStoreTest {
    private val noOpFileSync = FileSync { }
    private val noOpDirectorySync = DirectorySync { }

    @Test
    fun `checkpoint atomically replaces and deletes latest partial result`() {
        val directory = Files.createTempDirectory("bag-checkpoint").toFile()
        val workId = UUID.randomUUID().toString()
        try {
            BagExtractionCheckpointStore.write(
                directory = directory,
                workId = workId,
                resultJson = "first",
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            BagExtractionCheckpointStore.write(
                directory = directory,
                workId = workId,
                resultJson = "latest",
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            assertEquals("latest", BagExtractionCheckpointStore.read(directory, workId))
            assertTrue(BagExtractionCheckpointStore.delete(directory, workId, noOpDirectorySync))
            assertNull(BagExtractionCheckpointStore.read(directory, workId))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `empty placeholder is not a usable skip checkpoint`() {
        assertFalse(BagPhotoProcessingResult(capturedPhotoUris = "content://photo").hasDeterministicScanData())
        assertTrue(
            BagPhotoProcessingResult(
                capturedPhotoUris = "content://photo",
                photoAnalyses = listOf(
                    BagPhotoAnalysis(
                        uri = "content://photo",
                        side = BagCaptureSide.FRONT,
                        quality = BagCaptureQuality(
                            blurScore = 20f,
                            glarePercent = 0f,
                            overexposedPercent = 0f,
                            underexposedPercent = 0f,
                            textBlockCount = 1,
                            textDetected = true,
                        ),
                    ),
                ),
            ).hasDeterministicScanData(),
        )
    }
}
