package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BuiltInP1RecipeSnapshotMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotValueMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RecipeTechniqueSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
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
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompileResult
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompiler
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanSelections
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanValidationIssue
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
 * Legacy Hario Switch starts must explicitly choose a generic workflow. Exact
 * recipes instead resolve only through their recipe-owned stage plan, so a
 * missing exact plan can never fall back to a plausible generic one. Exact
 * plans compile without legacy Hario or Cezve branch selections.
 */
class BuiltinBrewerSessionStartFactory(
    private val catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
    private val builtInRecipeFor: (BuiltInRecipeId) -> BuiltInP1RecipeDefinition? =
        BuiltInP1RecipeCatalog::find,
    private val defaultsFor: (BrewerProfileId) -> BrewerProfileRecipeDefaults? =
        BuiltinBrewerProfileRecipeDefaults::find,
    private val stagePlanFor: (BrewerProfileId, HarioSwitchWorkflow?) -> BrewStagePlan? =
        BuiltinBrewerStagePlanFactory::create,
    private val exactStagePlanFor: (BuiltInRecipeId) -> BrewStagePlan? =
        BuiltInP1ExactStagePlanCatalog::find,
    private val compileStagePlan: (BrewStagePlan, StagePlanSelections) -> StagePlanCompileResult =
        { plan, selections -> StagePlanCompiler.compile(plan, selections) },
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    private val compatibilityValidator = EquipmentCompatibilityValidator(catalog)
    private val exactRecipeResolver = BuiltinBrewerExactRecipeResolver()

    fun create(
        input: BuiltinBrewerSessionStartInput,
    ): BuiltinBrewerSessionStartResult = resolveCatalogContext(input)

    private fun resolveCatalogContext(
        input: BuiltinBrewerSessionStartInput,
    ): BuiltinBrewerSessionStartResult {
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
        return resolveProfileContext(input, builtInRecipe)
    }

    private fun resolveProfileContext(
        input: BuiltinBrewerSessionStartInput,
        builtInRecipe: BuiltInP1RecipeDefinition?,
    ): BuiltinBrewerSessionStartResult {
        val profile = catalog.findBrewerProfile(input.brewerProfileId)
            ?: return unavailable(input, BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BREWER_PROFILE)
        if (builtInRecipe != null && profile.familyId != builtInRecipe.methodFamilyId) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_CATALOG_MISMATCH,
            )
        }
        return resolveDefaultsContext(input, builtInRecipe, profile)
    }

    private fun resolveDefaultsContext(
        input: BuiltinBrewerSessionStartInput,
        builtInRecipe: BuiltInP1RecipeDefinition?,
        profile: BrewerProfile,
    ): BuiltinBrewerSessionStartResult {
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

        return prepareRecipe(
            CatalogStartContext(
                input = input,
                builtInRecipe = builtInRecipe,
                profile = profile,
                defaults = defaults,
            ),
        )
    }

    private fun prepareRecipe(
        context: CatalogStartContext,
    ): BuiltinBrewerSessionStartResult {
        val exactRecipe = when (val definition = context.builtInRecipe) {
            null -> null
            else -> when (
                val resolution = exactRecipeResolver.resolve(
                    context.input,
                    definition,
                )
            ) {
                is ExactRecipeResolution.Invalid -> {
                    return BuiltinBrewerSessionStartResult.InvalidSetup(
                        brewerProfileId = context.input.brewerProfileId,
                        issues = resolution.issues,
                    )
                }

                is ExactRecipeResolution.Unavailable ->
                    return unavailable(context.input, resolution.reason)

                is ExactRecipeResolution.Resolved -> resolution
            }
        }
        return validatePreparedStart(
            PreparedStartContext(
                catalog = context,
                exactRecipe = exactRecipe,
            ),
        )
    }

    private fun validatePreparedStart(
        context: PreparedStartContext,
    ): BuiltinBrewerSessionStartResult {
        val setupIssues = BuiltinBrewerSessionSetupRules.validate(
            input = context.input,
            defaults = context.catalog.defaults,
            isExactRecipe = context.catalog.builtInRecipe != null,
        )
        if (setupIssues.isNotEmpty()) {
            return BuiltinBrewerSessionStartResult.InvalidSetup(
                context.catalog.input.brewerProfileId,
                setupIssues,
            )
        }

        val compatibility = compatibilityValidator.validate(context.input.equipment)
        val blockingCompatibilityIssues = compatibility.issues.filter {
            it.severity == CompatibilitySeverity.BLOCKING
        }
        if (blockingCompatibilityIssues.isNotEmpty()) {
            return BuiltinBrewerSessionStartResult.InvalidSetup(
                brewerProfileId = context.catalog.input.brewerProfileId,
                issues = listOf(
                    BuiltinBrewerSessionStartValidationIssue(
                        code = BuiltinBrewerSessionStartValidationCode.INCOMPATIBLE_EQUIPMENT,
                        equipmentIssues = blockingCompatibilityIssues,
                    ),
                ),
            )
        }
        return compileStartPlan(
            ValidatedStartContext(
                prepared = context,
                compatibility = compatibility,
            ),
        )
    }

    private fun compileStartPlan(
        context: ValidatedStartContext,
    ): BuiltinBrewerSessionStartResult {
        val prepared = context.prepared
        val catalogContext = prepared.catalog
        val builtInRecipe = catalogContext.builtInRecipe
        val profile = catalogContext.profile
        val effectiveInput = prepared.input
        val input = catalogContext.input

        val sourcePlan = if (builtInRecipe == null) {
            stagePlanFor(profile.id, effectiveInput.harioSwitchWorkflow)
        } else {
            exactStagePlanFor(builtInRecipe.id)
        } ?: return unavailable(
            input,
            BuiltinBrewerSessionStartUnavailableReason.NO_BUILTIN_STAGE_PLAN,
        )
        if (
            builtInRecipe != null &&
            !exactRecipeResolver.sourcePlanMatches(sourcePlan, builtInRecipe)
        ) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            )
        }
        val selections = if (builtInRecipe == null) {
            BuiltinBrewerSessionSetupRules.stagePlanSelections(effectiveInput)
        } else {
            StagePlanSelections()
        }
        val compiledPlan = when (val result = compileStagePlan(sourcePlan, selections)) {
            is StagePlanCompileResult.Compiled -> result.value
            is StagePlanCompileResult.Invalid -> {
                return BuiltinBrewerSessionStartResult.InvalidStagePlan(
                    profile.id,
                    result.issues,
                )
            }
        }
        if (
            builtInRecipe != null &&
            !exactRecipeResolver.compiledPlanMatches(
                compiledPlan,
                builtInRecipe,
            )
        ) {
            return unavailable(
                input,
                BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            )
        }
        return buildReadyResult(context, compiledPlan)
    }

    private fun buildReadyResult(
        context: ValidatedStartContext,
        compiledPlan: CompiledStagePlan,
    ): BuiltinBrewerSessionStartResult.Ready {
        val prepared = context.prepared
        val catalogContext = prepared.catalog
        val exactRecipe = prepared.exactRecipe
        val builtInRecipe = catalogContext.builtInRecipe
        val profile = catalogContext.profile
        val defaults = catalogContext.defaults
        val effectiveInput = prepared.input
        val quantities = exactRecipe?.quantities
            ?: BuiltinBrewerSessionSetupRules.quantities(effectiveInput, defaults)
        val ratioDefinition = exactRecipe?.ratioDefinition
            ?: defaults.quantitySemantics.ratioDefinition
        val primaryInputG = requireNotNull(quantities.valueFor(ratioDefinition.denominator))
        val ratioValue = exactRecipe?.ratioValue ?: primaryInputG / quantities.dryCoffeeDoseG
        val baseRecipe = BrewRecipeSnapshotV1(
            methodFamilyId = profile.familyId.value,
            brewerProfileId = profile.id.value,
            builtInRecipeId = builtInRecipe?.id?.value,
            equipment = BrewingSnapshotValueMapper.equipment(effectiveInput.equipment),
            quantities = BrewingSnapshotValueMapper.quantities(quantities),
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
            outputModel = BrewingSnapshotValueMapper.outputModel(defaults.quantitySemantics.outputModel),
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
            equipmentCompatibility = context.compatibility,
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

    private data class CatalogStartContext(
        val input: BuiltinBrewerSessionStartInput,
        val builtInRecipe: BuiltInP1RecipeDefinition?,
        val profile: BrewerProfile,
        val defaults: BrewerProfileRecipeDefaults,
    )

    private data class PreparedStartContext(
        val catalog: CatalogStartContext,
        val exactRecipe: ExactRecipeResolution.Resolved?,
    ) {
        val input: BuiltinBrewerSessionStartInput
            get() = exactRecipe?.input ?: catalog.input
    }

    private data class ValidatedStartContext(
        val prepared: PreparedStartContext,
        val compatibility: EquipmentCompatibilityResult,
    )

    private fun FilterSelection.logLabel(): String? = when (this) {
        FilterSelection.Unspecified -> null
        FilterSelection.IntentionallyUnfiltered -> FILTER_INTENTIONALLY_UNFILTERED
        is FilterSelection.Stack -> entries.sortedBy { entry -> entry.position }
            .joinToString(separator = ", ") { entry -> entry.filterProfileId.value }
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val FILTER_INTENTIONALLY_UNFILTERED = "INTENTIONALLY_UNFILTERED"
    }
}
