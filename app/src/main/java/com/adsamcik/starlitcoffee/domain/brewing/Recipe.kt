package com.adsamcik.starlitcoffee.domain.brewing

enum class QuantityRole {
    DRY_COFFEE_DOSE,
    BREW_WATER_INPUT,
    RESERVOIR_INPUT,
    BEVERAGE_YIELD,
    CONCENTRATE_YIELD,
    FINAL_SERVED_BEVERAGE,
    ICE,
    BYPASS_WATER,
    DILUTION_WATER,
    MEASURED_OUTPUT,
}

data class RatioDefinition(
    val numerator: QuantityRole,
    val denominator: QuantityRole,
) {
    init {
        require(numerator != denominator) { "Ratio terms must be different" }
    }
}

sealed interface OutputModel {
    data class BrewWaterMinusRetention(
        val retainedWaterGPerCoffeeG: Double,
    ) : OutputModel {
        init {
            require(retainedWaterGPerCoffeeG >= 0.0) { "Retention cannot be negative" }
        }
    }

    data object DirectTargetBeverageYield : OutputModel

    data class CollectedConcentrate(
        val retainedWaterGPerCoffeeG: Double,
    ) : OutputModel {
        init {
            require(retainedWaterGPerCoffeeG >= 0.0) { "Retention cannot be negative" }
        }
    }

    data object PreparedUnfilteredVolume : OutputModel

    data class ReservoirToEstimatedOutput(
        val internalRetentionG: Double = 0.0,
    ) : OutputModel {
        init {
            require(internalRetentionG >= 0.0) { "Internal retention cannot be negative" }
        }
    }

    data object UserMeasuredOutput : OutputModel

    data object NoMeaningfulBeverageYield : OutputModel
}

data class BrewQuantities(
    val dryCoffeeDoseG: Double,
    val brewWaterInputG: Double? = null,
    val reservoirInputG: Double? = null,
    val targetBeverageYieldG: Double? = null,
    val targetConcentrateYieldG: Double? = null,
    val finalServedBeverageG: Double? = null,
    val iceG: Double = 0.0,
    val bypassWaterG: Double = 0.0,
    val dilutionWaterG: Double = 0.0,
    val measuredOutputG: Double? = null,
) {
    init {
        require(dryCoffeeDoseG >= 0.0) { "Coffee dose cannot be negative" }
        listOf(
            brewWaterInputG,
            reservoirInputG,
            targetBeverageYieldG,
            targetConcentrateYieldG,
            finalServedBeverageG,
            measuredOutputG,
        ).forEach { value ->
            require(value == null || value >= 0.0) { "Quantity cannot be negative" }
        }
        require(iceG >= 0.0) { "Ice cannot be negative" }
        require(bypassWaterG >= 0.0) { "Bypass water cannot be negative" }
        require(dilutionWaterG >= 0.0) { "Dilution water cannot be negative" }
    }

    fun valueFor(role: QuantityRole): Double? = when (role) {
        QuantityRole.DRY_COFFEE_DOSE -> dryCoffeeDoseG
        QuantityRole.BREW_WATER_INPUT -> brewWaterInputG
        QuantityRole.RESERVOIR_INPUT -> reservoirInputG
        QuantityRole.BEVERAGE_YIELD -> targetBeverageYieldG
        QuantityRole.CONCENTRATE_YIELD -> targetConcentrateYieldG
        QuantityRole.FINAL_SERVED_BEVERAGE -> finalServedBeverageG
        QuantityRole.ICE -> iceG
        QuantityRole.BYPASS_WATER -> bypassWaterG
        QuantityRole.DILUTION_WATER -> dilutionWaterG
        QuantityRole.MEASURED_OUTPUT -> measuredOutputG
    }
}

enum class PourPattern {
    NONE,
    CONTINUOUS,
    SINGLE_POUR,
    PULSED,
}

enum class AgitationType {
    NONE,
    STIR,
    SWIRL,
    BREAK_CRUST,
}

enum class BrewerOrientation {
    STANDARD,
    INVERTED,
    CUSTOM,
}

enum class ValveDirective {
    OPEN,
    CLOSE,
}

enum class HeatStrategy {
    NONE,
    GENTLE,
    MEDIUM,
    HIGH,
    START_HOT,
    START_COLD,
}

data class BloomConfiguration(
    val waterG: Double? = null,
    val durationSeconds: Int? = null,
) {
    init {
        require(waterG == null || waterG >= 0.0) { "Bloom water cannot be negative" }
        require(durationSeconds == null || durationSeconds >= 0) { "Bloom duration cannot be negative" }
    }
}

data class RecipeTechnique(
    val bloom: BloomConfiguration? = null,
    val pourPattern: PourPattern = PourPattern.NONE,
    val pulseCount: Int? = null,
    val agitation: AgitationType = AgitationType.NONE,
    val steepDurationSeconds: Int? = null,
    val orientation: BrewerOrientation = BrewerOrientation.STANDARD,
    val valveSequence: List<ValveDirective> = emptyList(),
    val preInfusionSeconds: Int? = null,
    val heatStrategy: HeatStrategy = HeatStrategy.NONE,
    val stagePlanVariantId: RecipeVariantId? = null,
) {
    init {
        require(pulseCount == null || pulseCount >= 0) { "Pulse count cannot be negative" }
        require(steepDurationSeconds == null || steepDurationSeconds >= 0) {
            "Steep duration cannot be negative"
        }
        require(preInfusionSeconds == null || preInfusionSeconds >= 0) {
            "Pre-infusion duration cannot be negative"
        }
    }
}

data class ServingAddition(
    val id: String,
    val massG: Double? = null,
) {
    init {
        require(id.isNotBlank()) { "Serving addition ID cannot be blank" }
        require(massG == null || massG >= 0.0) { "Serving addition mass cannot be negative" }
    }
}

data class BrewRecipe(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId,
    val equipment: EquipmentConfiguration,
    val quantities: BrewQuantities,
    val ratioDefinition: RatioDefinition,
    val ratioValue: Double? = null,
    val temperatureC: Int? = null,
    val grinderId: String? = null,
    val grindSetting: String? = null,
    val technique: RecipeTechnique = RecipeTechnique(),
    val servingAdditions: List<ServingAddition> = emptyList(),
    val isDecaf: Boolean = false,
    val notes: String? = null,
    val outputModel: OutputModel,
) {
    init {
        require(schemaVersion > 0) { "Recipe schema version must be positive" }
        require(ratioValue == null || ratioValue > 0.0) { "Ratio must be positive" }
        require(temperatureC == null || temperatureC in 0..100) {
            "Temperature must be between 0 and 100°C"
        }
        require(equipment.brewerProfileId == brewerProfileId) {
            "Recipe and equipment must use the same brewer profile"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class BrewingCalculation(
    val retainedWaterG: Double? = null,
    val expectedBeverageYieldG: Double? = null,
    val expectedConcentrateYieldG: Double? = null,
    val expectedPreparedVolumeG: Double? = null,
    val expectedFinalServedBeverageG: Double? = null,
    val ratioValue: Double? = null,
)

object RecipeCalculator {
    fun calculate(recipe: BrewRecipe): BrewingCalculation {
        val quantities = recipe.quantities
        val brewInput = quantities.brewWaterInputG
        val reservoirInput = quantities.reservoirInputG
        val retention = when (val model = recipe.outputModel) {
            is OutputModel.BrewWaterMinusRetention -> quantities.dryCoffeeDoseG * model.retainedWaterGPerCoffeeG
            is OutputModel.CollectedConcentrate -> quantities.dryCoffeeDoseG * model.retainedWaterGPerCoffeeG
            else -> null
        }

        val beverageYield = when (val model = recipe.outputModel) {
            is OutputModel.BrewWaterMinusRetention -> brewInput?.minus(retention ?: 0.0)?.coerceAtLeast(0.0)
            OutputModel.DirectTargetBeverageYield -> quantities.targetBeverageYieldG ?: quantities.measuredOutputG
            is OutputModel.ReservoirToEstimatedOutput -> reservoirInput
                ?.minus(model.internalRetentionG)
                ?.coerceAtLeast(0.0)
            OutputModel.UserMeasuredOutput -> quantities.measuredOutputG
            else -> null
        }
        val concentrateYield = when (recipe.outputModel) {
            is OutputModel.CollectedConcentrate -> quantities.targetConcentrateYieldG
                ?: brewInput?.minus(retention ?: 0.0)?.coerceAtLeast(0.0)
            else -> null
        }
        val preparedVolume = when (recipe.outputModel) {
            OutputModel.PreparedUnfilteredVolume -> brewInput
            else -> null
        }
        val primaryOutput = beverageYield ?: concentrateYield ?: preparedVolume
        val finalServed = quantities.finalServedBeverageG ?: primaryOutput?.plus(
            quantities.bypassWaterG + quantities.dilutionWaterG,
        )
        return BrewingCalculation(
            retainedWaterG = retention,
            expectedBeverageYieldG = beverageYield,
            expectedConcentrateYieldG = concentrateYield,
            expectedPreparedVolumeG = preparedVolume,
            expectedFinalServedBeverageG = finalServed,
            ratioValue = calculateRatio(recipe.ratioDefinition, quantities, beverageYield, concentrateYield, finalServed),
        )
    }

    private fun calculateRatio(
        definition: RatioDefinition,
        quantities: BrewQuantities,
        beverageYield: Double?,
        concentrateYield: Double?,
        finalServed: Double?,
    ): Double? {
        val numerator = quantityFor(
            definition.numerator,
            quantities,
            beverageYield,
            concentrateYield,
            finalServed,
        ) ?: return null
        val denominator = quantityFor(
            definition.denominator,
            quantities,
            beverageYield,
            concentrateYield,
            finalServed,
        ) ?: return null
        return if (numerator > 0.0) denominator / numerator else null
    }

    private fun quantityFor(
        role: QuantityRole,
        quantities: BrewQuantities,
        beverageYield: Double?,
        concentrateYield: Double?,
        finalServed: Double?,
    ): Double? = when (role) {
        QuantityRole.BEVERAGE_YIELD -> beverageYield ?: quantities.targetBeverageYieldG
        QuantityRole.CONCENTRATE_YIELD -> concentrateYield ?: quantities.targetConcentrateYieldG
        QuantityRole.FINAL_SERVED_BEVERAGE -> finalServed ?: quantities.finalServedBeverageG
        else -> quantities.valueFor(role)
    }
}
