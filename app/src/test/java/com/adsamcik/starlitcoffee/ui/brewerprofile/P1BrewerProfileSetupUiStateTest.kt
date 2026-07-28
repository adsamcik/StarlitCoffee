package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.CapacityRecommendation
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
            .selectHarioSwitchWorkflow(HarioSwitchWorkflow.MANUAL_GRAVITY)
        val clever = hario.selectProfile(BrewerProfileId("clever_style"))

        assertEquals(HarioSwitchWorkflow.MANUAL_GRAVITY, hario.startSelection?.harioSwitchWorkflow)
        assertNull(clever.startSelection?.harioSwitchWorkflow)
        assertEquals(HarioSwitchWorkflow.MANUAL_GRAVITY, clever.harioSwitchWorkflow)
    }
}
