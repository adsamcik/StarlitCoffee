package com.adsamcik.starlitcoffee.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionCoordinator
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.viewmodel.ColdBrewStartFactory
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The root owns this submission across routes; the coordinator alone owns clocks and persistence. */
internal class CalculatorBrewStarter(
    private val brewViewModel: BrewViewModel,
    private val calculatorViewModel: CalculatorViewModel,
    private val coordinator: BrewSessionCoordinator,
    private val scope: CoroutineScope,
    private val onOpenSession: (String) -> Unit,
    private val onUnavailable: suspend () -> Unit,
) {
    var isStarting by mutableStateOf(false)
        private set
    var pendingColdRequest by mutableStateOf<BrewSessionStartRequest?>(null)
        private set

    fun start() {
        if (isStarting || pendingColdRequest != null) return
        val calculator = calculatorViewModel.uiState.value
        val request = CalculatorBrewSessionStartFactory().create(
            calculator, brewViewModel.uiState.value, brewViewModel.selectedBagId.value,
        )
        if (request != null && calculator.brewMethod == BrewMethod.COLD_BREW) {
            pendingColdRequest = request
            return
        }
        submit(request, startImmediately = !calculator.brewMethod.hasBloom)
    }

    fun dismissColdStart() { pendingColdRequest = null }

    fun startCold(timerOnly: Boolean, durationMillis: Long, originKnown: Boolean, originMillis: Long?) {
        val request = pendingColdRequest ?: return
        pendingColdRequest = null
        submit(ColdBrewStartFactory.create(request, timerOnly, durationMillis, originKnown, originMillis),
            startImmediately = true)
    }

    private fun submit(request: BrewSessionStartRequest?, startImmediately: Boolean) {
        isStarting = true
        scope.launch {
            try {
                if (request == null) {
                    onUnavailable()
                } else {
                    val result = coordinator.createOrResume(request, startImmediately = startImmediately)
                    when (result) {
                        is BrewSessionOperationResult.Active, is BrewSessionOperationResult.PendingEffect ->
                            onOpenSession(request.sessionId.value)
                        else -> onUnavailable()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onUnavailable()
            } finally {
                isStarting = false
            }
        }
    }
}
