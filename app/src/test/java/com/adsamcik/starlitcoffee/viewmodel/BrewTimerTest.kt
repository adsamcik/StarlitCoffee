package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization tests for the BrewViewModel timer/bloom engine.
 *
 * These pin down the CURRENT behavior (elapsed progression, pause/resume
 * accumulation, bloom countdown, reset, and no-op guards) before the timer is
 * extracted into a dedicated controller. They rely on the injectable [nowMs]
 * clock seam backed by the coroutine test scheduler so wall-clock elapsed time
 * advances deterministically with virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewTimerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTimerViewModel(): BrewViewModel =
        BrewViewModel(nowMs = { dispatcher.scheduler.currentTime })

    @Test
    fun `timer advances elapsed seconds with the virtual clock`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.startTimer()
        assertTrue(vm.uiState.value.timerRunning)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(3, vm.uiState.value.elapsedSeconds)

        vm.stopTimer()
        advanceUntilIdle()
    }

    @Test
    fun `pause accumulates elapsed and resume continues from the offset`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.startTimer()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, vm.uiState.value.elapsedSeconds)

        vm.pauseTimer()
        assertFalse(vm.uiState.value.timerRunning)

        // Time passes while paused — elapsed must not change.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, vm.uiState.value.elapsedSeconds)

        // Resume: elapsed continues from the accumulated 2s.
        vm.startTimer()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, vm.uiState.value.elapsedSeconds)

        vm.stopTimer()
        advanceUntilIdle()
    }

    @Test
    fun `ensureTimerRunning does not double-count while already running`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.startTimer()
        advanceTimeBy(1_000)
        runCurrent()
        // Idempotent: a second observer must not be launched.
        vm.ensureTimerRunning()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(2, vm.uiState.value.elapsedSeconds)

        vm.stopTimer()
        advanceUntilIdle()
    }

    @Test
    fun `stopTimer resets all timer and bloom state`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.startTimer()
        advanceTimeBy(2_000)
        runCurrent()
        vm.markBloom()
        runCurrent()

        vm.stopTimer()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.timerRunning)
        assertEquals(0, state.elapsedSeconds)
        assertNull(state.bloomMarkedAtSeconds)
        assertNull(state.bloomCountdownSeconds)
        assertFalse(state.bloomFinished)
        assertNull(state.bloomSpritesheetId)
    }

    @Test
    fun `markBloom starts a countdown that finishes`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.startTimer()
        val duration = vm.uiState.value.effectiveBloomDurationSeconds
        assertTrue("expected a positive bloom duration for PULSAR", duration > 0)

        vm.markBloom()
        runCurrent()
        assertEquals(duration, vm.uiState.value.bloomCountdownSeconds)
        assertFalse(vm.uiState.value.bloomFinished)

        advanceTimeBy(duration * 1_000L + 1_000L)
        runCurrent()

        assertTrue(vm.uiState.value.bloomFinished)
        assertEquals(0, vm.uiState.value.bloomCountdownSeconds)

        vm.stopTimer()
        advanceUntilIdle()
    }

    @Test
    fun `markBloom is a no-op when the timer is not running`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.markBloom()
        runCurrent()

        assertNull(vm.uiState.value.bloomMarkedAtSeconds)
        assertNull(vm.uiState.value.bloomCountdownSeconds)
    }

    @Test
    fun `startTimer is a no-op for non-active-timer methods`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        vm.setMethod(BrewMethod.COLD_BREW)
        vm.startTimer()
        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(vm.uiState.value.timerRunning)
        assertEquals(0, vm.uiState.value.elapsedSeconds)
    }

    @Test
    fun `toggleMinuteAlert flips the flag`() = runTest(dispatcher) {
        val vm = createTimerViewModel()
        runCurrent()

        val initial = vm.uiState.value.minuteAlertEnabled
        vm.toggleMinuteAlert()
        assertEquals(!initial, vm.uiState.value.minuteAlertEnabled)
        vm.toggleMinuteAlert()
        assertEquals(initial, vm.uiState.value.minuteAlertEnabled)
    }
}
