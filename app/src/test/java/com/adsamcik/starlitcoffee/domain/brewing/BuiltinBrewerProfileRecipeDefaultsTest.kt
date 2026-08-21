package com.adsamcik.starlitcoffee.domain.brewing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinBrewerProfileRecipeDefaultsTest {

    @Test
    fun `catalogue exposes only the P1 and expanded manual gravity profile defaults`() {
        val expectedProfileIds = setOf(
            "v60_unspecified",
            "v60_01",
            "v60_02",
            "v60_03",
            "manual_conical_generic",
            "manual_wave_155",
            "manual_wave_185",
            "manual_wedge_generic",
            "manual_thick_paper_carafe",
            "clever_style",
            "hario_switch",
            "valve_release_generic",
            "cezve_generic",
            "automatic_batch_generic",
            "moccamaster_kbgv_select",
            "automatic_single_cup_generic",
            "vietnamese_phin",
        )

        assertEquals(
            expectedProfileIds,
            BuiltinBrewerProfileRecipeDefaults.supportedProfileIds.map(BrewerProfileId::value).toSet(),
        )
        assertTrue(
            BuiltinBrewerProfileRecipeDefaults.supportedProfileIds.all { profileId ->
                BuiltinBrewingCatalog.instance.findBrewerProfile(profileId) != null
            },
        )
    }

    @Test
    fun `expanded manual gravity preserves the legacy V60 input and output semantics`() {
        val manualProfileIds = setOf(
            "v60_unspecified",
            "v60_01",
            "v60_02",
            "v60_03",
            "manual_conical_generic",
            "manual_wave_155",
            "manual_wave_185",
            "manual_wedge_generic",
            "manual_thick_paper_carafe",
        )

        manualProfileIds.forEach { rawId ->
            val profileId = BrewerProfileId(rawId)
            val defaults = requireNotNull(BuiltinBrewerProfileRecipeDefaults.find(profileId))
            val quantities = requireNotNull(
                BuiltinBrewerProfileRecipeDefaults.defaultQuantitiesForDose(profileId, dryCoffeeDoseG = 20.0),
            )

            assertEquals(16.0, defaults.ratio.waterPerCoffee, 0.001)
            assertEquals(
                TemperatureRecommendation.CelsiusRange(minimumC = 93, maximumC = 96),
                defaults.temperature,
            )
            assertEquals(
                BrewTimeRecommendation.SecondsRange(minimumSeconds = 150, maximumSeconds = 210),
                defaults.brewTime,
            )
            assertEquals(CapacityRecommendation.RequiresEquipmentConfiguration, defaults.capacity)
            assertEquals(QuantityRole.BREW_WATER_INPUT, defaults.quantitySemantics.inputRole)
            assertEquals(
                RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, QuantityRole.BREW_WATER_INPUT),
                defaults.quantitySemantics.ratioDefinition,
            )
            assertEquals(
                OutputModel.BrewWaterMinusRetention(retainedWaterGPerCoffeeG = 2.0),
                defaults.quantitySemantics.outputModel,
            )
            assertEquals(
                PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
                defaults.quantitySemantics.primaryOutput,
            )
            assertEquals(320.0, requireNotNull(quantities.brewWaterInputG), 0.001)
            assertNull(quantities.reservoirInputG)
        }
    }

    @Test
    fun `steep and release defaults are broad guidance rather than a completion timer`() {
        listOf("clever_style", "hario_switch", "valve_release_generic").forEach { rawId ->
            val defaults = requireNotNull(
                BuiltinBrewerProfileRecipeDefaults.find(BrewerProfileId(rawId)),
            )

            assertEquals(16.0, defaults.ratio.waterPerCoffee, 0.001)
            assertEquals(
                TemperatureRecommendation.CelsiusRange(minimumC = 90, maximumC = 96),
                defaults.temperature,
            )
            assertEquals(
                BrewTimeRecommendation.SecondsRange(minimumSeconds = 120, maximumSeconds = 240),
                defaults.brewTime,
            )
            assertEquals(QuantityRole.BREW_WATER_INPUT, defaults.quantitySemantics.inputRole)
            assertEquals(
                PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
                defaults.quantitySemantics.primaryOutput,
            )
        }
    }

    @Test
    fun `cezve keeps prepared volume separate from beverage yield semantics`() {
        val profileId = BrewerProfileId("cezve_generic")
        val defaults = requireNotNull(BuiltinBrewerProfileRecipeDefaults.find(profileId))
        val quantities = requireNotNull(
            BuiltinBrewerProfileRecipeDefaults.defaultQuantitiesForDose(profileId, dryCoffeeDoseG = 8.0),
        )

        assertEquals(10.0, defaults.ratio.waterPerCoffee, 0.001)
        assertEquals(TemperatureRecommendation.Unavailable, defaults.temperature)
        assertEquals(BrewTimeRecommendation.ObservedCompletion, defaults.brewTime)
        assertEquals(OutputModel.PreparedUnfilteredVolume, defaults.quantitySemantics.outputModel)
        assertEquals(
            PrimaryOutputQuantity.PREPARED_UNFILTERED_VOLUME,
            defaults.quantitySemantics.primaryOutput,
        )
        assertEquals(80.0, requireNotNull(quantities.brewWaterInputG), 0.001)
        assertNull(quantities.targetBeverageYieldG)
        assertNull(quantities.targetConcentrateYieldG)
    }

    @Test
    fun `automatic defaults use reservoir input and machine controlled completion`() {
        listOf("automatic_batch_generic", "automatic_single_cup_generic").forEach { rawId ->
            val profileId = BrewerProfileId(rawId)
            val defaults = requireNotNull(BuiltinBrewerProfileRecipeDefaults.find(profileId))
            val quantities = requireNotNull(
                BuiltinBrewerProfileRecipeDefaults.defaultQuantitiesForDose(profileId, dryCoffeeDoseG = 20.0),
            )

            assertEquals(16.0, defaults.ratio.waterPerCoffee, 0.001)
            assertEquals(TemperatureRecommendation.Unavailable, defaults.temperature)
            assertEquals(BrewTimeRecommendation.MachineControlled, defaults.brewTime)
            assertEquals(QuantityRole.RESERVOIR_INPUT, defaults.quantitySemantics.inputRole)
            assertEquals(
                RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, QuantityRole.RESERVOIR_INPUT),
                defaults.quantitySemantics.ratioDefinition,
            )
            assertEquals(
                OutputModel.ReservoirToEstimatedOutput(),
                defaults.quantitySemantics.outputModel,
            )
            assertEquals(
                PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD,
                defaults.quantitySemantics.primaryOutput,
            )
            assertEquals(320.0, requireNotNull(quantities.reservoirInputG), 0.001)
            assertNull(quantities.brewWaterInputG)
        }
    }

    @Test
    fun `phin retains concentrate output and a separate serving decision`() {
        val profileId = BrewerProfileId("vietnamese_phin")
        val defaults = requireNotNull(BuiltinBrewerProfileRecipeDefaults.find(profileId))
        val quantities = requireNotNull(
            BuiltinBrewerProfileRecipeDefaults.defaultQuantitiesForDose(profileId, dryCoffeeDoseG = 20.0),
        )

        assertEquals(5.0, defaults.ratio.waterPerCoffee, 0.001)
        assertEquals(
            TemperatureRecommendation.CelsiusRange(minimumC = 90, maximumC = 96),
            defaults.temperature,
        )
        assertEquals(
            BrewTimeRecommendation.SecondsRange(minimumSeconds = 180, maximumSeconds = 360),
            defaults.brewTime,
        )
        assertEquals(
            OutputModel.CollectedConcentrate(retainedWaterGPerCoffeeG = 0.0),
            defaults.quantitySemantics.outputModel,
        )
        assertEquals(PrimaryOutputQuantity.COLLECTED_CONCENTRATE, defaults.quantitySemantics.primaryOutput)
        assertTrue(defaults.quantitySemantics.servingIsSeparateFromExtraction)
        assertEquals(100.0, requireNotNull(quantities.brewWaterInputG), 0.001)
        assertNull(quantities.finalServedBeverageG)
    }

    @Test
    fun `unknown profiles receive no guessed defaults or quantities`() {
        val unknownProfileId = BrewerProfileId("future_brewer")

        assertNull(BuiltinBrewerProfileRecipeDefaults.find(unknownProfileId))
        assertNull(
            BuiltinBrewerProfileRecipeDefaults.defaultQuantitiesForDose(
                unknownProfileId,
                dryCoffeeDoseG = 20.0,
            ),
        )
    }
}
