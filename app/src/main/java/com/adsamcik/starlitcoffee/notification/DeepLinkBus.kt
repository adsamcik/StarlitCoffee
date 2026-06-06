package com.adsamcik.starlitcoffee.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-app-process bus for notification deep links. Notifications and the
 * activity that handles them are decoupled; the activity pushes a target
 * brew-log id into [pendingBrewLogId] and the Compose nav host pops it,
 * navigates, and clears it. Using a single tightly-scoped flow avoids
 * dragging a service locator across the navigation layer.
 *
 * The bus is intentionally process-scoped: deep-link state has no value once
 * the app process dies, so a SavedStateHandle round-trip would just add
 * complexity for no benefit.
 */
object DeepLinkBus {
    private val _pendingBrewLogId = MutableStateFlow<Long?>(null)
    val pendingBrewLogId: StateFlow<Long?> = _pendingBrewLogId.asStateFlow()

    // Set when the user taps a "bag analysis complete" notification. The nav
    // host pops it, navigates to the bag inventory, promotes the background
    // result the BrewViewModel is holding into the foreground form, and clears
    // this flag so a recompose doesn't re-trigger navigation. No payload is
    // needed: the analyzed result lives in the (process-scoped) BrewViewModel.
    private val _pendingBagAnalysis = MutableStateFlow(false)
    val pendingBagAnalysis: StateFlow<Boolean> = _pendingBagAnalysis.asStateFlow()

    fun postBrewLogDetail(brewLogId: Long) {
        if (brewLogId <= 0L) return
        _pendingBrewLogId.value = brewLogId
    }

    fun consumeBrewLogDetail() {
        _pendingBrewLogId.value = null
    }

    fun postBagAnalysisReady() {
        _pendingBagAnalysis.value = true
    }

    fun consumeBagAnalysisReady() {
        _pendingBagAnalysis.value = false
    }
}
