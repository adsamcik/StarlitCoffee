package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Resolves a conservative, context-specific apparent-loss coefficient from
 * optional measurements attached to completed brews.
 *
 * The aggregate is deliberately derived from the immutable log history instead
 * of being persisted. Editing, clearing, deleting, or reassigning a brew then
 * updates the next estimate without an irreversible learning side effect.
 */
object BeverageOutputCalibration {
    data class Observation(
        val processKey: String,
        val coffeeBagId: Long?,
        val coffeeDoseG: Float,
        val measuredWaterInputG: Float,
        val measuredBeverageOutputG: Float,
        val createdAt: Long,
    )

    enum class Scope {
        BUILT_IN,
        PROCESS,
        PROCESS_AND_BEAN,
    }

    data class Profile(
        val method: BrewMethod,
        val apparentLossGPerCoffeeG: Float,
        val sampleCount: Int,
        val priorSampleCount: Int,
        val scope: Scope,
    )

    fun resolve(
        method: BrewMethod,
        processKey: String,
        coffeeBagId: Long?,
        observations: List<Observation>,
    ): Profile? {
        val builtIn = BeverageOutputEstimator.modelFor(method) ?: return null
        val processSamples = observations
            .asSequence()
            .filter { it.processKey == processKey }
            .mapNotNull(::sample)
            .sortedByDescending(Sample::createdAt)
            .toList()

        if (coffeeBagId == null) {
            val coefficient = estimateLayer(builtIn.apparentLossGPerCoffeeG, processSamples)
            return Profile(
                method = method,
                apparentLossGPerCoffeeG = coefficient,
                sampleCount = processSamples.size,
                priorSampleCount = 0,
                scope = if (processSamples.isEmpty()) Scope.BUILT_IN else Scope.PROCESS,
            )
        }

        val beanSamples = processSamples.filter { it.coffeeBagId == coffeeBagId }
        val processPriorSamples = processSamples.filter { it.coffeeBagId != coffeeBagId }
        val processPrior = estimateLayer(
            prior = builtIn.apparentLossGPerCoffeeG,
            samples = processPriorSamples,
        )
        if (beanSamples.isEmpty()) {
            return Profile(
                method = method,
                apparentLossGPerCoffeeG = processPrior,
                sampleCount = processPriorSamples.size,
                priorSampleCount = 0,
                scope = if (processPriorSamples.isEmpty()) Scope.BUILT_IN else Scope.PROCESS,
            )
        }

        return Profile(
            method = method,
            apparentLossGPerCoffeeG = estimateLayer(processPrior, beanSamples),
            sampleCount = beanSamples.size,
            priorSampleCount = processPriorSamples.size,
            scope = Scope.PROCESS_AND_BEAN,
        )
    }

    private fun sample(observation: Observation): Sample? {
        val dose = observation.coffeeDoseG
        val water = observation.measuredWaterInputG
        val output = observation.measuredBeverageOutputG
        if (!dose.isFinite() || !water.isFinite() || !output.isFinite()) return null
        if (listOf(dose, water, output).any { it <= 0f }) return null
        if (output > water) return null
        val coefficient = (water - output) / dose
        if (coefficient !in MIN_LOSS_PER_COFFEE_G..MAX_LOSS_PER_COFFEE_G) return null
        return Sample(
            coffeeBagId = observation.coffeeBagId,
            coefficient = coefficient,
            createdAt = observation.createdAt,
        )
    }

    /**
     * The built-in/process prior remains one equal vote for the first two
     * observations. From the third observation onward, only user measurements
     * are used and recent samples receive a modest, bounded preference.
     */
    private fun estimateLayer(prior: Float, samples: List<Sample>): Float {
        if (samples.isEmpty()) return prior
        if (samples.size <= PRIOR_BLEND_SAMPLE_LIMIT) {
            return (prior + samples.sumOf { it.coefficient.toDouble() }.toFloat()) /
                (samples.size + 1)
        }

        val recent = samples
            .sortedByDescending(Sample::createdAt)
            .take(MAX_RECENT_SAMPLES)
        val coefficients = recent.map(Sample::coefficient)
        val median = median(coefficients)
        val deviations = coefficients.map { abs(it - median) }
        val medianAbsoluteDeviation = median(deviations)
        val robustRadius = max(
            MIN_WINSOR_RADIUS,
            MAD_RADIUS_MULTIPLIER * NORMALIZED_MAD_SCALE * medianAbsoluteDeviation,
        )
        val lower = (median - robustRadius).coerceAtLeast(MIN_LOSS_PER_COFFEE_G)
        val upper = (median + robustRadius).coerceAtMost(MAX_LOSS_PER_COFFEE_G)

        var weightedTotal = 0.0
        var totalWeight = 0.0
        recent.forEachIndexed { rank, current ->
            val weight = RECENCY_DECAY.pow(rank)
            weightedTotal += current.coefficient.coerceIn(lower, upper) * weight
            totalWeight += weight
        }
        return (weightedTotal / totalWeight).toFloat()
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2f
        } else {
            sorted[middle]
        }
    }

    private data class Sample(
        val coffeeBagId: Long?,
        val coefficient: Float,
        val createdAt: Long,
    )

    private const val PRIOR_BLEND_SAMPLE_LIMIT = 2
    private const val MAX_RECENT_SAMPLES = 12
    private const val RECENCY_DECAY = 0.85
    private const val MIN_LOSS_PER_COFFEE_G = 0.5f
    private const val MAX_LOSS_PER_COFFEE_G = 4f
    private const val MIN_WINSOR_RADIUS = 0.35f
    private const val MAD_RADIUS_MULTIPLIER = 2.5f
    private const val NORMALIZED_MAD_SCALE = 1.4826f
}
