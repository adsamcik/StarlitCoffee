package com.adsamcik.starlitcoffee.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TimerNotificationState(
    val phaseName: String = "",
    val elapsedSeconds: Int = 0,
    val instruction: String = "",
    val isRunning: Boolean = false,
)

object TimerStateHolder {

    private val _state = MutableStateFlow(TimerNotificationState())
    val state: StateFlow<TimerNotificationState> = _state

    fun update(phaseName: String, elapsedSeconds: Int, instruction: String, isRunning: Boolean) {
        _state.value = TimerNotificationState(phaseName, elapsedSeconds, instruction, isRunning)
    }

    fun reset() {
        _state.value = TimerNotificationState()
    }
}
