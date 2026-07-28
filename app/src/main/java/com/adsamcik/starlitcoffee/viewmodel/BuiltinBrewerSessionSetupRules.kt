package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BrewQuantities
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.StageConditionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanSelections
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRepeatId

/**
 * Applies setup rules in their user-facing issue order and derives the
 * legacy-only compiler inputs from a validated setup.
 */
internal object BuiltinBrewerSessionSetupRules {

    fun validate(
        input: BuiltinBrewerSessionStartInput,
        defaults: BrewerProfileRecipeDefaults,
        isExactRecipe: Boolean,
    ): List<BuiltinBrewerSessionStartValidationIssue> {
        val quantities = validateQuantities(input, defaults)
        return buildList {
            addAll(quantities.issues)
            equipmentProfileIssue(input)?.let { issue ->
                addIssue(issue)
            }
            addAll(capacityIssues(input, quantities, isExactRecipe))
            temperatureIssue(input)?.let { issue ->
                addIssue(issue)
            }
            workflowIssue(input, isExactRecipe)?.let { issue ->
                addIssue(issue)
            }
            addAll(cezveIssues(input))
        }
    }

    fun stagePlanSelections(
        input: BuiltinBrewerSessionStartInput,
    ): StagePlanSelections {
        if (input.brewerProfileId != CEZVE_GENERIC) return StagePlanSelections()

        val setup = input.cezveSetup ?: CezveSessionSetup()
        return StagePlanSelections(
            includedConditions = if (setup.includeSugar) {
                setOf(CEZVE_INCLUDE_SUGAR)
            } else {
                emptySet()
            },
            repeatCounts = mapOf(CEZVE_FOAM_RISE_CYCLES to setup.foamRiseCycles),
        )
    }

    fun quantities(
        input: BuiltinBrewerSessionStartInput,
        defaults: BrewerProfileRecipeDefaults,
    ): BrewQuantities {
        val defaultQuantities = requireNotNull(
            defaults.quantitySemantics.defaultQuantities(
                dryCoffeeDoseG = input.dryCoffeeDoseG,
                ratio = defaults.ratio,
            ),
        )
        val override = input.inputWaterG ?: return defaultQuantities
        return when (defaults.quantitySemantics.inputRole) {
            QuantityRole.BREW_WATER_INPUT ->
                defaultQuantities.copy(brewWaterInputG = override)

            QuantityRole.RESERVOIR_INPUT ->
                defaultQuantities.copy(reservoirInputG = override)

            else -> error("Built-in defaults must use a water input role")
        }
    }

    private fun validateQuantities(
        input: BuiltinBrewerSessionStartInput,
        defaults: BrewerProfileRecipeDefaults,
    ): QuantityValidation {
        val hasValidDryCoffeeDose =
            input.dryCoffeeDoseG.isFinite() && input.dryCoffeeDoseG > 0.0
        val resolvedInputWaterG = input.inputWaterG ?: if (hasValidDryCoffeeDose) {
            input.dryCoffeeDoseG * defaults.ratio.waterPerCoffee
        } else {
            null
        }
        val hasValidInputWater = resolvedInputWaterG == null ||
            (resolvedInputWaterG.isFinite() && resolvedInputWaterG > 0.0)
        val issues = buildList {
            if (!hasValidDryCoffeeDose) {
                addIssue(BuiltinBrewerSessionStartValidationCode.INVALID_DRY_COFFEE_DOSE)
            }
            if (!hasValidInputWater) {
                addIssue(BuiltinBrewerSessionStartValidationCode.INVALID_INPUT_WATER)
            }
        }
        return QuantityValidation(
            resolvedInputWaterG = resolvedInputWaterG,
            hasValidInputWater = hasValidInputWater,
            issues = issues,
        )
    }

    private fun equipmentProfileIssue(
        input: BuiltinBrewerSessionStartInput,
    ): BuiltinBrewerSessionStartValidationCode? =
        if (input.equipment.brewerProfileId != input.brewerProfileId) {
            BuiltinBrewerSessionStartValidationCode.EQUIPMENT_PROFILE_MISMATCH
        } else {
            null
        }

    private fun capacityIssues(
        input: BuiltinBrewerSessionStartInput,
        quantities: QuantityValidation,
        isExactRecipe: Boolean,
    ): List<BuiltinBrewerSessionStartValidationIssue> {
        val requiresExplicitCapacity = isExactRecipe ||
            input.brewerProfileId in
            BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds
        val capacityG = input.equipment.capacityOverrideG
        val resolvedInputWaterG = quantities.resolvedInputWaterG
        return when {
            capacityG == null && requiresExplicitCapacity -> listOfIssue(
                BuiltinBrewerSessionStartValidationCode.MISSING_EQUIPMENT_CAPACITY,
            )

            capacityG == null -> emptyList()
            !capacityG.isFinite() || capacityG <= 0.0 -> listOfIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_CAPACITY_OVERRIDE,
            )

            requiresExplicitCapacity &&
                resolvedInputWaterG != null &&
                quantities.hasValidInputWater &&
                resolvedInputWaterG > capacityG -> listOfIssue(
                BuiltinBrewerSessionStartValidationCode
                    .INPUT_WATER_EXCEEDS_EQUIPMENT_CAPACITY,
            )

            else -> emptyList()
        }
    }

    private fun temperatureIssue(
        input: BuiltinBrewerSessionStartInput,
    ): BuiltinBrewerSessionStartValidationCode? =
        if (input.temperatureC != null && input.temperatureC !in 0..100) {
            BuiltinBrewerSessionStartValidationCode.INVALID_TEMPERATURE
        } else {
            null
        }

    private fun workflowIssue(
        input: BuiltinBrewerSessionStartInput,
        isExactRecipe: Boolean,
    ): BuiltinBrewerSessionStartValidationCode? = when (input.brewerProfileId) {
        HARIO_SWITCH -> if (!isExactRecipe && input.harioSwitchWorkflow == null) {
            BuiltinBrewerSessionStartValidationCode.HARIO_SWITCH_WORKFLOW_REQUIRED
        } else {
            null
        }

        else -> if (input.harioSwitchWorkflow != null) {
            BuiltinBrewerSessionStartValidationCode.WORKFLOW_NOT_APPLICABLE
        } else {
            null
        }
    }

    private fun cezveIssues(
        input: BuiltinBrewerSessionStartInput,
    ): List<BuiltinBrewerSessionStartValidationIssue> {
        if (input.brewerProfileId != CEZVE_GENERIC) {
            return if (input.cezveSetup != null) {
                listOfIssue(
                    BuiltinBrewerSessionStartValidationCode.CEZVE_SETUP_NOT_APPLICABLE,
                )
            } else {
                emptyList()
            }
        }

        val setup = input.cezveSetup ?: CezveSessionSetup()
        return buildList {
            if (
                setup.foamRiseCycles !in
                MIN_CEZVE_FOAM_RISE_CYCLES..MAX_CEZVE_FOAM_RISE_CYCLES
            ) {
                addIssue(
                    BuiltinBrewerSessionStartValidationCode
                        .INVALID_CEZVE_FOAM_RISE_CYCLES,
                )
            }
            if (input.equipment.heatSource == HeatSourceClass.NONE) {
                addIssue(
                    BuiltinBrewerSessionStartValidationCode
                        .CEZVE_HEAT_SOURCE_REQUIRED,
                )
            }
        }
    }

    private fun listOfIssue(
        code: BuiltinBrewerSessionStartValidationCode,
    ): List<BuiltinBrewerSessionStartValidationIssue> =
        listOf(BuiltinBrewerSessionStartValidationIssue(code))

    private data class QuantityValidation(
        val resolvedInputWaterG: Double?,
        val hasValidInputWater: Boolean,
        val issues: List<BuiltinBrewerSessionStartValidationIssue>,
    )

    private val HARIO_SWITCH = BrewerProfileId("hario_switch")
    private val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
    private val CEZVE_INCLUDE_SUGAR = StageConditionId("cezve_include_sugar")
    private val CEZVE_FOAM_RISE_CYCLES =
        StageRepeatId("cezve_foam_rise_cycles")

    private const val MIN_CEZVE_FOAM_RISE_CYCLES = 1
    private const val MAX_CEZVE_FOAM_RISE_CYCLES = 2
}

private fun MutableList<BuiltinBrewerSessionStartValidationIssue>.addIssue(
    code: BuiltinBrewerSessionStartValidationCode,
) {
    add(BuiltinBrewerSessionStartValidationIssue(code))
}
