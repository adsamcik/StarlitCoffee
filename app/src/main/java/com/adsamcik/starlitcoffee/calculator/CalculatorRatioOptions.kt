package com.adsamcik.starlitcoffee.calculator

import com.adsamcik.starlitcoffee.data.model.BrewMethod

fun BrewMethod.calculatorRatioOptions(currentRatio: Float): List<Float> {
    val defaults = if (this == BrewMethod.ESPRESSO) {
        listOf(1f, 1.5f, 2f, 2.5f, 3f)
    } else {
        defaultRatioPresets.map { it.ratio }
    }
    return (defaults + currentRatio.takeIf { it.isFinite() && it > 0f }).filterNotNull().distinct().sorted()
}

fun formatCalculatorRatio(ratio: Float): String = ratio.toString().removeSuffix(".0")
