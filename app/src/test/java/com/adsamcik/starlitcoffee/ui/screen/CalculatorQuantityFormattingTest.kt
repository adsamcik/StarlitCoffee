package com.adsamcik.starlitcoffee.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorQuantityFormattingTest {
    @Test
    fun `card amounts keep the visual unit separate`() {
        assertEquals("—", formatQuantityCardAmount(0f))
        assertEquals("20", formatQuantityCardAmount(20f))
        assertEquals("29.4", formatQuantityCardAmount(29.4f))
    }

    @Test
    fun `spoken values include the localized unit when an amount exists`() {
        assertEquals("—", quantityCardSpokenValue("—", "g"))
        assertEquals("20 g", quantityCardSpokenValue("20", "g"))
    }
}
