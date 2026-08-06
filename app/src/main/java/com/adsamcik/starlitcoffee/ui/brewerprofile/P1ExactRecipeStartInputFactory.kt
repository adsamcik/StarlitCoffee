package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureBasis
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartInput

/** Optional user-owned context that does not alter the canonical recipe or equipment. */
data class P1ExactRecipeStartMetadata(
    val grinderId: String? = null,
    val isDecaf: Boolean = false,
    val notes: String? = null,
    val coffeeBagId: Long? = null,
)

sealed interface P1ExactRecipeStartInputResult {
    data class Ready(val input: BuiltinBrewerSessionStartInput) : P1ExactRecipeStartInputResult
    data object Unavailable : P1ExactRecipeStartInputResult
}

/** Maps one validated setup selection to the durable boundary without calculator defaults. */
object P1ExactRecipeStartInputFactory {

    fun create(
        selection: P1BrewerProfileStartSelection,
        metadata: P1ExactRecipeStartMetadata = P1ExactRecipeStartMetadata(),
        recipeFor: (BuiltInRecipeId) -> BuiltInP1RecipeDefinition? = BuiltInP1RecipeCatalog::find,
        hasExactPlan: (BuiltInRecipeId) -> Boolean = { recipeId ->
            BuiltInP1ExactStagePlanCatalog.find(recipeId) != null
        },
    ): P1ExactRecipeStartInputResult {
        val recipe = recipeFor(selection.builtInRecipeId)
        val recipeMatchesSelection = recipe != null &&
            recipe.brewerProfileId == selection.brewerProfileId &&
            hasExactPlan(recipe.id) &&
            selection.equipmentOption in recipe.equipmentOptions
        if (!recipeMatchesSelection) return P1ExactRecipeStartInputResult.Unavailable
        requireNotNull(recipe)

        val primaryRatio = recipe.ratios.firstOrNull()
        val ratioUsesSupportedInputs = primaryRatio != null &&
            primaryRatio.definition.numerator == QuantityRole.DRY_COFFEE_DOSE &&
            primaryRatio.definition.denominator in PRIMARY_INPUT_ROLES
        if (!ratioUsesSupportedInputs) return P1ExactRecipeStartInputResult.Unavailable
        requireNotNull(primaryRatio)

        val sourceInputG = recipe.quantities.valueFor(primaryRatio.definition.denominator)
        val primaryInputG = when {
            sourceInputG != null -> sourceInputG.takeIf(::isPositiveFinite)
            primaryRatio.definition.denominator == QuantityRole.RESERVOIR_INPUT ->
                selection.measuredReservoirInputG?.takeIf(::isPositiveFinite)
            else -> null
        }
        val canonicalDoseG = recipe.quantities.dryCoffeeDoseG.takeIf(::isPositiveFinite)
        if (primaryInputG == null || canonicalDoseG == null) {
            return P1ExactRecipeStartInputResult.Unavailable
        }
        val equipment = EquipmentConfiguration(
            brewerProfileId = selection.brewerProfileId,
            capacityOverrideG = selection.equipmentCapacityG,
            filterSelection = selection.equipmentOption.filterSelection,
            accessoryIds = selection.equipmentOption.accessoryIds,
            basketId = selection.equipmentOption.basketId,
            heatSource = selection.heatSource,
        )
        return P1ExactRecipeStartInputResult.Ready(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = selection.brewerProfileId,
                builtInRecipeId = selection.builtInRecipeId,
                dryCoffeeDoseG = canonicalDoseG,
                inputWaterG = primaryInputG,
                equipment = equipment,
                harioSwitchWorkflow = selection.harioSwitchWorkflow,
                cezveSetup = selection.cezveSetup,
                temperatureC = recipe.exactUserTemperatureC(),
                grinderId = metadata.grinderId,
                isDecaf = metadata.isDecaf,
                notes = metadata.notes,
                coffeeBagId = metadata.coffeeBagId,
            ),
        )
    }

    private fun isPositiveFinite(value: Double): Boolean = value.isFinite() && value > 0.0

    private fun BuiltInP1RecipeDefinition.exactUserTemperatureC(): Int? =
        if (temperature.basis == P1TemperatureBasis.USER_EXACT) {
            temperature.minimumC?.takeIf { value -> value % 1.0 == 0.0 }?.toInt()
        } else {
            null
        }

    private val PRIMARY_INPUT_ROLES = setOf(
        QuantityRole.BREW_WATER_INPUT,
        QuantityRole.RESERVOIR_INPUT,
    )
}
