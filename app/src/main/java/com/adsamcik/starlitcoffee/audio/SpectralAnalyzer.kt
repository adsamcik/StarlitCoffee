package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import kotlin.math.exp
import kotlin.math.ln
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

        // Spectral flatness (Wiener entropy) — noise-likeness measure
        val spectralFlatness = computeSpectralFlatness()

        // Cepstral peak prominence — pitch detection for speech/music veto
        val cepstralPeakProminence = computeCepstralPeakProminence()

        // Band coincidence — how many octave sub-bands have energy above a reference
        val bandCoincidenceCount = computeBandCoincidence(bandEnergyDb)

        // Save current frame as previous
        logMagnitude.copyInto(prevLogMagnitude)
        hasPreviousFrame = true

        val spectrum = if (includePowerSpectrum) powerSpec.copyOf() else null

        return SpectralFeatures(
            bandEnergyDb = bandEnergyDb,
            spectralFlux = spectralFlux,
            spectralTilt = spectralTilt,
            spectralFlatness = spectralFlatness,
            cepstralPeakProminence = cepstralPeakProminence,
            bandCoincidenceCount = bandCoincidenceCount,
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

    /**
     * Spectral Flatness (Wiener Entropy) over 200Hz–6kHz.
     * Ratio of geometric mean to arithmetic mean of power spectrum.
     * Water (broadband noise) → ~0.3-0.8, speech/music (tonal) → <0.15.
     */
    private fun computeSpectralFlatness(): Float {
        val lo = FLATNESS_LO_BIN.coerceAtMost(halfSize)
        val hi = FLATNESS_HI_BIN.coerceAtMost(halfSize)
        val count = hi - lo + 1
        if (count <= 0) return 0f

        // Geometric mean via exp(mean(ln(x))) to avoid overflow
        var logSum = 0.0
        var arithSum = 0.0
        var validCount = 0
        for (k in lo..hi) {
            val p = powerSpec[k].toDouble()
            if (p > EPSILON) {
                logSum += ln(p)
                arithSum += p
                validCount++
            }
        }

        if (validCount == 0 || arithSum < EPSILON) return 0f
        val geoMean = exp(logSum / validCount)
        val arithMean = arithSum / validCount
        return (geoMean / arithMean).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Cepstral Peak Prominence — detects periodic signals (speech, music, hum).
     * Computes real cepstrum and finds the strongest peak in the pitch range
     * (quefrency 2–20ms = 50–500Hz fundamental). Returns prominence in dB
     * above the cepstral mean. High CPP (>3 dB) = strong pitch = NOT water.
     */
    private fun computeCepstralPeakProminence(): Float {
        // Real cepstrum: IFFT(log(|FFT|))
        // We already have logMagnitude, so compute IFFT of it
        // Approximate via cosine transform of log-magnitude (symmetric spectrum)
        val n = halfSize + 1
        val loQuefrency = CPP_LO_QUEFRENCY // 2ms = sample 88 at 44100Hz
        val hiQuefrency = CPP_HI_QUEFRENCY // 20ms = sample 882 at 44100Hz

        // DCT-I approximation of cepstrum in the pitch range
        var maxCepstral = Float.MIN_VALUE
        var cepstralSum = 0.0
        var cepstralCount = 0

        for (q in loQuefrency..hiQuefrency.coerceAtMost(n - 1)) {
            var sum = 0.0
            for (k in 0 until n) {
                val angle = Math.PI * k * q / halfSize
                sum += logMagnitude[k] * kotlin.math.cos(angle)
            }
            val cepstralValue = (sum / n).toFloat()
            if (cepstralValue > maxCepstral) {
                maxCepstral = cepstralValue
            }
            cepstralSum += cepstralValue
            cepstralCount++
        }

        if (cepstralCount == 0) return 0f
        val cepstralMean = (cepstralSum / cepstralCount).toFloat()
        return max(0f, maxCepstral - cepstralMean)
    }

    /**
     * Band coincidence: counts how many octave sub-bands have energy
     * significantly above a rolling reference level.
     * Water fills ≥4/5 bands; speech/fan typically fills 2-3.
     *
     * Sub-bands: 200-400, 400-800, 800-1600, 1600-3200, 3200-6400 Hz
     */
    private fun computeBandCoincidence(bandEnergyDb: Map<FrequencyBand, Float>): Int {
        // Use the overall POUR band energy as a reference; each sub-band must
        // be within COINCIDENCE_MARGIN_DB of the strongest sub-band
        var maxSubBandDb = -96f
        val subBandEnergies = FloatArray(COINCIDENCE_BANDS.size)

        for ((i, range) in COINCIDENCE_BANDS.withIndex()) {
            val lo = range.first.coerceAtMost(halfSize)
            val hi = range.second.coerceAtMost(halfSize)
            var energy = 0.0
            val binCount = hi - lo + 1
            for (k in lo..hi) {
                energy += powerSpec[k].toDouble()
            }
            val db = if (binCount > 0 && energy > EPSILON) {
                (10.0 * log10(energy / binCount)).toFloat()
            } else {
                -96f
            }
            subBandEnergies[i] = db
            if (db > maxSubBandDb) maxSubBandDb = db
        }

        // Count bands within margin of maximum
        var count = 0
        for (db in subBandEnergies) {
            if (maxSubBandDb - db < COINCIDENCE_MARGIN_DB) {
                count++
            }
        }
        return count
    }

    companion object {
        private const val EPSILON = 1e-10f
        private const val SILENCE_DB = -96f
        private const val SILENCE_LOG_MAG = -200f

        // Spectral flatness: 200Hz–6kHz (bins 5–139 at 44100/1024)
        private const val FLATNESS_LO_BIN = 5    // ~200 Hz
        private const val FLATNESS_HI_BIN = 139  // ~6000 Hz

        // Cepstral peak prominence: pitch range 50-500Hz → quefrency 2-20ms
        // At 44100 Hz: 2ms = 88 samples, 20ms = 882 samples
        // But cepstrum length = FFT size / 2 = 512, so cap at 512
        private const val CPP_LO_QUEFRENCY = 88  // ~500 Hz fundamental
        private const val CPP_HI_QUEFRENCY = 512  // ~86 Hz fundamental (capped by FFT)

        // Band coincidence: 5 octave sub-bands
        // Bin indices for 1024-pt FFT at 44100 Hz
        private val COINCIDENCE_BANDS = arrayOf(
            Pair(5, 9),     // 200-400 Hz
            Pair(10, 18),   // 400-800 Hz
            Pair(19, 37),   // 800-1600 Hz
            Pair(38, 74),   // 1600-3200 Hz
            Pair(75, 139),  // 3200-6400 Hz
        )

        // Band must be within this many dB of the strongest to count as "coincident"
        private const val COINCIDENCE_MARGIN_DB = 20f
    }
}
