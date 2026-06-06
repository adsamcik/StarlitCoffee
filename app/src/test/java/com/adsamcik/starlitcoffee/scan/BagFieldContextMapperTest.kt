package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.domain.scanfield.FieldSource
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagFieldContextMapperTest {

    private fun candidate(
        field: String,
        value: String,
        source: BagFieldSourceType,
        confidence: BagFieldConfidence,
    ) = BagFieldCandidate(
        fieldName = field,
        value = value,
        sourceType = source,
        confidenceHint = confidence,
    )

    @Test
    fun `empty input yields empty map`() {
        assertTrue(BagFieldContextMapper.buildExistingFieldsContext(emptyList()).isEmpty())
    }

    @Test
    fun `low and needs-review candidates are filtered out`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("origin", "Kenya", BagFieldSourceType.OCR, BagFieldConfidence.LOW),
                candidate("roaster", "X", BagFieldSourceType.OCR, BagFieldConfidence.NEEDS_REVIEW),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `high and medium candidates are kept`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("origin", "Kenya", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
                candidate("roaster", "Acme", BagFieldSourceType.OCR, BagFieldConfidence.MEDIUM),
            ),
        )
        assertEquals(setOf("origin", "roaster"), result.keys)
        assertEquals("Kenya", result["origin"]?.value)
    }

    @Test
    fun `lookup source outranks ocr for the same field`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("origin", "FromOcr", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
                candidate(
                    "origin",
                    "FromLookup",
                    BagFieldSourceType.BARCODE_LOOKUP,
                    BagFieldConfidence.MEDIUM,
                ),
            ),
        )
        assertEquals("FromLookup", result["origin"]?.value)
        assertEquals(FieldSource.LOOKUP, result["origin"]?.source)
    }

    @Test
    fun `higher confidence wins within the same source`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("origin", "Medium", BagFieldSourceType.OCR, BagFieldConfidence.MEDIUM),
                candidate("origin", "High", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
            ),
        )
        assertEquals("High", result["origin"]?.value)
    }

    @Test
    fun `source types map to the expected FieldSource taxonomy`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("a", "v", BagFieldSourceType.LLM, BagFieldConfidence.HIGH),
                candidate("b", "v", BagFieldSourceType.QR_LINK_LOOKUP, BagFieldConfidence.HIGH),
                candidate("c", "v", BagFieldSourceType.CONSENSUS, BagFieldConfidence.HIGH),
                candidate("d", "v", BagFieldSourceType.OBSERVED_BARCODE_STEM, BagFieldConfidence.HIGH),
            ),
        )
        assertEquals(FieldSource.LLM, result["a"]?.source)
        assertEquals(FieldSource.LOOKUP, result["b"]?.source)
        assertEquals(FieldSource.OCR, result["c"]?.source)
        assertEquals(FieldSource.LOOKUP, result["d"]?.source)
    }

    @Test
    fun `blank values are dropped and value is trimmed`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(
                candidate("origin", "   ", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
                candidate("roaster", "  Acme  ", BagFieldSourceType.OCR, BagFieldConfidence.HIGH),
            ),
        )
        assertNull(result["origin"])
        assertEquals("Acme", result["roaster"]?.value)
        assertFalse(result.containsKey("origin"))
    }

    @Test
    fun `confidence hint name is carried into the context`() {
        val result = BagFieldContextMapper.buildExistingFieldsContext(
            listOf(candidate("origin", "Kenya", BagFieldSourceType.OCR, BagFieldConfidence.HIGH)),
        )
        assertEquals(BagFieldConfidence.HIGH.name, result["origin"]?.confidence)
    }
}
