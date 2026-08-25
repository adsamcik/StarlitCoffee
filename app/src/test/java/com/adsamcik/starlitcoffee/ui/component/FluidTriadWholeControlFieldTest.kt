package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadWholeControlFieldTest {
    @Test
    fun `each material phase owns its semantic anchor without becoming a closed blob`() {
        assertOwned(CalculatorQuantityTarget.COFFEE, x = 1f / 6f)
        assertOwned(CalculatorQuantityTarget.WATER_IN, x = 0.5f)
        assertOwned(CalculatorQuantityTarget.IN_CUP, x = 5f / 6f)

        assertNotOwned(CalculatorQuantityTarget.COFFEE, x = 0.5f)
        assertNotOwned(CalculatorQuantityTarget.WATER_IN, x = 1f / 6f)
        assertNotOwned(CalculatorQuantityTarget.WATER_IN, x = 5f / 6f)
        assertNotOwned(CalculatorQuantityTarget.IN_CUP, x = 0.5f)
    }

    @Test
    fun `authored pressure gives each state its intended material identity`() {
        assertTrue(
            FluidTriadWholeControlField.coffeeBoundary(0.5f) <
                FluidTriadWholeControlField.coffeeBoundary(0f),
        )
        assertTrue(
            FluidTriadWholeControlField.waterHalfWidth(0.5f) <
                FluidTriadWholeControlField.waterHalfWidth(0f),
        )
        assertTrue(
            FluidTriadWholeControlField.cupBoundary(0.5f) <
                FluidTriadWholeControlField.cupBoundary(0f),
        )
        assertTrue(
            FluidTriadWholeControlField.cupBoundary(0.75f) <
                FluidTriadWholeControlField.cupBoundary(0.25f),
        )
    }

    private fun assertOwned(target: CalculatorQuantityTarget, x: Float) {
        assertTrue(FluidTriadWholeControlField.ownershipDistance(target, x, 0.5f) < 0f)
    }

    private fun assertNotOwned(target: CalculatorQuantityTarget, x: Float) {
        assertTrue(FluidTriadWholeControlField.ownershipDistance(target, x, 0.5f) > 0f)
    }
}
