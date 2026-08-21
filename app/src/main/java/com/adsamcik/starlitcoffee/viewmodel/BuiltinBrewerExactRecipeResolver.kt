package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewQuantities
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureBasis
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.RatioDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import kotlin.math.abs

internal sealed interface ExactRecipeResolution {
    data class Resolved(
        val input: BuiltinBrewerSessionStartInput,
        val quantities: BrewQuantities,
        val ratioDefinition: RatioDefinition,
        val ratioValue: Double,
    ) : ExactRecipeResolution

    data class Invalid(
        val issues: List<BuiltinBrewerSessionStartValidationIssue>,
    ) : ExactRecipeResolution

    data class Unavailable(
        val reason: BuiltinBrewerSessionStartUnavailableReason,
    ) : ExactRecipeResolution
}

/**
 * Owns the fail-closed contract for exact built-in recipes.
 *
 * Validation is intentionally ordered: dose, primary input, equipment,
 * temperature, then recipe-owned workflow.
 */
internal class BuiltinBrewerExactRecipeResolver {

    fun resolve(
        input: BuiltinBrewerSessionStartInput,
        definition: BuiltInP1RecipeDefinition,
    ): ExactRecipeResolution {
        if (!hasSupportedRecipeIdentity(definition)) {
            return catalogMismatch()
        }

        val primaryRatio = definition.ratios.first()
        if (
            primaryRatio.definition.numerator != QuantityRole.DRY_COFFEE_DOSE ||
            primaryRatio.definition.denominator !in PRIMARY_INPUT_ROLES ||
            !hasValidCanonicalQuantities(definition)
        ) {
            return catalogMismatch()
        }

        val primaryInput = resolvePrimaryInput(
            suppliedInputG = input.inputWaterG,
            canonicalInputG = definition.quantities.valueFor(
                primaryRatio.definition.denominator,
            ),
        )
        val issues = buildList {
            exactDoseIssue(input.dryCoffeeDoseG, definition)?.let { issue ->
                addIssue(issue)
            }
            primaryInput.issue?.let { issue ->
                addIssue(issue)
            }
            if (!equipmentMatches(input.equipment, definition)) {
                addIssue(
                    BuiltinBrewerSessionStartValidationCode
                        .BUILTIN_RECIPE_EQUIPMENT_MISMATCH,
                )
            }
            exactTemperatureIssue(input.temperatureC, definition)?.let { issue ->
                addIssue(issue)
            }
            exactWorkflowIssue(input, definition)?.let { issue ->
                addIssue(issue)
            }
        }
        if (issues.isNotEmpty()) {
            return ExactRecipeResolution.Invalid(issues)
        }

        val primaryInputG = requireNotNull(primaryInput.value)
        val quantities = definition.quantities.withValue(
            role = primaryRatio.definition.denominator,
            value = primaryInputG,
        )
        val ratioValue = primaryRatio.ratioValue
            ?: primaryInputG / definition.quantities.dryCoffeeDoseG
        return ExactRecipeResolution.Resolved(
            input = input.copy(inputWaterG = primaryInputG),
            quantities = quantities,
            ratioDefinition = primaryRatio.definition,
            ratioValue = ratioValue,
        )
    }

    fun sourcePlanMatches(
        plan: BrewStagePlan,
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = plan.id.value == exactStagePlanId(definition) &&
        plan.version == EXACT_STAGE_PLAN_VERSION &&
        plan.nodes.size == definition.orderedStageCount &&
        plan.nodes.withIndex().all { (index, node) ->
            node is StagePlanNode.Stage &&
                node.definition.hasExactIdentity(definition, index)
        }

    fun compiledPlanMatches(
        plan: CompiledStagePlan,
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = plan.id.value == exactStagePlanId(definition) &&
        plan.version == EXACT_STAGE_PLAN_VERSION &&
        plan.stages.size == definition.orderedStageCount &&
        plan.stages.withIndex().all { (index, stage) ->
            stage.definition.hasExactIdentity(definition, index)
        }

    private fun hasSupportedRecipeIdentity(
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = when (definition.brewerProfileId) {
        HARIO_SWITCH -> definition.id in HARIO_SWITCH_RECIPE_IDS
        CEZVE_GENERIC -> definition.id in CEZVE_RECIPE_IDS
        else -> true
    }

    private fun catalogMismatch(): ExactRecipeResolution.Unavailable =
        ExactRecipeResolution.Unavailable(
            BuiltinBrewerSessionStartUnavailableReason
                .BUILTIN_RECIPE_CATALOG_MISMATCH,
        )

    private fun exactDoseIssue(
        suppliedDoseG: Double,
        definition: BuiltInP1RecipeDefinition,
    ): BuiltinBrewerSessionStartValidationCode? = when {
        !suppliedDoseG.isFinite() || suppliedDoseG <= 0.0 ->
            BuiltinBrewerSessionStartValidationCode.INVALID_DRY_COFFEE_DOSE

        !suppliedDoseG.sameQuantityAs(definition.quantities.dryCoffeeDoseG) ->
            BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_DOSE_MISMATCH

        else -> null
    }

    private fun resolvePrimaryInput(
        suppliedInputG: Double?,
        canonicalInputG: Double?,
    ): ExactPrimaryInputResolution = when {
        suppliedInputG != null && (!suppliedInputG.isFinite() || suppliedInputG <= 0.0) ->
            ExactPrimaryInputResolution(
                issue = BuiltinBrewerSessionStartValidationCode.INVALID_INPUT_WATER,
            )

        canonicalInputG != null -> ExactPrimaryInputResolution(
            value = canonicalInputG,
            issue = if (
                suppliedInputG != null &&
                !suppliedInputG.sameQuantityAs(canonicalInputG)
            ) {
                BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_MISMATCH
            } else {
                null
            },
        )

        suppliedInputG == null -> ExactPrimaryInputResolution(
            issue = BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_REQUIRED,
        )

        else -> ExactPrimaryInputResolution(value = suppliedInputG)
    }

    private fun hasValidCanonicalQuantities(
        definition: BuiltInP1RecipeDefinition,
    ): Boolean {
        val quantities = definition.quantities
        val resolvedQuantities = listOfNotNull(
            quantities.brewWaterInputG,
            quantities.reservoirInputG,
            quantities.targetBeverageYieldG,
            quantities.targetConcentrateYieldG,
            quantities.finalServedBeverageG,
            quantities.measuredOutputG,
            quantities.iceG,
            quantities.bypassWaterG,
            quantities.dilutionWaterG,
        )
        return quantities.dryCoffeeDoseG.isFinite() &&
            quantities.dryCoffeeDoseG > 0.0 &&
            resolvedQuantities.all { value -> value.isFinite() && value >= 0.0 } &&
            definition.ratios.all { ratio ->
                ratio.ratioValue == null || ratio.ratioValue.isFinite()
            }
    }

    private fun equipmentMatches(
        equipment: EquipmentConfiguration,
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = definition.equipmentOptions.any { option ->
        equipment.filterSelection.matches(option.filterSelection) &&
            equipment.basketId == option.basketId &&
            equipment.accessoryIds == option.accessoryIds
    }

    private fun FilterSelection.matches(sourceSelection: FilterSelection): Boolean =
        when {
            this is FilterSelection.Stack && sourceSelection is FilterSelection.Stack -> {
                entries.sortedBy { entry -> entry.position } ==
                    sourceSelection.entries.sortedBy { entry -> entry.position }
            }

            else -> this == sourceSelection
        }

    private fun exactTemperatureIssue(
        temperatureC: Int?,
        definition: BuiltInP1RecipeDefinition,
    ): BuiltinBrewerSessionStartValidationCode? {
        if (temperatureC == null || temperatureC !in 0..100) return null

        val temperature = temperatureC.toDouble()
        val semantics = definition.temperature
        return when (semantics.basis) {
            P1TemperatureBasis.USER_EXACT -> mismatchUnless(
                temperature.sameQuantityAs(requireNotNull(semantics.minimumC)),
            )

            P1TemperatureBasis.USER_RANGE,
            P1TemperatureBasis.USER_APPROXIMATE_RANGE,
            P1TemperatureBasis.USER_STARTING_RANGE,
            P1TemperatureBasis.MACHINE_CONTROLLED_REPORTED_RANGE,
            -> mismatchUnless(
                temperature + EXACT_QUANTITY_TOLERANCE >=
                    requireNotNull(semantics.minimumC) &&
                    temperature - EXACT_QUANTITY_TOLERANCE <=
                    requireNotNull(semantics.maximumC),
            )

            P1TemperatureBasis.HOT_UNSPECIFIED,
            P1TemperatureBasis.MACHINE_CONTROLLED,
            P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED,
            -> BuiltinBrewerSessionStartValidationCode
                .BUILTIN_RECIPE_TEMPERATURE_NOT_APPLICABLE
        }
    }

    private fun mismatchUnless(
        matches: Boolean,
    ): BuiltinBrewerSessionStartValidationCode? = if (matches) {
        null
    } else {
        BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_MISMATCH
    }

    private fun exactWorkflowIssue(
        input: BuiltinBrewerSessionStartInput,
        definition: BuiltInP1RecipeDefinition,
    ): BuiltinBrewerSessionStartValidationCode? {
        val hasHarioMismatch = definition.brewerProfileId == HARIO_SWITCH &&
            input.harioSwitchWorkflow != null &&
            input.harioSwitchWorkflow != expectedHarioWorkflow(definition.id)
        val hasCezveMismatch = expectedRiseCycles(definition.id)?.let { expected ->
            input.cezveSetup?.foamRiseCycles?.let { supplied -> supplied != expected }
        } == true
        return if (hasHarioMismatch || hasCezveMismatch) {
            BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_WORKFLOW_MISMATCH
        } else {
            null
        }
    }

    private fun expectedHarioWorkflow(
        recipeId: BuiltInRecipeId,
    ): HarioSwitchWorkflow? = when (recipeId) {
        SWITCH_OFFICIAL_RECIPE -> HarioSwitchWorkflow.STEEP_AND_RELEASE
        SWITCH_GRAVITY_RECIPE -> HarioSwitchWorkflow.MANUAL_GRAVITY
        else -> null
    }

    private fun expectedRiseCycles(recipeId: BuiltInRecipeId): Int? = when (recipeId) {
        CEZVE_SINGLE_RISE_RECIPE -> 1
        CEZVE_REPEATED_RISE_RECIPE -> 2
        else -> null
    }

    private fun BrewQuantities.withValue(
        role: QuantityRole,
        value: Double,
    ): BrewQuantities = when (role) {
        QuantityRole.BREW_WATER_INPUT -> copy(brewWaterInputG = value)
        QuantityRole.RESERVOIR_INPUT -> copy(reservoirInputG = value)
        else -> error("Exact built-in recipes require a water input denominator")
    }

    private fun Double.sameQuantityAs(other: Double): Boolean =
        abs(this - other) <= EXACT_QUANTITY_TOLERANCE

    private fun BrewStageDefinition.hasExactIdentity(
        definition: BuiltInP1RecipeDefinition,
        index: Int,
    ): Boolean {
        val sourceStageId = "stage_${(index + 1).toString().padStart(2, '0')}"
        val identity = "p1_${definition.id.value}_$sourceStageId"
        return id.value == identity && contentId.value == "${identity}_instruction"
    }

    private fun exactStagePlanId(definition: BuiltInP1RecipeDefinition): String =
        EXACT_RECIPE_PLAN_ID_PREFIX + definition.id.value

    private data class ExactPrimaryInputResolution(
        val value: Double? = null,
        val issue: BuiltinBrewerSessionStartValidationCode? = null,
    )

    private companion object {
        const val EXACT_RECIPE_PLAN_ID_PREFIX = "builtin_recipe_"
        const val EXACT_STAGE_PLAN_VERSION = 2
        const val EXACT_QUANTITY_TOLERANCE = 1e-6

        val HARIO_SWITCH = BrewerProfileId("hario_switch")
        val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
        val SWITCH_OFFICIAL_RECIPE = BuiltInRecipeId("switch_official_20_240")
        val SWITCH_HYBRID_RECIPE =
            BuiltInRecipeId("switch_ole_boen_hybrid_16_5_240")
        val SWITCH_GRAVITY_RECIPE = BuiltInRecipeId("switch_gravity_15_250")
        val CEZVE_SINGLE_RISE_RECIPE =
            BuiltInRecipeId("cezve_turkish_single_rise_6_65")
        val CEZVE_REPEATED_RISE_RECIPE =
            BuiltInRecipeId("cezve_bounded_repeated_rise_12_130")
        val HARIO_SWITCH_RECIPE_IDS = setOf(
            SWITCH_OFFICIAL_RECIPE,
            SWITCH_HYBRID_RECIPE,
            SWITCH_GRAVITY_RECIPE,
        )
        val CEZVE_RECIPE_IDS = setOf(
            CEZVE_SINGLE_RISE_RECIPE,
            CEZVE_REPEATED_RISE_RECIPE,
        )
        val PRIMARY_INPUT_ROLES = setOf(
            QuantityRole.BREW_WATER_INPUT,
            QuantityRole.RESERVOIR_INPUT,
        )
    }
}

private fun MutableList<BuiltinBrewerSessionStartValidationIssue>.addIssue(
    code: BuiltinBrewerSessionStartValidationCode,
) {
    add(BuiltinBrewerSessionStartValidationIssue(code))
}
