package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BagPhotoScanSupportTest {

    // --- Canonical Grouping ---

    @Test
    fun `resolveField groups multilingual aliases by canonical key`() {
        val resolved = BagPhotoScanSupport.resolveField(
            fieldName = "origin",
            candidates = listOf(
                BagFieldCandidate(
                    fieldName = "origin",
                    value = "Ethiopia",
                    rawValue = "Ethiopia",
                    canonicalKey = "ETHIOPIA",
                    sourceType = BagFieldSourceType.OCR,
                    confidenceHint = BagFieldConfidence.HIGH,
                ),
                BagFieldCandidate(
                    fieldName = "origin",
                    value = "Ethiopia",
                    rawValue = "Etiopie",
                    canonicalKey = "ETHIOPIA",
                    sourceType = BagFieldSourceType.OCR,
                    confidenceHint = BagFieldConfidence.HIGH,
                ),
            ),
        )

        assertNotNull(resolved)
        assertEquals(BagFieldSourceType.CONSENSUS, resolved!!.sourceType)
        assertEquals("ETHIOPIA", resolved.canonicalKey)
        assertEquals("Ethiopia", resolved.value)
    }

    @Test
    fun `buildPrefill carries field confidence into OCR prefill`() {
        val prefill = BagPhotoScanSupport.buildPrefill(
            resolvedFields = mapOf(
                "origin" to BagFieldEvidence(
                    fieldName = "origin",
                    value = "Ethiopia",
                    sourceType = BagFieldSourceType.CONSENSUS,
                    confidence = BagFieldConfidence.HIGH,
                ),
                "variety" to BagFieldEvidence(
                    fieldName = "variety",
                    value = "SL28",
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.MEDIUM,
                ),
            ),
        )

        assertEquals("Ethiopia", prefill.origin)
        assertEquals(BagFieldConfidence.HIGH, prefill.fieldConfidence["origin"])
        assertEquals(BagFieldConfidence.MEDIUM, prefill.fieldConfidence["variety"])
    }

    @Test
    fun `buildPrefill maps decaf evidence into OCR prefill`() {
        val prefill = BagPhotoScanSupport.buildPrefill(
            resolvedFields = mapOf(
                "isDecaf" to BagFieldEvidence(
                    fieldName = "isDecaf",
                    value = "Decaf",
                    canonicalKey = "true",
                    sourceType = BagFieldSourceType.CONSENSUS,
                    confidence = BagFieldConfidence.HIGH,
                ),
            ),
        )

        assertEquals(true, prefill.isDecaf)
        assertEquals(BagFieldConfidence.HIGH, prefill.fieldConfidence["isDecaf"])
    }

    @Test
    fun `resolveField keeps strongest representative while merging multilingual canonical aliases`() {
        val resolved = BagPhotoScanSupport.resolveField(
            fieldName = "origin",
            candidates = listOf(
                BagFieldCandidate(
                    fieldName = "origin",
                    value = "Etiopie",
                    rawValue = "Etiopie",
                    canonicalKey = "ETHIOPIA",
                    sourceType = BagFieldSourceType.OCR,
                    confidenceHint = BagFieldConfidence.HIGH,
                ),
                BagFieldCandidate(
                    fieldName = "origin",
                    value = "Ethiopia",
                    rawValue = "Etiopie",
                    canonicalKey = "ETHIOPIA",
                    sourceType = BagFieldSourceType.LOCAL_BARCODE_MATCH,
                    confidenceHint = BagFieldConfidence.HIGH,
                ),
            ),
        )

        assertNotNull(resolved)
        assertEquals(BagFieldSourceType.CONSENSUS, resolved!!.sourceType)
        assertEquals("Ethiopia", resolved.value)
        assertEquals("Etiopie", resolved.rawValue)
        assertEquals("ETHIOPIA", resolved.canonicalKey)
        assertEquals(BagFieldConfidence.HIGH, resolved.confidence)
    }
}
