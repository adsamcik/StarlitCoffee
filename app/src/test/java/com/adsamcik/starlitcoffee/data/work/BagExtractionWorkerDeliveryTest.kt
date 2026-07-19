package com.adsamcik.starlitcoffee.data.work

import androidx.work.workDataOf
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagExtractionWorkerDeliveryTest {

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
