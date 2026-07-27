package com.adsamcik.starlitcoffee.domain.brewing

enum class FilterMedium {
    PAPER,
    CLOTH,
    METAL,
    UNFILTERED,
}

enum class FilterGeometry {
    CONE,
    WAVE,
    WEDGE,
    DISC,
    BASKET,
    SOCK,
    FLAT_SHEET,
    BREWER_SPECIFIC,
}

enum class FilterStackRole {
    PRIMARY,
    SUPPORT,
    TOP,
    BOTTOM,
    POST_BREW,
}

enum class EvidenceConfidence {
    HIGH,
    MEDIUM,
    LIMITED,
    UNKNOWN,
}

data class FilterProfile(
    val id: FilterProfileId,
    val medium: FilterMedium,
    val geometry: FilterGeometry,
    val size: String? = null,
    val disposable: Boolean? = null,
    val knownFlowCategory: String? = null,
    val evidenceConfidence: EvidenceConfidence = EvidenceConfidence.UNKNOWN,
)

data class FilterStackEntry(
    val filterProfileId: FilterProfileId,
    val position: Int,
    val role: FilterStackRole = FilterStackRole.PRIMARY,
)

sealed interface FilterSelection {
    data object Unspecified : FilterSelection

    data object IntentionallyUnfiltered : FilterSelection

    data class Stack(val entries: List<FilterStackEntry>) : FilterSelection {
        init {
            require(entries.isNotEmpty()) { "A filter stack cannot be empty" }
            require(entries.map(FilterStackEntry::position).distinct().size == entries.size) {
                "Filter stack positions must be unique"
            }
        }
    }
}

enum class AccessoryBehavior {
    PREVENTS_PASSIVE_DRIP,
    MANUAL_FLOW_CONTROL,
    PRESSURE_ACTUATED_FLOW,
    CHANGES_CHAMBER_CAPACITY,
    DISTRIBUTES_WATER,
    SUPPORTS_FILTER_STACK,
    PRESSURE_GENERATING,
}

data class AccessoryProfile(
    val id: AccessoryProfileId,
    val behaviors: Set<AccessoryBehavior>,
)

data class BasketProfile(
    val id: BasketProfileId,
    val nominalDoseMinimumG: Double? = null,
    val nominalDoseMaximumG: Double? = null,
    val pressurised: Boolean? = null,
    val geometry: String? = null,
)

enum class HeatSourceClass {
    NONE,
    HOB,
    OPEN_FLAME,
    ELECTRIC_MACHINE,
    PORTABLE_HEATER,
}

enum class SafetyTag {
    HOT_LIQUID,
    HOT_METAL,
    HOT_GLASS,
    OPEN_FLAME,
    PRESSURE,
    OVERFLOW,
    UNSTABLE_ORIENTATION,
    FOOD_STORAGE,
}

data class CapacityRangeG(
    val minimumG: Double? = null,
    val maximumG: Double? = null,
) {
    init {
        require(minimumG == null || minimumG >= 0.0) { "Minimum capacity cannot be negative" }
        require(maximumG == null || maximumG > 0.0) { "Maximum capacity must be positive" }
        require(minimumG == null || maximumG == null || minimumG <= maximumG) {
            "Minimum capacity cannot exceed maximum capacity"
        }
    }
}

data class BrewerProfile(
    val id: BrewerProfileId,
    val familyId: MethodFamilyId,
    val displayName: String,
    val capacity: CapacityRangeG? = null,
    val compatibleFilterIds: Set<FilterProfileId> = emptySet(),
    val compatibleAccessoryIds: Set<AccessoryProfileId> = emptySet(),
    val compatibleBasketIds: Set<BasketProfileId> = emptySet(),
    val allowsIntentionallyUnfiltered: Boolean = false,
    val supportedRecipeVariants: Set<RecipeVariantId> = emptySet(),
    val outputModel: OutputModel,
    val safetyTags: Set<SafetyTag> = emptySet(),
)

data class EquipmentConfiguration(
    val brewerProfileId: BrewerProfileId,
    val capacityOverrideG: Double? = null,
    val filterSelection: FilterSelection = FilterSelection.Unspecified,
    val accessoryIds: Set<AccessoryProfileId> = emptySet(),
    val basketId: BasketProfileId? = null,
    val heatSource: HeatSourceClass = HeatSourceClass.NONE,
)

enum class CompatibilitySeverity {
    BLOCKING,
    CRITICAL_WARNING,
    ADVICE,
}

data class EquipmentCompatibilityIssue(
    val code: String,
    val severity: CompatibilitySeverity,
)

data class EquipmentCompatibilityResult(
    val issues: List<EquipmentCompatibilityIssue>,
) {
    val canStart: Boolean
        get() = issues.none { it.severity == CompatibilitySeverity.BLOCKING }
}

class EquipmentCompatibilityValidator(private val catalog: BrewingCatalog) {
    fun validate(configuration: EquipmentConfiguration): EquipmentCompatibilityResult {
        val profile = catalog.findBrewerProfile(configuration.brewerProfileId)
            ?: return EquipmentCompatibilityResult(
                listOf(EquipmentCompatibilityIssue("unknown_brewer_profile", CompatibilitySeverity.BLOCKING)),
            )

        val issues = buildList {
            validateCapacity(profile, configuration.capacityOverrideG)?.let(::add)
            validateFilterSelection(profile, configuration.filterSelection).forEach(::add)
            validateAccessories(profile, configuration.accessoryIds).forEach(::add)
            validateBasket(profile, configuration.basketId)?.let(::add)
            validateHeat(profile, configuration.heatSource)?.let(::add)
        }
        return EquipmentCompatibilityResult(issues)
    }

    private fun validateCapacity(
        profile: BrewerProfile,
        overrideG: Double?,
    ): EquipmentCompatibilityIssue? {
        if (overrideG == null) return null
        if (overrideG <= 0.0) {
            return EquipmentCompatibilityIssue("invalid_capacity_override", CompatibilitySeverity.BLOCKING)
        }
        val maximum = profile.capacity?.maximumG ?: return null
        return if (overrideG > maximum) {
            EquipmentCompatibilityIssue("capacity_exceeds_brewer", CompatibilitySeverity.BLOCKING)
        } else {
            null
        }
    }

    private fun validateFilterSelection(
        profile: BrewerProfile,
        selection: FilterSelection,
    ): List<EquipmentCompatibilityIssue> = when (selection) {
        FilterSelection.Unspecified -> emptyList()
        FilterSelection.IntentionallyUnfiltered -> {
            if (profile.allowsIntentionallyUnfiltered) emptyList()
            else listOf(EquipmentCompatibilityIssue("unfiltered_not_supported", CompatibilitySeverity.BLOCKING))
        }
        is FilterSelection.Stack -> selection.entries.sortedBy(FilterStackEntry::position).mapNotNull { entry ->
            val filter = catalog.findFilterProfile(entry.filterProfileId)
            when {
                filter == null -> EquipmentCompatibilityIssue(
                    "unknown_filter_profile",
                    CompatibilitySeverity.BLOCKING,
                )
                filter.medium == FilterMedium.UNFILTERED -> EquipmentCompatibilityIssue(
                    "unfiltered_must_be_explicit",
                    CompatibilitySeverity.BLOCKING,
                )
                entry.filterProfileId !in profile.compatibleFilterIds -> EquipmentCompatibilityIssue(
                    "filter_incompatible_with_brewer",
                    CompatibilitySeverity.BLOCKING,
                )
                else -> null
            }
        }
    }

    private fun validateAccessories(
        profile: BrewerProfile,
        accessoryIds: Set<AccessoryProfileId>,
    ): List<EquipmentCompatibilityIssue> = accessoryIds.mapNotNull { id ->
        when {
            catalog.findAccessoryProfile(id) == null -> EquipmentCompatibilityIssue(
                "unknown_accessory_profile",
                CompatibilitySeverity.BLOCKING,
            )
            id !in profile.compatibleAccessoryIds -> EquipmentCompatibilityIssue(
                "accessory_incompatible_with_brewer",
                CompatibilitySeverity.BLOCKING,
            )
            else -> null
        }
    }

    private fun validateBasket(
        profile: BrewerProfile,
        basketId: BasketProfileId?,
    ): EquipmentCompatibilityIssue? {
        if (basketId == null) return null
        return when {
            catalog.findBasketProfile(basketId) == null -> EquipmentCompatibilityIssue(
                "unknown_basket_profile",
                CompatibilitySeverity.BLOCKING,
            )
            basketId !in profile.compatibleBasketIds -> EquipmentCompatibilityIssue(
                "basket_incompatible_with_brewer",
                CompatibilitySeverity.BLOCKING,
            )
            else -> null
        }
    }

    private fun validateHeat(
        profile: BrewerProfile,
        heatSource: HeatSourceClass,
    ): EquipmentCompatibilityIssue? {
        if (heatSource == HeatSourceClass.NONE) return null
        val needsHeatSafety = SafetyTag.OPEN_FLAME in profile.safetyTags ||
            SafetyTag.HOT_METAL in profile.safetyTags ||
            SafetyTag.HOT_GLASS in profile.safetyTags
        return if (needsHeatSafety) {
            EquipmentCompatibilityIssue("heat_safety_required", CompatibilitySeverity.CRITICAL_WARNING)
        } else {
            null
        }
    }
}
