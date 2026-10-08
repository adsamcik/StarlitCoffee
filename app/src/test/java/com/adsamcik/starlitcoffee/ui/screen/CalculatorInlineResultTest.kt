package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.ui.component.CalculationQuantityIconType
import com.adsamcik.starlitcoffee.viewmodel.WaterAmountMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorInlineResultTest {
    @Test
    fun `compact espresso results identify beverage yield or dose instead of water`() {
        val fromDose = buildInlineResult(InputDirection.DOSE, WaterAmountMode.WATER_INPUT, 18f, 45f, true)
        assertEquals(CalculationQuantityIconType.CUP_OUTPUT, fromDose.icon)
        assertEquals(ResultSide.CUP, fromDose.side)
        val fromYield = buildInlineResult(InputDirection.WATER, WaterAmountMode.BEVERAGE_OUTPUT, 18f, 45f, true)
        assertEquals(CalculationQuantityIconType.COFFEE_DOSE, fromYield.icon)
        assertEquals(ResultSide.COFFEE, fromYield.side)
    }
}
