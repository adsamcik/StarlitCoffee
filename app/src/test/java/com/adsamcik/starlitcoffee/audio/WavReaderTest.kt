package com.adsamcik.starlitcoffee.audio

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WavReaderTest {

    // --- Helpers ---

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun createWavBytes(
        samples: ShortArray,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        audioFormat: Int = 1,
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val dataSize = samples.size * (bitsPerSample / 8) * channels
        val fmtChunkSize = 16
        val fileSize = 4 + (8 + fmtChunkSize) + (8 + dataSize)

        // RIFF header
        bos.write("RIFF".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(fileSize)
        bos.write("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        bos.write("fmt ".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(fmtChunkSize)
        bos.writeLittleEndianShort(audioFormat)
        bos.writeLittleEndianShort(channels)
        bos.writeLittleEndianInt(sampleRate)
        bos.writeLittleEndianInt(sampleRate * channels * (bitsPerSample / 8))
        bos.writeLittleEndianShort(channels * (bitsPerSample / 8))
        bos.writeLittleEndianShort(bitsPerSample)

        // data chunk
        bos.write("data".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(dataSize)
        for (sample in samples) {
            bos.writeLittleEndianShort(sample.toInt())
        }

        return bos.toByteArray()
    }

    private fun createWavWithExtraChunk(samples: ShortArray, sampleRate: Int = 44100): ByteArray {
        val bos = ByteArrayOutputStream()
        val dataSize = samples.size * 2
        val fmtChunkSize = 16
        val extraChunkSize = 10
        val fileSize = 4 + (8 + fmtChunkSize) + (8 + extraChunkSize) + (8 + dataSize)

        // RIFF header
        bos.write("RIFF".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(fileSize)
        bos.write("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        bos.write("fmt ".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(fmtChunkSize)
        bos.writeLittleEndianShort(1)  // PCM
        bos.writeLittleEndianShort(1)  // mono
        bos.writeLittleEndianInt(sampleRate)
        bos.writeLittleEndianInt(sampleRate * 2)
        bos.writeLittleEndianShort(2)
        bos.writeLittleEndianShort(16)

        // Unknown extra chunk (e.g. LIST, fact, etc.)
        bos.write("LIST".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(extraChunkSize)
        repeat(extraChunkSize) { bos.write(0) }

        // data chunk
        bos.write("data".toByteArray(Charsets.US_ASCII))
        bos.writeLittleEndianInt(dataSize)
        for (sample in samples) {
            bos.writeLittleEndianShort(sample.toInt())
        }

        return bos.toByteArray()
    }

    // --- readPcm16Mono tests ---

    @Test
    fun `reads empty WAV file`() {
        val wav = createWavBytes(ShortArray(0))
        val samples = WavReader.readPcm16Mono(ByteArrayInputStream(wav))
        assertEquals(0, samples.size)
    }

    @Test
    fun `reads simple sine wave samples`() {
        val original = shortArrayOf(0, 1000, 2000, 3000, 2000, 1000, 0, -1000, -2000, -3000)
        val wav = createWavBytes(original)
        val result = WavReader.readPcm16Mono(ByteArrayInputStream(wav))

        assertEquals(original.size, result.size)
        for (i in original.indices) {
            assertEquals("Sample $i mismatch", original[i], result[i])
        }
    }

    @Test
    fun `reads negative sample values correctly`() {
        val original = shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE)
        val wav = createWavBytes(original)
        val result = WavReader.readPcm16Mono(ByteArrayInputStream(wav))

        assertEquals(Short.MIN_VALUE, result[0])
        assertEquals((-1).toShort(), result[1])
        assertEquals(0.toShort(), result[2])
        assertEquals(1.toShort(), result[3])
        assertEquals(Short.MAX_VALUE, result[4])
    }

    @Test
    fun `reads WAV with extra chunks between fmt and data`() {
        val original = shortArrayOf(100, 200, 300)
        val wav = createWavWithExtraChunk(original)
        val result = WavReader.readPcm16Mono(ByteArrayInputStream(wav))

        assertEquals(original.size, result.size)
        for (i in original.indices) {
            assertEquals("Sample $i mismatch", original[i], result[i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-RIFF file`() {
        val bytes = "NOT_A_WAV_FILE_AT_ALL".toByteArray()
        WavReader.readPcm16Mono(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects stereo WAV`() {
        val wav = createWavBytes(shortArrayOf(1, 2), channels = 2)
        WavReader.readPcm16Mono(ByteArrayInputStream(wav))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects 8-bit WAV`() {
        val wav = createWavBytes(shortArrayOf(1, 2), bitsPerSample = 8)
        WavReader.readPcm16Mono(ByteArrayInputStream(wav))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-PCM format`() {
        val wav = createWavBytes(shortArrayOf(1, 2), audioFormat = 3) // IEEE float
        WavReader.readPcm16Mono(ByteArrayInputStream(wav))
    }

    // --- readSampleRate tests ---

    @Test
    fun `reads sample rate correctly`() {
        val wav = createWavBytes(shortArrayOf(0), sampleRate = 44100)
        val rate = WavReader.readSampleRate(ByteArrayInputStream(wav))
        assertEquals(44100, rate)
    }

    @Test
    fun `reads 48kHz sample rate`() {
        val wav = createWavBytes(shortArrayOf(0), sampleRate = 48000)
        val rate = WavReader.readSampleRate(ByteArrayInputStream(wav))
        assertEquals(48000, rate)
    }
}
