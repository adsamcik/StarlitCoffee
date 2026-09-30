package com.adsamcik.starlitcoffee.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CalcOp(val symbol: String) {
    MULTIPLY("×"),
    ADD("+"),
}

@Serializable
sealed class CalcToken {
    @Serializable
    @SerialName("number")
    data class Number(val value: String) : CalcToken() {
        val floatValue: Float get() = value.toFloatOrNull() ?: 0f
    }

    @Serializable
    @SerialName("cup")
    data class PresetRef(val preset: CupPreset) : CalcToken()

    @Serializable
    @SerialName("operator")
    data class Operator(val op: CalcOp) : CalcToken()
}

data class CalcResult(
    val totalDoseG: Float,
    val totalWaterMl: Float,
)
