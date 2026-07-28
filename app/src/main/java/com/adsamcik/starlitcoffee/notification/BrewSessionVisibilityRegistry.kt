package com.adsamcik.starlitcoffee.notification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks the durable brew sessions currently rendered by the app.
 *
 * Stage effects are still persisted and delivered by the coordinator. This
 * registry only lets the notification adapter avoid duplicating an alert that
 * the user can already see on the matching live-brew screen; being elsewhere
 * in the foreground app must not suppress an alert.
 */
object BrewSessionVisibilityRegistry {
    private val visibleCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** Creates a handle that is visible only while its owner is resumed. */
    fun foregroundHandle(sessionId: String): BrewSessionForegroundVisibilityHandle =
        BrewSessionForegroundVisibilityHandle(sessionId)

    fun register(sessionId: String) {
        if (sessionId.isBlank()) return
        visibleCounts.compute(sessionId) { _, count ->
            (count ?: AtomicInteger()).also { it.incrementAndGet() }
        }
    }

    fun unregister(sessionId: String) {
        if (sessionId.isBlank()) return
        visibleCounts.computeIfPresent(sessionId) { _, count ->
            if (count.decrementAndGet() <= 0) null else count
        }
    }

    fun isVisible(sessionId: String): Boolean = visibleCounts[sessionId]?.get()?.let { it > 0 } == true
}

/**
 * Couples session alert suppression to foreground visibility rather than a
 * retained Compose composition. A paused activity may still keep its
 * composition in memory, but must not make a stage alert disappear.
 */
class BrewSessionForegroundVisibilityHandle internal constructor(
    private val sessionId: String,
) {
    private var registered = false

    fun onResumed() {
        if (!registered) {
            BrewSessionVisibilityRegistry.register(sessionId)
            registered = true
        }
    }

    fun onPaused() {
        if (registered) {
            BrewSessionVisibilityRegistry.unregister(sessionId)
            registered = false
        }
    }

    fun dispose() = onPaused()
}
