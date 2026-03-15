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
}
