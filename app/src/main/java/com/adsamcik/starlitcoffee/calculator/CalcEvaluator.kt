package com.adsamcik.starlitcoffee.calculator

import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcResult
import com.adsamcik.starlitcoffee.data.model.CalcToken

object CalcEvaluator {

    enum class InputDirection { DOSE, WATER }

    fun evaluate(
        tokens: List<CalcToken>,
        ratio: Float,
        direction: InputDirection,
    ): CalcResult {
        if (tokens.isEmpty() || ratio <= 0f) return CalcResult(0f, 0f)

        val addGroups = splitByAdd(tokens)

        var totalDose = 0f
        var totalWater = 0f

        for (group in addGroups) {
            val (dose, water) = evaluateMultGroup(group, ratio, direction)
            totalDose += dose
            totalWater += water
        }

        return CalcResult(
            totalDoseG = totalDose.coerceAtLeast(0f),
            totalWaterMl = totalWater.coerceAtLeast(0f),
        )
    }

    private fun splitByAdd(tokens: List<CalcToken>): List<List<CalcToken>> {
        val groups = mutableListOf<MutableList<CalcToken>>()
        var current = mutableListOf<CalcToken>()

        for (token in tokens) {
            if (token is CalcToken.Operator && token.op == CalcOp.ADD) {
                if (current.isNotEmpty()) {
                    groups.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(token)
            }
        }

        if (current.isNotEmpty()) {
            groups.add(current)
        }

        return groups
    }

    private fun evaluateMultGroup(
        tokens: List<CalcToken>,
        ratio: Float,
        direction: InputDirection,
    ): Pair<Float, Float> {
        val values = tokens.filter { it !is CalcToken.Operator }

        if (values.isEmpty()) return Pair(0f, 0f)

        val numbers = values.filterIsInstance<CalcToken.Number>()
        val presets = values.filterIsInstance<CalcToken.PresetRef>()

        if (presets.isEmpty()) {
            val product = numbers.fold(1f) { acc, number -> acc * number.floatValue }
            return when (direction) {
                InputDirection.DOSE -> Pair(product, product * ratio)
                InputDirection.WATER -> Pair(product / ratio, product)
            }
        }

        val scalar = if (numbers.isEmpty()) {
            1f
        } else {
            numbers.fold(1f) { acc, number -> acc * number.floatValue }
        }

        val presetWater = presets.sumOf { it.preset.waterMl.toDouble() }.toFloat()
        val presetDose = presetWater / ratio

        return Pair(scalar * presetDose, scalar * presetWater)
    }
}
