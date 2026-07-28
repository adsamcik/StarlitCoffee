package com.adsamcik.starlitcoffee.data.work

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Owns the one bag-extraction reconciliation pass performed when this process starts.
 *
 * Application startup begins the lazy deferred, while UI consumers await the same result before
 * restoring active work or deleting staged photos. Awaiting is cancellation-safe for callers:
 * cancelling one consumer does not cancel the application-scoped recovery.
 */
internal class BagExtractionStartupRecovery(
    scope: CoroutineScope,
    recover: suspend () -> Set<String>,
) {
    private val recoveredState: Deferred<Set<String>> = scope.async(start = CoroutineStart.LAZY) {
        recover()
    }

    suspend fun await(): Set<String> = recoveredState.await()
}
