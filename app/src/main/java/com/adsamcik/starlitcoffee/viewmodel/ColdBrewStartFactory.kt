package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PhysicalBrewClock
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier

/** Reviewed refrigerated jar workflow, retaining the calculator's actual quantities and coffee. */
object ColdBrewStartFactory {
    const val DEFAULT_DURATION_MILLIS = 14L * 60L * 60L * 1_000L
    const val CONTENT_PREFIX = "cold_refrigerated_"

    fun create(
        calculatorRequest: BrewSessionStartRequest,
        timerOnly: Boolean,
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
        originKnown: Boolean = true,
        originWallClockMillis: Long? = null,
    ): BrewSessionStartRequest {
        require(calculatorRequest.executionContext.logPresentation.methodLabel == "COLD_BREW")
        val water = requireNotNull(calculatorRequest.recipe.quantities.brewWaterInputG)
        val dose = calculatorRequest.recipe.quantities.dryCoffeeDoseG
        val definitions = if (timerOnly) listOf(stage("steep", BrewStageAction.STEEP)) else listOf(
            stage("prepare", BrewStageAction.PREPARE),
            stage("combine", BrewStageAction.ADD_WATER).copy(referenceTargets = StageReferenceTargets(
                massTargets = listOf(mass("coffee", QuantityRole.DRY_COFFEE_DOSE, dose),
                    mass("water", QuantityRole.BREW_WATER_INPUT, water)))),
            stage("steep", BrewStageAction.STEEP),
            stage("filter", BrewStageAction.FILTER),
            stage("dilute", BrewStageAction.SERVE),
            stage("clean", BrewStageAction.CLEAN_UP),
        )
        val plan = CompiledStagePlan(StagePlanId(if (timerOnly) "cold_refrigerated_timer" else "cold_refrigerated_guide"),
            1, definitions.mapIndexed { index, definition ->
                CompiledBrewStage(StageInstanceId(definition.id, index + 1), definition)
            })
        val steep = plan.stages.single { it.definition.action == BrewStageAction.STEEP }.instanceId
        return calculatorRequest.copy(
            recipe = calculatorRequest.recipe.copy(temperatureC = null),
            stagePlan = plan,
            physicalClock = PhysicalBrewClock(steep, originKnown = originKnown,
                configuredOriginWallClockMillis = originWallClockMillis, reminderDurationMillis = durationMillis,
                timerOnly = timerOnly, endStageId = steep),
        )
    }

    private fun stage(id: String, action: BrewStageAction): BrewStageDefinition = BrewStageDefinition(
        StageId(CONTENT_PREFIX + id), action, StageContentId(CONTENT_PREFIX + id),
        completionMode = StageCompletionMode.Manual,
        safetyMessages = listOf(StageSafetyMessage("food_refrigerate_4c_during_steep", StageSafetySeverity.WARNING)),
    )

    private fun mass(id: String, role: QuantityRole, grams: Double) = StageMassTarget(
        StageTargetId("cold_$id"), role, StageMassReference.RECIPE_TOTAL, StageTargetQualifier.EXACT, grams)
}
