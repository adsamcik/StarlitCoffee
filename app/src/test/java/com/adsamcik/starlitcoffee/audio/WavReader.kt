package com.adsamcik.starlitcoffee.audio

import java.io.InputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads standard 16-bit mono PCM WAV files for use in regression tests.
 * Pure Kotlin — no Android dependencies.
 */
object WavReader {

    private const val RIFF_MAGIC = "RIFF"
    private const val WAVE_MAGIC = "WAVE"
    private const val FMT_CHUNK_ID = "fmt "
    private const val DATA_CHUNK_ID = "data"
    private const val PCM_FORMAT: Short = 1

    /**
     * Reads a WAV file from an InputStream and returns the PCM samples.
     * Supports: 16-bit, mono, PCM format (format code 1).
     *
     * @throws IllegalArgumentException if format is unsupported
     * @return PCM samples as ShortArray
     */
    fun readPcm16Mono(input: InputStream): ShortArray {
        val bytes = input.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // --- RIFF header ---
        val riff = readAscii(buf, 4)
        require(riff == RIFF_MAGIC) { "Not a RIFF file (got '$riff')" }
        buf.int // file size minus 8
        val wave = readAscii(buf, 4)
        require(wave == WAVE_MAGIC) { "Not a WAVE file (got '$wave')" }

        // --- Walk chunks until we find fmt and data ---
        var fmtParsed = false
        var audioFormat: Short = -1
        var numChannels: Short = -1
        var sampleRate = -1
        var bitsPerSample: Short = -1
        var dataBytes: ByteArray? = null

        while (buf.hasRemaining()) {
            if (buf.remaining() < 8) break
            val chunkId = readAscii(buf, 4)
            val chunkSize = buf.int

            when (chunkId) {
                FMT_CHUNK_ID -> {
                    val fmtStart = buf.position()
                    audioFormat = buf.short
                    numChannels = buf.short
                    sampleRate = buf.int
                    buf.int   // byte rate
                    buf.short // block align
                    bitsPerSample = buf.short
                    // Skip any extra fmt bytes (e.g. extension data)
                    buf.position(fmtStart + chunkSize)
                    fmtParsed = true

                    // Validate immediately so we fail before reading data
                    require(audioFormat == PCM_FORMAT) {
                        "Unsupported audio format: $audioFormat (only PCM/1 supported)"
                    }
                    require(numChannels == 1.toShort()) {
                        "Unsupported channel count: $numChannels (only mono supported)"
                    }
                    require(bitsPerSample == 16.toShort()) {
                        "Unsupported bit depth: $bitsPerSample (only 16-bit supported)"
                    }
                }
                DATA_CHUNK_ID -> {
                    dataBytes = ByteArray(chunkSize)
                    buf.get(dataBytes)
                }
                else -> {
                    // Skip unknown chunks
                    buf.position(buf.position() + chunkSize)
                }
            }
        }

        require(fmtParsed) { "No fmt chunk found in WAV file" }
        requireNotNull(dataBytes) { "No data chunk found in WAV file" }

        // --- Convert bytes → ShortArray (little-endian) ---
        val sampleCount = dataBytes.size / 2
        val samples = ShortArray(sampleCount)
        val dataBuf = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            samples[i] = dataBuf.short
        }
        return samples
    }

    /**
     * Reads a WAV file from a File path.
     */
    fun readPcm16Mono(file: File): ShortArray {
        return file.inputStream().buffered().use { readPcm16Mono(it) }
    }

    /**
     * Returns the sample rate from a WAV file header.
     */
    fun readSampleRate(input: InputStream): Int {
        val bytes = input.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = readAscii(buf, 4)
        require(riff == RIFF_MAGIC) { "Not a RIFF file (got '$riff')" }
        buf.int // file size
        val wave = readAscii(buf, 4)
        require(wave == WAVE_MAGIC) { "Not a WAVE file (got '$wave')" }

        while (buf.hasRemaining()) {
            val chunkId = readAscii(buf, 4)
            val chunkSize = buf.int
            if (chunkId == FMT_CHUNK_ID) {
                buf.short // audio format
                buf.short // channels
                return buf.int // sample rate
            }
            buf.position(buf.position() + chunkSize)
        }
        throw IllegalArgumentException("No fmt chunk found in WAV file")
    }

    private fun readAscii(buf: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }
}
