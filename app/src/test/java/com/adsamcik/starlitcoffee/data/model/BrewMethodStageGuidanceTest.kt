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
}
