package com.adsamcik.starlitcoffee.audio

import android.util.Log
import com.adsamcik.starlitcoffee.data.model.AudioConfig
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes raw PCM 16-bit mono audio buffers to WAV files.
 * One file per recording segment (typically one per brew phase).
 *
 * Usage:
 * 1. [open] with an output file
 * 2. [write] PCM ShortArray buffers
 * 3. [close] to finalize the WAV header with actual data size
 */
class AudioRecorder(
    private val config: AudioConfig = AudioConfig(),
) {
    private var raf: RandomAccessFile? = null
    private var totalSamplesWritten: Long = 0
    private var currentFile: File? = null

    val isOpen: Boolean get() = raf != null
    val outputFile: File? get() = currentFile

    /**
     * Opens a new WAV file for writing. Writes a placeholder header
     * that will be finalized on [close].
     * If already recording, closes the previous file first.
     */
    fun open(file: File) {
        if (raf != null) close()
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw").apply {
            write(buildWavHeader(dataSize = 0))
        }
        currentFile = file
        totalSamplesWritten = 0
    }

    /**
     * Writes PCM samples to the open WAV file.
     */
    fun write(samples: ShortArray, count: Int = samples.size) {
        val out = raf ?: return
        val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            byteBuffer.putShort(samples[i])
        }
        out.write(byteBuffer.array())
        totalSamplesWritten += count
    }

    /**
     * Finalizes the WAV header with actual data sizes and closes the file.
     * Safe to call multiple times.
     */
    fun close() {
        val out = raf ?: return
        try {
            val dataSize = totalSamplesWritten * 2 // 16-bit = 2 bytes per sample
            out.seek(0)
            out.write(buildWavHeader(dataSize = dataSize.toInt()))
            out.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to finalize WAV header", e)
            try { out.close() } catch (e2: Exception) {
                Log.w(TAG, "Failed to close audio output stream", e2)
            }
        }
        raf = null
    }

    /**
     * Builds a 44-byte WAV header for PCM 16-bit mono audio.
     */
    fun buildWavHeader(dataSize: Int): ByteArray {
        val totalSize = 36 + dataSize
        val byteRate = config.sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF header
            put('R'.code.toByte()); put('I'.code.toByte())
            put('F'.code.toByte()); put('F'.code.toByte())
            putInt(totalSize)
            put('W'.code.toByte()); put('A'.code.toByte())
            put('V'.code.toByte()); put('E'.code.toByte())

            // fmt subchunk
            put('f'.code.toByte()); put('m'.code.toByte())
            put('t'.code.toByte()); put(' '.code.toByte())
            putInt(16) // subchunk size (PCM)
            putShort(1) // audio format: PCM
            putShort(CHANNELS.toShort())
            putInt(config.sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())

            // data subchunk
            put('d'.code.toByte()); put('a'.code.toByte())
            put('t'.code.toByte()); put('a'.code.toByte())
            putInt(dataSize)
        }.array()
    }

    companion object {
        private const val TAG = "AudioRecorder"
        const val HEADER_SIZE = 44
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
    }
}
