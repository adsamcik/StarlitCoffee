package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelEvidencePreviewTest {
    private val front = "file:///scan/front.jpg"
    private val back = "file:///scan/back.jpg"
    private val validRegion = BagPhotoRect(0.1f, 0.2f, 0.7f, 0.8f)

    @Test
    fun `binds exact retained photo even when it is not first`() {
        val source = resolveLabelEvidencePhoto(evidence(uri = back, region = validRegion), "$front,$back,file:///scan/extra.jpg")
        assertEquals(back, source.uri)
        assertEquals(validRegion, source.region)
        assertFalse(source.sourceUnavailable)
    }

    @Test
    fun `removed explicit source never falls back to first remaining photo`() {
        val source = resolveLabelEvidencePhoto(evidence(uri = front, region = validRegion), back)
        assertNull(source.uri)
        assertNull(source.region)
        assertTrue(source.sourceUnavailable)
    }

    @Test
    fun `missing retained capture data cannot expose former photo`() {
        val source = resolveLabelEvidencePhoto(evidence(uri = front, region = validRegion), null)
        assertNull(source.uri)
        assertNull(source.region)
        assertTrue(source.sourceUnavailable)
    }

    @Test
    fun `legacy side alone cannot identify photo after removal or reordering`() {
        listOf(front, "$back,$front", "$front,$back,file:///scan/extra.jpg").forEach { retained ->
            val source = resolveLabelEvidencePhoto(evidence(uri = null).copy(side = BagCaptureSide.FRONT), retained)
            assertNull(source.uri)
            assertNull(source.region)
            assertTrue(source.sourceUnavailable)
        }
    }

    @Test
    fun `missing region shows entire exact photo without fabricated highlight`() {
        val source = resolveLabelEvidencePhoto(evidence(uri = back), "$front,$back")
        assertEquals(back, source.uri)
        assertNull(source.region)
        assertFalse(source.sourceUnavailable)
    }

    @Test
    fun `invalid or nonfinite bounds retain whole source photo`() {
        val invalidRegions = listOf(
            BagPhotoRect(Float.NaN, 0f, 1f, 1f),
            BagPhotoRect(0f, Float.NEGATIVE_INFINITY, 1f, 1f),
            BagPhotoRect(0f, 0f, Float.POSITIVE_INFINITY, 1f),
            BagPhotoRect(-0.1f, 0f, 1f, 1f),
            BagPhotoRect(0f, 0f, 1.1f, 1f),
            BagPhotoRect(0.5f, 0f, 0.5f, 1f),
            BagPhotoRect(0.8f, 0f, 0.2f, 1f),
            BagPhotoRect(0f, 0.8f, 1f, 0.2f),
        )
        invalidRegions.forEach { region ->
            val source = resolveLabelEvidencePhoto(evidence(uri = front, region = region), front)
            assertEquals(front, source.uri)
            assertNull(source.region)
            assertFalse(source.sourceUnavailable)
        }
    }

    @Test
    fun `generated context cannot draw a photographic highlight`() {
        val generated = evidence(uri = front, region = validRegion).copy(sourceType = BagFieldSourceType.LLM)
        val source = resolveLabelEvidencePhoto(generated, front)
        assertEquals(front, source.uri)
        assertNull(source.region)
    }

    @Test
    fun `lookup provenance does not claim missing photograph or borrow one`() {
        val lookup = evidence(uri = null).copy(sourceType = BagFieldSourceType.QR_LINK_LOOKUP)
        val source = resolveLabelEvidencePhoto(lookup, front)
        assertNull(source.uri)
        assertNull(source.region)
        assertFalse(source.sourceUnavailable)
    }

    @Test
    fun `later different reading cannot lend its photo or text to retained pending suggestion`() {
        val laterReading = evidence(uri = back, region = validRegion).copy(supportingText = "Origin Colombia")
        assertNull(labelReviewSourceEvidence("origin", laterReading, "Ethiopia"))
    }

    @Test
    fun `matching pending reading retains its own source only for same field`() {
        val matching = evidence(uri = back, region = validRegion)
        assertEquals(matching, labelReviewSourceEvidence("origin", matching, "Colombia"))
        assertNull(labelReviewSourceEvidence("region", matching, "Colombia"))
    }

    @Test
    fun `accepted source remains inspectable after current value was edited`() {
        val original = evidence(uri = front, region = validRegion)
        assertEquals(original, labelReviewSourceEvidence("origin", original, null))
    }

    @Test
    fun `missing optional field does not need a check`() {
        assertFalse(labelReviewNeedsCheck("", evidence(), null, null))
        assertFalse(labelReviewNeedsCheck("", null, null, null))
    }

    @Test
    fun `explicit correction suppresses old evidence warning while new suggestion remains reviewable`() {
        assertTrue(labelReviewNeedsCheck("Colombia", evidence(), null, null))
        assertFalse(labelReviewNeedsCheck("Colombia", evidence(), null, false))
        assertTrue(labelReviewNeedsCheck("Colombia", evidence(), "Ethiopia", false))
        assertFalse(labelReviewNeedsCheck("Colombia", evidence().copy(confidence = BagFieldConfidence.HIGH), null, null))
    }

    private fun evidence(uri: String? = front, region: BagPhotoRect? = null): BagFieldEvidence = BagFieldEvidence(
        fieldName = "origin",
        value = "Colombia",
        sourceType = BagFieldSourceType.OCR,
        confidence = BagFieldConfidence.LOW,
        previewUri = uri,
        previewRect = region,
    )
}
