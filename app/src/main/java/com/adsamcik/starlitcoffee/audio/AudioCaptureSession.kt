package com.adsamcik.starlitcoffee.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.adsamcik.starlitcoffee.data.model.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Wraps [AudioRecord] and emits raw PCM 16-bit mono buffers via a [Flow].
 * Caller is responsible for ensuring RECORD_AUDIO permission before calling [audioBufferFlow].
 */
class AudioCaptureSession(
    private val config: AudioConfig = AudioConfig(),
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    val bufferSizeBytes: Int = AudioRecord.getMinBufferSize(
        config.sampleRate,
        channelConfig,
        audioFormat,
    ).coerceAtLeast(config.sampleRate * 2) // at least 1 second buffer

    val bufferSizeSamples: Int = bufferSizeBytes / 2 // 16-bit = 2 bytes per sample

    /**
     * Returns a cold [Flow] that emits [ShortArray] PCM buffers continuously
     * while collected. The flow handles [AudioRecord] lifecycle internally —
     * recording starts on collection and stops on cancellation.
     */
    @SuppressLint("MissingPermission")
    fun audioBufferFlow(): Flow<ShortArray> = callbackFlow {
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeBytes,
            )
        } catch (_: SecurityException) {
            // Permission revoked at runtime
            close(SecurityException("RECORD_AUDIO permission not granted"))
            return@callbackFlow
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        recorder.startRecording()

        withContext(Dispatchers.IO) {
            val readBuffer = ShortArray(READ_CHUNK_SAMPLES)
            while (isActive) {
                val samplesRead = recorder.read(readBuffer, 0, readBuffer.size)
                if (samplesRead > 0) {
                    trySend(readBuffer.copyOf(samplesRead))
                } else if (samplesRead == AudioRecord.ERROR_BAD_VALUE ||
                    samplesRead == AudioRecord.ERROR_DEAD_OBJECT
                ) {
                    break
                }
            }
        }

        awaitClose {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // Already stopped
            }
            recorder.release()
        }
    }

    companion object {
        // ~23ms chunks at 44100Hz — good balance of latency vs overhead
        private const val READ_CHUNK_SAMPLES = 1024
    }
}
