package com.adsamcik.starlitcoffee.data.work

import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoAnalysis
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BagExtractionWorkerDeliveryTest {

    @Test
    fun `expired restart retains checkpoint evidence through OCR-only preview and interrupted terminal replay`() {
        val checkpoint = BagPhotoProcessingResult(fieldEvidence = mapOf(
            "name" to BagFieldEvidence(
                fieldName = "name",
                value = "Retained coffee",
                sourceType = BagFieldSourceType.OCR,
                confidence = BagFieldConfidence.HIGH,
                previewUri = "content://front",
            ),
        ))
        val restored = decodeBagExtractionResult(checkpoint.encodeToStoredJson())
        val ocrOnly = BagPhotoProcessingResult(
            capturedPhotoUris = "content://front",
            photoAnalyses = listOf(BagPhotoAnalysis(
                uri = "content://front",
                side = BagCaptureSide.FRONT,
                quality = BagCaptureQuality(20f, 0f, 0f, 0f, 1, true),
                extractedText = "Fresh OCR text",
            )),
        )
        val preview = retainBagExtractionPreview(ocrOnly, restored)
        val checkpointAfterPreview = decodeBagExtractionResult(preview.encodeToStoredJson())
        assertEquals(checkpoint.fieldEvidence, preview.fieldEvidence)
        listOf(
            LlmEnrichmentStatus.TIMED_OUT, LlmEnrichmentStatus.SETUP_REQUIRED,
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED, LlmEnrichmentStatus.UNAVAILABLE, LlmEnrichmentStatus.FAILED,
        ).forEach { status ->
            val result = retainBagExtractionPartial(
                terminalResult = ocrOnly.copy(llmStatus = status),
                latestPartialResult = checkpointAfterPreview,
            )
            val replay = StoredBagExtractionResult(
                successful = true,
                resultJson = result.encodeToStoredJson(),
                createdAtMillis = 42L,
            ).toTerminalReplay(null)

            assertEquals("Retained coffee", replay.result?.ocrPrefill?.name)
            assertEquals(checkpoint.fieldEvidence, replay.result?.fieldEvidence)
            assertEquals(status, replay.result?.llmStatus)
            assertEquals("content://front", replay.result?.capturedPhotoUris)
        }
    }

    @Test
    fun `barcode-only restart preview and timeout retain previous fields alongside fresh barcode`() {
        val checkpoint = BagPhotoProcessingResult(fieldEvidence = mapOf(
            "name" to BagFieldEvidence(
                fieldName = "name",
                value = "Retained coffee",
                sourceType = BagFieldSourceType.OCR,
                confidence = BagFieldConfidence.HIGH,
            ),
        ))
        val barcodeOnly = BagPhotoProcessingResult(
            capturedPhotoUris = "content://front",
            detectedBarcode = "12345678",
        )
        val preview = retainBagExtractionPreview(barcodeOnly, decodeBagExtractionResult(checkpoint.encodeToStoredJson()))
        val terminal = retainBagExtractionPartial(
            barcodeOnly.copy(llmStatus = LlmEnrichmentStatus.TIMED_OUT),
            decodeBagExtractionResult(preview.encodeToStoredJson()),
        )
        val replay = StoredBagExtractionResult(true, terminal.encodeToStoredJson(), 42L).toTerminalReplay(null)

        assertEquals(checkpoint.fieldEvidence, preview.fieldEvidence)
        assertEquals("12345678", preview.detectedBarcode)
        assertEquals("Retained coffee", replay.result?.ocrPrefill?.name)
        assertEquals("12345678", replay.result?.detectedBarcode)
        assertEquals(LlmEnrichmentStatus.TIMED_OUT, replay.result?.llmStatus)
    }

    @Test
    fun `successful empty extraction does not revive checkpoint values`() {
        val checkpoint = BagPhotoProcessingResult(detectedBarcode = "12345678")
        val successful = BagPhotoProcessingResult(
            capturedPhotoUris = "content://front",
            llmStatus = LlmEnrichmentStatus.SUCCEEDED,
        )

        assertSame(successful, retainBagExtractionPartial(successful, checkpoint))
    }

    @Test
    fun `later failure retains the latest partial fields through terminal result replay`() {
        val partial = BagPhotoProcessingResult(fieldEvidence = mapOf(
            "name" to BagFieldEvidence(
                fieldName = "name",
                value = "My coffee",
                sourceType = BagFieldSourceType.OCR,
                confidence = BagFieldConfidence.HIGH,
            ),
        ))
        val failure = bagExtractionFailureResult(partial, null, "content://front")
        val replay = StoredBagExtractionResult(
            successful = false,
            resultJson = failure.encodeToStoredJson(),
            createdAtMillis = 42L,
        ).toTerminalReplay(null)

        assertFalse(replay.successful)
        assertEquals("My coffee", replay.result?.ocrPrefill?.name)
        assertEquals(partial.fieldEvidence, replay.result?.fieldEvidence)
        assertEquals(LlmEnrichmentStatus.UNAVAILABLE, replay.result?.llmStatus)
        assertEquals("content://front", replay.result?.capturedPhotoUris)
    }

    @Test
    fun `failure before a fresh partial retains the persisted checkpoint`() {
        val checkpoint = BagPhotoProcessingResult(
            capturedPhotoUris = "content://front",
            detectedBarcode = "12345678",
        )

        val failure = bagExtractionFailureResult(
            latestPartialResult = BagPhotoProcessingResult(capturedPhotoUris = "content://front"),
            checkpointJson = checkpoint.encodeToStoredJson(),
            capturedPhotoUris = "",
        )

        assertEquals("12345678", failure.detectedBarcode)
        assertEquals("content://front", failure.capturedPhotoUris)
        assertEquals(LlmEnrichmentStatus.UNAVAILABLE, failure.llmStatus)
    }

    @Test
    fun `corrupt checkpoint still yields a terminal failure without fabricating fields`() {
        val failure = bagExtractionFailureResult(null, "invalid json", "content://front")

        assertTrue(failure.fieldEvidence.isEmpty())
        assertEquals("content://front", failure.capturedPhotoUris)
        assertEquals(LlmEnrichmentStatus.UNAVAILABLE, failure.llmStatus)
    }

    @Test
    fun `background scan input requests a completion notification`() {
        val input = workDataOf(BagExtractionWorker.KEY_NOTIFY_ON_COMPLETE to true)

        assertTrue(BagExtractionWorker.shouldNotifyOnCompletion(input))
    }

    @Test
    fun `foreground scan input does not request a completion notification`() {
        assertFalse(BagExtractionWorker.shouldNotifyOnCompletion(workDataOf()))
    }

    @Test
    fun `WorkManager result preserves rescan review context`() {
        val reviewContext = BagReviewContext.rescan(42L)
        val output = bagExtractionTerminalOutput(reviewContext)

        assertTrue(output.getBoolean(BagExtractionWorker.KEY_RESULT_STORED, false))
        assertEquals(
            reviewContext,
            decodeBagReviewContext(
                output.getString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON),
            ),
        )
    }

    @Test
    fun `process death after successful result persistence replays success without extraction`() {
        var extractionRuns = 0
        val reviewContext = BagReviewContext.rescan(42L)

        val replay = replayStoredBagExtractionResultOrRun(
            storedResult = StoredBagExtractionResult(
                successful = true,
                resultJson = """{"persisted":"success"}""",
                createdAtMillis = 42L,
                reviewContext = reviewContext,
            ),
            fallbackReviewContext = null,
            onReplay = { it },
            runExtraction = {
                extractionRuns++
                error("Extraction must not run after a durable terminal result")
            },
        )

        assertEquals(0, extractionRuns)
        assertTrue(replay.successful)
        assertEquals(
            reviewContext,
            decodeBagReviewContext(
                replay.outputData.getString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON),
            ),
        )
    }

    @Test
    fun `process death after failed result persistence replays failure without extraction`() {
        var extractionRuns = 0
        val reviewContext = BagReviewContext.rescan(84L)

        val replay = replayStoredBagExtractionResultOrRun(
            storedResult = StoredBagExtractionResult(
                successful = false,
                resultJson = """{"persisted":"failure"}""",
                createdAtMillis = 84L,
                reviewContext = reviewContext,
            ),
            fallbackReviewContext = null,
            onReplay = { it },
            runExtraction = {
                extractionRuns++
                error("Extraction must not run after a durable terminal failure")
            },
        )

        assertEquals(0, extractionRuns)
        assertFalse(replay.successful)
        assertEquals(
            reviewContext,
            decodeBagReviewContext(
                replay.outputData.getString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON),
            ),
        )
    }

    @Test
    fun `concurrent workers use distinct foreground notification ids`() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

        assertNotEquals(
            BagExtractionWorker.foregroundNotificationId(first),
            BagExtractionWorker.foregroundNotificationId(second),
        )
    }
}
