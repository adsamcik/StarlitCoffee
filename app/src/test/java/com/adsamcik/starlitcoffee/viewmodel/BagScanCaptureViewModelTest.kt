package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.SavedStateHandle
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
        viewModel = BagScanCaptureViewModel(SavedStateHandle())
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
        assertTrue(state.sessionId.isNotBlank())
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
    fun `removing the final photo emits an empty generation`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a")
        advanceTimeBy(pastDebounce)
        viewModel.removePhoto("uri-a")
        advanceTimeBy(pastDebounce)
        advanceUntilIdle()

        assertEquals(listOf("uri-a", ""), received)
    }

    @Test
    fun `finishing immediately after final removal preserves empty generation`() = runTest(mainDispatcher) {
        val received = mutableListOf<String>()
        backgroundScope.launch { viewModel.extractionRequests.collect { received += it } }

        viewModel.addPhoto("uri-a")
        advanceTimeBy(pastDebounce)
        viewModel.removePhoto("uri-a")
        viewModel.finishCapturing()
        advanceUntilIdle()

        assertEquals(listOf("uri-a", ""), received)
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
        val originalSessionId = viewModel.uiState.value.sessionId
        viewModel.addPhoto("uri-a")
        viewModel.finishCapturing()
        viewModel.reset()
        val state = viewModel.uiState.value
        assertEquals(BagScanPhase.CAPTURING, state.phase)
        assertTrue(state.photos.isEmpty())
        assertTrue(state.sessionId != originalSessionId)
    }

    @Test
    fun `saved state restores session phase and photos`() {
        val handle = SavedStateHandle()
        val original = BagScanCaptureViewModel(handle)
        original.addPhoto("uri-a")
        original.finishCapturing()

        val restored = BagScanCaptureViewModel(handle).uiState.value

        assertEquals(original.uiState.value.sessionId, restored.sessionId)
        assertEquals(BagScanPhase.REVIEWING, restored.phase)
        assertEquals(listOf("uri-a"), restored.photos.map { it.uri })
    }

    @Test
    fun `background review restores session photos and reviewing phase`() {
        viewModel.resumeReview(
            sessionId = "rescan-session",
            photoUrisCsv = "file:///front.jpg,file:///back.jpg",
        )

        val state = viewModel.uiState.value
        assertEquals("rescan-session", state.sessionId)
        assertEquals(BagScanPhase.REVIEWING, state.phase)
        assertEquals(
            listOf("file:///front.jpg", "file:///back.jpg"),
            state.photos.map { it.uri },
        )
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
