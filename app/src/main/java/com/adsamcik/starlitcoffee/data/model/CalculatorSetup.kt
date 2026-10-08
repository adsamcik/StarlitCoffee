package com.adsamcik.starlitcoffee.data.model

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The user's calculator intent, including the expression rather than a rounded result. */
@Serializable
data class CalculatorSetup(
    val ratio: Float,
    val quantity: String = CalculatorQuantityTarget.COFFEE.name,
    val tokens: List<CalcToken> = emptyList(),
    val filterType: String? = null,
    val grinderId: String? = null,
) {
    fun validatedFor(method: BrewMethod): CalculatorSetup? {
        if (!ratio.isFinite() || ratio <= 0f) return null
        val target = CalculatorQuantityTarget.entries.find { it.name == quantity }
        if (target == null || !target.isAvailableFor(method)) return null
        if (filterType != null && (method != BrewMethod.PULSAR || FilterType.entries.none { it.name == filterType })) {
            return null
        }
        if (tokens.size > MAX_TOKENS || tokens.any { !it.isValidSetupToken() }) return null
        return this
    }

    companion object {
        private const val MAX_TOKENS = 256
    }
}

private fun CalcToken.isValidSetupToken(): Boolean = when (this) {
    is CalcToken.Number -> value.length <= 32 &&
        value.toFloatOrNull()?.let { it.isFinite() && it >= 0f } == true
    is CalcToken.PresetRef -> preset.waterMl.isFinite() && preset.waterMl > 0f &&
        preset.doseG.isFinite() && preset.doseG >= 0f
    is CalcToken.Operator -> true
}

object CalculatorSetupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(setup: CalculatorSetup): String = json.encodeToString(setup)

    fun decode(raw: String, method: BrewMethod): CalculatorSetup? = try {
        json.decodeFromString<CalculatorSetup>(raw).validatedFor(method)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
