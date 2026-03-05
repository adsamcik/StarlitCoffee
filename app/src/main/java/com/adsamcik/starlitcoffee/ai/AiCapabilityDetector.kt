package com.adsamcik.starlitcoffee.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

/**
 * Detects on-device AI capability and manages model availability.
 *
 * Call [checkAndPrepare] at app startup to determine if Gemini Nano is
 * available. If the model is downloadable but not yet present, this will
 * initiate the download in the background.
 *
 * The [extractor] property provides a ready-to-use [AiLabelExtractor]
 * or null if no on-device AI is available.
 */
object AiCapabilityDetector {

    private const val TAG = "AiCapability"

    /** Current capability status. */
    @Volatile
    var status: AiCapability = AiCapability.UNKNOWN
        private set

    /** Ready-to-use extractor, or null if AI is unavailable. */
    val extractor: AiLabelExtractor?
        get() = if (status == AiCapability.AVAILABLE) GeminiNanoLabelExtractor() else null

    /**
     * Checks Gemini Nano availability and initiates download if needed.
     * Safe to call from a coroutine scope (e.g., Application.onCreate via
     * viewModelScope or lifecycleScope).
     */
    suspend fun checkAndPrepare() {
        try {
            val model = Generation.getClient()
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    Log.d(TAG, "Gemini Nano available")
                    status = AiCapability.AVAILABLE
                }
                FeatureStatus.DOWNLOADABLE -> {
                    Log.d(TAG, "Gemini Nano downloadable — initiating download")
                    status = AiCapability.DOWNLOADING
                    model.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadCompleted -> {
                                Log.d(TAG, "Gemini Nano download complete")
                                status = AiCapability.AVAILABLE
                            }
                            is DownloadStatus.DownloadFailed -> {
                                Log.e(TAG, "Download failed: ${downloadStatus.e.message}")
                                status = AiCapability.UNAVAILABLE
                            }
                            else -> { /* progress — keep DOWNLOADING status */ }
                        }
                    }
                }
                FeatureStatus.DOWNLOADING -> {
                    Log.d(TAG, "Gemini Nano already downloading")
                    status = AiCapability.DOWNLOADING
                }
                else -> {
                    Log.d(TAG, "Gemini Nano unavailable on this device")
                    status = AiCapability.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI capability detection failed: ${e.message}")
            status = AiCapability.UNAVAILABLE
        }
    }

    enum class AiCapability {
        UNKNOWN,
        AVAILABLE,
        DOWNLOADING,
        UNAVAILABLE,
    }
}
