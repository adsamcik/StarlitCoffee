package com.adsamcik.starlitcoffee.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.adsamcik.starlitcoffee.data.work.BagReviewContext

data class BagAnalysisDeepLink(
    val workId: String,
    val reviewContext: BagReviewContext? = null,
)

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
    // host pops it, navigates to the bag inventory, recovers that exact
    // WorkManager result, and clears the id so a recompose doesn't re-trigger.
    private val _pendingBagAnalysis = MutableStateFlow<BagAnalysisDeepLink?>(null)
    val pendingBagAnalysis: StateFlow<BagAnalysisDeepLink?> = _pendingBagAnalysis.asStateFlow()

    fun postBrewLogDetail(brewLogId: Long) {
        if (brewLogId <= 0L) return
        _pendingBrewLogId.value = brewLogId
    }

    fun consumeBrewLogDetail() {
        _pendingBrewLogId.value = null
    }

    fun postBagAnalysisReady(
        workId: String,
        reviewContext: BagReviewContext? = null,
    ) {
        if (workId.isBlank()) return
        _pendingBagAnalysis.value = BagAnalysisDeepLink(workId, reviewContext)
    }

    fun consumeBagAnalysisReady() {
        _pendingBagAnalysis.value = null
    }
}
