package com.adsamcik.starlitcoffee.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

class BrewDataBundlerTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("brew_bundler_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- Bundle creation ---

    @Test
    fun `bundle creates zip with session files`() {
        val timestamp = "1720000000000"
        createFakeSessionFiles(timestamp)

        val result = BrewDataBundler.bundle(tempDir, timestamp)

        assertTrue(result.success)
        assertTrue(result.zipFile.exists())
        assertEquals(4, result.fileCount)
        assertTrue(result.totalSizeBytes > 0)

        ZipFile(result.zipFile).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(entryNames.contains("brew_${timestamp}_meta.json"))
            assertTrue(entryNames.contains("brew_${timestamp}_phase_0_bloom.wav"))
            assertTrue(entryNames.contains("brew_${timestamp}_phase_0_bloom.jsonl"))
            assertTrue(entryNames.contains("brew_${timestamp}_user_labels.txt"))
            assertTrue(entryNames.contains("manifest.txt"))
        }
    }

    @Test
    fun `bundle includes manifest with correct content`() {
        val timestamp = "1720000000000"
        createFakeSessionFiles(timestamp)

        val result = BrewDataBundler.bundle(tempDir, timestamp)

        ZipFile(result.zipFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.txt")
            val manifest = zip.getInputStream(manifestEntry).bufferedReader().readText()

            assertTrue(manifest.contains("Starlit Coffee Brew Data Bundle"))
            assertTrue(manifest.contains("Brew timestamp: $timestamp"))
            assertTrue(manifest.contains("Files: 4"))
            assertTrue(manifest.contains("WAV recordings: 1"))
            assertTrue(manifest.contains("JSONL flight data: 1"))
            assertTrue(manifest.contains("JSON metadata: 1"))
            assertTrue(manifest.contains("Label files: 1"))
        }
    }

    // --- Error cases ---

    @Test
    fun `bundle returns error for empty directory`() {
        val result = BrewDataBundler.bundle(tempDir, "9999999999999")

        assertFalse(result.success)
        assertEquals(0, result.fileCount)
        assertTrue(result.errors.first().contains("No files found"))
    }

    @Test
    fun `bundle returns error for missing directory`() {
        val missing = File(tempDir, "nonexistent")

        val result = BrewDataBundler.bundle(missing, "1720000000000")

        assertFalse(result.success)
        assertEquals(0, result.fileCount)
        assertTrue(result.errors.first().contains("Session directory does not exist"))
    }

    // --- findLatestBrewTimestamp ---

    @Test
    fun `findLatestBrewTimestamp returns most recent`() {
        val older = File(tempDir, "brew_1000000000000_meta.json").apply {
            writeText("{}")
            setLastModified(1000L)
        }
        val newer = File(tempDir, "brew_2000000000000_meta.json").apply {
            writeText("{}")
            setLastModified(2000L)
        }

        val latest = BrewDataBundler.findLatestBrewTimestamp(tempDir)

        assertEquals("2000000000000", latest)
    }

    @Test
    fun `findLatestBrewTimestamp returns null for empty directory`() {
        val result = BrewDataBundler.findLatestBrewTimestamp(tempDir)
        assertNull(result)
    }

    // --- Filtering ---

    @Test
    fun `bundle skips non-matching files`() {
        val timestamp = "1720000000000"
        createFakeSessionFiles(timestamp)
        // Add unrelated files that should NOT be included
        File(tempDir, "other_recording.wav").writeBytes(ByteArray(16))
        File(tempDir, "notes.txt").writeText("some notes")
        File(tempDir, "brew_9999999999999_meta.json").writeText("{}")

        val result = BrewDataBundler.bundle(tempDir, timestamp)

        assertTrue(result.success)
        assertEquals(4, result.fileCount)

        ZipFile(result.zipFile).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            assertFalse(entryNames.contains("other_recording.wav"))
            assertFalse(entryNames.contains("notes.txt"))
            assertFalse(entryNames.contains("brew_9999999999999_meta.json"))
        }
    }

    // --- Helpers ---

    private fun createFakeSessionFiles(timestamp: String) {
        File(tempDir, "brew_${timestamp}_meta.json").writeText("""{"method":"PULSAR"}""")
        File(tempDir, "brew_${timestamp}_phase_0_bloom.wav").writeBytes(ByteArray(64))
        File(tempDir, "brew_${timestamp}_phase_0_bloom.jsonl").writeText("""{"rms":0.1}""")
        File(tempDir, "brew_${timestamp}_user_labels.txt").writeText("0.0\t5.0\tbloom")
    }
}
