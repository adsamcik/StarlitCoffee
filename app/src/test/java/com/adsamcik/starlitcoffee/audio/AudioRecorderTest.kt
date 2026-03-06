package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.AudioConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorderTest {

    private lateinit var tempDir: File
    private lateinit var recorder: AudioRecorder

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "audio_recorder_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        recorder = AudioRecorder(AudioConfig(sampleRate = 44100))
    }

    @After
    fun tearDown() {
        if (recorder.isOpen) recorder.close()
        tempDir.deleteRecursively()
    }

    // --- WAV Header ---

    @Test
    fun `WAV header is 44 bytes`() {
        val header = recorder.buildWavHeader(dataSize = 0)
        assertEquals(AudioRecorder.HEADER_SIZE, header.size)
    }

    @Test
    fun `WAV header starts with RIFF`() {
        val header = recorder.buildWavHeader(dataSize = 1000)
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        assertEquals("RIFF", riff)
    }

    @Test
    fun `WAV header contains WAVE format`() {
        val header = recorder.buildWavHeader(dataSize = 1000)
        val wave = String(header, 8, 4, Charsets.US_ASCII)
        assertEquals("WAVE", wave)
    }

    @Test
    fun `WAV header has correct data size`() {
        val dataSize = 88200 // 1 second at 44100 Hz, 16-bit mono
        val header = recorder.buildWavHeader(dataSize = dataSize)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // Byte 4-7: file size - 8 = 36 + dataSize
        buf.position(4)
        val fileSize = buf.int
        assertEquals(36 + dataSize, fileSize)

        // Byte 40-43: data subchunk size
        buf.position(40)
        val dataSizeRead = buf.int
        assertEquals(dataSize, dataSizeRead)
    }

    @Test
    fun `WAV header has correct sample rate`() {
        val header = recorder.buildWavHeader(dataSize = 0)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(24) // sample rate at byte 24
        assertEquals(44100, buf.int)
    }

    @Test
    fun `WAV header has PCM format`() {
        val header = recorder.buildWavHeader(dataSize = 0)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(20) // audio format at byte 20
        assertEquals(1.toShort(), buf.short) // 1 = PCM
    }

    @Test
    fun `WAV header has mono channel`() {
        val header = recorder.buildWavHeader(dataSize = 0)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(22) // num channels at byte 22
        assertEquals(1.toShort(), buf.short)
    }

    // --- File Operations ---

    @Test
    fun `open creates file with header`() {
        val file = File(tempDir, "test.wav")
        recorder.open(file)
        assertTrue(recorder.isOpen)
        assertEquals(file, recorder.outputFile)

        recorder.close()
        assertFalse(recorder.isOpen)
        assertTrue(file.exists())
        assertEquals(AudioRecorder.HEADER_SIZE.toLong(), file.length())
    }

    @Test
    fun `write produces correct file size`() {
        val file = File(tempDir, "test_write.wav")
        recorder.open(file)

        val samples = ShortArray(1000) { 500 }
        recorder.write(samples)

        recorder.close()

        val expectedSize = AudioRecorder.HEADER_SIZE + (1000 * 2L) // header + 1000 samples * 2 bytes
        assertEquals(expectedSize, file.length())
    }

    @Test
    fun `multiple writes accumulate correctly`() {
        val file = File(tempDir, "test_multi.wav")
        recorder.open(file)

        repeat(5) {
            recorder.write(ShortArray(100) { 1000 })
        }

        recorder.close()

        val expectedSize = AudioRecorder.HEADER_SIZE + (500 * 2L) // 5 * 100 samples
        assertEquals(expectedSize, file.length())
    }

    @Test
    fun `closed file has valid WAV header with correct data size`() {
        val file = File(tempDir, "test_valid.wav")
        recorder.open(file)

        val totalSamples = 2000
        recorder.write(ShortArray(totalSamples) { 500 })
        recorder.close()

        // Read back and verify header
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Check data size in header matches actual data
        buf.position(40)
        val headerDataSize = buf.int
        assertEquals(totalSamples * 2, headerDataSize)
    }

    @Test
    fun `open while already open closes previous file gracefully`() {
        val file1 = File(tempDir, "test1.wav")
        val file2 = File(tempDir, "test2.wav")
        recorder.open(file1)
        recorder.write(ShortArray(100) { 500 })
        recorder.open(file2) // should close file1, open file2
        assertTrue("First file should exist and be finalized", file1.exists())
        assertTrue("Second file should be the active output", recorder.outputFile == file2)
        recorder.close()
    }

    @Test
    fun `write on closed recorder is no-op`() {
        // Should not throw
        recorder.write(ShortArray(100) { 500 })
    }

    @Test
    fun `creates parent directories if needed`() {
        val nestedFile = File(tempDir, "sub/dir/test.wav")
        recorder.open(nestedFile)
        recorder.close()
        assertTrue(nestedFile.exists())
    }
}
