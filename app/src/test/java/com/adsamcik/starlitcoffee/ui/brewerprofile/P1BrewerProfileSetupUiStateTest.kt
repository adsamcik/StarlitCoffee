package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1BrewerProfileSetupUiStateTest {

    @Test
    fun `factory groups only complete recipes with executable exact plans`() {
        val state = P1BrewerProfileSetupStateFactory.create()

        assertEquals(
            listOf(
                "v60_02",
                "v60_unspecified",
                "manual_wave_185",
                "manual_wedge_generic",
                "manual_thick_paper_carafe",
                "manual_conical_generic",
                "clever_style",
                "hario_switch",
                "cezve_generic",
                "automatic_batch_generic",
                "vietnamese_phin",
            ),
            state.profiles.map { it.profileId.value },
        )
        assertEquals(19, state.profiles.sumOf { it.recipes.size })
        assertTrue(
            state.profiles.flatMap { it.recipes }.none { recipe ->
                recipe.id == BuiltInRecipeId("auto_cupone_20_300")
            },
        )
    }

    @Test
    fun `factory fails closed to the supplied executable recipe IDs`() {
        val recipeId = BuiltInRecipeId("v60_official_15_250")
        val state = P1BrewerProfileSetupStateFactory.create(
            selectedProfileId = BrewerProfileId("v60_02"),
            executableRecipeIds = setOf(recipeId),
        )

        assertEquals(listOf("v60_02"), state.profiles.map { it.profileId.value })
        assertEquals(recipeId, state.selectedRecipe?.id)
    }

    @Test
    fun `multiple recipes require an explicit source recipe choice`() {
        val initial = selected("clever_style").updateEquipmentCapacity("300")

        assertNull(initial.selectedRecipe)
        assertFalse(initial.canStart)

        val selected = initial.selectRecipe(BuiltInRecipeId("clever_water_first_15_250"))

        assertEquals(BuiltInRecipeId("clever_water_first_15_250"), selected.selectedRecipe?.id)
        assertTrue(selected.canStart)
    }

    @Test
    fun `a sole complete recipe and sole equipment option are selected automatically`() {
        val state = selected("manual_wave_185").updateEquipmentCapacity("500")

        assertEquals(BuiltInRecipeId("wave185_ozone_25_400"), state.selectedRecipe?.id)
        assertEquals(state.selectedRecipe?.equipmentOptions?.single(), state.selectedEquipmentOption)
        assertTrue(state.canStart)
    }

    @Test
    fun `automatic basket and filter alternatives remain indivisible`() {
        val recipeId = BuiltInRecipeId("auto_batch_500_30")
        val recipeSelected = selected("automatic_batch_generic")
            .selectRecipe(recipeId)
            .updateEquipmentCapacity("700")

        assertNull(recipeSelected.selectedEquipmentOption)
        assertFalse(recipeSelected.canStart)

        val cone = recipeSelected.selectEquipmentOption(0)
        val flat = recipeSelected.selectEquipmentOption(1)

        assertEquals("automatic_cone_basket", cone.selectedEquipmentOption?.basketId?.value)
        assertEquals("cone_paper", cone.selectedEquipmentOption.singleFilterId())
        assertEquals("automatic_flat_basket", flat.selectedEquipmentOption?.basketId?.value)
        assertEquals("flat_basket_paper", flat.selectedEquipmentOption.singleFilterId())
        assertTrue(cone.canStart)
        assertTrue(flat.canStart)
    }

    @Test
    fun `Hario valve workflow is owned by the exact recipe`() {
        val base = selected("hario_switch").updateEquipmentCapacity("400")
        val official = base.selectRecipe(BuiltInRecipeId("switch_official_20_240"))
        val hybrid = base.selectRecipe(BuiltInRecipeId("switch_ole_boen_hybrid_16_5_240"))
        val gravity = base.selectRecipe(BuiltInRecipeId("switch_gravity_15_250"))

        assertEquals(HarioSwitchWorkflow.STEEP_AND_RELEASE, official.startSelection?.harioSwitchWorkflow)
        assertNull(hybrid.startSelection?.harioSwitchWorkflow)
        assertEquals(HarioSwitchWorkflow.MANUAL_GRAVITY, gravity.startSelection?.harioSwitchWorkflow)
    }

    @Test
    fun `capacity confirmation must fit the selected canonical input`() {
        val recipeSelected = selected("clever_style")
            .selectRecipe(BuiltInRecipeId("clever_water_first_15_250"))

        val tooSmall = recipeSelected.updateEquipmentCapacity("249")
        val sufficient = recipeSelected.updateEquipmentCapacity("250")

        assertFalse(tooSmall.capacitySupportsSelectedRecipe)
        assertFalse(tooSmall.canStart)
        assertTrue(sufficient.capacitySupportsSelectedRecipe)
        assertTrue(sufficient.canStart)
    }

    @Test
    fun `Cezve recipe owns foam rises while sugar and heat remain explicit`() {
        val withoutHeat = selected("cezve_generic")
            .selectRecipe(BuiltInRecipeId("cezve_bounded_repeated_rise_12_130"))
            .updateEquipmentCapacity("150")

        assertNull(withoutHeat.startSelection)

        val configured = withoutHeat
            .selectCezveSugar(includeSugar = true)
            .selectCezveHeatSource(HeatSourceClass.HOB)
        val selection = requireNotNull(configured.startSelection)

        assertTrue(selection.cezveSetup?.includeSugar == true)
        assertEquals(2, selection.cezveSetup?.foamRiseCycles)
        assertEquals(HeatSourceClass.HOB, selection.heatSource)
    }

    @Test
    fun `unknown profile recipe and equipment choices never fall back`() {
        val initial = P1BrewerProfileSetupStateFactory.create()
        assertEquals(initial, initial.selectProfile(BrewerProfileId("future_brewer")))

        val clever = initial.selectProfile(BrewerProfileId("clever_style"))
        assertEquals(clever, clever.selectRecipe(BuiltInRecipeId("v60_official_15_250")))
        assertEquals(clever, clever.selectEquipmentOption(99))
    }

    private fun selected(profileId: String): P1BrewerProfileSetupUiState =
        P1BrewerProfileSetupStateFactory.create().selectProfile(BrewerProfileId(profileId))

    private fun com.adsamcik.starlitcoffee.domain.brewing.P1EquipmentOption?.singleFilterId(): String? =
        ((this?.filterSelection as? FilterSelection.Stack)?.entries?.singleOrNull()?.filterProfileId?.value)
}
