package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.data.work.BagScanDraft
import com.adsamcik.starlitcoffee.data.work.encodeToStoredJson
import com.adsamcik.starlitcoffee.ui.component.buildFieldDeltas
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedRecognitionRecoveryTest {
    @Test
    fun `restored draft with no result still shows active recognition and photo ownership`() {
        listOf(RecognitionRunState.RUNNING, RecognitionRunState.PARTIAL).forEach { runState ->
            val data = draft(runState).toScanReviewData()

            assertTrue(data.isProcessing)
            assertEquals("session-1", data.sessionId)
            assertEquals("generation-1", data.generationId)
            assertEquals("file:///front.jpg,file:///back.jpg", data.capturedPhotoUris)
            assertTrue(data.fieldEvidence.isEmpty())
        }
    }

    @Test
    fun `restored partial result preserves status and evidence provenance while running`() {
        val evidence = BagFieldEvidence(
            fieldName = "name",
            value = "Recognized coffee",
            sourceType = BagFieldSourceType.OCR,
            confidence = BagFieldConfidence.MEDIUM,
            supportingText = "Coffee label",
            previewUri = "file:///front.jpg",
        )
        val result = BagPhotoProcessingResult(
            capturedPhotoUris = "file:///front.jpg,file:///back.jpg",
            fieldEvidence = mapOf("name" to evidence),
            llmStatus = LlmEnrichmentStatus.UNAVAILABLE,
        )
        val data = draft(RecognitionRunState.PARTIAL).copy(
            reviewContext = BagReviewContext.rescan(42L),
            resultJson = result.encodeToStoredJson(),
        ).toScanReviewData()

        assertTrue(data.isProcessing)
        assertEquals("session-1", data.sessionId)
        assertEquals("generation-1", data.generationId)
        assertEquals(LlmEnrichmentStatus.UNAVAILABLE, data.llmStatus)
        assertEquals(evidence, data.fieldEvidence.getValue("name"))
        assertEquals("Recognized coffee", data.ocrPrefill?.name)
    }

    @Test
    fun `completed restored draft does not show active recognition`() {
        assertFalse(draft(RecognitionRunState.COMPLETE).toScanReviewData().isProcessing)
    }

    @Test
    fun `retry reconnects before queuing the requested unchanged session`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1")
        val calls = mutableListOf<String>()

        val queued = retryCurrentScanReview(
            requested = requested,
            currentData = { requested },
            reconnect = { calls += "reconnect" },
            retry = { sessionId ->
                calls += sessionId
                true
            },
        )

        assertTrue(queued)
        assertEquals(listOf("reconnect", "session-1"), calls)
    }

    @Test
    fun `changes during suspended reconnect prevent retrying a stale review`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1")
        val changedReviews = listOf(
            requested.copy(sessionId = "session-2"),
            requested.copy(generationId = "generation-2"),
            requested.copy(isProcessing = true),
        )
        changedReviews.forEach { changed ->
            val reconnectStarted = CompletableDeferred<Unit>()
            val releaseReconnect = CompletableDeferred<Unit>()
            var current = requested
            val retries = mutableListOf<String>()
            val recovery = async {
                retryCurrentScanReview(
                    requested = requested,
                    currentData = { current },
                    reconnect = {
                        reconnectStarted.complete(Unit)
                        releaseReconnect.await()
                    },
                    retry = { sessionId ->
                        retries += sessionId
                        true
                    },
                )
            }
            runCurrent()
            assertTrue(reconnectStarted.isCompleted)
            current = changed
            releaseReconnect.complete(Unit)

            assertFalse(recovery.await())
            assertTrue(retries.isEmpty())
        }
    }

    @Test
    fun `cancelled reconnect propagates cancellation without queuing retry`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1")
        var retryCalls = 0

        val failure = runCatching {
            retryCurrentScanReview(
                requested = requested,
                currentData = { requested },
                reconnect = { throw CancellationException("Review left during reconnect") },
                retry = {
                    retryCalls += 1
                    true
                },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(0, retryCalls)
    }

    @Test
    fun `retry reports provider refusal without claiming a new run`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1")

        assertFalse(
            retryCurrentScanReview(
                requested = requested,
                currentData = { requested },
                reconnect = { },
                retry = { false },
            ),
        )
    }

    @Test
    fun `missing session is rejected before reconnect or retry`() = runTest {
        val requested = ScanReviewData(generationId = "generation-1")
        var reconnectCalls = 0
        var retryCalls = 0

        val queued = retryCurrentScanReview(
            requested = requested,
            currentData = { requested },
            reconnect = { reconnectCalls += 1 },
            retry = {
                retryCalls += 1
                true
            },
        )

        assertFalse(queued)
        assertEquals(0, reconnectCalls)
        assertEquals(0, retryCalls)
    }

    @Test
    fun `already processing review is rejected before reconnect or retry`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1", isProcessing = true)
        var reconnectCalls = 0
        var retryCalls = 0

        val queued = retryCurrentScanReview(
            requested = requested,
            currentData = { requested },
            reconnect = { reconnectCalls += 1 },
            retry = {
                retryCalls += 1
                true
            },
        )

        assertFalse(queued)
        assertEquals(0, reconnectCalls)
        assertEquals(0, retryCalls)
    }

    @Test
    fun `initial stale generation is rejected before reconnect or retry`() = runTest {
        val requested = ScanReviewData(sessionId = "session-1", generationId = "generation-1")
        var reconnectCalls = 0
        var retryCalls = 0

        val queued = retryCurrentScanReview(
            requested = requested,
            currentData = { requested.copy(generationId = "generation-2") },
            reconnect = { reconnectCalls += 1 },
            retry = {
                retryCalls += 1
                true
            },
        )

        assertFalse(queued)
        assertEquals(0, reconnectCalls)
        assertEquals(0, retryCalls)
    }

    @Test
    fun `completed matching rescan inputs permit final no changes presentation`() {
        listOf(LlmEnrichmentStatus.SUCCEEDED, LlmEnrichmentStatus.NOT_RUN).forEach { status ->
            val data = recognizedReviewData(status)

            assertTrue(isRescanRecognitionComplete(data, isProcessing = false, RecognitionPreference.ENABLED))
            assertTrue(
                buildFieldDeltas(
                    CoffeeBagEntity(name = "Lot 1"),
                    data.fieldEvidence.mapValues { it.value.value },
                ).isEmpty(),
            )
        }
    }

    @Test
    fun `empty or invalid rescan evidence cannot claim recognition completed`() {
        val empty = ScanReviewData(llmStatus = LlmEnrichmentStatus.SUCCEEDED)
        val invalid = empty.copy(
            fieldEvidence = mapOf(
                "weight" to BagFieldEvidence(
                    fieldName = "weight",
                    value = "not a package weight",
                    sourceType = BagFieldSourceType.OCR,
                    confidence = BagFieldConfidence.LOW,
                ),
            ),
        )

        assertFalse(isRescanRecognitionComplete(empty, isProcessing = false, RecognitionPreference.ENABLED))
        assertFalse(isRescanRecognitionComplete(invalid, isProcessing = false, RecognitionPreference.ENABLED))
    }

    @Test
    fun `running retrying and failed rescans do not claim a final comparison`() {
        val recognized = recognizedReviewData(LlmEnrichmentStatus.SUCCEEDED)
        assertFalse(
            isRescanRecognitionComplete(recognized.copy(isProcessing = true), true, RecognitionPreference.ENABLED),
        )
        assertFalse(isRescanRecognitionComplete(recognized, isProcessing = true, RecognitionPreference.ENABLED))
        listOf(
            LlmEnrichmentStatus.FAILED,
            LlmEnrichmentStatus.TIMED_OUT,
            LlmEnrichmentStatus.UNAVAILABLE,
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED,
        ).forEach { status ->
            assertFalse(
                isRescanRecognitionComplete(recognized.copy(llmStatus = status), false, RecognitionPreference.ENABLED),
            )
        }
    }

    @Test
    fun `disabled recognition accepts available manual fallback but never an active run`() {
        LlmEnrichmentStatus.entries.forEach { status ->
            val data = recognizedReviewData(status)

            assertTrue(isRescanRecognitionComplete(data, isProcessing = false, RecognitionPreference.DISABLED))
            assertFalse(isRescanRecognitionComplete(data, isProcessing = true, RecognitionPreference.DISABLED))
        }
    }

    private fun recognizedReviewData(status: LlmEnrichmentStatus) = ScanReviewData(
        sessionId = "session-1",
        generationId = "generation-1",
        fieldEvidence = mapOf(
            "name" to BagFieldEvidence(
                fieldName = "name",
                value = "Lot 1",
                sourceType = BagFieldSourceType.OCR,
                confidence = BagFieldConfidence.HIGH,
            ),
        ),
        llmStatus = status,
    )

    private fun draft(runState: RecognitionRunState) = BagScanDraft(
        sessionId = "session-1",
        generationId = "generation-1",
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        photoUris = listOf("file:///front.jpg", "file:///back.jpg"),
        recognitionRunState = runState,
    )
}
