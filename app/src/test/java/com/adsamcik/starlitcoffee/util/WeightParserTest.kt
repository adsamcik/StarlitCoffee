package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightParserTest {

    @Test
    fun `parseToGrams handles plain gram input`() {
        assertEquals(250f, WeightParser.parseToGrams("250")!!, 0.01f)
        assertEquals(250f, WeightParser.parseToGrams("250g")!!, 0.01f)
    }

    @Test
    fun `parseToGrams handles kilogram input`() {
        assertEquals(1000f, WeightParser.parseToGrams("1kg")!!, 0.01f)
        assertEquals(2500f, WeightParser.parseToGrams("2.5kg")!!, 0.01f)
    }

    @Test
    fun `parseToGrams handles imperial input`() {
        assertEquals(453.59f, WeightParser.parseToGrams("1lb")!!, 0.01f)
        assertEquals(226.8f, WeightParser.parseToGrams("8oz")!!, 0.01f)
    }

    @Test
    fun `parseToGrams returns null for blank or invalid values`() {
        assertNull(WeightParser.parseToGrams(""))
        assertNull(WeightParser.parseToGrams("not a weight"))
        assertNull(WeightParser.parseToGrams(null))
    }

    // --- extractFirstWeightToken (noisy / OCR-merged input) ---

    @Test
    fun `extractFirstWeightToken returns a clean already-valid value`() {
        assertEquals("250g", WeightParser.extractFirstWeightToken("250g"))
        assertEquals("1kg", WeightParser.extractFirstWeightToken("1kg"))
    }

    @Test
    fun `extractFirstWeightToken recovers the first token from a merged cell`() {
        assertEquals("250g", WeightParser.extractFirstWeightToken("250gC1000g"))
        assertEquals("250g", WeightParser.extractFirstWeightToken("Hmotnost 250 g"))
    }

    @Test
    fun `extractFirstWeightToken returns null when no value+unit token exists`() {
        assertNull(WeightParser.extractFirstWeightToken("no weight here"))
        assertNull(WeightParser.extractFirstWeightToken(""))
        assertNull(WeightParser.extractFirstWeightToken(null))
    }

    @Test
    fun `extractFirstWeightToken keeps a bare number as grams`() {
        // parseToGrams treats a unit-less number as grams, so it is already valid.
        assertEquals("1000", WeightParser.extractFirstWeightToken("1000"))
    }
}
