package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the post-removal OcrFieldExtractor surface.
 *
 * Field-level extraction (name / roaster / origin / region / variety /
 * process / roastLevel / weight / dates / tasting notes) is the LLM's
 * job — see [com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider]
 * and `CoffeeBagCorpusExtractionTest`. This file used to contain ~90
 * regex-extraction assertions; those were removed when the heuristic
 * extractor was replaced by LLM-only extraction.
 *
 * What's still tested here:
 *  - [OcrFieldExtractor.extractBarcodeFromText] — narrow EAN-13/12
 *    numeric pattern detector that survived removal because barcode is
 *    language-agnostic and the pattern is unambiguous.
 *  - The [OcrFieldExtractor.OcrTextBlock.normalizedBounds] geometry helper.
 */
class OcrFieldExtractorTest {

    @Test
    fun `extractBarcodeFromText returns null on empty input`() {
        assertNull(OcrFieldExtractor.extractBarcodeFromText(""))
    }

    @Test
    fun `extractBarcodeFromText returns null when no 12 or 13 digit run is present`() {
        // 11 digits — too short. 14 digits — too long.
        assertNull(OcrFieldExtractor.extractBarcodeFromText("contact 12345678901 to order"))
        assertNull(OcrFieldExtractor.extractBarcodeFromText("ref 12345678901234 sku"))
    }

    @Test
    fun `extractBarcodeFromText recovers an EAN-13 number embedded in OCR text`() {
        val ocrText = """
            Merrybeans Kolumbie TUMBAGA DECAF - bezkofeinová
            #17838 - Datum pražení: 12.12.2025
            8594206183060
        """.trimIndent()
        val barcode = OcrFieldExtractor.extractBarcodeFromText(ocrText)
        assertNotNull("Should extract EAN-13 from OCR text", barcode)
        assertEquals("8594206183060", barcode)
    }

    @Test
    fun `extractBarcodeFromText accepts space-separated digit groups`() {
        // PaddleOCR often inserts a space between the EAN prefix and body.
        val ocrText = "8 594206 183060"
        val barcode = OcrFieldExtractor.extractBarcodeFromText(ocrText)
        assertEquals("8594206183060", barcode)
    }

    @Test
    fun `extractBarcodeFromText accepts 12-digit UPC-A`() {
        val ocrText = "any prefix 012345678905 suffix"
        val barcode = OcrFieldExtractor.extractBarcodeFromText(ocrText)
        assertEquals("012345678905", barcode)
    }

    @Test
    fun `extractBarcodeFromText returns the first match when multiple are present`() {
        val ocrText = """
            First: 8594206183060
            Second: 8594200941437
        """.trimIndent()
        val barcode = OcrFieldExtractor.extractBarcodeFromText(ocrText)
        assertEquals("8594206183060", barcode)
    }

    @Test
    fun `OcrExtractionResult defaults to all-null fields with empty rawText`() {
        // Post-removal contract: callers construct OcrExtractionResult(rawText = ...)
        // and rely on the LLM to populate the rest.
        val result = OcrFieldExtractor.OcrExtractionResult()
        assertNull(result.name)
        assertNull(result.roaster)
        assertNull(result.origin)
        assertNull(result.region)
        assertNull(result.farm)
        assertNull(result.variety)
        assertNull(result.processType)
        assertNull(result.altitude)
        assertNull(result.tastingNotes)
        assertNull(result.roastLevel)
        assertNull(result.roastDate)
        assertNull(result.expiryDate)
        assertNull(result.weight)
        assertNull(result.isDecaf)
        assertEquals("", result.rawText)
        assertEquals(emptyMap<String, BagFieldConfidence>(), result.fieldConfidence)
    }

    @Test
    fun `OcrTextBlock normalizedBounds returns null for zero-sized image`() {
        val block = OcrFieldExtractor.OcrTextBlock(
            text = "Merrybeans",
            heightPx = 120,
            topPx = 800,
            leftPx = 200,
            widthPx = 600,
            imageWidthPx = 0,
            imageHeightPx = 0,
        )
        assertNull(block.normalizedBounds())
    }

    @Test
    fun `OcrTextBlock normalizedBounds returns padded fractional rect`() {
        val block = OcrFieldExtractor.OcrTextBlock(
            text = "Merrybeans",
            heightPx = 200,
            topPx = 1000,
            leftPx = 100,
            widthPx = 800,
            imageWidthPx = 1000,
            imageHeightPx = 2000,
        )
        val bounds = block.normalizedBounds(paddingFraction = 0.05f)
        assertNotNull(bounds)
        // left = 100/1000 - 0.05 = 0.05, right = 900/1000 + 0.05 = 0.95
        // top = 1000/2000 - 0.05 = 0.45, bottom = 1200/2000 + 0.05 = 0.65
        assertEquals(0.05f, bounds!!.leftFraction, 0.001f)
        assertEquals(0.95f, bounds.rightFraction, 0.001f)
        assertEquals(0.45f, bounds.topFraction, 0.001f)
        assertEquals(0.65f, bounds.bottomFraction, 0.001f)
    }

    @Test
    fun `OcrTextBlock normalizedBounds clamps padding to image edges`() {
        val block = OcrFieldExtractor.OcrTextBlock(
            text = "edge",
            heightPx = 100,
            topPx = 0,
            leftPx = 0,
            widthPx = 1000,
            imageWidthPx = 1000,
            imageHeightPx = 1000,
        )
        val bounds = block.normalizedBounds(paddingFraction = 0.10f)
        assertNotNull(bounds)
        assertEquals(0f, bounds!!.leftFraction, 0.001f)
        assertEquals(1f, bounds.rightFraction, 0.001f)
        assertEquals(0f, bounds.topFraction, 0.001f)
    }
}
