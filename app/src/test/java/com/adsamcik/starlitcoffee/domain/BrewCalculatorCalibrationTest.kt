package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewCalculatorCalibrationTest {

    @Test
    fun `default apparent loss preserves existing method behavior`() {
        val result = BrewCalculator.calculate(
            method = BrewMethod.V60,
            inputMode = InputMode.COFFEE_TO_WATER,
            amount = 20f,
            effectiveRatio = 16f,
            bloomMultiplier = 2.5f,
            pulseCount = 4,
            isDecaf = false,
        )

        assertEquals(40f, result.retainedWaterG, TOLERANCE)
        assertEquals(280f, result.predictedCupVolumeG, TOLERANCE)
    }

    @Test
    fun `apparent loss override drives forward retained water and cup output`() {
        val result = BrewCalculator.calculate(
            method = BrewMethod.V60,
            inputMode = InputMode.COFFEE_TO_WATER,
            amount = 20f,
            effectiveRatio = 16f,
            bloomMultiplier = 2.5f,
            pulseCount = 4,
            isDecaf = false,
            apparentLossGPerCoffeeG = 2.5f,
        )

        assertEquals(50f, result.retainedWaterG, TOLERANCE)
        assertEquals(270f, result.predictedCupVolumeG, TOLERANCE)
    }

    @Test
    fun `apparent loss override drives inverse cup target planning`() {
        val result = BrewCalculator.calculate(
            method = BrewMethod.V60,
            inputMode = InputMode.BREW_SIZE_TO_BOTH,
            amount = 270f,
            effectiveRatio = 16f,
            bloomMultiplier = 2.5f,
            pulseCount = 4,
            isDecaf = false,
            apparentLossGPerCoffeeG = 2.5f,
        )

        assertEquals(20f, result.coffeeG, TOLERANCE)
        assertEquals(320f, result.waterG, TOLERANCE)
        assertEquals(50f, result.retainedWaterG, TOLERANCE)
        assertEquals(270f, result.predictedCupVolumeG, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
