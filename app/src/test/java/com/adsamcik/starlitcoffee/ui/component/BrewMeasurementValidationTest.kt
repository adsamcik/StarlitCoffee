package com.adsamcik.starlitcoffee.ui.component

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewMeasurementValidationTest {
    @Test
    fun `accepts a complete positive pair and decimal comma`() {
        val result = validateBrewMeasurements(
            waterInputText = "338,6",
            beverageOutputText = "294,2",
        )

        assertTrue(result.isValid)
        assertEquals(338.6f, result.waterInputG ?: 0f, 0.001f)
        assertEquals(294.2f, result.beverageOutputG ?: 0f, 0.001f)
        assertNull(result.issue)
    }

    @Test
    fun `does not turn blank or partial input into a calibration sample`() {
        val blank = validateBrewMeasurements("", "")
        val missingOutput = validateBrewMeasurements("340", "")
        val missingWater = validateBrewMeasurements("", "296")

        assertFalse(blank.isValid)
        assertEquals(BrewMeasurementInputIssue.EMPTY, blank.issue)
        assertEquals(BrewMeasurementInputIssue.INCOMPLETE, missingOutput.issue)
        assertEquals(BrewMeasurementInputIssue.INCOMPLETE, missingWater.issue)
    }

    @Test
    fun `rejects nonfinite zero and negative quantities`() {
        assertEquals(
            BrewMeasurementInputIssue.INVALID_WATER_INPUT,
            validateBrewMeasurements("NaN", "200").issue,
        )
        assertEquals(
            BrewMeasurementInputIssue.INVALID_WATER_INPUT,
            validateBrewMeasurements("0", "200").issue,
        )
        assertEquals(
            BrewMeasurementInputIssue.INVALID_BEVERAGE_OUTPUT,
            validateBrewMeasurements("340", "-1").issue,
        )
        assertEquals(
            BrewMeasurementInputIssue.INVALID_BEVERAGE_OUTPUT,
            validateBrewMeasurements("340", "Infinity").issue,
        )
    }

    @Test
    fun `rejects beverage output greater than water input`() {
        val result = validateBrewMeasurements("300", "301")

        assertFalse(result.isValid)
        assertEquals(BrewMeasurementInputIssue.BEVERAGE_EXCEEDS_WATER, result.issue)
        assertEquals(300f, result.waterInputG ?: 0f, 0.001f)
        assertEquals(301f, result.beverageOutputG ?: 0f, 0.001f)
    }

    @Test
    fun `saved measurements reopen with the locale decimal separator`() {
        assertEquals("339,6", formatMeasurementInput(339.6f, Locale.GERMANY))
        assertEquals("339.625", formatMeasurementInput(339.625f, Locale.US))
    }
}
