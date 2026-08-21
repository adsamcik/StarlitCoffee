package com.adsamcik.starlitcoffee.domain.brewing

/** A water-to-coffee ratio expressed as water grams per gram of dry coffee. */
data class CoffeeWaterRatioDefault(
    val waterPerCoffee: Double,
) {
    init {
        require(waterPerCoffee.isFinite() && waterPerCoffee > 0.0) {
            "Water-to-coffee ratio must be a finite positive value"
        }
    }
}

/** A temperature recommendation that never pretends a brewer exposes a setting it does not. */
sealed interface TemperatureRecommendation {
    data class CelsiusRange(
        val minimumC: Int,
        val maximumC: Int,
    ) : TemperatureRecommendation {
        init {
            require(minimumC in 0..100) { "Minimum temperature must be between 0 and 100°C" }
            require(maximumC in minimumC..100) {
                "Maximum temperature must be between the minimum and 100°C"
            }
        }
    }

    /** The temperature is set by a heat source or hidden inside a machine. */
    data object Unavailable : TemperatureRecommendation
}

/** A time recommendation is guidance only; it does not alter session completion rules. */
sealed interface BrewTimeRecommendation {
    data class SecondsRange(
        val minimumSeconds: Int,
        val maximumSeconds: Int,
    ) : BrewTimeRecommendation {
        init {
            require(minimumSeconds > 0) { "Minimum brew time must be positive" }
            require(maximumSeconds >= minimumSeconds) {
                "Maximum brew time must be at least the minimum"
            }
        }
    }

    /** A visible physical change, such as foam rising, determines completion. */
    data object ObservedCompletion : BrewTimeRecommendation

    /** The machine owns its cycle; the app must not invent an internal timer. */
    data object MachineControlled : BrewTimeRecommendation
}

/**
 * Capacity remains a property of an actual configuration when the profile is
 * intentionally generic or its form factor does not establish a safe limit.
 */
sealed interface CapacityRecommendation {
    data class ConfirmedRange(val range: CapacityRangeG) : CapacityRecommendation

    data object RequiresEquipmentConfiguration : CapacityRecommendation
}

/** The kind of output that should be presented and logged for a profile. */
enum class PrimaryOutputQuantity {
    ESTIMATED_BEVERAGE_YIELD,
    COLLECTED_CONCENTRATE,
    PREPARED_UNFILTERED_VOLUME,
}

/**
 * Keeps the input field, ratio, and output model together so a generic UI
 * cannot silently turn a reservoir input into brew water or a concentrate
 * into a ready-to-drink beverage.
 */
data class ProfileQuantitySemantics(
    val ratioDefinition: RatioDefinition,
    val inputRole: QuantityRole,
    val outputModel: OutputModel,
    val primaryOutput: PrimaryOutputQuantity,
    val servingIsSeparateFromExtraction: Boolean = false,
) {
    init {
        require(ratioDefinition.numerator == QuantityRole.DRY_COFFEE_DOSE) {
            "Built-in defaults express ratio from dry coffee"
        }
        require(ratioDefinition.denominator == inputRole) {
            "Ratio denominator must match the profile input role"
        }
        require(inputRole in setOf(QuantityRole.BREW_WATER_INPUT, QuantityRole.RESERVOIR_INPUT)) {
            "Built-in defaults support only brew-water or reservoir inputs"
        }
        require(primaryOutput.isCompatibleWith(outputModel)) {
            "Output quantity must match the output model"
        }
    }

    fun defaultQuantities(
        dryCoffeeDoseG: Double,
        ratio: CoffeeWaterRatioDefault,
    ): BrewQuantities {
        require(dryCoffeeDoseG.isFinite() && dryCoffeeDoseG >= 0.0) {
            "Dry coffee dose must be a finite non-negative value"
        }
        val waterInputG = dryCoffeeDoseG * ratio.waterPerCoffee
        return when (inputRole) {
            QuantityRole.BREW_WATER_INPUT -> BrewQuantities(
                dryCoffeeDoseG = dryCoffeeDoseG,
                brewWaterInputG = waterInputG,
            )

            QuantityRole.RESERVOIR_INPUT -> BrewQuantities(
                dryCoffeeDoseG = dryCoffeeDoseG,
                reservoirInputG = waterInputG,
            )

            else -> error("Unsupported built-in input role: $inputRole")
        }
    }

    private fun PrimaryOutputQuantity.isCompatibleWith(model: OutputModel): Boolean = when (this) {
        PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD -> {
            model is OutputModel.BrewWaterMinusRetention ||
                model is OutputModel.ReservoirToEstimatedOutput
        }

        PrimaryOutputQuantity.COLLECTED_CONCENTRATE -> model is OutputModel.CollectedConcentrate
        PrimaryOutputQuantity.PREPARED_UNFILTERED_VOLUME -> model == OutputModel.PreparedUnfilteredVolume
    }
}

data class BrewerProfileRecipeDefaults(
    val brewerProfileId: BrewerProfileId,
    val ratio: CoffeeWaterRatioDefault,
    val temperature: TemperatureRecommendation,
    val brewTime: BrewTimeRecommendation,
    val capacity: CapacityRecommendation,
    val quantitySemantics: ProfileQuantitySemantics,
)

/**
 * Conservative starting defaults for built-in P1 profiles and the expanded
 * manual-gravity catalogue. These are profile-keyed recommendations, not a
 * replacement for recipe snapshots or equipment validation.
 */
object BuiltinBrewerProfileRecipeDefaults {

    val supportedProfileIds: Set<BrewerProfileId>
        get() = defaultsByProfileId.keys

    fun find(profileId: BrewerProfileId): BrewerProfileRecipeDefaults? = defaultsByProfileId[profileId]

    /**
     * Builds only the input quantities implied by a profile's default ratio.
     * Output remains the responsibility of [ProfileQuantitySemantics.outputModel]
     * and may be measured or observed later in the brew flow.
     */
    fun defaultQuantitiesForDose(
        profileId: BrewerProfileId,
        dryCoffeeDoseG: Double,
    ): BrewQuantities? {
        val defaults = find(profileId) ?: return null
        return defaults.quantitySemantics.defaultQuantities(dryCoffeeDoseG, defaults.ratio)
    }

    private val defaultsByProfileId: Map<BrewerProfileId, BrewerProfileRecipeDefaults> by lazy {
        buildMap {
            MANUAL_GRAVITY_PROFILE_IDS.forEach { profileId ->
                put(profileId, manualGravityDefaults(profileId))
            }
            STEEP_AND_RELEASE_PROFILE_IDS.forEach { profileId ->
                put(profileId, steepAndReleaseDefaults(profileId))
            }
            put(CEZVE_GENERIC, cezveDefaults())
            put(AUTOMATIC_BATCH_GENERIC, automaticDefaults(AUTOMATIC_BATCH_GENERIC))
            put(MOCCAMASTER_KBGV_SELECT, automaticDefaults(MOCCAMASTER_KBGV_SELECT))
            put(AUTOMATIC_SINGLE_CUP_GENERIC, automaticDefaults(AUTOMATIC_SINGLE_CUP_GENERIC))
            put(VIETNAMESE_PHIN, vietnamesePhinDefaults())
        }
    }

    private fun manualGravityDefaults(profileId: BrewerProfileId): BrewerProfileRecipeDefaults = defaults(
        profileId = profileId,
        ratio = CoffeeWaterRatioDefault(waterPerCoffee = 16.0),
        temperature = TemperatureRecommendation.CelsiusRange(minimumC = 93, maximumC = 96),
        brewTime = BrewTimeRecommendation.SecondsRange(minimumSeconds = 150, maximumSeconds = 210),
        quantitySemantics = ProfileQuantitySemantics(
            ratioDefinition = RatioDefinition(
                numerator = QuantityRole.DRY_COFFEE_DOSE,
                denominator = QuantityRole.BREW_WATER_INPUT,
            ),
            inputRole = QuantityRole.BREW_WATER_INPUT,
            outputModel = OutputModel.BrewWaterMinusRetention(retainedWaterGPerCoffeeG = 2.0),
            primaryOutput = PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
        ),
    )

    private fun steepAndReleaseDefaults(profileId: BrewerProfileId): BrewerProfileRecipeDefaults = defaults(
        profileId = profileId,
        ratio = CoffeeWaterRatioDefault(waterPerCoffee = 16.0),
        temperature = TemperatureRecommendation.CelsiusRange(minimumC = 90, maximumC = 96),
        brewTime = BrewTimeRecommendation.SecondsRange(minimumSeconds = 120, maximumSeconds = 240),
        quantitySemantics = ProfileQuantitySemantics(
            ratioDefinition = RatioDefinition(
                numerator = QuantityRole.DRY_COFFEE_DOSE,
                denominator = QuantityRole.BREW_WATER_INPUT,
            ),
            inputRole = QuantityRole.BREW_WATER_INPUT,
            outputModel = OutputModel.BrewWaterMinusRetention(retainedWaterGPerCoffeeG = 2.0),
            primaryOutput = PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
        ),
    )

    private fun cezveDefaults(): BrewerProfileRecipeDefaults = defaults(
        profileId = CEZVE_GENERIC,
        ratio = CoffeeWaterRatioDefault(waterPerCoffee = 10.0),
        temperature = TemperatureRecommendation.Unavailable,
        brewTime = BrewTimeRecommendation.ObservedCompletion,
        quantitySemantics = ProfileQuantitySemantics(
            ratioDefinition = RatioDefinition(
                numerator = QuantityRole.DRY_COFFEE_DOSE,
                denominator = QuantityRole.BREW_WATER_INPUT,
            ),
            inputRole = QuantityRole.BREW_WATER_INPUT,
            outputModel = OutputModel.PreparedUnfilteredVolume,
            primaryOutput = PrimaryOutputQuantity.PREPARED_UNFILTERED_VOLUME,
        ),
    )

    private fun automaticDefaults(profileId: BrewerProfileId): BrewerProfileRecipeDefaults = defaults(
        profileId = profileId,
        ratio = CoffeeWaterRatioDefault(waterPerCoffee = 16.0),
        temperature = TemperatureRecommendation.Unavailable,
        brewTime = BrewTimeRecommendation.MachineControlled,
        quantitySemantics = ProfileQuantitySemantics(
            ratioDefinition = RatioDefinition(
                numerator = QuantityRole.DRY_COFFEE_DOSE,
                denominator = QuantityRole.RESERVOIR_INPUT,
            ),
            inputRole = QuantityRole.RESERVOIR_INPUT,
            outputModel = OutputModel.ReservoirToEstimatedOutput(),
            primaryOutput = PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
        ),
    )

    private fun vietnamesePhinDefaults(): BrewerProfileRecipeDefaults = defaults(
        profileId = VIETNAMESE_PHIN,
        ratio = CoffeeWaterRatioDefault(waterPerCoffee = 5.0),
        temperature = TemperatureRecommendation.CelsiusRange(minimumC = 90, maximumC = 96),
        brewTime = BrewTimeRecommendation.SecondsRange(minimumSeconds = 180, maximumSeconds = 360),
        quantitySemantics = ProfileQuantitySemantics(
            ratioDefinition = RatioDefinition(
                numerator = QuantityRole.DRY_COFFEE_DOSE,
                denominator = QuantityRole.BREW_WATER_INPUT,
            ),
            inputRole = QuantityRole.BREW_WATER_INPUT,
            outputModel = OutputModel.CollectedConcentrate(retainedWaterGPerCoffeeG = 0.0),
            primaryOutput = PrimaryOutputQuantity.COLLECTED_CONCENTRATE,
            servingIsSeparateFromExtraction = true,
        ),
    )

    private fun defaults(
        profileId: BrewerProfileId,
        ratio: CoffeeWaterRatioDefault,
        temperature: TemperatureRecommendation,
        brewTime: BrewTimeRecommendation,
        quantitySemantics: ProfileQuantitySemantics,
    ): BrewerProfileRecipeDefaults = BrewerProfileRecipeDefaults(
        brewerProfileId = profileId,
        ratio = ratio,
        temperature = temperature,
        brewTime = brewTime,
        capacity = CapacityRecommendation.RequiresEquipmentConfiguration,
        quantitySemantics = quantitySemantics,
    )

    private val MANUAL_GRAVITY_PROFILE_IDS = setOf(
        BrewerProfileId("v60_unspecified"),
        BrewerProfileId("v60_01"),
        BrewerProfileId("v60_02"),
        BrewerProfileId("v60_03"),
        BrewerProfileId("manual_conical_generic"),
        BrewerProfileId("manual_wave_155"),
        BrewerProfileId("manual_wave_185"),
        BrewerProfileId("manual_wedge_generic"),
        BrewerProfileId("manual_thick_paper_carafe"),
    )
    private val STEEP_AND_RELEASE_PROFILE_IDS = setOf(
        BrewerProfileId("clever_style"),
        BrewerProfileId("hario_switch"),
        BrewerProfileId("valve_release_generic"),
    )
    private val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
    private val AUTOMATIC_BATCH_GENERIC = BrewerProfileId("automatic_batch_generic")
    private val MOCCAMASTER_KBGV_SELECT = BrewerProfileId("moccamaster_kbgv_select")
    private val AUTOMATIC_SINGLE_CUP_GENERIC = BrewerProfileId("automatic_single_cup_generic")
    private val VIETNAMESE_PHIN = BrewerProfileId("vietnamese_phin")
}
