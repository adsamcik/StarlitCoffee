package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget

/** Informational calculator targets never become automatic physical-completion rules. */
internal fun calculatorStageTargets(
    plan: CompiledStagePlan,
    recipe: BrewRecipeSnapshotV1,
    preparation: BrewUiState,
): CompiledStagePlan = plan.copy(stages = plan.stages.map { stage ->
    val mass = calculatorMassTarget(stage.definition.action, recipe, preparation)
    val time = calculatorTimeTarget(stage.definition.action, preparation)
    stage.copy(definition = stage.definition.copy(referenceTargets = StageReferenceTargets(
        massTargets = listOfNotNull(mass), timeTargets = listOfNotNull(time),
    )))
})

private fun calculatorMassTarget(
    action: BrewStageAction,
    recipe: BrewRecipeSnapshotV1,
    preparation: BrewUiState,
): StageMassTarget? {
    val quantities = recipe.quantities
    val value = when (action) {
        BrewStageAction.BLOOM -> Triple(QuantityRole.BREW_WATER_INPUT, StageMassReference.STAGE_ADDED,
            preparation.bloomG.toDouble().takeIf { it.isFinite() && it > 0.0 })
        BrewStageAction.POUR -> Triple(QuantityRole.BREW_WATER_INPUT, StageMassReference.BREW_CUMULATIVE,
            quantities.brewWaterInputG)
        BrewStageAction.STEEP -> Triple(QuantityRole.BREW_WATER_INPUT, StageMassReference.STAGE_ADDED,
            quantities.brewWaterInputG)
        BrewStageAction.HEAT -> Triple(QuantityRole.RESERVOIR_INPUT, StageMassReference.RECIPE_TOTAL,
            quantities.reservoirInputG)
        BrewStageAction.OBSERVE -> Triple(QuantityRole.BEVERAGE_YIELD, StageMassReference.RECIPE_TOTAL,
            quantities.targetBeverageYieldG.takeIf { preparation.method.outputSemantics ==
                com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics.BEVERAGE_YIELD })
        else -> return null
    }
    return value.third?.let { grams -> StageMassTarget(
        id = StageTargetId("calculator_selected_amount"), role = value.first, reference = value.second,
        qualifier = StageTargetQualifier.EXACT, minimumGrams = grams,
    ) }
}

private fun calculatorTimeTarget(action: BrewStageAction, preparation: BrewUiState): StageTimeTarget? {
    val observesDuration = action in setOf(BrewStageAction.POUR, BrewStageAction.STEEP, BrewStageAction.OBSERVE)
    val low = preparation.timeTargetLowS
    val high = preparation.timeTargetHighS
    if (!observesDuration || low <= 0 || high < low) return null
    return StageTimeTarget(
        id = StageTargetId("calculator_time_reference"),
        reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
        qualifier = StageTargetQualifier.STARTING_POINT,
        minimumMillis = low * 1_000L, maximumMillis = high * 1_000L,
    )
}
