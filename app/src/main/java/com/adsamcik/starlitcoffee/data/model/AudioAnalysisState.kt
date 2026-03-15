package com.adsamcik.starlitcoffee.data.model

/**
 * Real-time audio analysis state exposed to UI.
 * Combines time-domain features (RMS, ZCR) with spectral features and detector state.
 */
data class AudioAnalysisState(
    val rmsDb: Float = SILENCE_DB,
    val peakDb: Float = SILENCE_DB,
    val dominantFrequencyHz: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    val isSilent: Boolean = true,
    val silenceDurationMs: Long = 0L,
    val isMonitoring: Boolean = false,
    val isRecording: Boolean = false,
    val currentPhaseLabel: String = "",
    val recordingFilePath: String? = null,
    val levelHistory: List<Float> = emptyList(),

    // Spectral features (Phase 2)
    val spectralFeatures: SpectralFeatures = SpectralFeatures.EMPTY,
    val detectorState: DetectorState = DetectorState.IDLE,
    val dripRate: Float = 0f,
    val noiseFloorDb: Map<FrequencyBand, Float> = emptyMap(),
    val lastBrewEvent: BrewAudioEvent? = null,

    // Spectral subtraction + trajectory
    val baselineCalibrated: Boolean = false,
    val trajectoryPhase: String = "",
    val brewConfidence: Float = 0f,
){
    companion object {
        const val SILENCE_DB = -96f
        const val LEVEL_HISTORY_SIZE = 50
    }
}

/**
 * Raw per-buffer analysis results from AudioAnalyzer. No Android dependencies.
 */
data class AudioFeatures(
    val rmsDb: Float,
    val peakDb: Float,
    val zeroCrossingRate: Float,
    val dominantFrequencyHz: Float,
)

/**
 * Events detected from audio analysis.
 */
sealed class AudioDetectionEvent {
    data class SilenceDetected(val durationMs: Long) : AudioDetectionEvent()
    data object SoundResumed : AudioDetectionEvent()
    data class LevelCrossing(val direction: Direction, val thresholdDb: Float) : AudioDetectionEvent() {
        enum class Direction { RISING, FALLING }
    }
}

/**
 * Audio capture & analysis configuration.
 */
data class AudioConfig(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val silenceThresholdDb: Float = DEFAULT_SILENCE_THRESHOLD_DB,
    val silenceDurationThresholdMs: Long = DEFAULT_SILENCE_DURATION_MS,
    val analysisIntervalMs: Long = DEFAULT_ANALYSIS_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_SILENCE_THRESHOLD_DB = -40f
        const val DEFAULT_SILENCE_DURATION_MS = 2000L
        const val DEFAULT_ANALYSIS_INTERVAL_MS = 100L
    }
}
