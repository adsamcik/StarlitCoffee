package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.PhaseMode
import com.adsamcik.starlitcoffee.service.TimerStateHolder
import com.adsamcik.starlitcoffee.viewmodel.BrewPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerState(
    val timerRunning: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentPhaseIndex: Int = 0,
    val phaseSecondsRemaining: Int = 0,
    val phaseOvertime: Boolean = false,
    val showNextPreview: Boolean = false,
    val lastDriftSeconds: Int = 0,
)

class TimerController(
    private val scope: CoroutineScope,
    private val timerStateHolder: TimerStateHolder,
) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var timerStartMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L
    private var phaseStartedAccumulatedMs: Long = 0L
    private var lastAutoAdvancedPhaseIndex = -1

    /** Current phases fed from the ViewModel. */
    var phases: List<BrewPhase> = emptyList()
        private set
    var onAutoAdvance: (() -> Unit)? = null

    fun setPhases(phases: List<BrewPhase>) {
        this.phases = phases
    }

    fun start() {
        if (timerJob?.isActive == true) return
        _state.update { it.copy(timerRunning = true) }
        timerStartMs = System.nanoTime() / 1_000_000L
        launchTimerLoop()
    }

    /**
     * Ensures the timer coroutine is running. Call on app resume to recover
     * from Doze or battery optimization pausing the coroutine.
     * Does NOT reset the clock — wall-clock anchoring handles the gap.
     */
    fun ensureRunning() {
        if (!_state.value.timerRunning) return
        if (timerJob?.isActive == true) return
        launchTimerLoop()
    }

    fun pause() {
        val nowMs = System.nanoTime() / 1_000_000L
        pausedAccumulatedMs += (nowMs - timerStartMs)
        _state.update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerJob = null

        val s = _state.value
        val phase = phases.getOrNull(s.currentPhaseIndex)
        timerStateHolder.update(
            phaseName = phase?.name ?: "",
            elapsedSeconds = s.elapsedSeconds,
            instruction = phase?.instruction ?: "",
            isRunning = false,
        )
    }

    fun stop() {
        pausedAccumulatedMs = 0L
        phaseStartedAccumulatedMs = 0L
        lastAutoAdvancedPhaseIndex = -1
        _state.update {
            TimerState() // full reset
        }
        timerJob?.cancel()
        timerJob = null
        timerStateHolder.reset()
    }

    fun advancePhase(rebalance: (List<BrewPhase>, Int, Int) -> List<BrewPhase>) {
        val nowMs = System.nanoTime() / 1_000_000L
        val totalElapsedMs = if (_state.value.timerRunning) {
            pausedAccumulatedMs + (nowMs - timerStartMs)
        } else {
            pausedAccumulatedMs
        }

        _state.update { s ->
            val nextIndex = (s.currentPhaseIndex + 1)
                .coerceAtMost(phases.lastIndex.coerceAtLeast(0))

            val drift = s.phaseSecondsRemaining
            val updatedPhases = if (drift != 0) {
                rebalance(phases, nextIndex, drift)
            } else {
                phases
            }
            this.phases = updatedPhases

            s.copy(
                currentPhaseIndex = nextIndex,
                phaseOvertime = false,
                lastDriftSeconds = drift,
            )
        }
        phaseStartedAccumulatedMs = totalElapsedMs
    }

    /**
     * Full reset used by [BrewViewModel.resetBrew]. Clears timer state and
     * accumulated time without touching UI state beyond timer fields.
     */
    fun resetForBrew() {
        timerJob?.cancel()
        timerJob = null
        pausedAccumulatedMs = 0L
        phaseStartedAccumulatedMs = 0L
        lastAutoAdvancedPhaseIndex = -1
        _state.value = TimerState()
        timerStateHolder.reset()
    }

    // ---- internal------------------------------------------------------------

    private fun launchTimerLoop() {
        timerJob = scope.launch {
            while (_state.value.timerRunning) {
                delay(250L)
                val nowMs = System.nanoTime() / 1_000_000L
                val totalElapsedMs = pausedAccumulatedMs + (nowMs - timerStartMs)
                val totalElapsedSeconds = (totalElapsedMs / 1000).toInt()
                val phaseElapsedSeconds =
                    ((totalElapsedMs - phaseStartedAccumulatedMs) / 1000).toInt()

                _state.update { s ->
                    val phase = phases.getOrNull(s.currentPhaseIndex)
                    val phaseDuration = phase?.durationSeconds ?: 0
                    val remaining = phaseDuration - phaseElapsedSeconds
                    val overtime = remaining < 0
                    s.copy(
                        elapsedSeconds = totalElapsedSeconds,
                        phaseSecondsRemaining = remaining,
                        phaseOvertime = overtime,
                        showNextPreview = remaining in 1..10 &&
                            s.currentPhaseIndex < phases.lastIndex,
                        )
                }

                // Auto-advance only AUTO_TIMED phases (passive waits like Saturate, Steep).
                // TIMED phases (pours) and EVENT_GATED phases require user action.
                val autoState = _state.value
                val autoPhase = phases.getOrNull(autoState.currentPhaseIndex)
                if (autoPhase?.mode == PhaseMode.AUTO_TIMED &&
                    autoState.phaseSecondsRemaining <= 0 &&
                    autoState.currentPhaseIndex < phases.lastIndex &&
                    autoState.currentPhaseIndex != lastAutoAdvancedPhaseIndex
                ) {
                    lastAutoAdvancedPhaseIndex = autoState.currentPhaseIndex
                    onAutoAdvance?.invoke()
                }

                val s = _state.value
                val phase = phases.getOrNull(s.currentPhaseIndex)
                timerStateHolder.update(
                    phaseName = phase?.name ?: "",
                    elapsedSeconds = totalElapsedSeconds,
                    instruction = phase?.instruction ?: "",
                    isRunning = true,
                )
            }
        }
    }
}
