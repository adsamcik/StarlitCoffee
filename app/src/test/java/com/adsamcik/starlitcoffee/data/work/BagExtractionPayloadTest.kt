package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.CoffeeMetadataMatchStrategy
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagExtractionPayloadTest {
    @Test
    fun `bag extraction result round trips compact payload`() {
        val result = BagPhotoProcessingResult(
            capturedPhotoUris = "content://front,content://back",
            detectedBarcode = "1234567890123",
            detectedQrUrl = "https://example.test/bag",
            offLookupName = "Moonlit Orchard",
            offLookupRoaster = "North Axis",
            fieldEvidence = mapOf(
                "name" to BagFieldEvidence(
                    fieldName = "name",
                    value = "Moonlit Orchard",
                    rawValue = "Moonlit Orchard",
                    canonicalKey = "moonlit_orchard",
                    sourceType = BagFieldSourceType.LLM,
                    confidence = BagFieldConfidence.HIGH,
                    side = BagCaptureSide.FRONT,
                    matchStrategy = CoffeeMetadataMatchStrategy.EXACT_ALIAS,
                    supportingText = "label text",
                    previewUri = "content://front",
                    previewRect = BagPhotoRect(0.1f, 0.2f, 0.8f, 0.9f),
                ),
            ),
            reviewHints = listOf(BagPhotoReviewHint(BagReviewSeverity.WARNING, "Check roaster")),
            llmStatus = LlmEnrichmentStatus.SUCCEEDED,
            thumbnailFocus = BagPhotoRect(0.2f, 0.3f, 0.7f, 0.8f),
        )

        val decoded = decodeBagExtractionResult(result.encodeToJson())
        val evidence = decoded.fieldEvidence.getValue("name")

        assertEquals("Moonlit Orchard", decoded.ocrPrefill?.name)
        assertEquals("content://front,content://back", decoded.capturedPhotoUris)
        assertEquals("1234567890123", decoded.detectedBarcode)
        assertEquals(LlmEnrichmentStatus.SUCCEEDED, decoded.llmStatus)
        assertEquals(BagFieldSourceType.LLM, evidence.sourceType)
        assertEquals(BagFieldConfidence.HIGH, evidence.confidence)
        assertEquals(BagCaptureSide.FRONT, evidence.side)
        assertEquals(CoffeeMetadataMatchStrategy.EXACT_ALIAS, evidence.matchStrategy)
        assertEquals(BagReviewSeverity.WARNING, decoded.reviewHints.single().severity)
        assertTrue(decoded.photoAnalyses.isEmpty())
    }

    @Test
    fun `bag extraction result trims bulky optional evidence`() {
        val result = BagPhotoProcessingResult(
            fieldEvidence = mapOf(
                "name" to BagFieldEvidence(
                    fieldName = "name",
                    value = "Moonlit Orchard",
                    sourceType = BagFieldSourceType.LLM,
                    confidence = BagFieldConfidence.HIGH,
                    supportingText = "x".repeat(10_000),
                    previewUri = "content://front",
                ),
            ),
            llmStatus = LlmEnrichmentStatus.SUCCEEDED,
        )

        val json = result.encodeToJson()
        val decoded = decodeBagExtractionResult(json)

        assertTrue(json.length <= 9_000)
        assertNull(decoded.fieldEvidence.getValue("name").supportingText)
        assertNull(decoded.fieldEvidence.getValue("name").previewUri)
    }

    @Test
    fun `known field values round trip and blank input decodes empty`() {
        val knownValues = KnownFieldValues(
            names = listOf("Moonlit Orchard"),
            roasters = listOf("North Axis"),
            origins = listOf("Ethiopia"),
            regions = listOf("Yirgacheffe"),
            varieties = listOf("Gesha"),
            processTypes = listOf("Washed"),
            roastLevels = listOf("Light"),
            farms = listOf("Konga"),
        )

        assertEquals(knownValues, decodeKnownFieldValues(knownValues.encodeToJson()))
        assertEquals(KnownFieldValues.EMPTY, decodeKnownFieldValues(null))
    }
}
