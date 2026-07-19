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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagExtractionPayloadTest {
    @Test
    fun `rescan review context round trips target and mode`() {
        val context = BagReviewContext.rescan(targetBagId = 42L)

        assertEquals(context, decodeBagReviewContext(encodeBagReviewContext(context)))
        assertNull(decodeBagReviewContext(null))
        assertNull(decodeBagReviewContext("""{"mode":"RESCAN"}"""))
    }

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

        assertTrue(json.toByteArray(Charsets.UTF_8).size <= 7_500)
        assertNull(decoded.fieldEvidence.getValue("name").supportingText)
        assertNull(decoded.fieldEvidence.getValue("name").previewUri)
    }

    @Test
    fun `progress preview payload keeps field values without bulky evidence`() {
        val result = BagPhotoProcessingResult(
            capturedPhotoUris = "file:///cache/front.jpg,file:///cache/back.jpg",
            fieldEvidence = mapOf(
                "name" to BagFieldEvidence(
                    fieldName = "name",
                    value = "Moonlit Orchard",
                    rawValue = "Moonlit Orchard",
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.MEDIUM,
                    supportingText = "x".repeat(10_000),
                    previewUri = "file:///cache/front.jpg",
                    previewRect = BagPhotoRect(0.1f, 0.2f, 0.8f, 0.9f),
                ),
            ),
        )

        val decoded = decodeBagExtractionResult(requireNotNull(result.encodeForProgressJson()))
        val evidence = decoded.fieldEvidence.getValue("name")

        assertEquals("Moonlit Orchard", evidence.value)
        assertEquals("file:///cache/front.jpg,file:///cache/back.jpg", decoded.capturedPhotoUris)
        assertNull(evidence.supportingText)
        assertNull(evidence.previewUri)
        assertNull(evidence.previewRect)
    }

    @Test
    fun `progress preview payload remains bounded for multibyte field values`() {
        val result = BagPhotoProcessingResult(
            capturedPhotoUris = "https://example.test/${"á".repeat(2_000)}",
            detectedQrUrl = "https://example.test/${"á".repeat(8_000)}",
            offLookupName = "á".repeat(1_000),
            offLookupRoaster = "á".repeat(1_000),
            fieldEvidence = (1..100).associate { index ->
                "field$index" to BagFieldEvidence(
                    fieldName = "field$index",
                    value = "á".repeat(1_000),
                    rawValue = "á".repeat(1_000),
                    canonicalKey = "á".repeat(1_000),
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.MEDIUM,
                    previewRect = BagPhotoRect(0.1f, 0.2f, 0.8f, 0.9f),
                )
            },
        )

        val json = result.encodeForProgressJson()

        assertNotNull(json)
        assertTrue(json!!.toByteArray(Charsets.UTF_8).size <= 8_000)
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
            altitudes = listOf("1,900 masl"),
        )

        assertEquals(knownValues, decodeKnownFieldValues(knownValues.encodeToJson()))
        assertEquals(KnownFieldValues.EMPTY, decodeKnownFieldValues(null))
    }

    @Test
    fun `work payloads remain bounded for large multibyte values`() {
        val largeValue = "á".repeat(1_000)
        val result = BagPhotoProcessingResult(
            capturedPhotoUris = "file:///$largeValue",
            fieldEvidence = (1..14).associate { index ->
                "field$index" to BagFieldEvidence(
                    fieldName = "field$index",
                    value = largeValue,
                    rawValue = largeValue,
                    canonicalKey = largeValue,
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.MEDIUM,
                )
            },
            reviewHints = List(20) {
                BagPhotoReviewHint(BagReviewSeverity.WARNING, largeValue)
            },
        )
        val knownValues = KnownFieldValues(
            names = List(100) { "$largeValue-$it" },
            roasters = List(100) { "$largeValue-$it" },
            origins = List(100) { "$largeValue-$it" },
            regions = List(100) { "$largeValue-$it" },
            varieties = List(100) { "$largeValue-$it" },
            processTypes = List(100) { "$largeValue-$it" },
            roastLevels = List(100) { "$largeValue-$it" },
            tastingNotes = List(100) { "$largeValue-$it" },
            farms = List(100) { "$largeValue-$it" },
        )

        assertTrue(result.encodeToJson().toByteArray(Charsets.UTF_8).size <= 7_500)
        assertTrue(knownValues.encodeToJson().toByteArray(Charsets.UTF_8).size <= 5_000)
    }
}
