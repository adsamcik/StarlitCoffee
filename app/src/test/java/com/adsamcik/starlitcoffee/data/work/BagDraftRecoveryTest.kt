package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BagDraftRecoveryTest {
    private val workId = UUID.randomUUID().toString()
    private val result = BagPhotoProcessingResult(
        ocrPrefill = OcrFieldExtractor.OcrExtractionResult(
            name = "Recognized coffee", roaster = "Final roaster", weight = "750 g",
        ),
        capturedPhotoUris = "content://front,content://back",
        fieldEvidence = mapOf(
            "name" to BagFieldEvidence(
                "name", "Recognized coffee", sourceType = BagFieldSourceType.OCR, confidence = BagFieldConfidence.HIGH,
            ),
            "weight" to BagFieldEvidence(
                "weight", "750 g", sourceType = BagFieldSourceType.OCR, confidence = BagFieldConfidence.NEEDS_REVIEW,
            ),
            "roaster" to BagFieldEvidence(
                "roaster", "Final roaster", sourceType = BagFieldSourceType.OCR, confidence = BagFieldConfidence.HIGH,
            ),
        ),
    )

    @Test
    fun `retained legacy result restores an exact durable review with weak readings kept as suggestions`() {
        val directory = Files.createTempDirectory("recovered-bag-draft").toFile()
        val restored = recoverBagScanDraft(
            null, "default-bag-photo-session", "generation", workId,
            BagReviewContext.rescan(42L), RecognitionPreference.DISABLED, result, 1L,
        )
        try {
            BagDraftStore.write(directory, restored, FileSync { }, DirectorySync { })
            val reopened = requireNotNull(BagDraftStore.read(directory, workId))

            assertEquals(workId, reopened.sessionId)
            assertEquals(workId, reopened.workId)
            assertEquals("generation", reopened.generationId)
            assertEquals(BagDraftPhase.REVIEWING, reopened.phase)
            assertEquals(BagReviewContext.rescan(42L), reopened.reviewContext)
            assertEquals(RecognitionPreference.DISABLED, reopened.recognitionPreference)
            assertEquals(listOf("content://front", "content://back"), reopened.photoUris)
            assertEquals("Recognized coffee", reopened.field(BagDraftField.NAME).value)
            assertNull(reopened.field(BagDraftField.WEIGHT).value)
            assertEquals("750 g", reopened.field(BagDraftField.WEIGHT).pendingSuggestion?.value)
            val retainedResult = decodeBagExtractionResult(requireNotNull(reopened.resultJson))
            assertEquals(result.fieldEvidence, retainedResult.fieldEvidence)
            assertEquals(result.capturedPhotoUris, retainedResult.capturedPhotoUris)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `existing draft and terminal owner survive old retained work unchanged`() {
        val current = recoverBagScanDraft(
            null, workId, "generation", workId, null, RecognitionPreference.ENABLED, result, 1L,
        ).applyUserEdit(BagDraftField.NAME, "My chosen coffee", 2L)
        listOf(current, current.closeAsTombstone(BagDraftPhase.SAVED, 3L)).forEach { owner ->
            assertSame(owner, recoverBagScanDraft(
                owner, workId, "old-generation", workId,
                BagReviewContext.rescan(99L), RecognitionPreference.DISABLED, result, 4L,
            ))
        }
    }

    @Test
    fun `stored final result settles interrupted draft without changing edited or focused fields`() {
        val interrupted = newBagScanDraft(
            workId, listOf("content://front", "content://back"), BagReviewContext.addNew(),
            RecognitionPreference.ENABLED, 1L,
        ).copy(generationId = "generation", recognitionRunState = RecognitionRunState.RUNNING)
            .applyUserEdit(BagDraftField.NAME, "My coffee", 2L)
        val completed = recoverBagScanDraft(
            interrupted, workId, "generation", workId, null, RecognitionPreference.ENABLED,
            result, 3L, focusedFields = setOf(BagDraftField.WEIGHT),
        )

        assertEquals(RecognitionRunState.COMPLETE, completed.recognitionRunState)
        assertEquals("My coffee", completed.field(BagDraftField.NAME).value)
        assertEquals("Recognized coffee", completed.field(BagDraftField.NAME).pendingSuggestion?.value)
        assertEquals("Final roaster", completed.field(BagDraftField.ROASTER).value)
        assertNull(completed.field(BagDraftField.WEIGHT).value)
        assertNull(completed.field(BagDraftField.WEIGHT).pendingSuggestion)
        assertSame(completed, recoverBagScanDraft(
            completed, workId, "generation", workId, null, RecognitionPreference.ENABLED, result, 4L,
        ))
    }

    @Test
    fun `ordinary UUID sessions retain their identity during recovery`() {
        val sessionId = UUID.randomUUID().toString()
        assertEquals(sessionId, recoveredBagReviewSessionId(sessionId, workId))
        assertEquals(workId, recoveredBagReviewSessionId("default-bag-photo-session", workId))
    }
}
