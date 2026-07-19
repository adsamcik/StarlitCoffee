package com.adsamcik.starlitcoffee.data.work

import androidx.work.WorkInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BagExtractionSchedulerTest {

    @Test
    fun `manifest present with missing WorkInfo is rescheduled`() {
        assertEquals(
            PersistedWorkReconciliationAction.RESCHEDULE,
            persistedWorkReconciliationAction(
                workInfoPresent = false,
                manifestState = ManifestReconciliationState.PRESENT,
            ),
        )
    }

    @Test
    fun `missing manifest and WorkInfo surfaces a terminal recoverable failure`() {
        assertEquals(
            PersistedWorkReconciliationAction.SURFACE_FAILURE,
            persistedWorkReconciliationAction(
                workInfoPresent = false,
                manifestState = ManifestReconciliationState.MISSING,
            ),
        )
    }

    @Test
    fun `durable WorkInfo before active promotion is kept for startup recovery`() {
        assertEquals(
            PersistedWorkReconciliationAction.KEEP,
            persistedWorkReconciliationAction(
                workInfoPresent = true,
                manifestState = ManifestReconciliationState.PRESENT,
            ),
        )
    }

    @Test
    fun `durable result before WorkManager terminal state retains manifest for re-entry`() {
        assertEquals(
            TerminalWorkReconciliationAction.RETAIN,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.RUNNING,
                durableResultPresent = true,
            ),
        )
        assertEquals(
            TerminalWorkReconciliationAction.RETAIN,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.ENQUEUED,
                durableResultPresent = false,
            ),
        )
    }

    @Test
    fun `successful and failed terminal WorkInfo clean only after durable result handling`() {
        assertEquals(
            TerminalWorkReconciliationAction.HANDLE_RESULT_AND_CLEAN,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.SUCCEEDED,
                durableResultPresent = true,
            ),
        )
        assertEquals(
            TerminalWorkReconciliationAction.HANDLE_RESULT_AND_CLEAN,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.FAILED,
                durableResultPresent = true,
            ),
        )

        val calls = mutableListOf<String>()
        assertTrue(
            performTerminalResultReconciliation(
                handleDurableResult = { calls += "result" },
                deleteManifest = {
                    calls += "manifest"
                    true
                },
                clearWorkState = { calls += "state" },
            ),
        )
        assertEquals(listOf("result", "manifest", "state"), calls)
    }

    @Test
    fun `terminal cleanup remains retryable when manifest deletion fails`() {
        val calls = mutableListOf<String>()

        assertFalse(
            performTerminalResultReconciliation(
                handleDurableResult = { calls += "result" },
                deleteManifest = {
                    calls += "manifest"
                    false
                },
                clearWorkState = { calls += "state" },
            ),
        )

        assertEquals(listOf("result", "manifest"), calls)
    }

    @Test
    fun `terminal cleanup never deletes manifest when durable result handling fails`() {
        val calls = mutableListOf<String>()

        val failure = runCatching {
            performTerminalResultReconciliation(
                handleDurableResult = {
                    calls += "result"
                    error("durable delivery state unavailable")
                },
                deleteManifest = {
                    calls += "manifest"
                    true
                },
                clearWorkState = { calls += "state" },
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(listOf("result"), calls)
    }

    @Test
    fun `cancelled WorkInfo discards manifest while OS stop and retry retain it`() {
        assertEquals(
            TerminalWorkReconciliationAction.DISCARD_CANCELLED,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.CANCELLED,
                durableResultPresent = false,
            ),
        )
        assertEquals(
            TerminalWorkReconciliationAction.RETAIN,
            terminalWorkReconciliationAction(
                workState = WorkInfo.State.BLOCKED,
                durableResultPresent = false,
            ),
        )
    }

    @Test
    fun `enqueue failure rolls back pending state before activation`() = runTest {
        var pendingPersisted = false
        var manifestWritten = false
        var activated = false
        var rolledBack = false

        try {
            performFailureAtomicEnqueue(
                persistPending = { pendingPersisted = true },
                writeManifest = { manifestWritten = true },
                enqueueDurably = { error("WorkManager database unavailable") },
                activate = { activated = true },
                rollbackFailedEnqueue = { rolledBack = true },
            )
            fail("Expected enqueue failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertTrue(pendingPersisted)
        assertTrue(manifestWritten)
        assertFalse(activated)
        assertTrue(rolledBack)
    }

    @Test
    fun `pending metadata is durable before manifest and enqueue`() = runTest {
        val calls = mutableListOf<String>()

        performFailureAtomicEnqueue(
            persistPending = { calls += "pending" },
            writeManifest = { calls += "manifest" },
            enqueueDurably = { calls += "enqueue" },
            activate = { calls += "active" },
            rollbackFailedEnqueue = { calls += "rollback" },
        )

        assertEquals(listOf("pending", "manifest", "enqueue", "active"), calls)
    }

    @Test
    fun `pending metadata failure rolls back without writing a manifest`() = runTest {
        val calls = mutableListOf<String>()

        runCatching {
            performFailureAtomicEnqueue(
                persistPending = {
                    calls += "pending"
                    error("preferences commit failed")
                },
                writeManifest = { calls += "manifest" },
                enqueueDurably = { calls += "enqueue" },
                activate = { calls += "active" },
                rollbackFailedEnqueue = { calls += "rollback" },
            )
        }

        assertEquals(listOf("pending", "rollback"), calls)
    }

    @Test
    fun `manifest write failure rolls back discoverable pending metadata`() = runTest {
        val calls = mutableListOf<String>()

        runCatching {
            performFailureAtomicEnqueue(
                persistPending = { calls += "pending" },
                writeManifest = {
                    calls += "manifest"
                    error("manifest write failed")
                },
                enqueueDurably = { calls += "enqueue" },
                activate = { calls += "active" },
                rollbackFailedEnqueue = { calls += "rollback" },
            )
        }

        assertEquals(listOf("pending", "manifest", "rollback"), calls)
    }

    @Test
    fun `cancelled generation cannot activate after durable enqueue finishes`() {
        assertFalse(
            shouldActivateDurablyEnqueuedWork(
                pending = true,
                manifestOwned = true,
                generationIsLatest = false,
            ),
        )
    }

    @Test
    fun `queued reviews are never selected for result ttl expiry`() {
        val queued = "queued"

        assertEquals(
            setOf("orphan-result", "orphan-manifest"),
            expirableWorkIds(
                expiredResultWorkIds = setOf(queued, "orphan-result"),
                expiredManifestWorkIds = setOf(queued, "orphan-manifest"),
                protectedWorkIds = setOf(queued),
            ),
        )
    }

    @Test
    fun `expired work cleanup clears every durable owner even after one cleanup error`() {
        val calls = mutableListOf<String>()
        var failures = 0

        performExpiredWorkCleanup(
            actions = ExpiredWorkCleanupActions(
                deleteManifest = { calls += "manifest" },
                deleteResult = {
                    calls += "result"
                    error("simulated result deletion failure")
                },
                clearMetadata = { calls += "metadata" },
                removeReviewIds = { calls += "reviews" },
                cancelNotification = { calls += "notification" },
            ),
            onFailure = { failures++ },
        )

        assertEquals(
            listOf("manifest", "result", "metadata", "reviews", "notification"),
            calls,
        )
        assertEquals(1, failures)
    }
}
