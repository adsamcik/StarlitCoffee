package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.grindersFor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Editable text is retained even while incomplete; existing expressions survive equipment edits. */
@Serializable
data class BrewingSetDraft(
    val set: BrewingSet,
    val name: String = set.name.orEmpty(),
    val ratio: String = set.setup.ratio.toString().removeSuffix(".0"),
    val amount: String? = null,
) {
    fun withMethod(method: BrewMethod): BrewingSetDraft = if (method == set.method) this else copy(
        set = set.copy(method = method, setup = initialBrewingSetup(method)),
        ratio = method.defaultRatio.toString().removeSuffix(".0"), amount = null,
    )

    fun build(grinders: GrinderDataProvider): BrewingSet? {
        val parsedRatio = ratio.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
        val tokens = when {
            amount == null -> set.setup.tokens
            amount.isBlank() -> emptyList()
            else -> {
                val normalized = amount.replace(',', '.')
                normalized.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
                listOf(CalcToken.Number(normalized))
            }
        }
        val filter = set.setup.filterType?.let(FilterType::valueOf).takeIf { set.method == BrewMethod.PULSAR }
        val supported = grinders.grindersFor(set.method, filter)
        return set.copy(name = name.trim().takeIf { it.isNotEmpty() }, setup = set.setup.copy(
            ratio = parsedRatio, tokens = tokens, filterType = filter?.name,
            grinderId = set.setup.grinderId?.takeIf { id -> supported.any { it.id == id } },
        )).validated()
    }

    fun encode(): String = Json.encodeToString(this)

    companion object {
        fun decode(raw: String): BrewingSetDraft = Json.decodeFromString(raw)
        fun encodeList(drafts: List<BrewingSetDraft>): String = Json.encodeToString(drafts)
        fun decodeList(raw: String): List<BrewingSetDraft> = Json.decodeFromString(raw)
    }
}

fun initialBrewingSetup(method: BrewMethod): CalculatorSetup = CalculatorSetup(
    ratio = method.defaultRatio, quantity = CalculatorQuantityTarget.COFFEE.name,
    tokens = listOf(CalcToken.Number(when (method) {
        BrewMethod.ESPRESSO -> "18"
        BrewMethod.AEROPRESS -> "15"
        else -> "20"
    })),
    filterType = FilterType.PAPER.name.takeIf { method == BrewMethod.PULSAR },
)
