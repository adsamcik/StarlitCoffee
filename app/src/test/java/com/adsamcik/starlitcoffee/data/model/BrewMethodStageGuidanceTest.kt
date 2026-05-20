package com.adsamcik.starlitcoffee.data.model

import com.adsamcik.starlitcoffee.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrewMethodStageGuidanceTest {

    // --- Method-specific timer guidance ---

    @Test
    fun `non bloom methods own timer guidance`() {
        val nonBloomMethods = BrewMethod.entries.filterNot { it.hasBloom }

        nonBloomMethods.forEach { method ->
            assertNotNull("${method.name} start guidance", method.stageGuidance.timerStartRes)
            assertNotNull("${method.name} active guidance", method.stageGuidance.timerActiveRes)
            assertNotNull("${method.name} ready guidance", method.stageGuidance.timerReadyRes)
            assertNotEquals(R.string.instruction_pour_total, method.stageGuidance.timerStartRes)
            assertNotEquals(R.string.format_pour_bloom_water, method.stageGuidance.timerStartRes)
        }
    }

    @Test
    fun `bloom methods keep existing bloom timer flow`() {
        val bloomMethods = BrewMethod.entries.filter { it.hasBloom }

        bloomMethods.forEach { method ->
            assertNull("${method.name} start guidance", method.stageGuidance.timerStartRes)
            assertNull("${method.name} active guidance", method.stageGuidance.timerActiveRes)
            assertNull("${method.name} ready guidance", method.stageGuidance.timerReadyRes)
        }
    }

    @Test
    fun `moka pot remains modeled without water temperature range`() {
        assertEquals(0, BrewMethod.MOKA_POT.tempRangeLow)
        assertEquals(0, BrewMethod.MOKA_POT.tempRangeHigh)
        assertEquals(R.string.instruction_moka_start, BrewMethod.MOKA_POT.stageGuidance.timerStartRes)
    }

    // --- Timing mode ---

    @Test
    fun `only cold brew is modeled as passive long duration`() {
        val passiveMethods = BrewMethod.entries.filter { it.timingMode == BrewTimingMode.PASSIVE_LONG_DURATION }

        assertEquals(listOf(BrewMethod.COLD_BREW), passiveMethods)
    }

    @Test
    fun `short brew methods keep active timer behavior`() {
        val shortMethods = BrewMethod.entries - BrewMethod.COLD_BREW

        shortMethods.forEach { method ->
            assertEquals("${method.name} timing mode", BrewTimingMode.ACTIVE_TIMER, method.timingMode)
        }
    }

    @Test
    fun `non bloom method guidance matches claimed support profiles`() {
        val expected = mapOf(
            BrewMethod.FRENCH_PRESS to Triple(
                R.string.instruction_french_press_start,
                R.string.instruction_french_press_steep,
                R.string.instruction_french_press_plunge,
            ),
            BrewMethod.AEROPRESS to Triple(
                R.string.instruction_aeropress_start,
                R.string.instruction_aeropress_steep,
                R.string.instruction_aeropress_press,
            ),
            BrewMethod.ESPRESSO to Triple(
                R.string.instruction_espresso_start,
                R.string.instruction_espresso_pull,
                R.string.instruction_espresso_stop,
            ),
            BrewMethod.MOKA_POT to Triple(
                R.string.instruction_moka_start,
                R.string.instruction_moka_flow,
                R.string.instruction_moka_remove,
            ),
            BrewMethod.COLD_BREW to Triple(
                R.string.instruction_cold_brew_start,
                R.string.instruction_cold_brew_steep,
                R.string.instruction_cold_brew_filter,
            ),
        )

        expected.forEach { (method, guidance) ->
            assertEquals("${method.name} start", guidance.first, method.stageGuidance.timerStartRes)
            assertEquals("${method.name} active", guidance.second, method.stageGuidance.timerActiveRes)
            assertEquals("${method.name} ready", guidance.third, method.stageGuidance.timerReadyRes)
        }
    }

    @Test
    fun `method time targets cover active and passive timer ranges`() {
        val expectedTargets = mapOf(
            BrewMethod.PULSAR to (210 to 270),
            BrewMethod.V60 to (150 to 210),
            BrewMethod.FRENCH_PRESS to (240 to 240),
            BrewMethod.AEROPRESS to (90 to 150),
            BrewMethod.ESPRESSO to (25 to 35),
            BrewMethod.MOKA_POT to (240 to 300),
            BrewMethod.COLD_BREW to (43_200 to 86_400),
        )

        expectedTargets.forEach { (method, target) ->
            assertEquals("${method.name} low target", target.first, method.timeTargetLow)
            assertEquals("${method.name} high target", target.second, method.timeTargetHigh)
        }
    }
}
