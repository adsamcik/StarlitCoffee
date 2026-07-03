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

    private fun lowCandidate(field: String) = BagFieldCandidate(
        fieldName = field,
        value = "Medium-Light",
        sourceType = BagFieldSourceType.LLM,
        confidenceHint = BagFieldConfidence.LOW,
    )

    // --- selectVisionFields ---

    @Test
    fun `selectVisionFields returns all vision-worthy fields when nothing is resolved`() {
        val fields = BagVisionPlanner.selectVisionFields(emptyMap())
        assertEquals(BagVisionPlanner.VISION_WORTHY_FIELDS, fields)
    }

    @Test
    fun `vision-worthy fields cover identity and concept fields, not structural ones`() {
        val fields = BagVisionPlanner.VISION_WORTHY_FIELDS
        // Identity + concept fields the cropped-label vision pass re-reads so the
        // combine pass can reconcile them (especially names).
        listOf("name", "roaster", "origin", "variety", "processType", "roastLevel", "isDecaf")
            .forEach { assertTrue("$it should be vision-worthy", it in fields) }
        // Structural numeric/date fields stay with OCR (more reliable on digits).
        listOf("weight", "altitude", "roastDate", "expiryDate")
            .forEach { assertFalse("$it should NOT be vision-worthy", it in fields) }
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

    // --- Idea #7: low-confidence vision abstention calibration ---

    @Test
    fun `filterVisionCandidates drops a low-confidence candidate overriding a present value`() {
        val resolved = mapOf(
            "roastLevel" to evidence("roastLevel", BagFieldSourceType.LLM, BagFieldConfidence.MEDIUM),
        )
        val kept = BagVisionPlanner.filterVisionCandidates(listOf(lowCandidate("roastLevel")), resolved)
        assertTrue("an uncertain vision guess must not clobber a present value", kept.isEmpty())
    }

    @Test
    fun `filterVisionCandidates keeps a low-confidence candidate that only fills a gap`() {
        val kept = BagVisionPlanner.filterVisionCandidates(listOf(lowCandidate("roastLevel")), emptyMap())
        assertEquals("an uncertain vision read may still fill an empty field", 1, kept.size)
    }

    // --- isAuthoritativelySettled ---

    @Test
    fun `isAuthoritativelySettled is true only for authoritative sources`() {
        assertTrue(
            BagVisionPlanner.isAuthoritativelySettled(
                evidence("origin", BagFieldSourceType.CONSENSUS, BagFieldConfidence.HIGH),
            ),
        )
        assertFalse(
            "a confident LLM read is not authoritative",
            BagVisionPlanner.isAuthoritativelySettled(
                evidence("origin", BagFieldSourceType.LLM, BagFieldConfidence.HIGH),
            ),
        )
        assertFalse(BagVisionPlanner.isAuthoritativelySettled(null))
    }
}
