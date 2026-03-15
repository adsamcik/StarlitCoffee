package com.adsamcik.starlitcoffee.data.model

/**
 * Frequency band definitions for spectral analysis.
 * Bin indices assume 1024-point FFT at 44100 Hz (bin width ≈ 43.07 Hz).
 */
enum class FrequencyBand(
    val displayName: String,
    val lowHz: Int,
    val highHz: Int,
    val lowBin: Int,
    val highBin: Int,
) {
    /** Turbulent pour: broadband water-on-surface noise, air-column resonance.
     *  Partially supported by research for air-column resonance (~200-3000 Hz). */
    POUR("Pour", 200, 3000, 5, 69),

    /** Low-frequency drip: vessel resonance modes, structure-borne impact.
     *  Captures indirect drip energy re-radiated by container/surface. */
    DRIP_LOW("Drip Low", 300, 2000, 7, 46),

    /** High-frequency drip: bubble resonance transients.
     *  Research shows dominant drip "plink" at ~8.66 kHz from entrained bubble oscillation. */
    DRIP_HIGH("Drip High", 4000, 11000, 93, 255),

    /** High-mid: fine spray, splash, and broadband transient detail. */
    HIGH_MID("High-Mid", 3000, 6000, 70, 139);

    val binCount: Int get() = highBin - lowBin + 1

    companion object {
        /** FFT size used for bin calculations */
        const val FFT_SIZE = 1024

        /** Sample rate used for bin calculations */
        const val SAMPLE_RATE = 44100

        /** Bin width in Hz: sampleRate / fftSize */
        const val BIN_WIDTH_HZ = SAMPLE_RATE.toFloat() / FFT_SIZE
    }
}

/**
 * Per-frame spectral features extracted from FFT output.
 */
data class SpectralFeatures(
    /** Band energy in dB (normalized per bin) for each band */
    val bandEnergyDb: Map<FrequencyBand, Float>,

    /** Half-wave rectified spectral flux (log-magnitude) per band */
    val spectralFlux: Map<FrequencyBand, Float>,

    /** Spectral tilt: ratio of low-pour energy to high-pour energy.
     *  Low values (1-10) indicate broadband water noise. */
    val spectralTilt: Float,

    /** Spectral flatness (Wiener entropy) over 200-6kHz.
     *  Ratio of geometric mean to arithmetic mean of power spectrum.
     *  Range 0..1: near 1 = noise-like (water), near 0 = tonal (speech/music). */
    val spectralFlatness: Float = 0f,

    /** Cepstral peak prominence in dB.
     *  High values (>3) indicate strong pitch (speech/music/hum) → use as veto.
     *  Low values (<2) indicate aperiodic noise (water). */
    val cepstralPeakProminence: Float = 0f,

    /** Number of octave sub-bands (out of 6) with energy above noise floor.
     *  Water lights ≥5/6 bands (broadband); speech/fan typically 2-3. */
    val bandCoincidenceCount: Int = 0,

    /** Whether the ambient baseline has completed calibration */
    val isBaselineCalibrated: Boolean = false,

    /** Full power spectrum (N/2 + 1 bins) — available for debug UI */
    val powerSpectrum: FloatArray? = null,
){
    companion object {
        val EMPTY = SpectralFeatures(
            bandEnergyDb = FrequencyBand.entries.associateWith { AudioAnalysisState.SILENCE_DB },
            spectralFlux = FrequencyBand.entries.associateWith { 0f },
            spectralTilt = 0f,
        )
    }
}

/**
 * Brew-specific audio events emitted by [BrewEventDetector].
 * These represent detected acoustic events during a brew session.
 */
sealed class BrewAudioEvent {
    /** Water pour has started — broadband energy onset detected */
    data class PourStarted(val confidenceDb: Float) : BrewAudioEvent()

    /** Water pour has stopped — sustained energy drop */
    data class PourStopped(val durationMs: Long) : BrewAudioEvent()

    /** Single drip impact detected during drawdown */
    data class DripDetected(val energyDb: Float) : BrewAudioEvent()

    /** Dripping rate updated (rolling 5-second window) */
    data class DripRateUpdated(val dripsPerSecond: Float) : BrewAudioEvent()

    /** Drawdown complete — no drips detected for threshold period */
    data class DrawdownComplete(val totalDrainTimeMs: Long) : BrewAudioEvent()
}

/**
 * States of the brew event detector state machine.
 */
enum class DetectorState(val displayName: String) {
    /** Listening, no brew activity detected */
    IDLE("Idle"),

    /** Active water pour detected */
    POURING("Pouring"),

    /** Post-pour dripping phase (drawdown) */
    DRIPPING("Dripping"),

    /** Drawdown complete — silence sustained */
    COMPLETE("Complete"),
}

/**
 * Configuration for the brew event detector.
 * All timing values assume 86 fps (1024-pt FFT, 512-sample hop at 44100 Hz).
 */
data class DetectorConfig(
    /** Frames of onset confirmation before triggering pour start (3 frames ≈ 35ms) */
    val pourOnsetConfirmFrames: Int = DEFAULT_POUR_ONSET_CONFIRM,

    /** Frames below threshold before pour is considered stopped (15 frames ≈ 180ms) */
    val pourOffsetConfirmFrames: Int = DEFAULT_POUR_OFFSET_CONFIRM,

    /** Energy-above-noise-floor margin (dB) for pour offset detection.
     *  When pour band energy drops below noiseFloor + this margin, offset counting starts. */
    val pourOffEnergyMarginDb: Float = DEFAULT_POUR_OFF_MARGIN_DB,

    /** Adaptive threshold multiplier for pour detection */
    val pourThresholdLambda: Float = DEFAULT_POUR_LAMBDA,

    /** Adaptive threshold multiplier for drip detection */
    val dripThresholdLambda: Float = DEFAULT_DRIP_LAMBDA,

    /** Minimum inter-onset interval for drips in frames.
     *  Research: 80ms minimum is unsupported; real drip spacing is environment-dependent.
     *  Conservative 46ms prevents double-counting while allowing fast drip sequences. */
    val dripMinIoiFrames: Int = DEFAULT_DRIP_MIN_IOI,

    /** Window size for drip rate estimation in frames (430 frames ≈ 5s) */
    val dripRateWindowFrames: Int = DEFAULT_DRIP_RATE_WINDOW,

    /** Frames with no drips before drawdown considered complete (430 frames ≈ 5s) */
    val drawdownCompleteFrames: Int = DEFAULT_DRAWDOWN_COMPLETE,

    /** Trailing window size for adaptive threshold statistics (43 frames ≈ 500ms) */
    val trailingWindowSize: Int = DEFAULT_TRAILING_WINDOW,

    /** Maximum noise floor change rate in dB per frame */
    val maxNoiseFloorChangePerFrame: Float = DEFAULT_MAX_NOISE_CHANGE,

    /** Minimum phase duration in frames before transitions allowed (430 frames ≈ 5s) */
    val minPhaseDurationFrames: Int = DEFAULT_MIN_PHASE_DURATION,
) {
    companion object {
        const val FRAMES_PER_SECOND = 86f
        const val MS_PER_FRAME = 1000f / FRAMES_PER_SECOND

        const val DEFAULT_POUR_ONSET_CONFIRM = 3
        const val DEFAULT_POUR_OFFSET_CONFIRM = 15
        const val DEFAULT_POUR_OFF_MARGIN_DB = 15f
        const val DEFAULT_POUR_LAMBDA = 2.0f
        const val DEFAULT_DRIP_LAMBDA = 2.5f
        // Research: 80ms minimum is unsupported; real drip spacing is environment-dependent.
        // Conservative 46ms prevents double-counting while allowing fast drip sequences.
        const val DEFAULT_DRIP_MIN_IOI = 4
        const val DEFAULT_DRIP_RATE_WINDOW = 430
        const val DEFAULT_DRAWDOWN_COMPLETE = 430
        const val DEFAULT_TRAILING_WINDOW = 43
        // 1 dB/s at 86fps ≈ 0.0116 dB/frame
        const val DEFAULT_MAX_NOISE_CHANGE = 1.0f / FRAMES_PER_SECOND
        const val DEFAULT_MIN_PHASE_DURATION = 430
    }
}
