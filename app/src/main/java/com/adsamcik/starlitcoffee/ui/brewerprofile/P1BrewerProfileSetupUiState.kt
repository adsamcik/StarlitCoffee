package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.P1EquipmentOption
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.viewmodel.CezveSessionSetup

/** A physical brewer with only source recipes that the durable engine can execute exactly. */
data class P1BrewerProfileSetupOption(
    val profileId: BrewerProfileId,
    val displayName: String,
    val methodFamilyName: String,
    val defaults: BrewerProfileRecipeDefaults,
    val hasCompatibleFilters: Boolean,
    val allowsIntentionallyUnfiltered: Boolean,
    val recipes: List<BuiltInP1RecipeDefinition>,
)

/** The immutable exact recipe and indivisible equipment choice passed to the session starter. */
data class P1BrewerProfileStartSelection(
    val brewerProfileId: BrewerProfileId,
    val builtInRecipeId: BuiltInRecipeId,
    val equipmentOption: P1EquipmentOption,
    val equipmentCapacityG: Double,
    /** User measurement only; never a replacement for a resolved source quantity. */
    val measuredReservoirInputG: Double? = null,
    val harioSwitchWorkflow: HarioSwitchWorkflow?,
    val cezveSetup: CezveSessionSetup?,
    val heatSource: HeatSourceClass,
)

/**
 * UI-owned selection state for the P1 brewer setup flow.
 *
 * The physical brewer is chosen first. A recipe or equipment configuration is
 * selected automatically only when exactly one source-compatible option exists.
 * Multiple recipes and paired filter/basket alternatives always require an
 * explicit choice, so the setup cannot silently average or cross-combine them.
 */
data class P1BrewerProfileSetupUiState(
    val profiles: List<P1BrewerProfileSetupOption>,
    val selectedProfileId: BrewerProfileId? = null,
    val selectedRecipeIdByProfile: Map<BrewerProfileId, BuiltInRecipeId> = emptyMap(),
    val selectedEquipmentOptionIndexByRecipe: Map<BuiltInRecipeId, Int> = emptyMap(),
    val capacityInputByProfile: Map<BrewerProfileId, String> = emptyMap(),
    val measuredReservoirInputByRecipe: Map<BuiltInRecipeId, String> = emptyMap(),
    val includeCezveSugar: Boolean = false,
    val cezveHeatSource: HeatSourceClass = HeatSourceClass.NONE,
    val isStarting: Boolean = false,
) {
    val selectedProfile: P1BrewerProfileSetupOption?
        get() = profiles.firstOrNull { it.profileId == selectedProfileId }

    /** A sole compatible source recipe is unambiguous; otherwise the user must choose. */
    val selectedRecipe: BuiltInP1RecipeDefinition?
        get() = selectedProfile?.let { profile ->
            profile.recipes.singleOrNull()
                ?: selectedRecipeIdByProfile[profile.profileId]?.let { selectedId ->
                    profile.recipes.firstOrNull { recipe -> recipe.id == selectedId }
                }
        }

    /** Equipment alternatives remain atomic source configurations. */
    val selectedEquipmentOption: P1EquipmentOption?
        get() = selectedRecipe?.let { recipe ->
            recipe.equipmentOptions.singleOrNull()
                ?: selectedEquipmentOptionIndexByRecipe[recipe.id]
                    ?.let(recipe.equipmentOptions::getOrNull)
        }

    /** Capacity stays scoped to the physical brewer the user selected. */
    val selectedEquipmentCapacityInput: String
        get() = selectedProfileId?.let { capacityInputByProfile[it] }.orEmpty()

    val selectedEquipmentCapacityG: Double?
        get() = selectedEquipmentCapacityInput.trim().replace(",", ".").toDoubleOrNull()
            ?.takeIf { capacity -> capacity.isFinite() && capacity > 0.0 }

    val requiresMeasuredReservoirInput: Boolean
        get() = selectedRecipe?.let { recipe ->
            recipe.primaryInputRole() == QuantityRole.RESERVOIR_INPUT &&
                recipe.canonicalPrimaryInputG() == null
        } == true

    val selectedMeasuredReservoirInput: String
        get() = selectedRecipe?.id?.let { measuredReservoirInputByRecipe[it] }.orEmpty()

    val selectedMeasuredReservoirInputG: Double?
        get() = selectedMeasuredReservoirInput.trim().replace(",", ".").toDoubleOrNull()
            ?.takeIf { input -> input.isFinite() && input > 0.0 }

    val selectedRecipeInputG: Double?
        get() = selectedRecipe?.canonicalPrimaryInputG()
            ?: selectedMeasuredReservoirInputG.takeIf { requiresMeasuredReservoirInput }

    val capacitySupportsSelectedRecipe: Boolean
        get() = selectedEquipmentCapacityG?.let { capacity ->
            selectedRecipeInputG?.let { input -> capacity >= input } == true
        } == true

    val requiresCezveSetup: Boolean
        get() = selectedProfile?.profileId == CEZVE_GENERIC_PROFILE_ID

    val hasRequiredCezveHeatSource: Boolean
        get() = !requiresCezveSetup || cezveHeatSource != HeatSourceClass.NONE

    val startSelection: P1BrewerProfileStartSelection?
        get() = selectedProfile?.let { option ->
            val recipe = selectedRecipe ?: return@let null
            val equipmentOption = selectedEquipmentOption ?: return@let null
            val capacityG = selectedEquipmentCapacityG ?: return@let null
            if (!capacitySupportsSelectedRecipe) return@let null
            if (requiresCezveSetup && !hasRequiredCezveHeatSource) return@let null
            P1BrewerProfileStartSelection(
                brewerProfileId = option.profileId,
                builtInRecipeId = recipe.id,
                equipmentOption = equipmentOption,
                equipmentCapacityG = capacityG,
                measuredReservoirInputG = selectedMeasuredReservoirInputG
                    .takeIf { requiresMeasuredReservoirInput },
                harioSwitchWorkflow = recipe.harioSwitchWorkflow(),
                cezveSetup = recipe.cezveSetup(includeCezveSugar),
                heatSource = cezveHeatSource.takeIf { requiresCezveSetup }
                    ?: HeatSourceClass.NONE,
            )
        }

    val canStart: Boolean
        get() = startSelection != null && !isStarting

    /** Ignores an ID the P1 setup does not expose instead of guessing a substitute. */
    fun selectProfile(profileId: BrewerProfileId): P1BrewerProfileSetupUiState =
        if (profiles.any { it.profileId == profileId }) copy(selectedProfileId = profileId) else this

    /** Rejects a recipe from another physical brewer instead of aliasing it. */
    fun selectRecipe(recipeId: BuiltInRecipeId): P1BrewerProfileSetupUiState {
        val profile = selectedProfile ?: return this
        if (profile.recipes.none { recipe -> recipe.id == recipeId }) return this
        return copy(
            selectedRecipeIdByProfile = selectedRecipeIdByProfile + (profile.profileId to recipeId),
        )
    }

    /** Selects one complete source equipment option by its canonical list position. */
    fun selectEquipmentOption(index: Int): P1BrewerProfileSetupUiState {
        val recipe = selectedRecipe ?: return this
        if (index !in recipe.equipmentOptions.indices) return this
        return copy(
            selectedEquipmentOptionIndexByRecipe = selectedEquipmentOptionIndexByRecipe +
                (recipe.id to index),
        )
    }

    fun updateEquipmentCapacity(rawCapacity: String): P1BrewerProfileSetupUiState {
        val profileId = selectedProfileId ?: return this
        return copy(capacityInputByProfile = capacityInputByProfile + (profileId to rawCapacity))
    }

    fun updateMeasuredReservoirInput(rawInput: String): P1BrewerProfileSetupUiState {
        val recipe = selectedRecipe ?: return this
        if (!requiresMeasuredReservoirInput) return this
        return copy(
            measuredReservoirInputByRecipe = measuredReservoirInputByRecipe +
                (recipe.id to rawInput),
        )
    }

    fun selectCezveSugar(includeSugar: Boolean): P1BrewerProfileSetupUiState =
        if (requiresCezveSetup) copy(includeCezveSugar = includeSugar) else this

    fun selectCezveHeatSource(heatSource: HeatSourceClass): P1BrewerProfileSetupUiState =
        if (requiresCezveSetup && heatSource != HeatSourceClass.NONE) {
            copy(cezveHeatSource = heatSource)
        } else {
            this
        }

    fun withStarting(isStarting: Boolean): P1BrewerProfileSetupUiState = copy(isStarting = isStarting)
}

/** Builds exact setup state without assuming recipe aliases or fallback plans. */
object P1BrewerProfileSetupStateFactory {

    fun create(
        catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
        selectedProfileId: BrewerProfileId? = null,
        visibleProfileIds: Set<BrewerProfileId>? = null,
        executableRecipeIds: Set<BuiltInRecipeId> = BuiltInP1ExactStagePlanCatalog.recipeIds,
    ): P1BrewerProfileSetupUiState {
        val executableRecipesByProfile = BuiltInP1RecipeCatalog.recipes
            .asSequence()
            .filter { recipe -> recipe.id in executableRecipeIds }
            .filter(BuiltInP1RecipeDefinition::isActionableForSetup)
            .groupBy(BuiltInP1RecipeDefinition::brewerProfileId)
        val supportedIds = executableRecipesByProfile.keys
            .intersect(BuiltinBrewerProfileRecipeDefaults.supportedProfileIds)
            .let { supported -> visibleProfileIds?.let(supported::intersect) ?: supported }
        val profilesById = catalog.brewerProfiles.associateBy { it.id }
        val orderedIds = DISPLAY_ORDER.filter { it in supportedIds } +
            (supportedIds - DISPLAY_ORDER.toSet()).sortedBy(BrewerProfileId::value)

        val options = orderedIds.mapNotNull { profileId ->
            val profile = profilesById[profileId] ?: return@mapNotNull null
            val defaults = BuiltinBrewerProfileRecipeDefaults.find(profileId) ?: return@mapNotNull null
            P1BrewerProfileSetupOption(
                profileId = profile.id,
                displayName = profile.displayName,
                methodFamilyName = catalog.findMethodFamily(profile.familyId)?.displayName
                    ?: profile.familyId.value,
                defaults = defaults,
                hasCompatibleFilters = profile.compatibleFilterIds.isNotEmpty(),
                allowsIntentionallyUnfiltered = profile.allowsIntentionallyUnfiltered,
                recipes = executableRecipesByProfile[profileId].orEmpty(),
            )
        }

        return P1BrewerProfileSetupUiState(
            profiles = options,
            selectedProfileId = selectedProfileId?.takeIf { id -> options.any { it.profileId == id } },
        )
    }

    private val DISPLAY_ORDER = listOf(
        BrewerProfileId("v60_02"),
        BrewerProfileId("v60_unspecified"),
        BrewerProfileId("manual_wave_185"),
        BrewerProfileId("manual_wedge_generic"),
        BrewerProfileId("manual_thick_paper_carafe"),
        BrewerProfileId("manual_conical_generic"),
        BrewerProfileId("clever_style"),
        HARIO_SWITCH_PROFILE_ID,
        BrewerProfileId("cezve_generic"),
        BrewerProfileId("moccamaster_kbgv_select"),
        BrewerProfileId("automatic_batch_generic"),
        BrewerProfileId("automatic_single_cup_generic"),
        BrewerProfileId("vietnamese_phin"),
    )
}

private fun BuiltInP1RecipeDefinition.isActionableForSetup(): Boolean {
    val primaryRatio = ratios.firstOrNull() ?: return false
    val inputG = quantities.valueFor(primaryRatio.definition.denominator)
    val hasUsableInput = inputG?.let { value -> value.isFinite() && value > 0.0 }
        ?: (primaryRatio.definition.denominator == QuantityRole.RESERVOIR_INPUT)
    return primaryRatio.definition.numerator == QuantityRole.DRY_COFFEE_DOSE &&
        primaryRatio.definition.denominator in PRIMARY_INPUT_ROLES &&
        hasUsableInput &&
        quantities.dryCoffeeDoseG.isFinite() && quantities.dryCoffeeDoseG > 0.0 &&
        equipmentOptions.isNotEmpty()
}

private fun BuiltInP1RecipeDefinition.canonicalPrimaryInputG(): Double? =
    ratios.firstOrNull()?.definition?.denominator?.let(quantities::valueFor)

private fun BuiltInP1RecipeDefinition.primaryInputRole(): QuantityRole? =
    ratios.firstOrNull()?.definition?.denominator

private fun BuiltInP1RecipeDefinition.harioSwitchWorkflow(): HarioSwitchWorkflow? = when (id) {
    SWITCH_OFFICIAL_RECIPE -> HarioSwitchWorkflow.STEEP_AND_RELEASE
    SWITCH_GRAVITY_RECIPE -> HarioSwitchWorkflow.MANUAL_GRAVITY
    else -> null
}

private fun BuiltInP1RecipeDefinition.cezveSetup(includeSugar: Boolean): CezveSessionSetup? = when (id) {
    CEZVE_SINGLE_RISE_RECIPE -> CezveSessionSetup(includeSugar = includeSugar, foamRiseCycles = 1)
    CEZVE_REPEATED_RISE_RECIPE -> CezveSessionSetup(includeSugar = includeSugar, foamRiseCycles = 2)
    else -> null
}

private val HARIO_SWITCH_PROFILE_ID = BrewerProfileId("hario_switch")
private val CEZVE_GENERIC_PROFILE_ID = BrewerProfileId("cezve_generic")
private val PRIMARY_INPUT_ROLES = setOf(QuantityRole.BREW_WATER_INPUT, QuantityRole.RESERVOIR_INPUT)
private val SWITCH_OFFICIAL_RECIPE = BuiltInRecipeId("switch_official_20_240")
private val SWITCH_GRAVITY_RECIPE = BuiltInRecipeId("switch_gravity_15_250")
private val CEZVE_SINGLE_RISE_RECIPE = BuiltInRecipeId("cezve_turkish_single_rise_6_65")
private val CEZVE_REPEATED_RISE_RECIPE = BuiltInRecipeId("cezve_bounded_repeated_rise_12_130")
