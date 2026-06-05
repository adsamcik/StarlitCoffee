package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the brew timer and bloom-countdown coroutines extracted from
 * [BrewViewModel].
 *
 * [BrewViewModel] remains the single owner of [BrewUiState]; this controller
 * reads the current state via [state] and applies timer/bloom field changes via
 * [update] (which the ViewModel backs with `_uiState.update`). Wall-clock
 * elapsed time is anchored through the injectable [nowMs] clock so it survives
 * the coroutine being paused (Doze) and is deterministic under test.
 */
internal class BrewTimerController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val state: () -> BrewUiState,
    private val update: (transform: (BrewUiState) -> BrewUiState) -> Unit,
) {
    private var timerJob: Job? = null
    private var bloomCountdownJob: Job? = null
    private var timerStartMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L

    fun start() {
        if (state().method.timingMode != BrewTimingMode.ACTIVE_TIMER) return
        if (timerJob?.isActive == true) return
        update { it.copy(timerRunning = true) }
        timerStartMs = nowMs()
        launchTimerLoop()
        resumeBloomCountdownIfNeeded()
    }

    private fun resumeBloomCountdownIfNeeded() {
        val current = state()
        if (current.bloomMarkedAtSeconds == null) return
        if (current.bloomFinished) return
        val remaining = current.bloomCountdownSeconds ?: return
        if (remaining <= 0) return

        bloomCountdownJob?.cancel()
        bloomCountdownJob = scope.launch {
            for (tick in remaining - 1 downTo 0) {
                delay(1000L)
                update { it.copy(bloomCountdownSeconds = tick) }
            }
            update { it.copy(bloomFinished = true, bloomCountdownSeconds = 0) }
        }
    }

    private fun launchTimerLoop() {
        timerJob = scope.launch {
            while (state().timerRunning) {
                delay(250L)
                val currentMs = nowMs()
                val totalElapsedMs = pausedAccumulatedMs + (currentMs - timerStartMs)
                val totalElapsedSeconds = (totalElapsedMs / 1000).toInt()
                update { it.copy(elapsedSeconds = totalElapsedSeconds) }
            }
        }
    }

    /**
     * Ensures the timer coroutine is running. Call on app resume to recover
     * from Doze or battery optimization pausing the coroutine.
     * Does NOT reset the clock — wall-clock anchoring handles the gap.
     */
    fun ensureRunning() {
        if (!state().timerRunning) return
        if (timerJob?.isActive == true) return
        launchTimerLoop()
    }

    fun pause() {
        val currentMs = nowMs()
        pausedAccumulatedMs += (currentMs - timerStartMs)
        update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerJob = null
        bloomCountdownJob?.cancel()
    }

    fun stop() {
        pausedAccumulatedMs = 0L
        bloomCountdownJob?.cancel()
        bloomCountdownJob = null
        update {
            it.copy(
                timerRunning = false,
                elapsedSeconds = 0,
                bloomMarkedAtSeconds = null,
                bloomCountdownSeconds = null,
                bloomFinished = false,
                bloomSpritesheetId = null,
            )
        }
        timerJob?.cancel()
        timerJob = null
    }

    fun markBloom() {
        val current = state()
        if (current.bloomMarkedAtSeconds != null) return
        if (!current.timerRunning) return

        val duration = current.effectiveBloomDurationSeconds
        update {
            it.copy(
                bloomMarkedAtSeconds = it.elapsedSeconds,
                bloomCountdownSeconds = duration,
                bloomFinished = false,
            )
        }

        bloomCountdownJob?.cancel()
        bloomCountdownJob = scope.launch {
            for (remaining in duration - 1 downTo 0) {
                delay(1000L)
                update { it.copy(bloomCountdownSeconds = remaining) }
            }
            update { it.copy(bloomFinished = true, bloomCountdownSeconds = 0) }
        }
    }
}
