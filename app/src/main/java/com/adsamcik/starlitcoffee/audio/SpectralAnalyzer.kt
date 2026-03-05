package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Extracts spectral features from windowed audio frames using FFT.
 * Pure computation — no Android dependencies, fully testable.
 *
 * Features per frame:
 * - Band energy (dB, normalized per bin) for POUR, DRIP, HIGH_MID bands
 * - Spectral flux (half-wave rectified, log-magnitude) per band
 * - Spectral tilt (low-pour / high-pour energy ratio)
 *
 * Maintains previous frame's log-magnitude for spectral flux computation.
 */
class SpectralAnalyzer(
    private val fftEngine: FftEngine = FftEngine(),
) {
    private val size = fftEngine.size
    private val halfSize = size / 2

    // Reusable work arrays (avoid allocation per frame)
    private val windowedReal = FloatArray(size)
    private val windowedImag = FloatArray(size)
    private val powerSpec = FloatArray(halfSize + 1)
    private val magnitudeSpec = FloatArray(halfSize + 1)
    private val logMagnitude = FloatArray(halfSize + 1)
    private val prevLogMagnitude = FloatArray(halfSize + 1) { SILENCE_LOG_MAG }

    private var hasPreviousFrame = false

    /**
     * Analyzes a single windowed frame and returns spectral features.
     *
     * @param samples filtered PCM frame from AudioPreProcessor (length = FFT size)
     * @param includePowerSpectrum if true, include full power spectrum in result (for debug UI)
     */
    fun analyze(
        samples: ShortArray,
        includePowerSpectrum: Boolean = false,
    ): SpectralFeatures {
        // Apply Hann window + normalize to float
        fftEngine.applyHannWindow(samples, 0, samples.size, windowedReal)
        windowedImag.fill(0f)

        // FFT
        fftEngine.fft(windowedReal, windowedImag)

        // Power spectrum + magnitude
        fftEngine.powerSpectrum(windowedReal, windowedImag, powerSpec)
        fftEngine.magnitudeSpectrum(windowedReal, windowedImag, magnitudeSpec)

        // Log magnitude for spectral flux
        for (k in 0..halfSize) {
            logMagnitude[k] = 20f * log10(magnitudeSpec[k] + EPSILON)
        }

        // Band energies
        val bandEnergyDb = computeBandEnergies()

        // Spectral flux (per-band, half-wave rectified on log magnitude)
        val spectralFlux = if (hasPreviousFrame) {
            computeSpectralFlux()
        } else {
            FrequencyBand.entries.associateWith { 0f }
        }

        // Spectral tilt: energy ratio of low-pour (200-1000Hz) to high-pour (1000-3000Hz)
        val spectralTilt = computeSpectralTilt()

        // Save current frame as previous
        logMagnitude.copyInto(prevLogMagnitude)
        hasPreviousFrame = true

        val spectrum = if (includePowerSpectrum) powerSpec.copyOf() else null

        return SpectralFeatures(
            bandEnergyDb = bandEnergyDb,
            spectralFlux = spectralFlux,
            spectralTilt = spectralTilt,
            powerSpectrum = spectrum,
        )
    }

    /** Resets internal state. Call when starting a new session. */
    fun reset() {
        prevLogMagnitude.fill(SILENCE_LOG_MAG)
        hasPreviousFrame = false
    }

    private fun computeBandEnergies(): Map<FrequencyBand, Float> {
        return FrequencyBand.entries.associateWith { band ->
            var energy = 0.0
            val lo = band.lowBin.coerceAtMost(halfSize)
            val hi = band.highBin.coerceAtMost(halfSize)
            for (k in lo..hi) {
                energy += powerSpec[k].toDouble()
            }
            // Normalize per bin, convert to dB
            val perBinEnergy = energy / band.binCount
            if (perBinEnergy < EPSILON) {
                SILENCE_DB
            } else {
                (10.0 * log10(perBinEnergy)).toFloat().coerceAtLeast(SILENCE_DB)
            }
        }
    }

    private fun computeSpectralFlux(): Map<FrequencyBand, Float> {
        return FrequencyBand.entries.associateWith { band ->
            var flux = 0.0
            val lo = band.lowBin.coerceAtMost(halfSize)
            val hi = band.highBin.coerceAtMost(halfSize)
            for (k in lo..hi) {
                // Half-wave rectified: only positive changes (onsets)
                val diff = logMagnitude[k] - prevLogMagnitude[k]
                flux += max(0.0, diff.toDouble())
            }
            // Normalize by bin count
            (flux / band.binCount).toFloat()
        }
    }

    private fun computeSpectralTilt(): Float {
        // Low pour: 200-1000 Hz (bins 5-23)
        // High pour: 1000-3000 Hz (bins 24-69)
        val lowBinStart = 5
        val lowBinEnd = 23
        val highBinStart = 24
        val highBinEnd = 69.coerceAtMost(halfSize)

        var lowEnergy = 0.0
        for (k in lowBinStart..lowBinEnd.coerceAtMost(halfSize)) {
            lowEnergy += powerSpec[k].toDouble()
        }

        var highEnergy = 0.0
        for (k in highBinStart..highBinEnd) {
            highEnergy += powerSpec[k].toDouble()
        }

        return if (highEnergy < EPSILON) {
            0f
        } else {
            (lowEnergy / highEnergy).toFloat()
        }
    }

    companion object {
        private const val EPSILON = 1e-10f
        private const val SILENCE_DB = -96f
        private const val SILENCE_LOG_MAG = -200f
    }
}
