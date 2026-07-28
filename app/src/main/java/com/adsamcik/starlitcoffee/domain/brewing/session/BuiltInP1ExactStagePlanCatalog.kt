package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId

/**
 * Executable, source-faithful stage plans for the mandatory P1 recipe set.
 *
 * The catalog deliberately has no generic fallback. An unknown recipe stays
 * unknown instead of silently receiving a plausible-looking but false plan.
 */
object BuiltInP1ExactStagePlanCatalog {
    private val planSpecs =
        P1ManualGravityPlanSpecs +
            P1ImmersionPlanSpecs +
            P1CezveAndAutomaticPlanSpecs +
            P1PhinPlanSpecs

    private val recipeDefinitions = BuiltInP1RecipeCatalog.recipes.associateBy { it.id }

    init {
        check(planSpecs.map(P1ExactPlanSpec::recipeId).distinct().size == planSpecs.size) {
            "Exact P1 stage-plan recipe IDs must be unique"
        }
        check(planSpecs.map(P1ExactPlanSpec::recipeId).toSet() == recipeDefinitions.keys) {
            "Exact P1 stage plans must cover the canonical recipe catalog one-to-one"
        }
        planSpecs.forEach { plan ->
            check(plan.stages.size == recipeDefinitions.getValue(plan.recipeId).orderedStageCount) {
                "Exact stage count differs from the canonical recipe: ${plan.recipeId.value}"
            }
        }
    }

    val plans: List<BrewStagePlan> = planSpecs.map(::buildStagePlan)

    val recipeIds: Set<BuiltInRecipeId> = planSpecs.mapTo(linkedSetOf(), P1ExactPlanSpec::recipeId)

    private val plansByRecipeId: Map<BuiltInRecipeId, BrewStagePlan> =
        planSpecs.map(P1ExactPlanSpec::recipeId).zip(plans).toMap()

    fun find(recipeId: BuiltInRecipeId): BrewStagePlan? = plansByRecipeId[recipeId]
}

internal data class P1ExactPlanSpec(
    val recipeId: BuiltInRecipeId,
    val stages: List<P1ExactStageSpec>,
)

internal data class P1ExactStageSpec(
    val action: BrewStageAction,
    val completion: P1ExactCompletion = P1ExactCompletion.Manual,
    val timeTargets: List<P1TimeCue> = emptyList(),
    val massTargets: List<P1WaterCue> = emptyList(),
    val temperatureTarget: StageTemperatureTarget? = null,
    val sourceWarning: Boolean = false,
    val visualPriority: P1VisualPriority = P1VisualPriority.OPTIONAL,
)

internal sealed interface P1ExactCompletion {
    data object Manual : P1ExactCompletion

    data object Observation : P1ExactCompletion

    data class Countdown(val durationSeconds: Int) : P1ExactCompletion

    data class CumulativeWater(val targetGrams: Double) : P1ExactCompletion
}

internal data class P1TimeCue(
    val reference: StageTimeReference,
    val qualifier: StageTargetQualifier,
    val minimumSeconds: Int,
    val maximumSeconds: Int = minimumSeconds,
)

internal data class P1WaterCue(
    val reference: StageMassReference,
    val qualifier: StageTargetQualifier,
    val minimumGrams: Double,
    val maximumGrams: Double = minimumGrams,
)

internal enum class P1VisualPriority {
    OPTIONAL,
    MANDATORY,
    SAFETY_CRITICAL,
}

internal fun p1Plan(
    recipeId: String,
    vararg stages: P1ExactStageSpec,
): P1ExactPlanSpec = P1ExactPlanSpec(
    recipeId = BuiltInRecipeId(recipeId),
    stages = stages.toList(),
)

internal fun p1Stage(
    action: BrewStageAction,
    completion: P1ExactCompletion = P1ExactCompletion.Manual,
    timeTargets: List<P1TimeCue> = emptyList(),
    massTargets: List<P1WaterCue> = emptyList(),
    temperatureTarget: StageTemperatureTarget? = null,
    sourceWarning: Boolean = false,
    visualPriority: P1VisualPriority = P1VisualPriority.OPTIONAL,
): P1ExactStageSpec = P1ExactStageSpec(
    action = action,
    completion = completion,
    timeTargets = timeTargets,
    massTargets = massTargets,
    temperatureTarget = temperatureTarget,
    sourceWarning = sourceWarning,
    visualPriority = visualPriority,
)

internal fun countdownCompletion(durationSeconds: Int): P1ExactCompletion =
    P1ExactCompletion.Countdown(durationSeconds)

internal fun cumulativeWaterCompletion(targetGrams: Double): P1ExactCompletion =
    P1ExactCompletion.CumulativeWater(targetGrams)

internal fun timeCue(
    reference: StageTimeReference,
    qualifier: StageTargetQualifier,
    minimumSeconds: Int,
    maximumSeconds: Int = minimumSeconds,
): P1TimeCue = P1TimeCue(reference, qualifier, minimumSeconds, maximumSeconds)

internal fun waterCue(
    reference: StageMassReference,
    qualifier: StageTargetQualifier,
    minimumGrams: Double,
    maximumGrams: Double = minimumGrams,
): P1WaterCue = P1WaterCue(reference, qualifier, minimumGrams, maximumGrams)

internal fun temperatureCue(
    qualifier: StageTargetQualifier,
    minimumC: Double,
    maximumC: Double = minimumC,
): StageTemperatureTarget = StageTemperatureTarget(qualifier, minimumC, maximumC)

private fun buildStagePlan(spec: P1ExactPlanSpec): BrewStagePlan = BrewStagePlan(
    id = StagePlanId("builtin_recipe_${spec.recipeId.value}"),
    version = 1,
    nodes = spec.stages.mapIndexed { index, stage ->
        StagePlanNode.Stage(buildStage(spec.recipeId, stage, index))
    },
)

private fun buildStage(
    recipeId: BuiltInRecipeId,
    spec: P1ExactStageSpec,
    index: Int,
): BrewStageDefinition {
    val sourceStageId = "stage_${(index + 1).toString().padStart(2, '0')}"
    val identity = "p1_${recipeId.value}_$sourceStageId"
    return BrewStageDefinition(
        id = StageId(identity),
        action = spec.action,
        contentId = StageContentId("${identity}_instruction"),
        instructionAssetId = InstructionAssetId("instruction_${identity}_instruction_default"),
        requiresIllustration = spec.visualPriority != P1VisualPriority.OPTIONAL,
        safetyMessages = safetyMessages(identity, spec),
        equipmentRequirement = StageEquipmentRequirement(
            StageEquipmentStateId("p1_equipment_${recipeId.value}_$sourceStageId"),
        ),
        completionMode = completionMode(identity, spec.completion),
        referenceTargets = StageReferenceTargets(
            timeTargets = spec.timeTargets.mapIndexed { targetIndex, cue ->
                cue.toTarget(identity, targetIndex)
            },
            massTargets = spec.massTargets.mapIndexed { targetIndex, cue ->
                cue.toTarget(identity, targetIndex)
            },
            temperatureTarget = spec.temperatureTarget,
        ),
    )
}

private fun completionMode(
    identity: String,
    completion: P1ExactCompletion,
): StageCompletionMode = when (completion) {
    P1ExactCompletion.Manual -> StageCompletionMode.Manual
    P1ExactCompletion.Observation -> StageCompletionMode.ObservedEvent(
        StageObservationId("p1_obs_${identity.removePrefix("p1_")}"),
    )
    is P1ExactCompletion.Countdown -> StageCompletionMode.Countdown(
        completion.durationSeconds * MILLIS_PER_SECOND,
    )
    is P1ExactCompletion.CumulativeWater -> StageCompletionMode.CumulativeAmount(
        completion.targetGrams,
    )
}

private fun safetyMessages(
    identity: String,
    spec: P1ExactStageSpec,
): List<StageSafetyMessage> {
    val severity = when {
        spec.visualPriority == P1VisualPriority.SAFETY_CRITICAL -> StageSafetySeverity.CRITICAL
        spec.sourceWarning -> StageSafetySeverity.WARNING
        else -> null
    }
    return severity?.let {
        listOf(StageSafetyMessage(code = "p1_warning_${identity.removePrefix("p1_")}", severity = it))
    }.orEmpty()
}

private fun P1TimeCue.toTarget(identity: String, index: Int): StageTimeTarget = StageTimeTarget(
    reference = reference,
    id = StageTargetId("p1_target_${identity.removePrefix("p1_")}_time_${index + 1}"),
    qualifier = qualifier,
    minimumMillis = minimumSeconds * MILLIS_PER_SECOND,
    maximumMillis = maximumSeconds * MILLIS_PER_SECOND,
)

private fun P1WaterCue.toTarget(identity: String, index: Int): StageMassTarget = StageMassTarget(
    id = StageTargetId("p1_target_${identity.removePrefix("p1_")}_mass_${index + 1}"),
    role = QuantityRole.BREW_WATER_INPUT,
    reference = reference,
    qualifier = qualifier,
    minimumGrams = minimumGrams,
    maximumGrams = maximumGrams,
)

private const val MILLIS_PER_SECOND = 1_000L
