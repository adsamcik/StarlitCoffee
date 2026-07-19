package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrewLogOperationViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list deletion waits for persistence and ignores double submit`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = BrewLogListViewModel {
            calls++
            gate.await()
            true
        }

        viewModel.delete(TEST_LOG)
        viewModel.delete(TEST_LOG.copy(id = 2L))

        assertEquals(1, calls)
        assertEquals(TEST_LOG.id, viewModel.uiState.value.deletingLogId)
        assertNull(viewModel.uiState.value.deletedLogId)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletingLogId)
        assertEquals(TEST_LOG.id, viewModel.uiState.value.deletedLogId)
    }

    @Test
    fun `list deletion failure remains retryable`() = runTest(dispatcher) {
        var shouldFail = true
        val viewModel = BrewLogListViewModel {
            if (shouldFail) error("disk failure")
            true
        }

        viewModel.delete(TEST_LOG)
        advanceUntilIdle()
        assertEquals(TEST_LOG.id, viewModel.uiState.value.failedLogId)

        shouldFail = false
        viewModel.delete(TEST_LOG)
        advanceUntilIdle()
        assertEquals(TEST_LOG.id, viewModel.uiState.value.deletedLogId)
    }

    @Test
    fun `selection waits for delayed feedback save and snapshots submitted values`() =
        runTest(dispatcher) {
            var gate = CompletableDeferred<Unit>()
            val persisted = mutableListOf<BrewLogFeedbackSubmission>()
            val viewModel = BrewLogFeedbackViewModel { submission ->
                gate.await()
                persisted += submission
                true
            }
            val first = submission(notes = "first")
            val later = submission(notes = "edited while saving")

            viewModel.save(first, BrewLogFeedbackSaveTarget.Stay)
            viewModel.save(later, BrewLogFeedbackSaveTarget.SelectLog(2L))
            assertTrue(viewModel.uiState.value.isSaving)
            assertNull(viewModel.uiState.value.completion)

            gate.complete(Unit)
            advanceUntilIdle()

            val completion = requireNotNull(viewModel.uiState.value.completion)
            assertEquals(first, completion.submission)
            assertEquals(BrewLogFeedbackSaveTarget.SelectLog(2L), completion.target)
            assertTrue(requiresFollowUpFeedbackSave(completion.submission, later))
            assertEquals(listOf(first), persisted)

            viewModel.consumeCompletion()
            gate = CompletableDeferred()
            viewModel.save(later, BrewLogFeedbackSaveTarget.SelectLog(2L))
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(first, later), persisted)
            assertFalse(
                requiresFollowUpFeedbackSave(
                    requireNotNull(viewModel.uiState.value.completion).submission,
                    later,
                ),
            )
        }

    @Test
    fun `failed selection save reports its target and remains retryable`() = runTest(dispatcher) {
        var shouldFail = true
        val viewModel = BrewLogFeedbackViewModel {
            if (shouldFail) error("write failure")
            true
        }
        val target = BrewLogFeedbackSaveTarget.SelectLog(2L)

        viewModel.save(submission(notes = "retry me"), target)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(target, viewModel.uiState.value.failure)

        shouldFail = false
        viewModel.consumeFailure()
        viewModel.save(submission(notes = "retry me"), target)
        advanceUntilIdle()
        assertEquals(target, requireNotNull(viewModel.uiState.value.completion).target)
    }

    private fun submission(notes: String) = BrewLogFeedbackSubmission(
        logId = TEST_LOG.id,
        rating = 3f,
        notes = notes,
        tasteFeedback = null,
        descriptors = setOf("Smooth"),
    )

    private companion object {
        val TEST_LOG = BrewLogEntity(
            id = 1L,
            method = "PULSAR",
            doseG = 20f,
            waterG = 340f,
            ratio = 17f,
        )
    }
}
