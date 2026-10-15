package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagDraftConversionTest {
    @Test
    fun `conversion survives restart with content photos and user revisions but no former target`() {
        val original = rescanDraft()
            .applyUserEdit(BagDraftField.NAME, "My coffee", nowMillis = 3L)
            .applyUserEdit(BagDraftField.ROASTER, null, nowMillis = 4L)
        val converted = original.convertRescanToNewBag("rescan-generation", "new-bag-generation", 5L)
        val directory = Files.createTempDirectory("rescan-conversion").toFile()
        try {
            BagDraftStore.write(directory, converted, FileSync { }, DirectorySync { })
            val restored = requireNotNull(BagDraftStore.read(directory, converted.sessionId))

            assertEquals(original.sessionId, restored.sessionId)
            assertEquals(original.fields, restored.fields)
            assertEquals(original.photoUris, restored.photoUris)
            assertEquals(BagReviewContext.addNew(), restored.reviewContext)
            assertEquals(BagDraftPhase.REVIEWING, restored.phase)
            assertEquals(RecognitionRunState.COMPLETE, restored.recognitionRunState)
            assertEquals("new-bag-generation", restored.generationId)
            assertNull(restored.workId)
            assertNull(restored.pendingExternalSetupReason)
            val result = decodeBagExtractionResult(requireNotNull(restored.resultJson))
            assertEquals("Coffee from photo", result.ocrPrefill?.name)
            assertEquals("content://front", result.fieldEvidence["name"]?.previewUri)
            assertEquals("Coffee from photo", result.fieldEvidence["name"]?.supportingText)
            assertEquals(LlmEnrichmentStatus.NOT_RUN, result.llmStatus)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `late former rescan result cannot alter converted draft even before work cancellation`() {
        val converted = rescanDraft().convertRescanToNewBag("rescan-generation", "new-bag-generation", 5L)
        val late = converted.applyRecognitionResult(
            incoming = BagPhotoProcessingResult(
                ocrPrefill = OcrFieldExtractor.OcrExtractionResult(name = "Former target", roaster = "Old roaster"),
                capturedPhotoUris = "content://wrong-photo",
            ),
            sourceRevision = 6L,
            generationId = "rescan-generation",
            workId = "rescan-work",
            terminal = true,
        )

        assertEquals(converted, late)
    }

    @Test
    fun `stale click and closed or already converted drafts do not change ownership`() {
        val draft = rescanDraft()
        assertEquals(draft, draft.convertRescanToNewBag("stale-generation", "new-bag-generation", 5L))
        val closed = draft.closeAsTombstone(BagDraftPhase.SAVED, 4L)
        assertEquals(closed, closed.convertRescanToNewBag("rescan-generation", "new-bag-generation", 5L))
        val converted = draft.convertRescanToNewBag("rescan-generation", "new-bag-generation", 5L)
        assertEquals(converted, converted.convertRescanToNewBag("new-bag-generation", "third-generation", 6L))
    }

    private fun rescanDraft(): BagScanDraft = newBagScanDraft(
        sessionId = UUID.randomUUID().toString(),
        photoUris = listOf("content://front", "content://back"),
        reviewContext = BagReviewContext.rescan(42L),
        preference = RecognitionPreference.ENABLED,
        nowMillis = 1L,
    ).applyRecognitionResult(
        incoming = BagPhotoProcessingResult(
            ocrPrefill = OcrFieldExtractor.OcrExtractionResult(name = "Coffee from photo", roaster = "Read roaster"),
            fieldEvidence = mapOf(
                "name" to BagFieldEvidence(
                    fieldName = "name", value = "Coffee from photo", sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.HIGH, supportingText = "Coffee from photo", previewUri = "content://front",
                ),
                "roaster" to BagFieldEvidence(
                    fieldName = "roaster", value = "Read roaster", sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.MEDIUM,
                ),
            ),
            capturedPhotoUris = "content://front,content://back",
            llmStatus = LlmEnrichmentStatus.NOT_RUN,
        ),
        sourceRevision = 2L,
        generationId = "rescan-generation",
        workId = "rescan-work",
        terminal = false,
    ).copy(pendingExternalSetupReason = "former-rescan-setup")
}
