package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BagVisionPlanner] — the gating + merge policy for the bag
 * scan vision second pass. Verifies vision is triggered for unsettled
 * visual-only fields, skipped for authoritatively/confidently resolved ones,
 * and that vision candidates can never override an authoritative value.
 */
class BrewBagVisionTest {

    private fun evidence(
        field: String,
        source: BagFieldSourceType,
        confidence: BagFieldConfidence,
    ) = BagFieldEvidence(
        fieldName = field,
        value = "value",
        sourceType = source,
        confidence = confidence,
    )

    private fun candidate(field: String) = BagFieldCandidate(
        fieldName = field,
        value = "Medium-Light",
        sourceType = BagFieldSourceType.LLM,
        confidenceHint = BagFieldConfidence.HIGH,
    )

    // --- selectVisionFields ---

    @Test
    fun `selectVisionFields returns all vision-worthy fields when nothing is resolved`() {
        val fields = BagVisionPlanner.selectVisionFields(emptyMap())
        assertEquals(BagVisionPlanner.VISION_WORTHY_FIELDS, fields)
    }

    @Test
    fun `selectVisionFields excludes a field resolved by barcode lookup`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.BARCODE_LOOKUP, BagFieldConfidence.HIGH),
        )
        val fields = BagVisionPlanner.selectVisionFields(resolved)
        assertFalse("roastLevel resolved by barcode should not need vision", "roastLevel" in fields)
        assertTrue("isDecaf is still unresolved", "isDecaf" in fields)
    }

    @Test
    fun `selectVisionFields still includes a field only the text LLM guessed`() {
        // The resolver weights LLM highly, so a hallucinated visual field can
        // resolve to HIGH; for visual fields we re-verify against the image.
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.LLM, BagFieldConfidence.HIGH),
        )
        assertTrue("roastLevel" in BagVisionPlanner.selectVisionFields(resolved))
    }

    @Test
    fun `selectVisionFields excludes a high-confidence OCR read`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
        )
        assertFalse("roastLevel" in BagVisionPlanner.selectVisionFields(resolved))
    }

    @Test
    fun `selectVisionFields includes a low-confidence OCR read`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.OCR, BagFieldConfidence.LOW),
        )
        assertTrue("roastLevel" in BagVisionPlanner.selectVisionFields(resolved))
    }

    // --- filterVisionCandidates ---

    @Test
    fun `filterVisionCandidates keeps a candidate that fills a gap`() {
        val kept = BagVisionPlanner.filterVisionCandidates(listOf(candidate("roastLevel")), emptyMap())
        assertEquals(1, kept.size)
    }

    @Test
    fun `filterVisionCandidates drops a candidate that would override a barcode value`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.BARCODE_LOOKUP, BagFieldConfidence.HIGH),
        )
        val kept = BagVisionPlanner.filterVisionCandidates(listOf(candidate("roastLevel")), resolved)
        assertTrue("vision must not override an authoritative value", kept.isEmpty())
    }

    @Test
    fun `filterVisionCandidates keeps a candidate refining a weak text-LLM value`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.LLM, BagFieldConfidence.HIGH),
        )
        val kept = BagVisionPlanner.filterVisionCandidates(listOf(candidate("roastLevel")), resolved)
        assertEquals(1, kept.size)
    }
}
