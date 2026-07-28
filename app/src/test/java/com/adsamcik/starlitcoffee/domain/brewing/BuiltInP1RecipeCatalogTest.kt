package com.adsamcik.starlitcoffee.domain.brewing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInP1RecipeCatalogTest {

    private val recipes = BuiltInP1RecipeCatalog.recipes
    private val equipmentCatalog = BuiltinBrewingCatalog.instance

    @Test
    fun `catalog has the twenty unique mandatory source recipes`() {
        assertEquals(20, recipes.size)
        assertEquals(20, recipes.map { it.id }.distinct().size)
        assertEquals(8, recipes.count { it.sourceMethodFamilyId == "manual_gravity" })
        assertEquals(5, recipes.count { it.sourceMethodFamilyId == "steep_release" })
        assertEquals(2, recipes.count { it.sourceMethodFamilyId == "heated_unfiltered" })
        assertEquals(3, recipes.count { it.sourceMethodFamilyId == "automatic_batch" })
        assertEquals(2, recipes.count { it.sourceMethodFamilyId == "phin" })
        assertEquals(
            "aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d",
            BuiltInP1RecipeCatalog.SOURCE_SHA256,
        )
    }

    @Test
    fun `every app profile family and catalogued equipment id resolves`() {
        recipes.forEach { recipe ->
            assertNotNull(recipe.sourceBrewerProfileId.value, equipmentCatalog.findMethodFamily(recipe.methodFamilyId))
            val profile = requireNotNull(equipmentCatalog.findBrewerProfile(recipe.brewerProfileId))
            assertEquals(recipe.id.value, recipe.methodFamilyId, profile.familyId)

            recipe.equipmentOptions.forEach { option ->
                when (val selection = option.filterSelection) {
                    FilterSelection.IntentionallyUnfiltered,
                    FilterSelection.Unspecified,
                    -> Unit

                    is FilterSelection.Stack -> selection.entries.forEach { entry ->
                        assertNotNull(
                            "${recipe.id.value}:${entry.filterProfileId.value}",
                            equipmentCatalog.findFilterProfile(entry.filterProfileId),
                        )
                    }
                }
                option.basketId?.let { basketId ->
                    assertNotNull("${recipe.id.value}:${basketId.value}", equipmentCatalog.findBasketProfile(basketId))
                }
                option.accessoryIds.forEach { accessoryId ->
                    assertNotNull(
                        "${recipe.id.value}:${accessoryId.value}",
                        equipmentCatalog.findAccessoryProfile(accessoryId),
                    )
                }
            }
        }
    }

    @Test
    fun `generic valve release has no invented exact recipe`() {
        assertFalse(recipes.any { it.brewerProfileId == BrewerProfileId("valve_release_generic") })
    }

    @Test
    fun `source quantities and ratio input roles remain exact`() {
        val expected = mapOf(
            "v60_official_15_250" to ExpectedQuantity(15.0, 250.0),
            "v60_rao_20_330" to ExpectedQuantity(20.0, 330.0),
            "v60_kasuya_4_6_20_300" to ExpectedQuantity(20.0, 300.0),
            "v60_kurasu_flash_16_150_70" to ExpectedQuantity(16.0, 150.0, iceG = 70.0),
            "wave185_ozone_25_400" to ExpectedQuantity(25.0, 400.0),
            "wedge_pulse_23_5_400" to ExpectedQuantity(23.5, 400.0),
            "chemex_42_700" to ExpectedQuantity(42.0, 700.0),
            "generic_conical_low_agitation_20_320" to ExpectedQuantity(20.0, 320.0),
            "clever_water_first_15_250" to ExpectedQuantity(15.0, 250.0),
            "clever_coffee_first_15_250" to ExpectedQuantity(15.0, 250.0),
            "switch_official_20_240" to ExpectedQuantity(20.0, 240.0),
            "switch_ole_boen_hybrid_16_5_240" to ExpectedQuantity(16.5, 240.0),
            "switch_gravity_15_250" to ExpectedQuantity(15.0, 250.0),
            "cezve_turkish_single_rise_6_65" to ExpectedQuantity(6.0, 65.0),
            "cezve_bounded_repeated_rise_12_130" to ExpectedQuantity(12.0, 130.0),
            "auto_batch_500_30" to ExpectedQuantity(30.0, 500.0, QuantityRole.RESERVOIR_INPUT),
            "auto_batch_1000_60" to ExpectedQuantity(60.0, 1_000.0, QuantityRole.RESERVOIR_INPUT),
            "auto_cupone_20_300" to ExpectedQuantity(20.0, null, QuantityRole.RESERVOIR_INPUT),
            "phin_gravity_14_118" to ExpectedQuantity(14.0, 118.0),
            "phin_screw_18_120" to ExpectedQuantity(18.0, 120.0),
        )

        assertEquals(expected.keys, recipes.mapTo(mutableSetOf()) { it.id.value })
        recipes.forEach { recipe ->
            val quantity = requireNotNull(expected[recipe.id.value])
            assertEquals(recipe.id.value, quantity.coffeeG, recipe.quantities.dryCoffeeDoseG, 0.0)
            assertEquals(recipe.id.value, quantity.inputG, recipe.quantities.valueFor(quantity.inputRole))
            assertEquals(recipe.id.value, quantity.iceG, recipe.quantities.iceG, 0.0)
            assertEquals(QuantityRole.DRY_COFFEE_DOSE, recipe.ratios.first().definition.numerator)
            assertEquals(quantity.inputRole, recipe.ratios.first().definition.denominator)

            if (quantity.inputRole == QuantityRole.RESERVOIR_INPUT) {
                assertNull(recipe.quantities.brewWaterInputG)
            } else {
                assertNull(recipe.quantities.reservoirInputG)
            }
            recipe.ratios.first().ratioValue?.let { ratio ->
                val resolvedInput = requireNotNull(quantity.inputG)
                assertEquals(recipe.id.value, resolvedInput, ratio * quantity.coffeeG, 0.25)
            }
        }

        val iced = recipe("v60_kurasu_flash_16_150_70")
        assertEquals(2, iced.ratios.size)
        assertEquals(
            setOf(QuantityRole.BREW_WATER_INPUT, QuantityRole.ICE),
            iced.ratios.last().includedDenominatorRoles,
        )
        assertEquals(13.75, iced.ratios.last().ratioValue!!, 0.0)
    }

    @Test
    fun `cezve is explicitly unfiltered`() {
        val cezve = recipes.filter { it.methodFamilyId == MethodFamilyId("heated_unfiltered") }

        assertEquals(2, cezve.size)
        assertTrue(
            cezve.all { recipe ->
                recipe.equipmentOptions.all { it.filterSelection == FilterSelection.IntentionallyUnfiltered }
            },
        )
    }

    @Test
    fun `phin gravity and screw approaches retain different accessory state`() {
        val gravity = recipe("phin_gravity_14_118")
        val screw = recipe("phin_screw_18_120")

        assertTrue(gravity.equipmentOptions.single().accessoryIds.isEmpty())
        assertEquals(
            setOf(AccessoryProfileId("phin_screw_insert")),
            screw.equipmentOptions.single().accessoryIds,
        )
        assertEquals("phin_metal", gravity.equipmentOptions.single().singleFilterId())
        assertEquals("phin_metal", screw.equipmentOptions.single().singleFilterId())
    }

    @Test
    fun `automatic alternatives keep matching paper and basket paired`() {
        val expectedBatchPairs = setOf(
            "cone_paper" to "automatic_cone_basket",
            "flat_basket_paper" to "automatic_flat_basket",
        )
        listOf("auto_batch_500_30", "auto_batch_1000_60").forEach { id ->
            val pairs = recipe(id).equipmentOptions.mapTo(mutableSetOf()) { option ->
                option.singleFilterId() to requireNotNull(option.basketId).value
            }
            assertEquals(expectedBatchPairs, pairs)
            assertFalse("cone_paper" to "automatic_flat_basket" in pairs)
            assertFalse("flat_basket_paper" to "automatic_cone_basket" in pairs)
        }

        val cupOne = recipe("auto_cupone_20_300").equipmentOptions.single()
        assertEquals("number_one_paper", cupOne.singleFilterId())
        assertEquals(BasketProfileId("automatic_number_one_basket"), cupOne.basketId)
    }

    @Test
    fun `Cup One marked reservoir stays unresolved rather than becoming an invented mass`() {
        val cupOne = recipe("auto_cupone_20_300")

        assertNull(cupOne.quantities.reservoirInputG)
        assertNull(cupOne.quantities.brewWaterInputG)
        assertNull(cupOne.ratios.single().ratioValue)
        assertEquals(QuantityRole.RESERVOIR_INPUT, cupOne.ratios.single().definition.denominator)
    }

    @Test
    fun `all definitions retain evidence stage count and unresolved grind fields`() {
        val expectedStageCounts = mapOf(
            "v60_official_15_250" to 5,
            "v60_rao_20_330" to 6,
            "v60_kasuya_4_6_20_300" to 7,
            "v60_kurasu_flash_16_150_70" to 6,
            "wave185_ozone_25_400" to 8,
            "wedge_pulse_23_5_400" to 6,
            "chemex_42_700" to 7,
            "generic_conical_low_agitation_20_320" to 5,
            "clever_water_first_15_250" to 6,
            "clever_coffee_first_15_250" to 4,
            "switch_official_20_240" to 5,
            "switch_ole_boen_hybrid_16_5_240" to 5,
            "switch_gravity_15_250" to 4,
            "cezve_turkish_single_rise_6_65" to 6,
            "cezve_bounded_repeated_rise_12_130" to 6,
            "auto_batch_500_30" to 5,
            "auto_batch_1000_60" to 4,
            "auto_cupone_20_300" to 6,
            "phin_gravity_14_118" to 7,
            "phin_screw_18_120" to 6,
        )

        recipes.forEach { recipe ->
            assertEquals(recipe.id.value, recipe.id.value, recipe.exactRecipeApproachId.value)
            assertTrue(recipe.id.value, recipe.evidence.sourceIds.isNotEmpty())
            assertEquals(LocalDate.of(2026, 7, 27), recipe.evidence.reviewedOn)
            assertEquals(recipe.id.value, expectedStageCounts[recipe.id.value], recipe.orderedStageCount)
            assertTrue(recipe.id.value, recipe.unresolvedFields.contains("measured_particle_size"))
            assertTrue(recipe.id.value, recipe.unresolvedFields.contains("exact_grinder_setting"))
            assertEquals(P1UnresolvedGrindField.entries.toSet(), recipe.unresolvedGrindFields)
        }
    }

    @Test
    fun `unresolved temperature time and completion values remain semantic`() {
        val genericCone = recipe("generic_conical_low_agitation_20_320")
        assertEquals(P1TimeBasis.GEOMETRY_DEPENDENT, genericCone.expectedTime.basis)
        assertNull(genericCone.expectedTime.minimumSeconds)

        val switchOfficial = recipe("switch_official_20_240")
        assertEquals(P1TemperatureBasis.HOT_UNSPECIFIED, switchOfficial.temperature.basis)
        assertNull(switchOfficial.temperature.minimumC)

        val cezve = recipe("cezve_turkish_single_rise_6_65")
        assertEquals(P1TimeBasis.OBSERVATION_DEPENDENT, cezve.expectedTime.basis)
        assertEquals(P1CompletionSemantics.FIRST_FOAM_RISE_BEFORE_ROLLING_BOIL, cezve.completion)

        val automatic = recipe("auto_batch_500_30")
        assertEquals(P1TemperatureBasis.MACHINE_CONTROLLED, automatic.temperature.basis)
        assertEquals(P1TimeBasis.MACHINE_SPECIFIC, automatic.expectedTime.basis)
    }

    private fun recipe(id: String): BuiltInP1RecipeDefinition =
        requireNotNull(BuiltInP1RecipeCatalog.find(BuiltInRecipeId(id)))

    private fun P1EquipmentOption.singleFilterId(): String =
        (filterSelection as FilterSelection.Stack).entries.single().filterProfileId.value

    private data class ExpectedQuantity(
        val coffeeG: Double,
        val inputG: Double?,
        val inputRole: QuantityRole = QuantityRole.BREW_WATER_INPUT,
        val iceG: Double = 0.0,
    )
}
