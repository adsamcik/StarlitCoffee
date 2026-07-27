package com.adsamcik.starlitcoffee.domain.brewing

data class MethodFamilyDefinition(
    val id: MethodFamilyId,
    val displayName: String,
)

class BrewingCatalog(
    val methodFamilies: List<MethodFamilyDefinition>,
    val brewerProfiles: List<BrewerProfile>,
    val filterProfiles: List<FilterProfile>,
    val accessoryProfiles: List<AccessoryProfile> = emptyList(),
    val basketProfiles: List<BasketProfile> = emptyList(),
    private val methodAliases: Map<String, BrewerProfileId> = emptyMap(),
) {
    init {
        requireUnique(methodFamilies.map { it.id.value }, "method family")
        requireUnique(brewerProfiles.map { it.id.value }, "brewer profile")
        requireUnique(filterProfiles.map { it.id.value }, "filter profile")
        requireUnique(accessoryProfiles.map { it.id.value }, "accessory profile")
        requireUnique(basketProfiles.map { it.id.value }, "basket profile")
        require(brewerProfiles.all { profile -> methodFamilies.any { it.id == profile.familyId } }) {
            "Every brewer profile must reference a known method family"
        }
        require(methodAliases.values.all { alias -> brewerProfiles.any { it.id == alias } }) {
            "Every legacy alias must reference a known brewer profile"
        }
    }

    fun findMethodFamily(id: MethodFamilyId): MethodFamilyDefinition? =
        methodFamilies.find { it.id == id }

    fun findBrewerProfile(id: BrewerProfileId): BrewerProfile? =
        brewerProfiles.find { it.id == id }

    fun findFilterProfile(id: FilterProfileId): FilterProfile? =
        filterProfiles.find { it.id == id }

    fun findAccessoryProfile(id: AccessoryProfileId): AccessoryProfile? =
        accessoryProfiles.find { it.id == id }

    fun findBasketProfile(id: BasketProfileId): BasketProfile? =
        basketProfiles.find { it.id == id }

    fun resolveLegacyMethod(rawId: String): CatalogResolution<BrewerProfileId> {
        val direct = brewerProfiles.find { it.id.value == rawId }?.id
        val alias = methodAliases[rawId]
        return (direct ?: alias)?.let { CatalogResolution.Known(it) }
            ?: CatalogResolution.Unknown(rawId)
    }

    private fun requireUnique(ids: List<String>, label: String) {
        require(ids.distinct().size == ids.size) { "Duplicate $label ID" }
    }
}

object BuiltinBrewingCatalog {
    val instance: BrewingCatalog = BrewingCatalog(
        methodFamilies = listOf(
            MethodFamilyDefinition(MethodFamilyId("valve_controlled_no_bypass"), "Valve-controlled no-bypass"),
            MethodFamilyDefinition(MethodFamilyId("manual_gravity"), "Manual gravity"),
            MethodFamilyDefinition(MethodFamilyId("full_immersion_press"), "Full immersion press"),
            MethodFamilyDefinition(MethodFamilyId("chamber_plunger"), "Chamber and plunger"),
            MethodFamilyDefinition(MethodFamilyId("espresso"), "Espresso"),
            MethodFamilyDefinition(MethodFamilyId("steam_pressure_multichamber"), "Steam-pressure multi-chamber"),
            MethodFamilyDefinition(MethodFamilyId("cold_immersion"), "Cold immersion"),
            MethodFamilyDefinition(MethodFamilyId("steep_and_release"), "Steep and release"),
            MethodFamilyDefinition(MethodFamilyId("heated_unfiltered"), "Heated unfiltered"),
            MethodFamilyDefinition(MethodFamilyId("automatic_batch"), "Automatic batch"),
            MethodFamilyDefinition(
                MethodFamilyId("restricted_flow_gravity_concentrate"),
                "Restricted-flow gravity concentrate",
            ),
        ),
        filterProfiles = listOf(
            FilterProfile(
                id = FilterProfileId("pulsar_paper"),
                medium = FilterMedium.PAPER,
                geometry = FilterGeometry.BREWER_SPECIFIC,
                disposable = true,
                evidenceConfidence = EvidenceConfidence.HIGH,
            ),
            FilterProfile(
                id = FilterProfileId("pulsar_19k_metal"),
                medium = FilterMedium.METAL,
                geometry = FilterGeometry.BREWER_SPECIFIC,
                disposable = false,
                evidenceConfidence = EvidenceConfidence.HIGH,
            ),
            FilterProfile(
                id = FilterProfileId("pulsar_40k_metal"),
                medium = FilterMedium.METAL,
                geometry = FilterGeometry.BREWER_SPECIFIC,
                disposable = false,
                evidenceConfidence = EvidenceConfidence.HIGH,
            ),
            FilterProfile(FilterProfileId("cone_paper"), FilterMedium.PAPER, FilterGeometry.CONE),
            FilterProfile(FilterProfileId("wave_paper"), FilterMedium.PAPER, FilterGeometry.WAVE),
            FilterProfile(FilterProfileId("wedge_paper"), FilterMedium.PAPER, FilterGeometry.WEDGE),
            FilterProfile(FilterProfileId("aeropress_paper"), FilterMedium.PAPER, FilterGeometry.DISC),
            FilterProfile(FilterProfileId("aeropress_metal"), FilterMedium.METAL, FilterGeometry.DISC),
            FilterProfile(FilterProfileId("espresso_paper_disc"), FilterMedium.PAPER, FilterGeometry.DISC),
            FilterProfile(FilterProfileId("cold_brew_filter_bag"), FilterMedium.CLOTH, FilterGeometry.SOCK),
            FilterProfile(FilterProfileId("phin_metal"), FilterMedium.METAL, FilterGeometry.BREWER_SPECIFIC),
        ),
        accessoryProfiles = listOf(
            AccessoryProfile(
                AccessoryProfileId("aeropress_flow_control_cap"),
                setOf(AccessoryBehavior.PREVENTS_PASSIVE_DRIP, AccessoryBehavior.PRESSURE_ACTUATED_FLOW),
            ),
            AccessoryProfile(
                AccessoryProfileId("phin_screw_insert"),
                setOf(AccessoryBehavior.MANUAL_FLOW_CONTROL),
            ),
        ),
        basketProfiles = listOf(
            BasketProfile(BasketProfileId("espresso_generic_single"), pressurised = null, geometry = "single"),
            BasketProfile(BasketProfileId("espresso_generic_double"), pressurised = null, geometry = "double"),
        ),
        brewerProfiles = listOf(
            profile(
                "pulsar_standard",
                "valve_controlled_no_bypass",
                "Pulsar",
                OutputModel.BrewWaterMinusRetention(2.0),
                filters = setOf("pulsar_paper", "pulsar_19k_metal", "pulsar_40k_metal"),
                safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.OVERFLOW),
            ),
            profile("v60_unspecified", "manual_gravity", "V60", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("v60_01", "manual_gravity", "V60 01", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("v60_02", "manual_gravity", "V60 02", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("v60_03", "manual_gravity", "V60 03", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("manual_conical_generic", "manual_gravity", "Generic conical dripper", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("manual_wave_155", "manual_gravity", "Flat-bottom wave 155", OutputModel.BrewWaterMinusRetention(2.0), setOf("wave_paper")),
            profile("manual_wave_185", "manual_gravity", "Flat-bottom wave 185", OutputModel.BrewWaterMinusRetention(2.0), setOf("wave_paper")),
            profile("manual_wedge_generic", "manual_gravity", "Wedge dripper", OutputModel.BrewWaterMinusRetention(2.0), setOf("wedge_paper")),
            profile("manual_thick_paper_carafe", "manual_gravity", "Thick-paper carafe brewer", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper")),
            profile("french_press_generic", "full_immersion_press", "French press", OutputModel.BrewWaterMinusRetention(2.0)),
            profile("aeropress_standard", "chamber_plunger", "AeroPress", OutputModel.BrewWaterMinusRetention(2.0), setOf("aeropress_paper", "aeropress_metal"), accessories = setOf("aeropress_flow_control_cap"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.UNSTABLE_ORIENTATION)),
            profile("aeropress_xl", "chamber_plunger", "AeroPress XL", OutputModel.BrewWaterMinusRetention(2.0), setOf("aeropress_paper", "aeropress_metal"), accessories = setOf("aeropress_flow_control_cap"), safety = setOf(SafetyTag.HOT_LIQUID)),
            profile("espresso_pump_generic", "espresso", "Generic pump espresso", OutputModel.DirectTargetBeverageYield, setOf("espresso_paper_disc"), baskets = setOf("espresso_generic_single", "espresso_generic_double"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.PRESSURE)),
            profile("espresso_lever_generic", "espresso", "Generic lever espresso", OutputModel.DirectTargetBeverageYield, setOf("espresso_paper_disc"), baskets = setOf("espresso_generic_single", "espresso_generic_double"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.PRESSURE)),
            profile("espresso_portable_generic", "espresso", "Generic portable espresso", OutputModel.DirectTargetBeverageYield, setOf("espresso_paper_disc"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.PRESSURE)),
            profile("moka_generic_unspecified", "steam_pressure_multichamber", "Moka pot", OutputModel.ReservoirToEstimatedOutput(), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.HOT_METAL, SafetyTag.PRESSURE)),
            profile("cold_immersion_generic", "cold_immersion", "Cold-brew vessel", OutputModel.CollectedConcentrate(2.0), setOf("cold_brew_filter_bag"), safety = setOf(SafetyTag.FOOD_STORAGE)),
            profile("clever_style", "steep_and_release", "Clever-style brewer", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.OVERFLOW)),
            profile("hario_switch", "steep_and_release", "Hario Switch", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.HOT_GLASS, SafetyTag.OVERFLOW)),
            profile("valve_release_generic", "steep_and_release", "Generic valve-release brewer", OutputModel.BrewWaterMinusRetention(2.0), setOf("cone_paper"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.OVERFLOW)),
            profile("cezve_generic", "heated_unfiltered", "Cezve / ibrik", OutputModel.PreparedUnfilteredVolume, allowsUnfiltered = true, safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.HOT_METAL, SafetyTag.OPEN_FLAME)),
            profile("automatic_batch_generic", "automatic_batch", "Automatic batch brewer", OutputModel.ReservoirToEstimatedOutput(), setOf("cone_paper", "wave_paper"), safety = setOf(SafetyTag.HOT_LIQUID)),
            profile("automatic_single_cup_generic", "automatic_batch", "Podless single-cup brewer", OutputModel.ReservoirToEstimatedOutput(), setOf("cone_paper"), safety = setOf(SafetyTag.HOT_LIQUID)),
            profile("vietnamese_phin", "restricted_flow_gravity_concentrate", "Vietnamese phin", OutputModel.CollectedConcentrate(0.0), setOf("phin_metal"), accessories = setOf("phin_screw_insert"), safety = setOf(SafetyTag.HOT_LIQUID, SafetyTag.HOT_METAL)),
        ),
        methodAliases = mapOf(
            "PULSAR" to BrewerProfileId("pulsar_standard"),
            "V60" to BrewerProfileId("v60_unspecified"),
            "FRENCH_PRESS" to BrewerProfileId("french_press_generic"),
            "AEROPRESS" to BrewerProfileId("aeropress_standard"),
            "ESPRESSO" to BrewerProfileId("espresso_pump_generic"),
            "MOKA_POT" to BrewerProfileId("moka_generic_unspecified"),
            "COLD_BREW" to BrewerProfileId("cold_immersion_generic"),
        ),
    )

    private fun profile(
        id: String,
        familyId: String,
        displayName: String,
        outputModel: OutputModel,
        filters: Set<String> = emptySet(),
        accessories: Set<String> = emptySet(),
        baskets: Set<String> = emptySet(),
        allowsUnfiltered: Boolean = false,
        safety: Set<SafetyTag> = emptySet(),
    ): BrewerProfile = BrewerProfile(
        id = BrewerProfileId(id),
        familyId = MethodFamilyId(familyId),
        displayName = displayName,
        compatibleFilterIds = filters.mapTo(mutableSetOf(), ::FilterProfileId),
        compatibleAccessoryIds = accessories.mapTo(mutableSetOf(), ::AccessoryProfileId),
        compatibleBasketIds = baskets.mapTo(mutableSetOf(), ::BasketProfileId),
        allowsIntentionallyUnfiltered = allowsUnfiltered,
        outputModel = outputModel,
        safetyTags = safety,
    )
}
