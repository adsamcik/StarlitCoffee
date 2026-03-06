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
 * 3. Pour-to-drip transition is a MONOTONIC energy decay (not oscillating)
 * 4. Drip rate DECREASES over time (gravity drainage physics)
 * 5. The full pour→drip→silence sequence takes 30-300 seconds (not 0.5s)
 */
class BrewTrajectoryMatcher {

    /** Confidence that the current acoustic trajectory matches a brew (0-1) */
    var brewConfidence: Float = 0f
        private set

    /** Which trajectory phase we think we're in */
    var trajectoryPhase: TrajectoryPhase = TrajectoryPhase.UNKNOWN
        private set

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
    }

    // --- Trajectory Matching ---

    private fun updateTrajectory() {
        val n = energyHistory.count
        if (n < 3) {
            trajectoryPhase = TrajectoryPhase.UNKNOWN
            brewConfidence = 0f
            return
        }

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
            // POUR: high energy + high flatness + high coincidence + stable
            currentEnergy > POUR_ENERGY_THRESHOLD &&
                currentFlatness > POUR_FLATNESS_THRESHOLD &&
                currentCoincidence > POUR_COINCIDENCE_THRESHOLD -> {
                // Stability bonus: sustained pour has low variance
                val stabilityScore = 1f - (energyVariance / 50f).coerceIn(0f, 1f)
                // Flatness bonus
                val flatnessScore = (currentFlatness / 0.15f).coerceIn(0f, 1f)
                // Coincidence bonus
                val coincidenceScore = (currentCoincidence / 4f).coerceIn(0f, 1f)

                confidence = (stabilityScore * 0.3f + flatnessScore * 0.3f + coincidenceScore * 0.4f)
                TrajectoryPhase.POURING
            }

            // POUR_ONSET: energy rising rapidly + broadband
            energyTrend > ONSET_ENERGY_RISE &&
                currentFlatness > POUR_FLATNESS_THRESHOLD -> {
                confidence = min(1f, energyTrend / 15f)
                TrajectoryPhase.POUR_ONSET
            }

            // DRIP_DECAY: energy falling + was recently higher
            n >= 5 && isMonotonicDecay(energyHistory.lastN(5)) &&
                energyHistory.lastN(5).first() > POUR_ENERGY_THRESHOLD -> {
                // Monotonic decay is a strong brew signal
                confidence = 0.7f
                TrajectoryPhase.DRIP_DECAY
            }

            // DRIPPING: low energy + drip IOI increasing (slowing down)
            currentEnergy < DRIP_ENERGY_CEILING &&
                dripIoiHistory.count >= 3 &&
                isMonotonicIncrease(dripIoiHistory.lastN(3)) -> {
                // Drip rate monotonically decreasing = physics of gravity drainage
                confidence = 0.8f
                TrajectoryPhase.DRIPPING
            }

            // SILENCE: very low energy, low coincidence
            currentEnergy < SILENCE_THRESHOLD -> {
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

    private fun isMonotonicDecay(values: List<Float>): Boolean {
        if (values.size < 2) return false
        for (i in 1 until values.size) {
            if (values[i] > values[i - 1] + MONOTONIC_TOLERANCE) return false
        }
        // Must actually decrease significantly
        return values.first() - values.last() > MONOTONIC_MIN_RANGE
    }

    private fun isMonotonicIncrease(values: List<Float>): Boolean {
        if (values.size < 2) return false
        for (i in 1 until values.size) {
            if (values[i] < values[i - 1] - MONOTONIC_TOLERANCE) return false
        }
        return values.last() - values.first() > MONOTONIC_MIN_RANGE * 0.1f
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

        // Thresholds derived from real Pulsar recordings
        private const val POUR_ENERGY_THRESHOLD = -20f  // dB in POUR band
        private const val POUR_FLATNESS_THRESHOLD = 0.08f
        private const val POUR_COINCIDENCE_THRESHOLD = 3f
        private const val DRIP_ENERGY_CEILING = -25f
        private const val SILENCE_THRESHOLD = -35f
        private const val ONSET_ENERGY_RISE = 5f // dB rise over 3 seconds

        // Monotonic detection
        private const val MONOTONIC_TOLERANCE = 2f // dB tolerance for "monotonic"
        private const val MONOTONIC_MIN_RANGE = 5f // Minimum total change to count
    }
}
