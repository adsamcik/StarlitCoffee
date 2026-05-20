package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
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
        val retainedWaterG: Float,
        val predictedCupVolumeG: Float,
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
        val (coffeeG, waterG) = computeCoffeeAndWater(method, inputMode, amount, effectiveRatio)

        val bloomG = if (method.hasBloom) coffeeG * bloomMultiplier else 0f
        val remainingWaterG = waterG - bloomG

        val pulseSizeG = if (method.hasPulses && pulseCount > 0) {
            remainingWaterG / pulseCount
        } else {
            0f
        }

        val timeTargetLowS = if (isDecaf) {
            method.decafTimeAdjustmentPolicy.lowTarget(method.timeTargetLow)
        } else {
            method.timeTargetLow
        }
        val timeTargetHighS = if (isDecaf) {
            method.decafTimeAdjustmentPolicy.highTarget(method.timeTargetHigh)
        } else {
            method.timeTargetHigh
        }

        val refillCount = if (method.capacityMaxG != null && waterG > method.capacityMaxG) {
            kotlin.math.ceil(waterG.toDouble() / method.capacityMaxG).toInt() - 1
        } else {
            0
        }

        val retainedWaterG = when (method.outputSemantics) {
            BrewOutputSemantics.WATER_IN_MINUS_ABSORPTION -> coffeeG * method.absorptionRatio
            BrewOutputSemantics.BEVERAGE_YIELD -> 0f
        }
        val predictedCupVolumeG = when (method.outputSemantics) {
            BrewOutputSemantics.WATER_IN_MINUS_ABSORPTION -> (waterG - retainedWaterG).coerceAtLeast(0f)
            BrewOutputSemantics.BEVERAGE_YIELD -> waterG.coerceAtLeast(0f)
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
            retainedWaterG = retainedWaterG,
            predictedCupVolumeG = predictedCupVolumeG,
        )
    }

    private fun computeCoffeeAndWater(
        method: BrewMethod,
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
                when (method.outputSemantics) {
                    BrewOutputSemantics.WATER_IN_MINUS_ABSORPTION -> {
                        val brewMl = amount
                        val divisor = effectiveRatio - method.absorptionRatio
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
                    BrewOutputSemantics.BEVERAGE_YIELD -> {
                        val beverageYieldG = amount
                        val coffeeG = if (effectiveRatio != 0f) beverageYieldG / effectiveRatio else 0f
                        coffeeG to beverageYieldG
                    }
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
        val warningRange = method.ratioWarningRange
        return when {
            effectiveRatio <= 0f -> "Ratio must be greater than zero"
            coffeeG <= 0f || waterG <= 0f -> null
            warningRange == null -> null
            effectiveRatio < warningRange.strongBelow -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually strong"
            effectiveRatio > warningRange.weakAbove -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually weak"
            else -> null
        }
    }
}
