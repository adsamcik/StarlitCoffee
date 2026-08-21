package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTemperatureTarget

/**
 * Exact-Learn input that keeps authored teaching copy and executable recipe
 * facts together. Generic guidance presentation density is not an execution
 * source and therefore cannot erase these values.
 */
data class P1ExactLearnGuide(
    val recipe: BuiltInP1RecipeDefinition,
    val guidance: P1ExactRecipeGuidance,
    val stageFactsByContentId: Map<StageContentId, P1ExactLearnStageFacts>,
)

data class P1ExactLearnStageFacts(
    val startCondition: String?,
    val timing: String?,
    val addedWater: String?,
    val cumulativeWater: String?,
    val beverageYield: String?,
    val temperatureTarget: StageTemperatureTarget?,
    val equipmentState: String,
)

object P1ExactLearnGuideFactory {
    fun create(
        recipe: BuiltInP1RecipeDefinition,
        guidance: P1ExactRecipeGuidance,
        plan: BrewStagePlan,
    ): P1ExactLearnGuide {
        val planStages = plan.nodes.map { node ->
            require(node is StagePlanNode.Stage) { "Exact Learn requires a direct ordered plan" }
            node.definition
        }
        require(guidance.recipeId == recipe.id)
        require(guidance.stages.size == planStages.size)

        val facts = guidance.stages.zip(planStages).associate { (stage, definition) ->
            require(stage.stageId == definition.id)
            require(stage.contentId == definition.contentId)
            stage.contentId to P1ExactLearnStageFacts(
                startCondition = stage.startTimeOrPrecedingCondition.meaningfulSourceValue(
                    "After the preceding stage",
                    "—",
                ),
                timing = stage.targetDurationOrRange.meaningfulSourceValue("Condition-dependent", "—"),
                addedWater = stage.addedWaterTarget.meaningfulSourceValue("None", "—"),
                cumulativeWater = stage.cumulativeWaterTarget.meaningfulSourceValue("None", "—"),
                beverageYield = stage.beverageYieldTarget.meaningfulSourceValue(
                    "None",
                    "not applicable or not specified",
                    "—",
                ),
                temperatureTarget = definition.referenceTargets.temperatureTarget,
                equipmentState = stage.equipmentState,
            )
        }
        return P1ExactLearnGuide(recipe, guidance, facts)
    }

    private fun String.meaningfulSourceValue(vararg absentValues: String): String? =
        trim().takeIf { value -> value.isNotEmpty() && absentValues.none(value::equals) }
}
