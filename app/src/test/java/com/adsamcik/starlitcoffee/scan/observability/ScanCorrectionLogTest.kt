package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage of [ScanCorrectionLog.buildCorrections]. */
class ScanCorrectionLogTest {

    @Test
    fun `an unchanged field is recorded as not edited`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("roastLevel" to "Dark"),
            finalValues = mapOf("roastLevel" to "Dark"),
            now = 1L,
        )
        assertEquals(1, corrections.size)
        val c = corrections.first()
        assertEquals("roastLevel", c.fieldName)
        assertFalse(c.wasEdited)
        assertEquals("Dark", c.modelValue)
        assertEquals("Dark", c.finalValue)
    }

    @Test
    fun `case and whitespace differences do not count as an edit`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("origin" to "Ethiopia"),
            finalValues = mapOf("origin" to "  ethiopia "),
        )
        assertFalse(corrections.first().wasEdited)
    }

    @Test
    fun `a changed value is recorded as edited with model confidence`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("processType" to "Whole Bean"),
            finalValues = mapOf("processType" to "Washed"),
            confidence = mapOf("processType" to BagFieldConfidence.HIGH),
        )
        val c = corrections.first()
        assertTrue(c.wasEdited)
        assertEquals("Whole Bean", c.modelValue)
        assertEquals("Washed", c.finalValue)
        assertEquals("HIGH", c.modelConfidence)
    }

    @Test
    fun `clearing a model value counts as an edit`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("roastLevel" to "Dark"),
            finalValues = mapOf("roastLevel" to ""),
        )
        val c = corrections.first()
        assertTrue(c.wasEdited)
        assertNull(c.finalValue)
    }

    @Test
    fun `fields the model never proposed are not recorded`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("name" to "", "roaster" to null),
            finalValues = mapOf("name" to "Manually typed", "roaster" to "Also manual"),
        )
        assertTrue("Only model-proposed fields are measured", corrections.isEmpty())
    }

    @Test
    fun `confidence is null when unknown`() {
        val corrections = ScanCorrectionLog.buildCorrections(
            modelValues = mapOf("name" to "Yirgacheffe"),
            finalValues = mapOf("name" to "Yirgacheffe Natural"),
        )
        val c = corrections.first()
        assertTrue(c.wasEdited) // superset is still a change at this layer
        assertNull(c.modelConfidence)
    }
}
