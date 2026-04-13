package com.adsamcik.starlitcoffee.data.model

enum class CalcOp(val symbol: String) {
    MULTIPLY("×"),
    ADD("+"),
}

sealed class CalcToken {
    data class Number(val value: String) : CalcToken() {
        val floatValue: Float get() = value.toFloatOrNull() ?: 0f
    }

    data class PresetRef(val preset: CupPreset) : CalcToken()

    data class Operator(val op: CalcOp) : CalcToken()
}

data class CalcResult(
    val totalDoseG: Float,
    val totalWaterMl: Float,
)
