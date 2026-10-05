package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded process-wide locks serialize persistence and delivery across runtime instances. */
internal object BrewSessionOperationLocks {
    private val locks = Array(64) { Mutex() }

    suspend fun <T> withSession(sessionId: SessionId, action: suspend () -> T): T =
        locks[(sessionId.value.hashCode() and Int.MAX_VALUE) % locks.size].withLock { action() }
}
