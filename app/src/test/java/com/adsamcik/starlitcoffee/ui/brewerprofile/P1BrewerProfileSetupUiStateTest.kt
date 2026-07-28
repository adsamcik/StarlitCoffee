package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.CapacityRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1BrewerProfileSetupUiStateTest {

    @Test
    fun `factory exposes only profiles with defaults and durable stage plans`() {
        val state = P1BrewerProfileSetupStateFactory.create()

        assertEquals(
            listOf(
                "clever_style",
                "hario_switch",
                "valve_release_generic",
                "cezve_generic",
                "automatic_batch_generic",
                "automatic_single_cup_generic",
                "vietnamese_phin",
            ),
            state.profiles.map { it.profileId.value },
        )
        assertTrue(
            state.profiles.all { option ->
                option.defaults.capacity is CapacityRecommendation.RequiresEquipmentConfiguration
            },
        )
    }

    @Test
    fun `unknown selection stays unavailable rather than falling back`() {
        val initial = P1BrewerProfileSetupStateFactory.create()
        val selected = initial.selectProfile(BrewerProfileId("future_brewer"))

        assertEquals(initial, selected)
        assertNull(selected.startSelection)
        assertFalse(selected.canStart)
    }

    @Test
    fun `only Hario Switch carries its contextual workflow into the start selection`() {
        val initial = P1BrewerProfileSetupStateFactory.create()
        val hario = initial
            .selectProfile(BrewerProfileId("hario_switch"))
            .updateEquipmentCapacity("400")
            .selectHarioSwitchWorkflow(HarioSwitchWorkflow.MANUAL_GRAVITY)
        val clever = hario
            .selectProfile(BrewerProfileId("clever_style"))
            .updateEquipmentCapacity("400")

        assertEquals(HarioSwitchWorkflow.MANUAL_GRAVITY, hario.startSelection?.harioSwitchWorkflow)
        assertNull(clever.startSelection?.harioSwitchWorkflow)
        assertEquals(HarioSwitchWorkflow.MANUAL_GRAVITY, clever.harioSwitchWorkflow)
    }

    @Test
    fun `P1 start requires a positive capacity scoped to the selected brewer`() {
        val initial = P1BrewerProfileSetupStateFactory.create()
        val clever = initial.selectProfile(BrewerProfileId("clever_style"))

        assertNull(clever.startSelection)
        assertFalse(clever.canStart)

        val invalidCapacity = clever.updateEquipmentCapacity("0")
        assertNull(invalidCapacity.startSelection)

        val configuredClever = clever.updateEquipmentCapacity("340")
        assertTrue(configuredClever.canStart)
        assertEquals(340.0, requireNotNull(configuredClever.startSelection?.equipmentCapacityG), 0.001)

        val hario = configuredClever.selectProfile(BrewerProfileId("hario_switch"))
        assertEquals("", hario.selectedEquipmentCapacityInput)
        assertFalse(hario.canStart)
    }

    @Test
    fun `Cezve start selection carries sugar foam and an explicit heat source`() {
        val withoutHeat = P1BrewerProfileSetupStateFactory.create()
            .selectProfile(BrewerProfileId("cezve_generic"))
            .updateEquipmentCapacity("120")

        assertTrue(withoutHeat.requiresCezveSetup)
        assertFalse(withoutHeat.hasRequiredCezveHeatSource)
        assertNull(withoutHeat.startSelection)

        val configured = withoutHeat
            .selectCezveSugar(includeSugar = true)
            .selectCezveFoamRiseCycles(cycles = 2)
            .selectCezveHeatSource(HeatSourceClass.HOB)
        val selection = requireNotNull(configured.startSelection)

        assertTrue(selection.cezveSetup?.includeSugar == true)
        assertEquals(2, selection.cezveSetup?.foamRiseCycles)
        assertEquals(HeatSourceClass.HOB, selection.heatSource)
    }
}
