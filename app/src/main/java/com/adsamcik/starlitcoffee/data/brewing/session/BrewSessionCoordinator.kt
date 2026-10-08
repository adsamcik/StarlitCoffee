package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.PhysicalBrewClock
import kotlinx.coroutines.CancellationException

data class BrewSessionStartRequest(
    val sessionId: SessionId,
    val recipe: BrewRecipeSnapshotV1,
    val stagePlan: CompiledStagePlan,
    val executionContext: SessionExecutionContextSnapshotV1,
    val physicalClock: PhysicalBrewClock? = null,
)

data class ActiveBrewSession(
    val recipe: BrewRecipeSnapshotV1,
    val runtime: SessionRuntimeState,
    val executionContext: SessionExecutionContextSnapshotV1,
)

/**
 * Effect delivery is separated from the coordinator so persistence stays
 * ordered and testable. A delivery must be externally idempotent by the
 * effect ID: a process can die after delivery but before acknowledgement.
 */
interface BrewSessionEffectHandler {
    fun synchronize(session: ActiveBrewSession) = Unit
    suspend fun deliver(
        effect: PendingSessionEffect,
        session: ActiveBrewSession,
    ): SessionEffectDelivery
}

sealed interface SessionEffectDelivery {
    /** The coordinator should persist an acknowledgement transition next. */
    data object Delivered : SessionEffectDelivery

    /**
     * A finalizer has atomically written its business result and acknowledgement.
     * The coordinator must reload the session instead of writing an acknowledgement.
     */
    data object AcknowledgedAtomically : SessionEffectDelivery

    /** Keep the outbox item for a later retry without presenting a false success. */
    data class Deferred(val reason: String) : SessionEffectDelivery
}

sealed interface BrewSessionOperationResult {
    data class Active(val session: ActiveBrewSession) : BrewSessionOperationResult

    data class PendingEffect(
        val session: ActiveBrewSession,
        val effect: PendingSessionEffect,
        val reason: String,
    ) : BrewSessionOperationResult

    data class NotFound(val sessionId: SessionId) : BrewSessionOperationResult

    data class Unavailable(
        val sessionId: SessionId,
        val restoreFailure: ActiveBrewSessionRestoreResult,
    ) : BrewSessionOperationResult

    data class ConcurrentUpdate(val sessionId: SessionId) : BrewSessionOperationResult
}

/**
 * The sole owner of reducer persistence and outbox acknowledgement.
 *
 * Every entry point (screen, worker, startup recovery, and notification action)
 * must call this coordinator instead of invoking [SessionReducer] directly.
 * That gives every transition the same ordering:
 *
 * reducer → compare-and-set persistence → externally idempotent effect →
 * persisted acknowledgement.
 */
class BrewSessionCoordinator(
    private val sessionRepository: ActiveBrewSessionRepository,
    private val clockedEngine: ClockedSessionEngine,
    private val effectHandler: BrewSessionEffectHandler,
    private val maxCompareAndSetAttempts: Int = DEFAULT_COMPARE_AND_SET_ATTEMPTS,
) {
    init {
        require(maxCompareAndSetAttempts > 0) {
            "A session coordinator needs at least one compare-and-set attempt"
        }
    }

    /**
     * Creates an inactive durable session, then optionally starts it through the ordinary
     * reducer path. A bloom awaiting its physical first-water action stays READY.
     * If another owner created the same ID first, it restores
     * that exact durable session rather than overwriting it.
     */
    suspend fun createOrResume(
        request: BrewSessionStartRequest,
        startEventId: SessionEventId? = null,
        startImmediately: Boolean = true,
    ): BrewSessionOperationResult = BrewSessionOperationLocks.withSession(request.sessionId) {
        createOrResumeLocked(request, startEventId, startImmediately)
    }

    private suspend fun createOrResumeLocked(
        request: BrewSessionStartRequest,
        startEventId: SessionEventId?,
        startImmediately: Boolean,
    ): BrewSessionOperationResult {
        val existing = sessionRepository.getSession(request.sessionId.value)
        if (existing != null) return loadAndDrainLocked(request.sessionId)

        val initial = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(physicalClock = request.physicalClock)
        val now = clockedEngine.now()
        val entity = ActiveBrewSessionEntityMapper.create(
            recipe = request.recipe,
            runtime = initial,
            executionContext = request.executionContext,
            nowWallClockMillis = now.wallClockMillis,
        )
        return try {
            sessionRepository.create(entity)
            if (startImmediately) {
                dispatchLocked(request.sessionId, SessionEvent.Start(startEventId))
            } else {
                loadAndDrainLocked(request.sessionId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A duplicate primary key can be a harmless concurrent create. If
            // it is not, the second lookup leaves the original failure visible.
            val afterConflict = sessionRepository.getSession(request.sessionId.value)
            if (afterConflict != null) {
                loadAndDrainLocked(request.sessionId)
            } else {
                throw error
            }
        }
    }

    suspend fun dispatch(
        sessionId: SessionId,
        event: SessionEvent,
    ): BrewSessionOperationResult = BrewSessionOperationLocks.withSession(sessionId) {
        dispatchLocked(sessionId, event)
    }

    /** Deadline identity is checked inside the same lock as persistence and notification delivery. */
    suspend fun reconcileDeadline(
        sessionId: SessionId,
        stageInstanceKey: String,
        scheduleToken: String,
        dueAtWallClockMillis: Long,
        eventId: SessionEventId,
        useMonotonicClock: Boolean = false,
    ): BrewSessionOperationResult = BrewSessionOperationLocks.withSession(sessionId) {
        val entity = sessionRepository.getSession(sessionId.value)
            ?: return@withSession BrewSessionOperationResult.NotFound(sessionId)
        val loaded = load(sessionId)
        if (loaded !is BrewSessionOperationResult.Active) return@withSession loaded
        val runtime = loaded.session.runtime
        val timer = runtime.userTimer
        val matchesIndexed = entity.scheduledEventToken == scheduleToken &&
            entity.deadlineAtWallClockMillis == dueAtWallClockMillis
        val matchesTimer = timer?.scheduleToken(sessionId) == scheduleToken &&
            timer.deadlineAtWallClockMillis == dueAtWallClockMillis && !timer.reached
        val valid = listOf(runtime.status == BrewSessionStatus.RUNNING,
            runtime.currentStage?.instanceId?.persistentKey == stageInstanceKey,
            matchesIndexed || matchesTimer, clockedEngine.now().wallClockMillis >= dueAtWallClockMillis).all { it }
        // A duplicate wake-up may follow a committed deadline whose effects were interrupted.
        // It must not advance again, but can finish the durable outbox.
        if (!valid) return@withSession drain(loaded.session)
        dispatchLocked(sessionId, SessionEvent.Reconcile(eventId, useMonotonicClock))
    }

    private suspend fun dispatchLocked(
        sessionId: SessionId,
        event: SessionEvent,
    ): BrewSessionOperationResult {
        repeat(maxCompareAndSetAttempts) {
            when (val loaded = load(sessionId)) {
                is BrewSessionOperationResult.Active -> {
                    val before = loaded.session
                    val previous = sessionRepository.getSession(sessionId.value)
                        ?: return BrewSessionOperationResult.NotFound(sessionId)
                    val now = clockedEngine.now()
                    val transition = SessionReducer.reduce(before.runtime, event, now)
                    if (transition.wasIgnored) return drain(before)

                    val entity = ActiveBrewSessionEntityMapper.update(
                        previous = previous,
                        recipe = before.recipe,
                        runtime = transition.state,
                        executionContext = before.executionContext,
                        nowWallClockMillis = now.wallClockMillis,
                    )
                    val saved = sessionRepository.saveIfCurrent(
                        session = entity,
                        expectedRevision = before.runtime.revision,
                    )
                    if (saved != null) {
                        return drain(
                            ActiveBrewSession(
                                recipe = before.recipe,
                                runtime = transition.state,
                                executionContext = before.executionContext,
                            ),
                        )
                    }
                }

                else -> return loaded
            }
        }
        return BrewSessionOperationResult.ConcurrentUpdate(sessionId)
    }

    /**
     * Reconciles every durable session that can still make progress or that has
     * a completed log effect after a crash. Unsupported documents are returned
     * to callers as inspectable unavailable sessions and are not deleted.
     */
    suspend fun reconcileRecoverableSessions(useMonotonicClock: Boolean = false): List<BrewSessionOperationResult> =
        sessionRepository.getRecoverableSessions().map { entity ->
            val sessionId = SessionId(entity.sessionId)
            when (val loaded = load(sessionId)) {
                is BrewSessionOperationResult.Active -> {
                    when (loaded.session.runtime.status) {
                        BrewSessionStatus.RUNNING -> dispatch(sessionId, SessionEvent.Reconcile(
                            useMonotonicClock = useMonotonicClock, rescheduleDeadline = useMonotonicClock))
                        else -> loadAndDrain(sessionId)
                    }
                }

                else -> loaded
            }
        }

    suspend fun loadAndDrain(sessionId: SessionId): BrewSessionOperationResult =
        BrewSessionOperationLocks.withSession(sessionId) { loadAndDrainLocked(sessionId) }

    private suspend fun loadAndDrainLocked(sessionId: SessionId): BrewSessionOperationResult = when (
        val loaded = load(sessionId)
    ) {
        is BrewSessionOperationResult.Active -> drain(loaded.session)
        else -> loaded
    }

    private suspend fun load(sessionId: SessionId): BrewSessionOperationResult {
        val entity = sessionRepository.getSession(sessionId.value)
            ?: return BrewSessionOperationResult.NotFound(sessionId)
        return when (val restored = ActiveBrewSessionEntityMapper.restore(entity)) {
            is ActiveBrewSessionRestoreResult.Restored -> BrewSessionOperationResult.Active(
                ActiveBrewSession(
                    recipe = restored.value.recipe,
                    runtime = restored.value.runtime,
                    executionContext = restored.value.executionContext,
                ),
            )

            else -> BrewSessionOperationResult.Unavailable(sessionId, restored)
        }
    }

    private suspend fun drain(initial: ActiveBrewSession): BrewSessionOperationResult {
        var current = initial
        while (true) {
            when (val latest = load(current.runtime.sessionId)) {
                is BrewSessionOperationResult.Active -> current = latest.session
                else -> return latest
            }
            effectHandler.synchronize(current)
            // Cancelling a deadline can cancel the WorkManager coroutine draining this outbox.
            // Deliver and acknowledge its user-visible effects before cancelling wake-up work.
            val effect = current.runtime.pendingEffects.firstOrNull {
                it !is PendingSessionEffect.CancelStageDeadline && it !is PendingSessionEffect.CancelSessionWork
            } ?: current.runtime.pendingEffects.firstOrNull()
                ?: return BrewSessionOperationResult.Active(current)
            when (val delivery = effectHandler.deliver(effect, current)) {
                SessionEffectDelivery.Delivered -> {
                    when (val acknowledged = acknowledge(current, effect.effectId)) {
                        is BrewSessionOperationResult.Active -> current = acknowledged.session
                        else -> return acknowledged
                    }
                }

                SessionEffectDelivery.AcknowledgedAtomically -> {
                    when (val reloaded = load(current.runtime.sessionId)) {
                        is BrewSessionOperationResult.Active -> current = reloaded.session
                        else -> return reloaded
                    }
                }

                is SessionEffectDelivery.Deferred -> {
                    return BrewSessionOperationResult.PendingEffect(current, effect, delivery.reason)
                }
            }
        }
    }

    private suspend fun acknowledge(
        current: ActiveBrewSession,
        effectId: SessionEffectId,
    ): BrewSessionOperationResult {
        val now = clockedEngine.now()
        val event = SessionEvent.AcknowledgeEffect(
            effectIdToAcknowledge = effectId,
            eventId = SessionEventId("ack:" + effectId.value),
        )
        val transition = SessionReducer.reduce(current.runtime, event, now)
        if (transition.wasIgnored) {
            return BrewSessionOperationResult.Active(current)
        }

        val previous = sessionRepository.getSession(current.runtime.sessionId.value)
            ?: return BrewSessionOperationResult.NotFound(current.runtime.sessionId)
        val entity = ActiveBrewSessionEntityMapper.update(
            previous = previous,
            recipe = current.recipe,
            runtime = transition.state,
            executionContext = current.executionContext,
            nowWallClockMillis = now.wallClockMillis,
        )
        val saved = sessionRepository.saveIfCurrent(entity, current.runtime.revision)
            ?: return loadAndDrainLocked(current.runtime.sessionId)
        return BrewSessionOperationResult.Active(
            ActiveBrewSession(current.recipe, transition.state, current.executionContext),
        )
    }

    private companion object {
        const val DEFAULT_COMPARE_AND_SET_ATTEMPTS = 3
    }
}
