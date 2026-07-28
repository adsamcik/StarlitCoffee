package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId

/**
 * Keeps a durable brew discoverable when its live timer is not foreground-visible.
 *
 * This adapter deliberately receives a restored snapshot rather than individual
 * timing fields so the notification can never describe a session that was not
 * accepted by the durable storage mapper.
 */
interface BrewSessionStatusNotifier {
    fun publish(session: ActiveBrewSession)

    fun clear(sessionId: SessionId)
}

/** Safe default for deterministic unit tests and non-Android callers. */
object NoOpBrewSessionStatusNotifier : BrewSessionStatusNotifier {
    override fun publish(session: ActiveBrewSession) = Unit

    override fun clear(sessionId: SessionId) = Unit
}

/**
 * Restores a session before routing it to the status adapter. A missing,
 * unsupported, damaged, or non-running snapshot must remove any stale status
 * notification instead of leaving a timer that can no longer be resumed.
 */
internal suspend fun publishRestoredBrewSessionStatus(
    sessionId: SessionId,
    sessionRepository: ActiveBrewSessionRepository,
    statusNotifier: BrewSessionStatusNotifier,
) {
    val entity = sessionRepository.getSession(sessionId.value)
    val restored = entity?.let(ActiveBrewSessionEntityMapper::restore)
        as? ActiveBrewSessionRestoreResult.Restored
    val restoredSession = restored?.value
    if (restoredSession == null || restoredSession.runtime.status != BrewSessionStatus.RUNNING) {
        statusNotifier.clear(sessionId)
        return
    }
    statusNotifier.publish(
        ActiveBrewSession(
            recipe = restoredSession.recipe,
            runtime = restoredSession.runtime,
            executionContext = restoredSession.executionContext,
        ),
    )
}
