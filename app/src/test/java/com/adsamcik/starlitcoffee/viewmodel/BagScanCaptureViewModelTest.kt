package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BagScanCaptureViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)
    private lateinit var viewModel: BagScanCaptureViewModel

    // Slightly above the VM's internal EXTRACTION_DEBOUNCE_MS (900ms).
    private val pastDebounce = 1000L

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        viewModel = BagScanCaptureViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Phase + photo state ---

    @Test
    fun `initial state is capturing with no photos and front side`() {
        val state = viewModel.uiState.value
        assertEquals(BagScanPhase.CAPTURING, state.phase)
        assertTrue(state.photos.isEmpty())
        assertEquals(BagCaptureSide.FRONT, state.nextSide)
        assertEquals(false, state.hasPhotos)
    }

    @Test
    fun `next side becomes back after first photo`() {
        viewModel.addPhoto("uri-a")
        val state = viewModel.uiState.value
        assertEquals(1, state.photos.size)
        assertEquals(BagCaptureSide.BACK, state.nextSide)
        assertEquals("uri-a", state.photosCsv())
    }

    @Test
    fun `removePhoto drops the matching photo`() {
        viewModel.addPhoto("uri-a")
        viewModel.addPhoto("uri-b")
        viewModel.removePhoto("uri-a")
        val state = viewModel.uiState.value
        assertEquals(listOf("uri-b"), state.photos.map { it.uri })
    }

    @Test
    fun `finishCapturing switches to reviewing phase`() {
        viewModel.addPhoto("uri-a")
        viewModel.finishCapturing()
        assertEquals(BagScanPhase.REVIEWING, viewModel.uiState.value.phase)
    }

    @Test
    fun `backToCapture returns to capturing phase`() {
        viewModel.finishCapturing()
        viewModel.backToCapture()
        assertEquals(BagScanPhase.CAPTURING, viewModel.uiState.value.phase)
    }

    @Test
    fun `reset clears photos and phase`() {
        viewModel.addPhoto("uri-a")
        viewModel.finishCapturing()
        viewModel.reset()
        val state = viewModel.uiState.value
        assertEquals(BagScanPhase.CAPTURING, state.phase)
        assertTrue(state.photos.isEmpty())
    }

    // --- Debounced extraction requests ---

    @Test
    fun `single photo emits one debounced extraction request`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a")
        advanceTimeBy(pastDebounce)
        advanceUntilIdle()

        assertEquals(listOf("uri-a"), received)
    }

    @Test
    fun `rapid captures coalesce into a single request with all photos`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a")
        advanceTimeBy(100) // shorter than the debounce window
        viewModel.addPhoto("uri-b")
        advanceTimeBy(pastDebounce)
        advanceUntilIdle()

        assertEquals(listOf("uri-a,uri-b"), received)
    }

    @Test
    fun `finishCapturing flushes a pending request immediately`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a") // debounce still pending
        viewModel.finishCapturing()
        advanceUntilIdle()

        assertEquals(listOf("uri-a"), received)
    }

    @Test
    fun `finishCapturing does not re-request an already-processed photo set`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a")
        advanceTimeBy(pastDebounce) // debounced request "uri-a" fires
        viewModel.finishCapturing() // same CSV — no duplicate
        advanceUntilIdle()

        assertEquals(listOf("uri-a"), received)
    }

    @Test
    fun `skipping photos emits no extraction request`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.finishCapturing() // no photos captured
        advanceUntilIdle()

        assertEquals(BagScanPhase.REVIEWING, viewModel.uiState.value.phase)
        assertTrue(received.isEmpty())
    }
}
