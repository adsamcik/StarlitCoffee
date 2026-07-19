package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedScanFlowPolicyTest {

    @Test
    fun `completed result is hidden while user captures more photos`() {
        assertFalse(shouldShowCompletedScanReview(hasCompletedResult = true, isReviewing = false))
    }

    @Test
    fun `new completed result reopens review`() {
        assertTrue(shouldShowCompletedScanReview(hasCompletedResult = true, isReviewing = true))
    }

    @Test
    fun `manual no photo review retains scan session identity`() {
        assertEquals("session-42", initialScanReviewData("session-42").sessionId)
    }

    @Test
    fun `transferred draft keeps staged photos for receiving form`() {
        assertFalse(shouldDeleteStagedPhotosOnExit(ownershipTransferred = true))
        assertTrue(shouldDeleteStagedPhotosOnExit(ownershipTransferred = false))
    }

    @Test
    fun `rescan relocates origin from region while retaining evidence provenance`() {
        val fields = resolveRescanFieldEvidence(
            mapOf(
                "region" to BagFieldEvidence(
                    fieldName = "region",
                    value = "Ethiopia",
                    rawValue = "ETHIOPIA",
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.HIGH,
                    supportingText = "Origin: ETHIOPIA",
                ),
            ),
        )

        assertNull(fields["region"])
        assertEquals("Ethiopia", fields["origin"]?.value)
        assertEquals("ETHIOPIA", fields["origin"]?.rawValue)
        assertEquals(BagFieldSourceType.OCR, fields["origin"]?.sourceType)
        assertEquals(BagFieldConfidence.HIGH, fields["origin"]?.confidence)
        assertEquals("Origin: ETHIOPIA", fields["origin"]?.supportingText)
    }

    @Test
    fun `rescan moves decaf marker out of process into decaf flag`() {
        val fields = resolveRescanFieldEvidence(
            mapOf(
                "processType" to BagFieldEvidence(
                    fieldName = "processType",
                    value = "Decaf",
                    sourceType = BagFieldSourceType.LLM,
                    confidence = BagFieldConfidence.MEDIUM,
                ),
            ),
        )

        assertNull(fields["processType"])
        assertEquals("true", fields["isDecaf"]?.value)
        assertEquals(BagFieldSourceType.LLM, fields["isDecaf"]?.sourceType)
        assertEquals(BagFieldConfidence.MEDIUM, fields["isDecaf"]?.confidence)
    }
}
