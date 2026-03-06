package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.InputMode

/**
 * Pure calculation engine for brew parameters.
 * No Android dependencies, no mutable state — every function is deterministic.
 */
object BrewCalculator {

    data class BrewCalculation(
        val coffeeG: Float,
        val waterG: Float,
        val effectiveRatio: Float,
        val bloomG: Float,
        val remainingWaterG: Float,
        val pulseSizeG: Float,
        val effectivePulseCount: Int,
        val timeTargetLowS: Int,
        val timeTargetHighS: Int,
        val refillCount: Int,
        val ratioWarning: String?,
        val bloomWarning: String?,
    )

    fun calculate(
        method: BrewMethod,
        inputMode: InputMode,
        amount: Float,
        effectiveRatio: Float,
        bloomMultiplier: Float,
        pulseCount: Int,
        isDecaf: Boolean,
    ): BrewCalculation {
        val (coffeeG, waterG) = computeCoffeeAndWater(inputMode, amount, effectiveRatio)

        val bloomG = if (method.hasBloom) coffeeG * bloomMultiplier else 0f
        val remainingWaterG = waterG - bloomG

        val pulseSizeG = if (method.hasPulses && pulseCount > 0) {
            remainingWaterG / pulseCount
        } else {
            0f
        }

        val timeTargetLowS = method.timeTargetLow.let {
            if (isDecaf) (it - 30).coerceAtLeast(120) else it
        }
        val timeTargetHighS = method.timeTargetHigh.let {
            if (isDecaf) (it - 30).coerceAtLeast(150) else it
        }

        val refillCount = if (method.capacityMaxG != null && waterG > method.capacityMaxG) {
            kotlin.math.ceil(waterG.toDouble() / method.capacityMaxG).toInt() - 1
        } else {
            0
        }

        val ratioWarning = computeRatioWarning(method, effectiveRatio, coffeeG, waterG)

        val bloomWarning = if (method.hasBloom && bloomG > waterG && waterG > 0f) {
            "Bloom (${"%.0f".format(bloomG)}g) exceeds total water (${"%.0f".format(waterG)}g)"
        } else {
            null
        }

        return BrewCalculation(
            coffeeG = coffeeG,
            waterG = waterG,
            effectiveRatio = effectiveRatio,
            bloomG = bloomG,
            remainingWaterG = remainingWaterG,
            pulseSizeG = pulseSizeG,
            effectivePulseCount = pulseCount,
            timeTargetLowS = timeTargetLowS,
            timeTargetHighS = timeTargetHighS,
            refillCount = refillCount,
            ratioWarning = ratioWarning,
            bloomWarning = bloomWarning,
        )
    }

    private fun computeCoffeeAndWater(
        inputMode: InputMode,
        amount: Float,
        effectiveRatio: Float,
    ): Pair<Float, Float> {
        return when (inputMode) {
            InputMode.COFFEE_TO_WATER -> {
                val coffeeG = amount
                val waterG = if (effectiveRatio != 0f) coffeeG * effectiveRatio else 0f
                coffeeG to waterG
            }
            InputMode.WATER_TO_COFFEE -> {
                val waterG = amount
                val coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                coffeeG to waterG
            }
            InputMode.BREW_SIZE_TO_BOTH -> {
                val brewMl = amount
                val absorptionFactor = 2.0f
                val divisor = effectiveRatio - absorptionFactor
                if (divisor > 0f) {
                    val coffeeG = brewMl / divisor
                    val waterG = coffeeG * effectiveRatio
                    coffeeG to waterG
                } else {
                    val waterG = brewMl
                    val coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                    coffeeG to waterG
                }
            }
            InputMode.CUP_SIZE_TO_BOTH -> {
                val waterG = amount
                val coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                coffeeG to waterG
            }
        }
    }

    private fun computeRatioWarning(
        method: BrewMethod,
        effectiveRatio: Float,
        coffeeG: Float,
        waterG: Float,
    ): String? {
        return when {
            effectiveRatio <= 0f -> "Ratio must be greater than zero"
            coffeeG <= 0f || waterG <= 0f -> null
            method == BrewMethod.ESPRESSO -> null
            effectiveRatio < 10f -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually strong"
            effectiveRatio > 20f -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually weak"
            else -> null
        }
    }
}
