package com.adsamcik.starlitcoffee.domain.brewing

import java.time.LocalDate

/** Canonical source identity, retained independently of the app's coarser brewer taxonomy. */
@JvmInline
value class SourceBrewerProfileId(val value: String) {
    init {
        BrewerProfileId(value)
    }
}

/**
 * Stable identity of an exact source recipe approach.
 *
 * The canonical library has a prose `recipe_approach` but no separate approach
 * identifier. Its `recipe_id` is therefore the only non-invented exact key and
 * is intentionally reused here instead of persisting English guidance text.
 */
@JvmInline
value class ExactRecipeApproachId(val value: String) {
    init {
        BuiltInRecipeId(value)
    }
}

enum class P1RecipeEvidenceClass {
    OFFICIAL_PROCEDURE,
    ORIGINAL_CREATOR_RECIPE,
    EXPERT_ESTABLISHED,
    BATTLE_TESTED,
    COMPETITION_PROVEN,
    HISTORICALLY_DOCUMENTED,
}

enum class P1SourceConfidence {
    HIGH,
    MEDIUM_HIGH,
    MEDIUM,
    MEDIUM_LOW,
}

data class P1RecipeEvidence(
    val evidenceClass: P1RecipeEvidenceClass,
    val confidence: P1SourceConfidence,
    val sourceIds: Set<String>,
    val reviewedOn: LocalDate,
) {
    init {
        require(sourceIds.isNotEmpty()) { "A built-in recipe must retain at least one source ID" }
        require(sourceIds.all { it.startsWith("SRC-") }) { "Source IDs must use the canonical SRC- prefix" }
    }
}

/** One source ratio, including composite inputs such as hot water plus brew ice. */
data class P1RatioSemantics(
    val definition: RatioDefinition,
    val ratioValue: Double?,
    val includedDenominatorRoles: Set<QuantityRole> = setOf(definition.denominator),
) {
    init {
        require(ratioValue == null || ratioValue > 0.0) { "Ratio must be positive when resolved" }
        require(includedDenominatorRoles.isNotEmpty()) { "A ratio denominator cannot be empty" }
        require(definition.denominator in includedDenominatorRoles) {
            "The base ratio denominator must be included in its denominator roles"
        }
        require(definition.numerator !in includedDenominatorRoles) {
            "A ratio numerator cannot also be an input denominator"
        }
    }
}

enum class P1TemperatureBasis {
    USER_EXACT,
    USER_RANGE,
    USER_APPROXIMATE_RANGE,
    USER_STARTING_RANGE,
    HOT_UNSPECIFIED,
    MACHINE_CONTROLLED,
    MACHINE_CONTROLLED_REPORTED_RANGE,
    COLD_START_OBSERVATION_CONTROLLED,
}

data class P1TemperatureSemantics(
    val basis: P1TemperatureBasis,
    val minimumC: Double? = null,
    val maximumC: Double? = null,
) {
    init {
        require(minimumC == null || minimumC in 0.0..100.0) { "Minimum temperature is invalid" }
        require(maximumC == null || maximumC in 0.0..100.0) { "Maximum temperature is invalid" }
        require(minimumC == null || maximumC == null || minimumC <= maximumC) {
            "Minimum temperature cannot exceed maximum temperature"
        }
        when (basis) {
            P1TemperatureBasis.USER_EXACT -> require(minimumC != null && minimumC == maximumC)
            P1TemperatureBasis.USER_RANGE,
            P1TemperatureBasis.USER_APPROXIMATE_RANGE,
            P1TemperatureBasis.USER_STARTING_RANGE,
            P1TemperatureBasis.MACHINE_CONTROLLED_REPORTED_RANGE,
            -> require(minimumC != null && maximumC != null)

            P1TemperatureBasis.HOT_UNSPECIFIED,
            P1TemperatureBasis.MACHINE_CONTROLLED,
            P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED,
            -> require(minimumC == null && maximumC == null)
        }
    }
}

enum class P1TimeBasis {
    APPROXIMATE,
    RANGE,
    PRACTICAL_STARTING_RANGE,
    APPROXIMATE_WITH_OBSERVATION,
    GEOMETRY_DEPENDENT,
    OBSERVATION_DEPENDENT,
    MACHINE_SPECIFIC,
}

data class P1TimeSemantics(
    val basis: P1TimeBasis,
    val minimumSeconds: Int? = null,
    val maximumSeconds: Int? = null,
) {
    init {
        require(minimumSeconds == null || minimumSeconds >= 0) { "Minimum time cannot be negative" }
        require(maximumSeconds == null || maximumSeconds >= 0) { "Maximum time cannot be negative" }
        require(minimumSeconds == null || maximumSeconds == null || minimumSeconds <= maximumSeconds) {
            "Minimum time cannot exceed maximum time"
        }
        when (basis) {
            P1TimeBasis.APPROXIMATE,
            P1TimeBasis.RANGE,
            P1TimeBasis.PRACTICAL_STARTING_RANGE,
            P1TimeBasis.APPROXIMATE_WITH_OBSERVATION,
            -> require(minimumSeconds != null && maximumSeconds != null)

            P1TimeBasis.GEOMETRY_DEPENDENT,
            P1TimeBasis.OBSERVATION_DEPENDENT,
            P1TimeBasis.MACHINE_SPECIFIC,
            -> require(minimumSeconds == null && maximumSeconds == null)
        }
    }
}

enum class P1CompletionSemantics {
    DRAWDOWN,
    DRAWDOWN_AND_BREW_ICE_MELT,
    VALVE_RELEASE_AND_DRAWDOWN,
    FIRST_FOAM_RISE_BEFORE_ROLLING_BOIL,
    SECOND_FOAM_RISE_BEFORE_ROLLING_BOIL,
    MACHINE_CYCLE_DRAINAGE_AND_HOMOGENIZATION,
    MACHINE_CYCLE_AND_RESIDUAL_DRAINAGE,
    FIRST_AND_LAST_DRIP_WITHOUT_FORCED_PRESSURE,
    GRAVITY_DRIP_WITHOUT_FORCED_PRESSURE,
}

enum class P1UnresolvedGrindField {
    MEASURED_PARTICLE_SIZE,
    EXACT_GRINDER_SETTING,
}

/** An indivisible compatible equipment alternative; paired values must not be cross-combined. */
data class P1EquipmentOption(
    val filterSelection: FilterSelection,
    val basketId: BasketProfileId? = null,
    val accessoryIds: Set<AccessoryProfileId> = emptySet(),
)

data class BuiltInP1RecipeDefinition(
    val id: BuiltInRecipeId,
    val exactRecipeApproachId: ExactRecipeApproachId,
    val sourceMethodFamilyId: String,
    val sourceBrewerProfileId: SourceBrewerProfileId,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId,
    val quantities: BrewQuantities,
    val ratios: List<P1RatioSemantics>,
    val temperature: P1TemperatureSemantics,
    val expectedTime: P1TimeSemantics,
    val completion: P1CompletionSemantics,
    val evidence: P1RecipeEvidence,
    val unresolvedFields: Set<String>,
    val unresolvedGrindFields: Set<P1UnresolvedGrindField>,
    val orderedStageCount: Int,
    val equipmentOptions: List<P1EquipmentOption>,
) {
    init {
        MethodFamilyId(sourceMethodFamilyId)
        require(exactRecipeApproachId.value == id.value) {
            "Exact approach identity must retain the canonical recipe ID"
        }
        require(quantities.dryCoffeeDoseG > 0.0) { "P1 recipes require a positive coffee dose" }
        require(ratios.isNotEmpty()) { "P1 recipes must retain source ratio semantics" }
        require(orderedStageCount > 0) { "P1 recipes must retain their ordered source stage count" }
        require(equipmentOptions.isNotEmpty()) { "P1 recipes require an explicit equipment option" }
        require(unresolvedFields.isNotEmpty()) { "P1 recipes must retain unresolved source fields" }
        require(unresolvedGrindFields.containsAll(P1UnresolvedGrindField.entries)) {
            "P1 recipes must retain unresolved particle-size and grinder-setting fields"
        }
    }
}

/**
 * Immutable, domain-only transcription of the mandatory P1 recipes in the
 * 2026-07-27 canonical library. It contains no display copy or session logic.
 */
object BuiltInP1RecipeCatalog {
    const val SOURCE_SCHEMA_VERSION = "1.0.0"
    const val SOURCE_SHA256 = "aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d"
    val sourceExecutionDate: LocalDate = LocalDate.of(2026, 7, 27)

    private val brewWaterRatio = RatioDefinition(
        numerator = QuantityRole.DRY_COFFEE_DOSE,
        denominator = QuantityRole.BREW_WATER_INPUT,
    )

    val recipes: List<BuiltInP1RecipeDefinition> = listOf(
        recipe(
            id = "v60_official_15_250",
            sourceFamily = "manual_gravity",
            sourceProfile = "hario_v60_02",
            appFamily = "manual_gravity",
            appProfile = "v60_02",
            coffeeG = 15.0,
            inputG = 250.0,
            ratioValue = 16.67,
            temperature = userRange(92.0, 96.0),
            time = approximate(150),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-HARIO-V60-OFFICIAL"),
            stageCount = 5,
            equipment = listOf(singleFilterOption("cone_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "v60_rao_20_330",
            sourceFamily = "manual_gravity",
            sourceProfile = "plastic_hario_v60_02",
            appFamily = "manual_gravity",
            appProfile = "v60_02",
            coffeeG = 20.0,
            inputG = 330.0,
            ratioValue = 16.5,
            temperature = userExact(97.0),
            time = range(240, 270),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.ORIGINAL_CREATOR_RECIPE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-HARIO-RAO-V60"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("cone_paper")),
        ),
        recipe(
            id = "v60_kasuya_4_6_20_300",
            sourceFamily = "manual_gravity",
            sourceProfile = "hario_v60",
            appFamily = "manual_gravity",
            appProfile = "v60_unspecified",
            coffeeG = 20.0,
            inputG = 300.0,
            ratioValue = 15.0,
            temperature = userExact(92.0),
            time = approximate(210),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.ORIGINAL_CREATOR_RECIPE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-HARIO-KASUYA"),
            stageCount = 7,
            equipment = listOf(singleFilterOption("cone_paper")),
        ),
        recipe(
            id = "v60_kurasu_flash_16_150_70",
            sourceFamily = "manual_gravity",
            sourceProfile = "hario_v60_with_conical_paper",
            appFamily = "manual_gravity",
            appProfile = "v60_unspecified",
            coffeeG = 16.0,
            inputG = 150.0,
            iceG = 70.0,
            ratioValue = 9.375,
            additionalRatios = listOf(
                P1RatioSemantics(
                    definition = brewWaterRatio,
                    ratioValue = 13.75,
                    includedDenominatorRoles = setOf(QuantityRole.BREW_WATER_INPUT, QuantityRole.ICE),
                ),
            ),
            temperature = userExact(91.0),
            time = approximateWithObservation(130),
            completion = P1CompletionSemantics.DRAWDOWN_AND_BREW_ICE_MELT,
            evidenceClass = P1RecipeEvidenceClass.EXPERT_ESTABLISHED,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-KURASU-ICED"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("cone_paper")),
        ),
        recipe(
            id = "wave185_ozone_25_400",
            sourceFamily = "manual_gravity",
            sourceProfile = "kalita_wave_185",
            appFamily = "manual_gravity",
            appProfile = "manual_wave_185",
            coffeeG = 25.0,
            inputG = 400.0,
            ratioValue = 16.0,
            temperature = userExact(93.0),
            time = approximate(180),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.EXPERT_ESTABLISHED,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-KALITA-OZONE"),
            stageCount = 8,
            equipment = listOf(singleFilterOption("wave_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "wedge_pulse_23_5_400",
            sourceFamily = "manual_gravity",
            sourceProfile = "melitta_style_wedge_dripper_with_2_or_compatible_4_paper",
            appFamily = "manual_gravity",
            appProfile = "manual_wedge_generic",
            coffeeG = 23.5,
            inputG = 400.0,
            ratioValue = 17.02,
            temperature = approximateUserRange(91.0, 96.0),
            time = range(210, 240),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.BATTLE_TESTED,
            confidence = P1SourceConfidence.MEDIUM,
            sources = setOf("SRC-MELITTA-VOLTAGE", "SRC-CAFEC-FILTERS"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("wedge_paper")),
        ),
        recipe(
            id = "chemex_42_700",
            sourceFamily = "manual_gravity",
            sourceProfile = "chemex_six_cup_carafe",
            appFamily = "manual_gravity",
            appProfile = "manual_thick_paper_carafe",
            coffeeG = 42.0,
            inputG = 700.0,
            ratioValue = 16.67,
            temperature = startingUserRange(94.0, 96.0),
            time = startingRange(300, 390),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.EXPERT_ESTABLISHED,
            confidence = P1SourceConfidence.MEDIUM_HIGH,
            sources = setOf("SRC-CHEMEX-FAQ", "SRC-SCHMIEDER-FLOW"),
            stageCount = 7,
            equipment = listOf(singleFilterOption("chemex_six_cup_bonded_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "generic_conical_low_agitation_20_320",
            sourceFamily = "manual_gravity",
            sourceProfile = "generic_open_bottom_60_conical_dripper_with_compatible_paper",
            appFamily = "manual_gravity",
            appProfile = "manual_conical_generic",
            coffeeG = 20.0,
            inputG = 320.0,
            ratioValue = 16.0,
            temperature = startingUserRange(93.0, 96.0),
            time = P1TimeSemantics(P1TimeBasis.GEOMETRY_DEPENDENT),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.BATTLE_TESTED,
            confidence = P1SourceConfidence.MEDIUM,
            sources = setOf("SRC-HARIO-V60-OFFICIAL", "SRC-SCHMIEDER-FLOW", "SRC-COFFEEADASTRA-FINES"),
            stageCount = 5,
            equipment = listOf(singleFilterOption("cone_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "clever_water_first_15_250",
            sourceFamily = "steep_release",
            sourceProfile = "clever_style_bottom_actuated_dripper",
            appFamily = "steep_and_release",
            appProfile = "clever_style",
            coffeeG = 15.0,
            inputG = 250.0,
            ratioValue = 16.67,
            temperature = approximateUserRange(95.0, 100.0),
            time = approximate(160),
            completion = P1CompletionSemantics.VALVE_RELEASE_AND_DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.ORIGINAL_CREATOR_RECIPE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-CLEVER-HOFFMANN", "SRC-CLEVER-COFFEECHRONICLER"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("wedge_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "clever_coffee_first_15_250",
            sourceFamily = "steep_release",
            sourceProfile = "clever_style_bottom_actuated_dripper",
            appFamily = "steep_and_release",
            appProfile = "clever_style",
            coffeeG = 15.0,
            inputG = 250.0,
            ratioValue = 16.67,
            temperature = userExact(95.0),
            time = range(180, 210),
            completion = P1CompletionSemantics.VALVE_RELEASE_AND_DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.BATTLE_TESTED,
            confidence = P1SourceConfidence.MEDIUM,
            sources = setOf("SRC-CLEVER-COFFEECHRONICLER", "SRC-HARIO-V60-OFFICIAL"),
            stageCount = 4,
            equipment = listOf(singleFilterOption("wedge_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "switch_official_20_240",
            sourceFamily = "steep_release",
            sourceProfile = "hario_switch_02",
            appFamily = "steep_and_release",
            appProfile = "hario_switch",
            coffeeG = 20.0,
            inputG = 240.0,
            ratioValue = 12.0,
            temperature = P1TemperatureSemantics(P1TemperatureBasis.HOT_UNSPECIFIED),
            time = range(150, 180),
            completion = P1CompletionSemantics.VALVE_RELEASE_AND_DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-HARIO-SWITCH"),
            stageCount = 5,
            equipment = listOf(singleFilterOption("cone_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "switch_ole_boen_hybrid_16_5_240",
            sourceFamily = "steep_release",
            sourceProfile = "hario_switch_02",
            appFamily = "steep_and_release",
            appProfile = "hario_switch",
            coffeeG = 16.5,
            inputG = 240.0,
            ratioValue = 14.55,
            temperature = userExact(96.0),
            time = range(180, 195),
            completion = P1CompletionSemantics.VALVE_RELEASE_AND_DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.COMPETITION_PROVEN,
            confidence = P1SourceConfidence.MEDIUM_HIGH,
            sources = setOf("SRC-KURASU-SWITCH", "SRC-HARIO-SWITCH"),
            stageCount = 5,
            equipment = listOf(singleFilterOption("cone_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "switch_gravity_15_250",
            sourceFamily = "steep_release",
            sourceProfile = "hario_switch_02_used_open",
            appFamily = "steep_and_release",
            appProfile = "hario_switch",
            coffeeG = 15.0,
            inputG = 250.0,
            ratioValue = 16.67,
            temperature = userRange(92.0, 96.0),
            time = approximate(150),
            completion = P1CompletionSemantics.DRAWDOWN,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-HARIO-SWITCH", "SRC-HARIO-V60-OFFICIAL"),
            stageCount = 4,
            equipment = listOf(singleFilterOption("cone_paper")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "cezve_turkish_single_rise_6_65",
            sourceFamily = "heated_unfiltered",
            sourceProfile = "small_one_cup_cezve_ibrik_with_secure_handle_and_adequate_foam_headroom",
            appFamily = "heated_unfiltered",
            appProfile = "cezve_generic",
            coffeeG = 6.0,
            inputG = 65.0,
            ratioValue = 10.83,
            temperature = P1TemperatureSemantics(P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED),
            time = P1TimeSemantics(P1TimeBasis.OBSERVATION_DEPENDENT),
            completion = P1CompletionSemantics.FIRST_FOAM_RISE_BEFORE_ROLLING_BOIL,
            evidenceClass = P1RecipeEvidenceClass.HISTORICALLY_DOCUMENTED,
            confidence = P1SourceConfidence.MEDIUM_HIGH,
            sources = setOf("SRC-MEHMET-EFENDI", "SRC-UNESCO-TURKISH"),
            stageCount = 6,
            equipment = listOf(P1EquipmentOption(FilterSelection.IntentionallyUnfiltered)),
        ),
        recipe(
            id = "cezve_bounded_repeated_rise_12_130",
            sourceFamily = "heated_unfiltered",
            sourceProfile = "two_cup_cezve_with_ample_neck_headroom",
            appFamily = "heated_unfiltered",
            appProfile = "cezve_generic",
            coffeeG = 12.0,
            inputG = 130.0,
            ratioValue = 10.83,
            temperature = P1TemperatureSemantics(P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED),
            time = P1TimeSemantics(P1TimeBasis.OBSERVATION_DEPENDENT),
            completion = P1CompletionSemantics.SECOND_FOAM_RISE_BEFORE_ROLLING_BOIL,
            evidenceClass = P1RecipeEvidenceClass.HISTORICALLY_DOCUMENTED,
            confidence = P1SourceConfidence.MEDIUM_LOW,
            sources = setOf("SRC-UNESCO-TURKISH", "SRC-MEHMET-EFENDI"),
            stageCount = 6,
            equipment = listOf(P1EquipmentOption(FilterSelection.IntentionallyUnfiltered)),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "auto_batch_500_30",
            sourceFamily = "automatic_batch",
            sourceProfile = "one_button_home_batch_brewer_with_cone_or_flat_paper_basket_sized_for_500_g_water",
            appFamily = "automatic_batch",
            appProfile = "automatic_batch_generic",
            coffeeG = 30.0,
            inputG = 500.0,
            inputRole = QuantityRole.RESERVOIR_INPUT,
            ratioValue = 16.67,
            temperature = P1TemperatureSemantics(P1TemperatureBasis.MACHINE_CONTROLLED),
            time = P1TimeSemantics(P1TimeBasis.MACHINE_SPECIFIC),
            completion = P1CompletionSemantics.MACHINE_CYCLE_DRAINAGE_AND_HOMOGENIZATION,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-MOCCAMASTER-BREW", "SRC-SCA-CERTIFIED-HOME", "SRC-ECBC-STANDARD"),
            stageCount = 5,
            equipment = automaticBatchPaperOptions(),
        ),
        recipe(
            id = "auto_batch_1000_60",
            sourceFamily = "automatic_batch",
            sourceProfile = "home_batch_brewer_with_flat_or_cone_basket_rated_for_1_000_g_reservoir_water_and_thermal_or_glass_carafe",
            appFamily = "automatic_batch",
            appProfile = "automatic_batch_generic",
            coffeeG = 60.0,
            inputG = 1_000.0,
            inputRole = QuantityRole.RESERVOIR_INPUT,
            ratioValue = 16.67,
            temperature = P1TemperatureSemantics(P1TemperatureBasis.MACHINE_CONTROLLED),
            time = P1TimeSemantics(P1TimeBasis.MACHINE_SPECIFIC),
            completion = P1CompletionSemantics.MACHINE_CYCLE_DRAINAGE_AND_HOMOGENIZATION,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-MOCCAMASTER-BREW", "SRC-SCA-CERTIFIED-HOME", "SRC-ECBC-STANDARD"),
            stageCount = 4,
            equipment = automaticBatchPaperOptions(),
        ),
        recipe(
            id = "auto_cupone_20_300",
            sourceFamily = "automatic_batch",
            sourceProfile = "technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir",
            appFamily = "automatic_batch",
            appProfile = "automatic_single_cup_generic",
            coffeeG = 20.0,
            inputG = null,
            inputRole = QuantityRole.RESERVOIR_INPUT,
            ratioValue = null,
            temperature = P1TemperatureSemantics(
                basis = P1TemperatureBasis.MACHINE_CONTROLLED_REPORTED_RANGE,
                minimumC = 92.0,
                maximumC = 96.0,
            ),
            time = approximateWithObservation(240),
            completion = P1CompletionSemantics.MACHINE_CYCLE_AND_RESIDUAL_DRAINAGE,
            evidenceClass = P1RecipeEvidenceClass.OFFICIAL_PROCEDURE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-CUPONE-MANUAL"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("number_one_paper", "automatic_number_one_basket")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "phin_gravity_14_118",
            sourceFamily = "phin",
            sourceProfile = "single_serving_gravity_phin_with_loose_drop_in_press_disc",
            appFamily = "restricted_flow_gravity_concentrate",
            appProfile = "vietnamese_phin",
            coffeeG = 14.0,
            inputG = 118.0,
            ratioValue = 8.43,
            temperature = userRange(91.0, 93.0),
            time = approximate(300),
            completion = P1CompletionSemantics.FIRST_AND_LAST_DRIP_WITHOUT_FORCED_PRESSURE,
            evidenceClass = P1RecipeEvidenceClass.ORIGINAL_CREATOR_RECIPE,
            confidence = P1SourceConfidence.HIGH,
            sources = setOf("SRC-NGUYEN-PHIN"),
            stageCount = 7,
            equipment = listOf(singleFilterOption("phin_metal")),
            beverageOutputUnresolved = true,
        ),
        recipe(
            id = "phin_screw_18_120",
            sourceFamily = "phin",
            sourceProfile = "single_serving_screw_insert_phin_of_approximately_120_150_ml_chamber_capacity",
            appFamily = "restricted_flow_gravity_concentrate",
            appProfile = "vietnamese_phin",
            coffeeG = 18.0,
            inputG = 120.0,
            ratioValue = 6.67,
            temperature = startingUserRange(94.0, 98.0),
            time = range(300, 480),
            completion = P1CompletionSemantics.GRAVITY_DRIP_WITHOUT_FORCED_PRESSURE,
            evidenceClass = P1RecipeEvidenceClass.BATTLE_TESTED,
            confidence = P1SourceConfidence.MEDIUM,
            sources = setOf("SRC-TRUNGNGUYEN-PHIN", "SRC-GOURMETKAVA-PHIN", "SRC-NGUYEN-PHIN"),
            stageCount = 6,
            equipment = listOf(singleFilterOption("phin_metal", accessoryIds = setOf("phin_screw_insert"))),
        ),
    )

    private val recipesById: Map<BuiltInRecipeId, BuiltInP1RecipeDefinition> = recipes.associateBy { it.id }

    init {
        require(recipes.size == 20) { "The mandatory P1 catalog must contain exactly 20 recipes" }
        require(recipesById.size == recipes.size) { "Built-in P1 recipe IDs must be unique" }
        require(recipes.count { it.sourceMethodFamilyId == "manual_gravity" } == 8)
        require(recipes.count { it.sourceMethodFamilyId == "steep_release" } == 5)
        require(recipes.count { it.sourceMethodFamilyId == "heated_unfiltered" } == 2)
        require(recipes.count { it.sourceMethodFamilyId == "automatic_batch" } == 3)
        require(recipes.count { it.sourceMethodFamilyId == "phin" } == 2)
    }

    fun find(id: BuiltInRecipeId): BuiltInP1RecipeDefinition? = recipesById[id]

    @Suppress("LongParameterList")
    private fun recipe(
        id: String,
        sourceFamily: String,
        sourceProfile: String,
        appFamily: String,
        appProfile: String,
        coffeeG: Double,
        inputG: Double?,
        inputRole: QuantityRole = QuantityRole.BREW_WATER_INPUT,
        iceG: Double = 0.0,
        ratioValue: Double?,
        additionalRatios: List<P1RatioSemantics> = emptyList(),
        temperature: P1TemperatureSemantics,
        time: P1TimeSemantics,
        completion: P1CompletionSemantics,
        evidenceClass: P1RecipeEvidenceClass,
        confidence: P1SourceConfidence,
        sources: Set<String>,
        stageCount: Int,
        equipment: List<P1EquipmentOption>,
        beverageOutputUnresolved: Boolean = false,
    ): BuiltInP1RecipeDefinition {
        require(inputRole == QuantityRole.BREW_WATER_INPUT || inputRole == QuantityRole.RESERVOIR_INPUT)
        val ratioDefinition = RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, inputRole)
        val quantities = BrewQuantities(
            dryCoffeeDoseG = coffeeG,
            brewWaterInputG = inputG.takeIf { inputRole == QuantityRole.BREW_WATER_INPUT },
            reservoirInputG = inputG.takeIf { inputRole == QuantityRole.RESERVOIR_INPUT },
            iceG = iceG,
        )
        return BuiltInP1RecipeDefinition(
            id = BuiltInRecipeId(id),
            exactRecipeApproachId = ExactRecipeApproachId(id),
            sourceMethodFamilyId = sourceFamily,
            sourceBrewerProfileId = SourceBrewerProfileId(sourceProfile),
            methodFamilyId = MethodFamilyId(appFamily),
            brewerProfileId = BrewerProfileId(appProfile),
            quantities = quantities,
            ratios = listOf(P1RatioSemantics(ratioDefinition, ratioValue)) + additionalRatios,
            temperature = temperature,
            expectedTime = time,
            completion = completion,
            evidence = P1RecipeEvidence(evidenceClass, confidence, sources, sourceExecutionDate),
            unresolvedFields = buildSet {
                if (beverageOutputUnresolved) add("beverage_output")
                add("water_composition")
                add("measured_particle_size")
                add("exact_grinder_setting")
            },
            unresolvedGrindFields = P1UnresolvedGrindField.entries.toSet(),
            orderedStageCount = stageCount,
            equipmentOptions = equipment,
        )
    }

    private fun singleFilterOption(
        filterId: String,
        basketId: String? = null,
        accessoryIds: Set<String> = emptySet(),
    ): P1EquipmentOption = P1EquipmentOption(
        filterSelection = FilterSelection.Stack(
            listOf(FilterStackEntry(FilterProfileId(filterId), position = 0)),
        ),
        basketId = basketId?.let(::BasketProfileId),
        accessoryIds = accessoryIds.mapTo(mutableSetOf(), ::AccessoryProfileId),
    )

    private fun automaticBatchPaperOptions(): List<P1EquipmentOption> = listOf(
        singleFilterOption("cone_paper", "automatic_cone_basket"),
        singleFilterOption("flat_basket_paper", "automatic_flat_basket"),
    )

    private fun userExact(value: Double) = P1TemperatureSemantics(P1TemperatureBasis.USER_EXACT, value, value)

    private fun userRange(minimum: Double, maximum: Double) =
        P1TemperatureSemantics(P1TemperatureBasis.USER_RANGE, minimum, maximum)

    private fun approximateUserRange(minimum: Double, maximum: Double) =
        P1TemperatureSemantics(P1TemperatureBasis.USER_APPROXIMATE_RANGE, minimum, maximum)

    private fun startingUserRange(minimum: Double, maximum: Double) =
        P1TemperatureSemantics(P1TemperatureBasis.USER_STARTING_RANGE, minimum, maximum)

    private fun approximate(seconds: Int) = P1TimeSemantics(P1TimeBasis.APPROXIMATE, seconds, seconds)

    private fun approximateWithObservation(seconds: Int) =
        P1TimeSemantics(P1TimeBasis.APPROXIMATE_WITH_OBSERVATION, seconds, seconds)

    private fun range(minimumSeconds: Int, maximumSeconds: Int) =
        P1TimeSemantics(P1TimeBasis.RANGE, minimumSeconds, maximumSeconds)

    private fun startingRange(minimumSeconds: Int, maximumSeconds: Int) =
        P1TimeSemantics(P1TimeBasis.PRACTICAL_STARTING_RANGE, minimumSeconds, maximumSeconds)
}
