package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BuiltInP1RecipeSnapshotMapper
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
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CompatibilitySeverity
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityIssue
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityResult
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentCompatibilityValidator
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.OutputModel
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureBasis
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.RatioDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
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
import kotlin.math.abs

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
    val builtInRecipeId: BuiltInRecipeId? = null,
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
    UNKNOWN_BUILTIN_RECIPE,
    BUILTIN_RECIPE_CATALOG_MISMATCH,
    BUILTIN_RECIPE_WORKFLOW_UNSUPPORTED,
    BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
    MISSING_PROFILE_DEFAULTS,
    PROFILE_DEFAULTS_OUTPUT_MISMATCH,
    NO_BUILTIN_STAGE_PLAN,
}

enum class BuiltinBrewerSessionStartValidationCode {
    INVALID_DRY_COFFEE_DOSE,
    INVALID_INPUT_WATER,
    BUILTIN_RECIPE_PROFILE_MISMATCH,
    BUILTIN_RECIPE_DOSE_MISMATCH,
    BUILTIN_RECIPE_INPUT_MISMATCH,
    BUILTIN_RECIPE_INPUT_REQUIRED,
    BUILTIN_RECIPE_EQUIPMENT_MISMATCH,
    BUILTIN_RECIPE_TEMPERATURE_MISMATCH,
    BUILTIN_RECIPE_TEMPERATURE_NOT_APPLICABLE,
    BUILTIN_RECIPE_WORKFLOW_MISMATCH,
    MISSING_EQUIPMENT_CAPACITY,
    INVALID_CAPACITY_OVERRIDE,
    INPUT_WATER_EXCEEDS_EQUIPMENT_CAPACITY,
    INVALID_TEMPERATURE,
    EQUIPMENT_PROFILE_MISMATCH,
    HARIO_SWITCH_WORKFLOW_REQUIRED,
    WORKFLOW_NOT_APPLICABLE,
    CEZVE_SETUP_NOT_APPLICABLE,
    INVALID_CEZVE_FOAM_RISE_CYCLES,
    CEZVE_HEAT_SOURCE_REQUIRED,
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
    private val builtInRecipeFor: (BuiltInRecipeId) -> BuiltInP1RecipeDefinition? =
        BuiltInP1RecipeCatalog::find,
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
        val builtInRecipe = when (val recipeId = input.builtInRecipeId) {
            null -> null
            else -> builtInRecipeFor(recipeId)
                ?: return unavailable(
                    input,
                    BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BUILTIN_RECIPE,
                )
        }
        if (builtInRecipe != null && input.brewerProfileId != builtInRecipe.brewerProfileId) {
            return invalidSetup(
                input,
                BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_PROFILE_MISMATCH,
            )
        }

        val profile = catalog.findBrewerProfile(input.brewerProfileId)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BREWER_PROFILE)
        if (builtInRecipe != null && profile.familyId != builtInRecipe.methodFamilyId) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_CATALOG_MISMATCH,
            )
        }
        val defaults = defaultsFor(profile.id)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.MISSING_PROFILE_DEFAULTS)
        if (profile.outputModel != defaults.quantitySemantics.outputModel) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.PROFILE_DEFAULTS_OUTPUT_MISMATCH,
            )
        }
        if (
            builtInRecipe != null &&
                defaults.quantitySemantics.ratioDefinition != builtInRecipe.ratios.first().definition
        ) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_CATALOG_MISMATCH,
            )
        }

        val exactRecipe = when (val result = builtInRecipe?.let { resolveExactRecipe(input, it) }) {
            is ExactRecipeResolution.Invalid -> {
                return BuiltinBrewerSessionStartResult.InvalidSetup(
                    brewerProfileId = input.brewerProfileId,
                    issues = result.issues,
                )
            }
            is ExactRecipeResolution.Unavailable -> return unavailable(input, result.reason)
            is ExactRecipeResolution.Resolved -> result
            null -> null
        }
        val effectiveInput = exactRecipe?.input ?: input

        val setupIssues = validateSetup(effectiveInput, defaults)
        if (setupIssues.isNotEmpty()) {
            return BuiltinBrewerSessionStartResult.InvalidSetup(input.brewerProfileId, setupIssues)
        }

        val compatibility = compatibilityValidator.validate(effectiveInput.equipment)
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

        val sourcePlan = stagePlanFor(profile.id, effectiveInput.harioSwitchWorkflow)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.NO_BUILTIN_STAGE_PLAN)
        if (builtInRecipe != null && sourcePlan.id.value != exactStagePlanId(builtInRecipe)) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            )
        }
        val compiledPlan = when (val result = compileStagePlan(sourcePlan, selectionsFor(effectiveInput))) {
            is StagePlanCompileResult.Compiled -> result.value
            is StagePlanCompileResult.Invalid -> {
                return BuiltinBrewerSessionStartResult.InvalidStagePlan(profile.id, result.issues)
            }
        }
        if (builtInRecipe != null && !compiledPlanMatchesExactRecipe(compiledPlan, builtInRecipe)) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            )
        }

        val quantities = exactRecipe?.quantities ?: quantities(effectiveInput, defaults)
        val ratioDefinition = exactRecipe?.ratioDefinition ?: defaults.quantitySemantics.ratioDefinition
        val primaryInputG = requireNotNull(quantities.valueFor(ratioDefinition.denominator))
        val ratioValue = exactRecipe?.ratioValue ?: primaryInputG / quantities.dryCoffeeDoseG
        val baseRecipe = BrewRecipeSnapshotV1(
            methodFamilyId = profile.familyId.value,
            brewerProfileId = profile.id.value,
            builtInRecipeId = builtInRecipe?.id?.value,
            equipment = effectiveInput.equipment.toSnapshot(),
            quantities = quantities.toSnapshot(),
            ratioDefinition = RatioDefinitionSnapshotV1(
                numerator = ratioDefinition.numerator.name,
                denominator = ratioDefinition.denominator.name,
            ),
            ratioValue = ratioValue,
            temperatureC = effectiveInput.temperatureC,
            grinderId = effectiveInput.grinderId.normalizedOrNull(),
            grindSetting = effectiveInput.grindSetting.normalizedOrNull(),
            technique = RecipeTechniqueSnapshotV1(
                stagePlanVariantId = compiledPlan.id.value,
            ),
            isDecaf = effectiveInput.isDecaf,
            notes = effectiveInput.notes.normalizedOrNull(),
            outputModel = defaults.quantitySemantics.outputModel.toSnapshot(),
        )
        val recipe = builtInRecipe?.let { definition ->
            BuiltInP1RecipeSnapshotMapper.enrich(baseRecipe, definition)
        } ?: baseRecipe
        return BuiltinBrewerSessionStartResult.Ready(
            request = BrewSessionStartRequest(
                sessionId = SessionId(newUuid().toString()),
                recipe = recipe,
                stagePlan = compiledPlan,
                executionContext = SessionExecutionContextSnapshotV1(
                    coffeeBagId = effectiveInput.coffeeBagId,
                    sourceRecipeId = effectiveInput.sourceRecipeId,
                    logPresentation = BrewLogPresentationContextSnapshotV1(
                        methodLabel = effectiveInput.methodLabel.normalizedOrNull()
                            ?: profile.displayName,
                        doseG = quantities.dryCoffeeDoseG,
                        waterG = primaryInputG,
                        ratio = ratioValue,
                        grindLabel = effectiveInput.grindSetting.normalizedOrNull(),
                        filterLabel = effectiveInput.filterLabel.normalizedOrNull()
                            ?: effectiveInput.equipment.filterSelection.logLabel(),
                        isDecaf = effectiveInput.isDecaf,
                        notes = effectiveInput.notes.normalizedOrNull(),
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

    private fun invalidSetup(
        input: BuiltinBrewerSessionStartInput,
        code: BuiltinBrewerSessionStartValidationCode,
    ): BuiltinBrewerSessionStartResult.InvalidSetup = BuiltinBrewerSessionStartResult.InvalidSetup(
        brewerProfileId = input.brewerProfileId,
        issues = listOf(BuiltinBrewerSessionStartValidationIssue(code)),
    )

    private fun resolveExactRecipe(
        input: BuiltinBrewerSessionStartInput,
        definition: BuiltInP1RecipeDefinition,
    ): ExactRecipeResolution {
        if (definition.id == SWITCH_HYBRID_RECIPE) {
            return ExactRecipeResolution.Unavailable(
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_WORKFLOW_UNSUPPORTED,
            )
        }
        if (
            (definition.brewerProfileId == HARIO_SWITCH && definition.id !in HARIO_SWITCH_RECIPE_IDS) ||
                (definition.brewerProfileId == CEZVE_GENERIC && definition.id !in CEZVE_RECIPE_IDS)
        ) {
            return ExactRecipeResolution.Unavailable(
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_CATALOG_MISMATCH,
            )
        }

        val primaryRatio = definition.ratios.first()
        if (
            primaryRatio.definition.numerator != QuantityRole.DRY_COFFEE_DOSE ||
                primaryRatio.definition.denominator !in PRIMARY_INPUT_ROLES ||
                !hasValidCanonicalQuantities(definition)
        ) {
            return ExactRecipeResolution.Unavailable(
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_CATALOG_MISMATCH,
            )
        }

        val issues = mutableListOf<BuiltinBrewerSessionStartValidationIssue>()
        if (!input.dryCoffeeDoseG.isFinite() || input.dryCoffeeDoseG <= 0.0) {
            issues.addIssue(BuiltinBrewerSessionStartValidationCode.INVALID_DRY_COFFEE_DOSE)
        } else if (!input.dryCoffeeDoseG.sameQuantityAs(definition.quantities.dryCoffeeDoseG)) {
            issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_DOSE_MISMATCH)
        }

        val canonicalInputG = definition.quantities.valueFor(primaryRatio.definition.denominator)
        val suppliedInputG = input.inputWaterG
        val resolvedInputG = when {
            suppliedInputG != null && (!suppliedInputG.isFinite() || suppliedInputG <= 0.0) -> {
                issues.addIssue(BuiltinBrewerSessionStartValidationCode.INVALID_INPUT_WATER)
                null
            }
            canonicalInputG != null -> {
                if (suppliedInputG != null && !suppliedInputG.sameQuantityAs(canonicalInputG)) {
                    issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_MISMATCH)
                }
                canonicalInputG
            }
            suppliedInputG == null -> {
                issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_REQUIRED)
                null
            }
            else -> suppliedInputG
        }

        if (!equipmentMatchesExactRecipe(input.equipment, definition)) {
            issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_EQUIPMENT_MISMATCH)
        }
        exactTemperatureIssue(input.temperatureC, definition)?.let { issue ->
            issues.addIssue(issue)
        }
        val effectiveInput = applyExactWorkflow(input, definition, issues)
        if (issues.isNotEmpty()) return ExactRecipeResolution.Invalid(issues)

        val primaryInputG = requireNotNull(resolvedInputG)
        val quantities = definition.quantities.withValue(
            role = primaryRatio.definition.denominator,
            value = primaryInputG,
        )
        val ratioValue = primaryRatio.ratioValue
            ?: primaryInputG / definition.quantities.dryCoffeeDoseG
        return ExactRecipeResolution.Resolved(
            input = effectiveInput.copy(inputWaterG = primaryInputG),
            quantities = quantities,
            ratioDefinition = primaryRatio.definition,
            ratioValue = ratioValue,
        )
    }

    private fun hasValidCanonicalQuantities(definition: BuiltInP1RecipeDefinition): Boolean {
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
        return quantities.dryCoffeeDoseG.isFinite() && quantities.dryCoffeeDoseG > 0.0 &&
            resolvedQuantities.all { value -> value.isFinite() && value >= 0.0 } &&
            definition.ratios.all { ratio ->
                ratio.ratioValue == null || ratio.ratioValue.isFinite()
            }
    }

    private fun BrewQuantities.withValue(role: QuantityRole, value: Double): BrewQuantities =
        when (role) {
            QuantityRole.BREW_WATER_INPUT -> copy(brewWaterInputG = value)
            QuantityRole.RESERVOIR_INPUT -> copy(reservoirInputG = value)
            else -> error("Exact built-in recipes require a water input denominator")
        }

    private fun Double.sameQuantityAs(other: Double): Boolean =
        abs(this - other) <= EXACT_QUANTITY_TOLERANCE

    private fun MutableList<BuiltinBrewerSessionStartValidationIssue>.addIssue(
        code: BuiltinBrewerSessionStartValidationCode,
    ) {
        add(BuiltinBrewerSessionStartValidationIssue(code))
    }

    private fun equipmentMatchesExactRecipe(
        equipment: EquipmentConfiguration,
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = definition.equipmentOptions.any { option ->
        equipment.filterSelection.matches(option.filterSelection) &&
            equipment.basketId == option.basketId &&
            equipment.accessoryIds == option.accessoryIds
    }

    private fun FilterSelection.matches(sourceSelection: FilterSelection): Boolean = when {
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
            P1TemperatureBasis.USER_EXACT -> if (
                temperature.sameQuantityAs(requireNotNull(semantics.minimumC))
            ) {
                null
            } else {
                BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_MISMATCH
            }

            P1TemperatureBasis.USER_RANGE,
            P1TemperatureBasis.USER_APPROXIMATE_RANGE,
            P1TemperatureBasis.USER_STARTING_RANGE,
            P1TemperatureBasis.MACHINE_CONTROLLED_REPORTED_RANGE,
            -> if (
                temperature + EXACT_QUANTITY_TOLERANCE >= requireNotNull(semantics.minimumC) &&
                temperature - EXACT_QUANTITY_TOLERANCE <= requireNotNull(semantics.maximumC)
            ) {
                null
            } else {
                BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_MISMATCH
            }

            P1TemperatureBasis.HOT_UNSPECIFIED,
            P1TemperatureBasis.MACHINE_CONTROLLED,
            P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED,
            -> BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_NOT_APPLICABLE
        }
    }

    private fun applyExactWorkflow(
        input: BuiltinBrewerSessionStartInput,
        definition: BuiltInP1RecipeDefinition,
        issues: MutableList<BuiltinBrewerSessionStartValidationIssue>,
    ): BuiltinBrewerSessionStartInput = when (definition.id) {
        SWITCH_OFFICIAL_RECIPE -> input.withHarioSwitchWorkflow(
            expected = HarioSwitchWorkflow.STEEP_AND_RELEASE,
            issues = issues,
        )
        SWITCH_GRAVITY_RECIPE -> input.withHarioSwitchWorkflow(
            expected = HarioSwitchWorkflow.MANUAL_GRAVITY,
            issues = issues,
        )
        CEZVE_SINGLE_RISE_RECIPE -> input.withCezveRiseCycles(expected = 1, issues = issues)
        CEZVE_REPEATED_RISE_RECIPE -> input.withCezveRiseCycles(expected = 2, issues = issues)
        else -> input
    }

    private fun BuiltinBrewerSessionStartInput.withHarioSwitchWorkflow(
        expected: HarioSwitchWorkflow,
        issues: MutableList<BuiltinBrewerSessionStartValidationIssue>,
    ): BuiltinBrewerSessionStartInput {
        if (harioSwitchWorkflow != null && harioSwitchWorkflow != expected) {
            issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_WORKFLOW_MISMATCH)
        }
        return copy(harioSwitchWorkflow = expected)
    }

    private fun BuiltinBrewerSessionStartInput.withCezveRiseCycles(
        expected: Int,
        issues: MutableList<BuiltinBrewerSessionStartValidationIssue>,
    ): BuiltinBrewerSessionStartInput {
        if (cezveSetup != null && cezveSetup.foamRiseCycles != expected) {
            issues.addIssue(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_WORKFLOW_MISMATCH)
        }
        return copy(
            cezveSetup = (cezveSetup ?: CezveSessionSetup()).copy(foamRiseCycles = expected),
        )
    }

    private fun compiledPlanMatchesExactRecipe(
        plan: CompiledStagePlan,
        definition: BuiltInP1RecipeDefinition,
    ): Boolean {
        if (plan.stages.size != definition.orderedStageCount) return false
        val actions = plan.stages.map { stage -> stage.definition.action }
        return when (definition.id) {
            CLEVER_WATER_FIRST_RECIPE -> actions.precedes(
                first = BrewStageAction.ADD_WATER,
                second = BrewStageAction.ADD_COFFEE,
            )
            CLEVER_COFFEE_FIRST_RECIPE -> actions.precedes(
                first = BrewStageAction.ADD_COFFEE,
                second = BrewStageAction.ADD_WATER,
            )
            SWITCH_OFFICIAL_RECIPE ->
                BrewStageAction.RELEASE in actions && BrewStageAction.POUR !in actions
            SWITCH_GRAVITY_RECIPE ->
                BrewStageAction.POUR in actions && BrewStageAction.RELEASE !in actions
            SWITCH_HYBRID_RECIPE -> false
            else -> true
        }
    }

    private fun List<BrewStageAction>.precedes(
        first: BrewStageAction,
        second: BrewStageAction,
    ): Boolean = indexOf(first).let { firstIndex ->
        firstIndex >= 0 && firstIndex < indexOf(second)
    }

    private fun exactStagePlanId(definition: BuiltInP1RecipeDefinition): String =
        EXACT_RECIPE_PLAN_ID_PREFIX + definition.id.value

    private fun validateSetup(
        input: BuiltinBrewerSessionStartInput,
        defaults: BrewerProfileRecipeDefaults,
    ): List<BuiltinBrewerSessionStartValidationIssue> = buildList {
        val hasValidDryCoffeeDose = input.dryCoffeeDoseG.isFinite() && input.dryCoffeeDoseG > 0.0
        if (!hasValidDryCoffeeDose) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_DRY_COFFEE_DOSE,
            ))
        }

        val resolvedInputWaterG = input.inputWaterG ?: if (hasValidDryCoffeeDose) {
            input.dryCoffeeDoseG * defaults.ratio.waterPerCoffee
        } else {
            null
        }
        val hasValidInputWater = resolvedInputWaterG == null || (
            resolvedInputWaterG.isFinite() && resolvedInputWaterG > 0.0
        )
        if (!hasValidInputWater) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INVALID_INPUT_WATER,
            ))
        }
        if (input.equipment.brewerProfileId != input.brewerProfileId) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.EQUIPMENT_PROFILE_MISMATCH,
            ))
        }

        val capacityG = input.equipment.capacityOverrideG
        val requiresExplicitCapacity =
            input.brewerProfileId in BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds
        val hasValidEquipmentCapacity = when {
            capacityG == null -> {
                if (requiresExplicitCapacity) {
                    add(BuiltinBrewerSessionStartValidationIssue(
                        BuiltinBrewerSessionStartValidationCode.MISSING_EQUIPMENT_CAPACITY,
                    ))
                }
                false
            }
            !capacityG.isFinite() || capacityG <= 0.0 -> {
                add(BuiltinBrewerSessionStartValidationIssue(
                    BuiltinBrewerSessionStartValidationCode.INVALID_CAPACITY_OVERRIDE,
                ))
                false
            }
            else -> true
        }
        if (
            requiresExplicitCapacity && hasValidEquipmentCapacity &&
                resolvedInputWaterG != null && hasValidInputWater &&
                resolvedInputWaterG > requireNotNull(capacityG)
        ) {
            add(BuiltinBrewerSessionStartValidationIssue(
                BuiltinBrewerSessionStartValidationCode.INPUT_WATER_EXCEEDS_EQUIPMENT_CAPACITY,
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
            if (input.equipment.heatSource == HeatSourceClass.NONE) {
                add(BuiltinBrewerSessionStartValidationIssue(
                    BuiltinBrewerSessionStartValidationCode.CEZVE_HEAT_SOURCE_REQUIRED,
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

    private sealed interface ExactRecipeResolution {
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

    private companion object {
        const val EXACT_RECIPE_PLAN_ID_PREFIX = "builtin_recipe_"
        const val EXACT_QUANTITY_TOLERANCE = 1e-6

        val HARIO_SWITCH = BrewerProfileId("hario_switch")
        val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
        val CLEVER_WATER_FIRST_RECIPE = BuiltInRecipeId("clever_water_first_15_250")
        val CLEVER_COFFEE_FIRST_RECIPE = BuiltInRecipeId("clever_coffee_first_15_250")
        val SWITCH_OFFICIAL_RECIPE = BuiltInRecipeId("switch_official_20_240")
        val SWITCH_HYBRID_RECIPE = BuiltInRecipeId("switch_ole_boen_hybrid_16_5_240")
        val SWITCH_GRAVITY_RECIPE = BuiltInRecipeId("switch_gravity_15_250")
        val CEZVE_SINGLE_RISE_RECIPE = BuiltInRecipeId("cezve_turkish_single_rise_6_65")
        val CEZVE_REPEATED_RISE_RECIPE = BuiltInRecipeId("cezve_bounded_repeated_rise_12_130")
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
