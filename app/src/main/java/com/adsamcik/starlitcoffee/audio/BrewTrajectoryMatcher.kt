package com.adsamcik.starlitcoffee.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks the temporal evolution of spectral features and matches them against
 * expected Pulsar brew acoustic trajectories.
 *
 * Instead of classifying individual frames ("is this water?"), this class asks
 * "does the acoustic evolution over the last N seconds match what a Pulsar brew
 * should sound like?" — exploiting the known physics of coffee brewing.
 *
 * Known Pulsar acoustic trajectory:
 * ```
 * Time →
 * Energy:  ___/‾‾‾‾‾‾‾‾\___........___
 *          idle | pour (plateau) | drip (decay) | silence
 *
 * Drip IOI: N/A | N/A | fast→slow (monotonic increase) | N/A
 *
 * Flatness: low | high (0.1-0.5) | medium | low
 *
 * Coincidence: 1-2 | 4-5 | 2-3 | 1
 * ```
 *
 * Key invariants that distinguish brew from other sounds:
 * 1. Pour onset is a SUSTAINED broadband rise (not a transient like a clank)
 * 2. Pour plateau is STABLE (low energy variance, unlike speech)
 * 3. Pour-to-drip transition shows an overall energy DECLINE (allowing local fluctuations)
 * 4. Drip rate generally DECREASES over time (with allowance for local irregularity)
 * 5. The full pour→drip→silence sequence takes 30-300 seconds (not 0.5s)
 */
class BrewTrajectoryMatcher {

    /** Confidence that the current acoustic trajectory matches a brew (0-1) */
    var brewConfidence: Float = 0f
        private set

    /** Which trajectory phase we think we're in */
    var trajectoryPhase: TrajectoryPhase = TrajectoryPhase.UNKNOWN
        private set

    /** Ambient energy level from initial quiet period (dB). Used for relative thresholds. */
    private var ambientEnergyDb = DEFAULT_AMBIENT_DB
    private var ambientCalibrated = false

    // Rolling windows of features (1-second resolution, ~86 frames → 1 sample/sec)
    private val energyHistory = RollingWindow(HISTORY_SECONDS)
    private val flatnessHistory = RollingWindow(HISTORY_SECONDS)
    private val coincidenceHistory = RollingWindow(HISTORY_SECONDS)
    private val dripIoiHistory = RollingWindow(HISTORY_SECONDS)

    // Accumulator for per-second averaging
    private var frameAccumulator = 0
    private var energySum = 0f
    private var flatnessSum = 0f
    private var coincidenceSum = 0f

    /**
     * Feed one spectral frame's summary features.
     * Call at 86fps — internally downsamples to 1 sample/second for trajectory matching.
     */
    fun feedFrame(
        pourEnergyDb: Float,
        spectralFlatness: Float,
        bandCoincidence: Int,
        dripInterOnsetInterval: Float = 0f,
    ) {
        energySum += pourEnergyDb
        flatnessSum += spectralFlatness
        coincidenceSum += bandCoincidence.toFloat()
        frameAccumulator++

        if (frameAccumulator >= FRAMES_PER_SECOND) {
            // Downsample: average over ~1 second
            val avgEnergy = energySum / frameAccumulator
            val avgFlatness = flatnessSum / frameAccumulator
            val avgCoincidence = coincidenceSum / frameAccumulator

            energyHistory.add(avgEnergy)
            flatnessHistory.add(avgFlatness)
            coincidenceHistory.add(avgCoincidence)
            if (dripInterOnsetInterval > 0f) {
                dripIoiHistory.add(dripInterOnsetInterval)
            }

            // Update trajectory assessment
            updateTrajectory()

            frameAccumulator = 0
            energySum = 0f
            flatnessSum = 0f
            coincidenceSum = 0f
        }
    }

    fun reset() {
        energyHistory.clear()
        flatnessHistory.clear()
        coincidenceHistory.clear()
        dripIoiHistory.clear()
        frameAccumulator = 0
        energySum = 0f
        flatnessSum = 0f
        coincidenceSum = 0f
        brewConfidence = 0f
        trajectoryPhase = TrajectoryPhase.UNKNOWN
        ambientEnergyDb = DEFAULT_AMBIENT_DB
        ambientCalibrated = false
    }

    // --- Trajectory Matching ---

    private fun updateTrajectory() {
        val n = energyHistory.count
        if (n < 3) {
            trajectoryPhase = TrajectoryPhase.UNKNOWN
            brewConfidence = 0f
            return
        }

        // Calibrate ambient from initial quiet period.
        // Cap at DEFAULT_AMBIENT_DB so loud-only sessions use the conservative fallback.
        if (!ambientCalibrated && n >= 5) {
            ambientEnergyDb = minOf(
                energyHistory.lastN(n).minOrNull() ?: DEFAULT_AMBIENT_DB,
                DEFAULT_AMBIENT_DB,
            )
            ambientCalibrated = true
        }

        // All thresholds relative to calibrated ambient level
        val pourThreshold = ambientEnergyDb + POUR_ENERGY_ABOVE_AMBIENT
        val dripCeiling = ambientEnergyDb + DRIP_ENERGY_ABOVE_AMBIENT
        val silenceThreshold = ambientEnergyDb + SILENCE_ABOVE_AMBIENT

        // Compute trajectory features
        val recentEnergy = energyHistory.lastN(3)
        val recentFlatness = flatnessHistory.lastN(3)
        val recentCoincidence = coincidenceHistory.lastN(3)

        val currentEnergy = recentEnergy.last()
        val currentFlatness = recentFlatness.last()
        val currentCoincidence = recentCoincidence.last()

        // Energy trend (positive = rising, negative = falling)
        val energyTrend = if (n >= 3) {
            recentEnergy.last() - recentEnergy.first()
        } else 0f

        // Energy stability (low = plateau, high = changing)
        val energyVariance = computeVariance(recentEnergy)

        // Phase detection with confidence scoring
        var confidence = 0f

        trajectoryPhase = when {
            // POUR: high residual energy + high coincidence + stable
            // Flatness is a bonus, not a gate — sustained pour can have low flatness
            currentEnergy > pourThreshold &&
                currentCoincidence > POUR_COINCIDENCE_THRESHOLD -> {
                val stabilityScore = 1f - (energyVariance / 50f).coerceIn(0f, 1f)
                val flatnessScore = (currentFlatness / 0.15f).coerceIn(0f, 1f)
                val coincidenceScore = (currentCoincidence / 4f).coerceIn(0f, 1f)

                confidence = (stabilityScore * 0.3f + flatnessScore * 0.2f + coincidenceScore * 0.5f)
                TrajectoryPhase.POURING
            }

            // POUR_ONSET: energy rising rapidly + broadband coincidence
            energyTrend > ONSET_ENERGY_RISE &&
                currentCoincidence >= 3 -> {
                confidence = min(1f, energyTrend / 15f)
                TrajectoryPhase.POUR_ONSET
            }

            // DRIP_DECAY: energy trending down + was recently higher
            n >= 5 && isTrendingDown(energyHistory.lastN(min(n, 10))) &&
                energyHistory.lastN(min(n, 10)).first() > pourThreshold -> {
                // Overall downward trend is a strong brew signal
                confidence = 0.7f
                TrajectoryPhase.DRIP_DECAY
            }

            // DRIPPING: low energy + drip IOI trending up (slowing down)
            currentEnergy < dripCeiling &&
                dripIoiHistory.count >= 3 &&
                isTrendingUp(dripIoiHistory.lastN(min(dripIoiHistory.count, 5))) -> {
                // Drip rate generally decreasing = physics of gravity drainage
                confidence = 0.8f
                TrajectoryPhase.DRIPPING
            }

            // SILENCE: very low energy, low coincidence
            currentEnergy < silenceThreshold -> {
                confidence = 0.5f
                TrajectoryPhase.SILENCE
            }

            else -> {
                confidence = 0f
                TrajectoryPhase.UNKNOWN
            }
        }

        brewConfidence = confidence
    }

    /**
     * Checks if values show an overall downward trend.
     * Uses soft monotonicity: allows local upward excursions (plateaus, rebounds)
     * as long as the smoothed envelope trends down overall.
     *
     * Research basis: drip rate decay is "conditionally supported" — real drawdown
     * has regime changes, fines-driven stalls, and channel-opening rebounds.
     */
    private fun isTrendingDown(values: List<Float>): Boolean {
        if (values.size < 3) return false

        // Smooth with 3-sample moving average to absorb local fluctuations
        val smoothed = smooth(values, windowSize = 3)

        // Check overall trend via first vs last (smoothed)
        val overallDrop = smoothed.first() - smoothed.last()
        if (overallDrop < TREND_MIN_RANGE) return false

        // Count violations: smoothed samples that rise above previous
        var violations = 0
        for (i in 1 until smoothed.size) {
            if (smoothed[i] > smoothed[i - 1] + TREND_TOLERANCE) {
                violations++
            }
        }

        // Allow up to ~30% of samples to be violations
        val maxViolations = (smoothed.size * TREND_MAX_VIOLATION_RATIO).toInt().coerceAtLeast(1)
        return violations <= maxViolations
    }

    /**
     * Checks if values show an overall upward trend (drip IOI increasing = drips slowing).
     * Allows local decreases (e.g., a cluster of fast drips followed by slowing).
     */
    private fun isTrendingUp(values: List<Float>): Boolean {
        if (values.size < 3) return false

        val smoothed = smooth(values, windowSize = 3)

        val overallRise = smoothed.last() - smoothed.first()
        if (overallRise < TREND_MIN_RANGE * 0.1f) return false

        var violations = 0
        for (i in 1 until smoothed.size) {
            if (smoothed[i] < smoothed[i - 1] - TREND_TOLERANCE) {
                violations++
            }
        }

        val maxViolations = (smoothed.size * TREND_MAX_VIOLATION_RATIO).toInt().coerceAtLeast(1)
        return violations <= maxViolations
    }

    private fun smooth(values: List<Float>, windowSize: Int = 3): List<Float> {
        if (values.size < windowSize) return values
        return values.windowed(windowSize) { window -> window.average().toFloat() }
    }

    private fun computeVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /** Phases of the expected Pulsar brew acoustic trajectory */
    enum class TrajectoryPhase(val displayName: String) {
        UNKNOWN("Unknown"),
        POUR_ONSET("Pour onset"),
        POURING("Pouring"),
        DRIP_DECAY("Drip decay"),
        DRIPPING("Dripping"),
        SILENCE("Silence"),
    }

    /** Fixed-size rolling window for 1-sample-per-second features */
    internal class RollingWindow(private val capacity: Int) {
        private val data = FloatArray(capacity)
        private var writePos = 0
        var count = 0
            private set

        fun add(value: Float) {
            data[writePos] = value
            writePos = (writePos + 1) % capacity
            if (count < capacity) count++
        }

        fun clear() {
            data.fill(0f)
            writePos = 0
            count = 0
        }

        fun lastN(n: Int): List<Float> {
            val actual = min(n, count)
            val result = mutableListOf<Float>()
            for (i in 0 until actual) {
                val idx = (writePos - actual + i + capacity) % capacity
                result.add(data[idx])
            }
            return result
        }
    }

    companion object {
        private const val FRAMES_PER_SECOND = 86
        private const val HISTORY_SECONDS = 60 // Track last 60 seconds

        // Relative thresholds (dB above ambient baseline).
        // Research: absolute dBFS thresholds vary ±9 dB across Android phones.
        private const val POUR_ENERGY_ABOVE_AMBIENT = 15f   // pour must be 15dB above ambient
        private const val POUR_COINCIDENCE_THRESHOLD = 3f
        private const val DRIP_ENERGY_ABOVE_AMBIENT = 10f   // drip ceiling is 10dB above ambient
        private const val SILENCE_ABOVE_AMBIENT = 3f         // silence is within 3dB of ambient
        private const val DEFAULT_AMBIENT_DB = -40f           // fallback if calibration fails
        private const val ONSET_ENERGY_RISE = 5f // dB rise over 3 seconds

        // Trend detection (soft monotonicity)
        private const val TREND_TOLERANCE = 2f // dB tolerance for trend violations
        private const val TREND_MIN_RANGE = 5f // Minimum total change to count
        private const val TREND_MAX_VIOLATION_RATIO = 0.3f // Allow ~30% of samples to violate
    }
}
