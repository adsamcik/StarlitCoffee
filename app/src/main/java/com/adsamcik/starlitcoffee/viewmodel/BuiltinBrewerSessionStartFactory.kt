package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterSelectionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterStackEntrySnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RecipeTechniqueSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BrewQuantities
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CompatibilitySeverity
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityIssue
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityResult
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityValidator
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.OutputModel
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageConditionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompileResult
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompiler
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanSelections
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanValidationIssue
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRepeatId
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import java.util.UUID

/**
 * The user-controlled optional section and bounded repeat for a Cezve plan.
 *
 * This deliberately exposes only the meaningful P1 choices instead of the
 * generic compiler selections, so a caller cannot accidentally enable a
 * branch belonging to a different brewer.
 */
data class CezveSessionSetup(
    val includeSugar: Boolean = false,
    val foamRiseCycles: Int = 1,
)

/**
 * A pure request to begin one of the built-in P1 brewer sessions.
 *
 * [inputWaterG] represents brew water for manual/immersion/heated profiles
 * and reservoir water for automatic brewers. When omitted, the profile's
 * conservative default ratio supplies the correct input quantity. The public
 * name intentionally avoids calling reservoir water "brew water".
 */
data class BuiltinBrewerSessionStartInput(
    val brewerProfileId: BrewerProfileId,
    val dryCoffeeDoseG: Double,
    val inputWaterG: Double? = null,
    val equipment: EquipmentConfiguration = EquipmentConfiguration(brewerProfileId),
    val harioSwitchWorkflow: HarioSwitchWorkflow? = null,
    val cezveSetup: CezveSessionSetup? = null,
    val temperatureC: Int? = null,
    val grinderId: String? = null,
    val grindSetting: String? = null,
    val methodLabel: String? = null,
    val filterLabel: String? = null,
    val isDecaf: Boolean = false,
    val notes: String? = null,
    val coffeeBagId: Long? = null,
    val sourceRecipeId: Long? = null,
)

enum class BuiltinBrewerSessionStartUnavailableReason {
    UNKNOWN_BREWER_PROFILE,
    MISSING_PROFILE_DEFAULTS,
    PROFILE_DEFAULTS_OUTPUT_MISMATCH,
    NO_BUILTIN_STAGE_PLAN,
}

enum class BuiltinBrewerSessionStartValidationCode {
    INVALID_DRY_COFFEE_DOSE,
    INVALID_INPUT_WATER,
    INVALID_CAPACITY_OVERRIDE,
    INVALID_TEMPERATURE,
    EQUIPMENT_PROFILE_MISMATCH,
    HARIO_SWITCH_WORKFLOW_REQUIRED,
    WORKFLOW_NOT_APPLICABLE,
    CEZVE_SETUP_NOT_APPLICABLE,
    INVALID_CEZVE_FOAM_RISE_CYCLES,
    INCOMPATIBLE_EQUIPMENT,
}

/** A structured setup issue safe for UI mapping without parsing exception text. */
data class BuiltinBrewerSessionStartValidationIssue(
    val code: BuiltinBrewerSessionStartValidationCode,
    val equipmentIssues: List<EquipmentCompatibilityIssue> = emptyList(),
)

/** The start boundary never substitutes a nearby profile, workflow, or plan. */
sealed interface BuiltinBrewerSessionStartResult {
    data class Ready(
        val request: BrewSessionStartRequest,
        val brewerProfile: BrewerProfile,
        val defaults: BrewerProfileRecipeDefaults,
        /** Non-blocking safety and advice remain available for the setup UI. */
        val equipmentCompatibility: EquipmentCompatibilityResult,
    ) : BuiltinBrewerSessionStartResult

    data class Unavailable(
        val brewerProfileId: BrewerProfileId,
        val reason: BuiltinBrewerSessionStartUnavailableReason,
    ) : BuiltinBrewerSessionStartResult

    data class InvalidSetup(
        val brewerProfileId: BrewerProfileId,
        val issues: List<BuiltinBrewerSessionStartValidationIssue>,
    ) : BuiltinBrewerSessionStartResult

    data class InvalidStagePlan(
        val brewerProfileId: BrewerProfileId,
        val issues: List<StagePlanValidationIssue>,
    ) : BuiltinBrewerSessionStartResult
}

/**
 * Builds a complete immutable durable-session request for a built-in P1
 * profile. It owns the profile/default/plan boundary but performs no I/O,
 * navigation, persistence, or clock access.
 *
 * Hario Switch has no implicit workflow: callers must explicitly choose its
 * immersion-release or manual-gravity plan. Other profiles reject that choice
 * instead of ignoring it. Cezve's two bounded plan decisions are likewise
 * validated before compilation.
 */
class BuiltinBrewerSessionStartFactory(
    private val catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
    private val defaultsFor: (BrewerProfileId) -> BrewerProfileRecipeDefaults? =
        BuiltinBrewerProfileRecipeDefaults::find,
    private val stagePlanFor: (BrewerProfileId, HarioSwitchWorkflow?) -> BrewStagePlan? =
        BuiltinBrewerStagePlanFactory::create,
    private val compileStagePlan: (BrewStagePlan, StagePlanSelections) -> StagePlanCompileResult =
        { plan, selections -> StagePlanCompiler.compile(plan, selections) },
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    private val compatibilityValidator = EquipmentCompatibilityValidator(catalog)

    fun create(input: BuiltinBrewerSessionStartInput): BuiltinBrewerSessionStartResult {
        val profile = catalog.findBrewerProfile(input.brewerProfileId)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BREWER_PROFILE)
        val defaults = defaultsFor(profile.id)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.MISSING_PROFILE_DEFAULTS)
        if (profile.outputModel != defaults.quantitySemantics.outputModel) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.PROFILE_DEFAULTS_OUTPUT_MISMATCH,
            )
        }

        val setupIssues = validateSetup(input)
        if (setupIssues.isNotEmpty()) {
            return BuiltinBrewerSessionStartResult.InvalidSetup(input.brewerProfileId, setupIssues)
        }

        val compatibility = compatibilityValidator.validate(input.equipment)
        val blockingCompatibilityIssues = compatibility.issues.filter {
            it.severity == CompatibilitySeverity.BLOCKING
        }
        if (blockingCompatibilityIssues.isNotEmpty()) {
            return BuiltinBrewerSessionStartResult.InvalidSetup(
                brewerProfileId = input.brewerProfileId,
                issues = listOf(
                    BuiltinBrewerSessionStartValidationIssue(
                        code = BuiltinBrewerSessionStartValidationCode.INCOMPATIBLE_EQUIPMENT,
                        equipmentIssues = blockingCompatibilityIssues,
                    ),
                ),
            )
        }

        val sourcePlan = stagePlanFor(profile.id, input.harioSwitchWorkflow)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.NO_BUILTIN_STAGE_PLAN)
        val compiledPlan = when (val result = compileStagePlan(sourcePlan, selectionsFor(input))) {
            is StagePlanCompileResult.Compiled -> result.value
            is StagePlanCompileResult.Invalid -> {
                return BuiltinBrewerSessionStartResult.InvalidStagePlan(profile.id, result.issues)
            }
        }
        val quantities = quantities(input, defaults)
        val inputWaterG = requireNotNull(quantities.valueFor(defaults.quantitySemantics.inputRole))
        val ratioValue = inputWaterG / quantities.dryCoffeeDoseG
        val recipe = BrewRecipeSnapshotV1(
            methodFamilyId = profile.familyId.value,
            brewerProfileId = profile.id.value,
            equipment = input.equipment.toSnapshot(),
            quantities = quantities.toSnapshot(),
            ratioDefinition = RatioDefinitionSnapshotV1(
                numerator = defaults.quantitySemantics.ratioDefinition.numerator.name,
                denominator = defaults.quantitySemantics.ratioDefinition.denominator.name,
            ),
            ratioValue = ratioValue,
            temperatureC = input.temperatureC,
            grinderId = input.grinderId.normalizedOrNull(),
            grindSetting = input.grindSetting.normalizedOrNull(),
            technique = RecipeTechniqueSnapshotV1(
                stagePlanVariantId = compiledPlan.id.value,
            ),
            isDecaf = input.isDecaf,
            notes = input.notes.normalizedOrNull(),
            outputModel = defaults.quantitySemantics.outputModel.toSnapshot(),
        )
        return BuiltinBrewerSessionStartResult.Ready(
            request = BrewSessionStartRequest(
                sessionId = SessionId(newUuid().toString()),
                recipe = recipe,
                stagePlan = compiledPlan,
                executionContext = SessionExecutionContextSnapshotV1(
                    coffeeBagId = input.coffeeBagId,
                    sourceRecipeId = input.sourceRecipeId,
                    logPresentation = BrewLogPresentationContextSnapshotV1(
                        methodLabel = input.methodLabel.normalizedOrNull() ?: profile.displayName,
                        doseG = quantities.dryCoffeeDoseG,
                        waterG = inputWaterG,
                        ratio = ratioValue,
                        grindLabel = input.grindSetting.normalizedOrNull(),
                        filterLabel = input.filterLabel.normalizedOrNull()
                            ?: input.equipment.filterSelection.logLabel(),
                        isDecaf = input.isDecaf,
                        notes = input.notes.normalizedOrNull(),
                    ),
                ),
            ),
            brewerProfile = profile,
            defaults = defaults,
            equipmentCompatibility = compatibility,
        )
    }

    private fun unavailable(
        input: BuiltinBrewerSessionStartInput,
        reason: BuiltinBrewerSessionStartUnavailableReason,
    ): BuiltinBrewerSessionStartResult.Unavailable = BuiltinBrewerSessionStartResult.Unavailable(
        brewerProfileId = input.brewerProfileId,
        reason = reason,
    )

    private fun validateSetup(
        input: BuiltinBrewerSessionStartInput,
    ): List<BuiltinBrewerSessionStartValidationIssue> = buildList {
        if (!input.dryCoffeeDoseG.isFinite() || input.dryCoffeeDoseG <= 0.0) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_DRY_COFFEE_DOSE,
            ))
        }
        if (input.inputWaterG != null && (!input.inputWaterG.isFinite() || input.inputWaterG <= 0.0)) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_INPUT_WATER,
            ))
        }
        if (input.equipment.brewerProfileId != input.brewerProfileId) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.EQUIPMENT_PROFILE_MISMATCH,
            ))
        }
        if (input.equipment.capacityOverrideG?.isFinite() == false) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_CAPACITY_OVERRIDE,
            ))
        }
        if (input.temperatureC != null && input.temperatureC !in 0..100) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_TEMPERATURE,
            ))
        }
        when (input.brewerProfileId) {
            HARIO_SWITCH -> if (input.harioSwitchWorkflow == null) {
                add(BuiltinBrewerSessionStartValidationIssue(
                    BuiltinBrewerSessionStartValidationCode.HARIO_SWITCH_WORKFLOW_REQUIRED,
                ))
            }

            else -> if (input.harioSwitchWorkflow != null) {
                add(BuiltinBrewerSessionStartValidationIssue(
                    BuiltinBrewerSessionStartValidationCode.WORKFLOW_NOT_APPLICABLE,
                ))
            }
        }
        if (input.brewerProfileId != CEZVE_GENERIC && input.cezveSetup != null) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.CEZVE_SETUP_NOT_APPLICABLE,
            ))
        }
        if (input.brewerProfileId == CEZVE_GENERIC) {
            val cycles = (input.cezveSetup ?: CezveSessionSetup()).foamRiseCycles
            if (cycles !in MIN_CEZVE_FOAM_RISE_CYCLES..MAX_CEZVE_FOAM_RISE_CYCLES) {
                add(BuiltinBrewerSessionStartValidationIssue(
                    BuiltinBrewerSessionStartValidationCode.INVALID_CEZVE_FOAM_RISE_CYCLES,
                ))
            }
        }
    }

    private fun selectionsFor(input: BuiltinBrewerSessionStartInput): StagePlanSelections {
        if (input.brewerProfileId != CEZVE_GENERIC) return StagePlanSelections()
        val setup = input.cezveSetup ?: CezveSessionSetup()
        return StagePlanSelections(
            includedConditions = if (setup.includeSugar) setOf(CEZVE_INCLUDE_SUGAR) else emptySet(),
            repeatCounts = mapOf(CEZVE_FOAM_RISE_CYCLES to setup.foamRiseCycles),
        )
    }

    private fun quantities(
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
            QuantityRole.BREW_WATER_INPUT -> defaultQuantities.copy(brewWaterInputG = override)
            QuantityRole.RESERVOIR_INPUT -> defaultQuantities.copy(reservoirInputG = override)
            else -> error("Built-in defaults must use a water input role")
        }
    }

    private fun EquipmentConfiguration.toSnapshot(): EquipmentConfigurationSnapshotV1 =
        EquipmentConfigurationSnapshotV1(
            brewerProfileId = brewerProfileId.value,
            capacityOverrideG = capacityOverrideG,
            filterSelection = filterSelection.toSnapshot(),
            accessoryIds = accessoryIds.map { it.value }.sorted(),
            basketId = basketId?.value,
            heatSource = heatSource.name,
        )

    private fun FilterSelection.toSnapshot(): FilterSelectionSnapshotV1 = when (this) {
        FilterSelection.Unspecified -> FilterSelectionSnapshotV1(mode = FILTER_UNSPECIFIED)
        FilterSelection.IntentionallyUnfiltered -> {
            FilterSelectionSnapshotV1(mode = FILTER_INTENTIONALLY_UNFILTERED)
        }

        is FilterSelection.Stack -> FilterSelectionSnapshotV1(
            mode = FILTER_STACK,
            entries = entries.sortedBy { entry -> entry.position }.map { entry ->
                FilterStackEntrySnapshotV1(
                    filterProfileId = entry.filterProfileId.value,
                    position = entry.position,
                    role = entry.role.name,
                )
            },
        )
    }

    private fun BrewQuantities.toSnapshot(): BrewQuantitiesSnapshotV1 = BrewQuantitiesSnapshotV1(
        dryCoffeeDoseG = dryCoffeeDoseG,
        brewWaterInputG = brewWaterInputG,
        reservoirInputG = reservoirInputG,
        targetBeverageYieldG = targetBeverageYieldG,
        targetConcentrateYieldG = targetConcentrateYieldG,
        finalServedBeverageG = finalServedBeverageG,
        iceG = iceG,
        bypassWaterG = bypassWaterG,
        dilutionWaterG = dilutionWaterG,
        measuredOutputG = measuredOutputG,
    )

    private fun OutputModel.toSnapshot(): OutputModelSnapshotV1 = when (this) {
        is OutputModel.BrewWaterMinusRetention -> OutputModelSnapshotV1(
            kind = OUTPUT_BREW_WATER_MINUS_RETENTION,
            retainedWaterGPerCoffeeG = retainedWaterGPerCoffeeG,
        )

        OutputModel.DirectTargetBeverageYield -> OutputModelSnapshotV1(
            kind = OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD,
        )

        is OutputModel.CollectedConcentrate -> OutputModelSnapshotV1(
            kind = OUTPUT_COLLECTED_CONCENTRATE,
            retainedWaterGPerCoffeeG = retainedWaterGPerCoffeeG,
        )

        OutputModel.PreparedUnfilteredVolume -> OutputModelSnapshotV1(
            kind = OUTPUT_PREPARED_UNFILTERED_VOLUME,
        )

        is OutputModel.ReservoirToEstimatedOutput -> OutputModelSnapshotV1(
            kind = OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT,
            internalRetentionG = internalRetentionG,
        )

        OutputModel.UserMeasuredOutput -> OutputModelSnapshotV1(kind = OUTPUT_USER_MEASURED_OUTPUT)
        OutputModel.NoMeaningfulBeverageYield -> {
            OutputModelSnapshotV1(kind = OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD)
        }
    }

    private fun FilterSelection.logLabel(): String? = when (this) {
        FilterSelection.Unspecified -> null
        FilterSelection.IntentionallyUnfiltered -> FILTER_INTENTIONALLY_UNFILTERED
        is FilterSelection.Stack -> entries.sortedBy { entry -> entry.position }
            .joinToString(separator = ", ") { entry -> entry.filterProfileId.value }
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        val HARIO_SWITCH = BrewerProfileId("hario_switch")
        val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
        val CEZVE_INCLUDE_SUGAR = StageConditionId("cezve_include_sugar")
        val CEZVE_FOAM_RISE_CYCLES = StageRepeatId("cezve_foam_rise_cycles")

        const val MIN_CEZVE_FOAM_RISE_CYCLES = 1
        const val MAX_CEZVE_FOAM_RISE_CYCLES = 2

        const val FILTER_UNSPECIFIED = "UNSPECIFIED"
        const val FILTER_INTENTIONALLY_UNFILTERED = "INTENTIONALLY_UNFILTERED"
        const val FILTER_STACK = "STACK"

        const val OUTPUT_BREW_WATER_MINUS_RETENTION = "BREW_WATER_MINUS_RETENTION"
        const val OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD = "DIRECT_TARGET_BEVERAGE_YIELD"
        const val OUTPUT_COLLECTED_CONCENTRATE = "COLLECTED_CONCENTRATE"
        const val OUTPUT_PREPARED_UNFILTERED_VOLUME = "PREPARED_UNFILTERED_VOLUME"
        const val OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT = "RESERVOIR_TO_ESTIMATED_OUTPUT"
        const val OUTPUT_USER_MEASURED_OUTPUT = "USER_MEASURED_OUTPUT"
        const val OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD = "NO_MEANINGFUL_BEVERAGE_YIELD"
    }
}
