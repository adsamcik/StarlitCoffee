package com.adsamcik.starlitcoffee.data.network.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LlmCallGate {
    suspend fun <T> withPermit(block: suspend () -> T): T
}

/**
 * Process-wide serialization gate for Mindlayer calls.
 *
 * The SDK owns a single local engine/session backend, so live-scan escalation,
 * manual benchmark calls, prewarm, and brew-photo enrichment must not overlap.
 */
object MindlayerLlmCallGate : LlmCallGate {
    private val mutex = Mutex()

    override suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}
