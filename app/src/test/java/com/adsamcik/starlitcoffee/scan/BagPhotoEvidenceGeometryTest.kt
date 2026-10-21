package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagPhotoEvidenceGeometryTest {
    private val block = OcrFieldExtractor.OcrTextBlock(
        text = "250 g",
        heightPx = 30,
        topPx = 80,
        leftPx = 40,
        widthPx = 100,
        imageWidthPx = 400,
        imageHeightPx = 600,
    )

    @Test
    fun `original OCR highlights its normalized region on the retained photo`() {
        assertEquals(block.normalizedBounds(), sourcePhotoEvidenceRect("original", block))
    }

    @Test
    fun `transformed OCR falls back to the whole original photo without a false region`() {
        listOf("aligned", "enhanced", "unknown").forEach { pass ->
            assertNull(sourcePhotoEvidenceRect(pass, block))
        }
        assertNull(sourcePhotoEvidenceRect("original", null))
    }
}
