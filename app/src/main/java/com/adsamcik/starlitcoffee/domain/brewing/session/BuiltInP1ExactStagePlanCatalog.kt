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
    visualPriority: P1VisualPriority = P1VisualPriority.OPTIONAL,
): P1ExactStageSpec = P1ExactStageSpec(
    action = action,
    completion = completion,
    timeTargets = timeTargets,
    massTargets = massTargets,
    temperatureTarget = temperatureTarget,
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
    version = 2,
    nodes = spec.stages.mapIndexed { index, stage ->
        StagePlanNode.Stage(
            buildStage(
                recipeId = spec.recipeId,
                spec = stage,
                nextSpec = spec.stages.getOrNull(index + 1),
                index = index,
            ),
        )
    },
)

private fun buildStage(
    recipeId: BuiltInRecipeId,
    spec: P1ExactStageSpec,
    nextSpec: P1ExactStageSpec?,
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
        safetyMessages = P1ExactStageSafetyCatalog.messagesFor(identity),
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
        advanceConstraint = advanceConstraint(spec, nextSpec),
    )
}

private fun advanceConstraint(
    spec: P1ExactStageSpec,
    nextSpec: P1ExactStageSpec?,
): StageAdvanceConstraint {
    val notBeforeStageElapsedMillis = spec.timeTargets
        .filter { cue ->
            cue.reference == StageTimeReference.STAGE_DURATION && cue.qualifier in STAGE_MINIMUM_QUALIFIERS
        }
        .maxOfOrNull(P1TimeCue::minimumSeconds)
        ?.times(MILLIS_PER_SECOND)
    val currentBrewBoundarySeconds = spec.timeTargets
        .filter { cue ->
            cue.reference == StageTimeReference.BREW_ELAPSED_AT_COMPLETION &&
                cue.qualifier in BREW_MINIMUM_QUALIFIERS
        }
        .maxOfOrNull(P1TimeCue::minimumSeconds)
    val nextBrewBoundarySeconds = nextSpec?.timeTargets
        ?.filter { cue ->
            cue.reference == StageTimeReference.BREW_ELAPSED_AT_START &&
                cue.qualifier in BREW_MINIMUM_QUALIFIERS
        }
        ?.maxOfOrNull(P1TimeCue::minimumSeconds)
    val notBeforeBrewElapsedMillis = listOfNotNull(
        currentBrewBoundarySeconds,
        nextBrewBoundarySeconds,
    ).maxOrNull()?.times(MILLIS_PER_SECOND)

    return StageAdvanceConstraint(
        notBeforeStageElapsedMillis = notBeforeStageElapsedMillis,
        notBeforeBrewElapsedMillis = notBeforeBrewElapsedMillis,
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
private val STAGE_MINIMUM_QUALIFIERS = setOf(
    StageTargetQualifier.EXACT,
    StageTargetQualifier.APPROXIMATE,
    StageTargetQualifier.RANGE,
    StageTargetQualifier.NO_EARLIER_THAN,
)
private val BREW_MINIMUM_QUALIFIERS = setOf(
    StageTargetQualifier.EXACT,
    StageTargetQualifier.RANGE,
    StageTargetQualifier.NO_EARLIER_THAN,
)

/** Safety is authored independently from illustration-review importance. */
private object P1ExactStageSafetyCatalog {
    private val messagesByStageId: Map<String, StageSafetyMessage> = buildMap {
        critical("filter_air_channel_hot_liquid_overflow", "p1_chemex_42_700_stage_01")
        critical(
            "stable_server_hot_liquid",
            "p1_clever_water_first_15_250_stage_05",
            "p1_clever_coffee_first_15_250_stage_04",
        )
        critical("hot_metal_valve", "p1_switch_official_20_240_stage_04")

        critical("cezve_overflow", "p1_cezve_turkish_single_rise_6_65_stage_01")
        critical("open_flame_unattended_hot_metal", "p1_cezve_turkish_single_rise_6_65_stage_03")
        critical("cezve_boil_over_hot_liquid", "p1_cezve_turkish_single_rise_6_65_stage_04")
        warning(
            "hot_liquid",
            "p1_cezve_turkish_single_rise_6_65_stage_05",
            "p1_cezve_turkish_single_rise_6_65_stage_06",
        )

        critical("cezve_overflow", "p1_cezve_bounded_repeated_rise_12_130_stage_01")
        critical("cezve_boil_over_hot_liquid", "p1_cezve_bounded_repeated_rise_12_130_stage_03")
        warning("hot_metal", "p1_cezve_bounded_repeated_rise_12_130_stage_04")
        critical("cezve_boil_over_hot_liquid", "p1_cezve_bounded_repeated_rise_12_130_stage_05")
        warning("hot_liquid", "p1_cezve_bounded_repeated_rise_12_130_stage_06")

        critical(
            "basket_overflow",
            "p1_auto_batch_500_30_stage_01",
            "p1_auto_batch_1000_60_stage_01",
        )
        warning("reservoir_overflow", "p1_auto_batch_500_30_stage_02")
        critical("hot_liquid_machine_cycle", "p1_auto_batch_500_30_stage_03")
        warning(
            "hot_glass_hotplate",
            "p1_auto_batch_500_30_stage_05",
            "p1_auto_batch_1000_60_stage_04",
        )

        critical("power_off_unplug_outlet_overflow", "p1_auto_cupone_20_300_stage_01")
        critical(
            "hot_outlet_machine_cycle",
            "p1_auto_cupone_20_300_stage_03",
            "p1_auto_cupone_20_300_stage_04",
        )
        warning("hot_outlet_hot_liquid", "p1_auto_cupone_20_300_stage_05")
        critical("power_off_unplug_hot_outlet_electrical", "p1_auto_cupone_20_300_stage_06")

        critical("stable_cup_hot_liquid", "p1_phin_gravity_14_118_stage_01")
        warning(
            "gravity_brewer_no_pressure",
            "p1_phin_gravity_14_118_stage_02",
            "p1_phin_gravity_14_118_stage_05",
        )
        warning("hot_metal", "p1_phin_gravity_14_118_stage_07")
        critical("stable_cup_hot_metal", "p1_phin_screw_18_120_stage_01")
        critical("gravity_brewer_no_pressure", "p1_phin_screw_18_120_stage_02")
        critical("hot_metal_gravity_brewer_no_pressure", "p1_phin_screw_18_120_stage_05")
        warning("hot_metal", "p1_phin_screw_18_120_stage_06")
    }

    fun messagesFor(stageId: String): List<StageSafetyMessage> =
        listOfNotNull(messagesByStageId[stageId])

    private fun MutableMap<String, StageSafetyMessage>.critical(code: String, vararg stageIds: String) {
        add(code, StageSafetySeverity.CRITICAL, stageIds)
    }

    private fun MutableMap<String, StageSafetyMessage>.warning(code: String, vararg stageIds: String) {
        add(code, StageSafetySeverity.WARNING, stageIds)
    }

    private fun MutableMap<String, StageSafetyMessage>.add(
        code: String,
        severity: StageSafetySeverity,
        stageIds: Array<out String>,
    ) {
        stageIds.forEach { stageId ->
            check(put(stageId, StageSafetyMessage(code, severity)) == null) {
                "Duplicate exact-stage safety mapping: $stageId"
            }
        }
    }
}
