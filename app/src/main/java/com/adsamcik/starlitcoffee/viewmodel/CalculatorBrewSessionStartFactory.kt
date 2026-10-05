package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup

/** Freezes the calculator preview and the latest coffee/grind preparation without recalculating them. */
class CalculatorBrewSessionStartFactory(
    private val legacyFactory: LegacyBrewSessionStartFactory = LegacyBrewSessionStartFactory(),
) {
    fun create(
        calculator: CalcUiState,
        preparation: BrewUiState,
        selectedCoffeeBagId: Long?,
    ): BrewSessionStartRequest? {
        if (!calculator.isValidFor(preparation)) return null
        val setup = CalculatorSetup(
            ratio = calculator.ratio,
            quantity = calculator.quantityTarget.name,
            tokens = calculator.tokens.toList(),
            filterType = preparation.filterType?.name,
            grinderId = preparation.selectedGrinderId,
        ).validatedFor(calculator.brewMethod) ?: return null
        val state = preparation.copy(
            coffeeG = calculator.previewDoseG,
            waterG = calculator.previewWaterMl,
            effectiveRatio = calculator.ratio,
            predictedCupVolumeG = calculator.previewBeverageG ?: 0f,
            beverageOutputCalibration = calculator.beverageOutputCalibration,
        )
        val ready = legacyFactory.create(state, selectedCoffeeBagId, sourceRecipeId = null)
            as? LegacyBrewSessionStartResult.Ready ?: return null
        return ready.request.copy(
            recipe = ready.request.recipe.copy(calculatorSetup = setup),
            stagePlan = calculatorStageTargets(ready.request.stagePlan, ready.request.recipe, state),
        )
    }

    private fun Float.positiveFinite(): Boolean = isFinite() && this > 0f

    private fun CalcUiState.isValidFor(preparation: BrewUiState): Boolean = listOf(
        hasValidExpression,
        brewMethod == preparation.method,
        previewDoseG.positiveFinite(),
        previewWaterMl.positiveFinite(),
        previewBeverageG?.positiveFinite() != false,
    ).all { it }
}
