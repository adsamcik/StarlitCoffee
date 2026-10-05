package com.adsamcik.starlitcoffee.data.brewing.session

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkManager
import com.adsamcik.starlitcoffee.notification.DurableBrewSessionStageNotifier
import com.adsamcik.starlitcoffee.notification.DurableBrewSessionStatusNotifier
import com.adsamcik.starlitcoffee.notification.BrewSessionNotificationBoundaryStore
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import com.adsamcik.starlitcoffee.data.work.LongBrewCompletionWorker
import com.adsamcik.starlitcoffee.data.work.WorkManagerLongSessionScheduler
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionScheduler
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock

/**
 * The small manual composition root for durable brewing sessions.
 *
 * A foreground screen, startup reconciliation, and a WorkManager deadline
 * prompt must share the same coordinator and effect ordering. Keeping that
 * wiring here avoids a second, subtly different execution path in the worker.
 */
class BrewSessionRuntime private constructor(
    val coordinator: BrewSessionCoordinator,
    private val sessionRepository: ActiveBrewSessionRepository,
    private val scheduler: LongSessionScheduler,
    private val statusNotifier: BrewSessionStatusNotifier,
    private val notificationBoundaryStore: BrewSessionNotificationBoundaryStore?,
) {
    /**
     * Reads only indexed scheduling metadata. The worker intentionally does
     * not decode a recipe or runtime document before deciding whether an old
     * WorkManager prompt is stale.
     */
    suspend fun scheduledDeadline(sessionId: SessionId, scheduleToken: String? = null): ScheduledBrewSessionDeadline? =
        sessionRepository.getSession(sessionId.value)?.let { entity ->
            if (scheduleToken != null && scheduleToken != entity.scheduledEventToken) {
                val restored = ActiveBrewSessionEntityMapper.restore(entity)
                    as? ActiveBrewSessionRestoreResult.Restored ?: return@let null
                val timer = restored.value.runtime.userTimer ?: return@let null
                if (timer.scheduleToken(sessionId) != scheduleToken || timer.reached) return@let null
                return@let ScheduledBrewSessionDeadline(
                    entity.sessionId, entity.status, timer.stageInstanceId.persistentKey,
                    scheduleToken, timer.deadlineAtWallClockMillis,
                )
            }
            ScheduledBrewSessionDeadline(
                sessionId = entity.sessionId,
                status = entity.status,
                stageInstanceKey = entity.currentStageId,
                scheduleToken = entity.scheduledEventToken,
                dueAtWallClockMillis = entity.deadlineAtWallClockMillis,
            )
        }

    /** Publishes a quiet ongoing status only after restoring the exact durable snapshot. */
    suspend fun publishBackgroundStatus(sessionId: SessionId) {
        publishRestoredBrewSessionStatus(sessionId, sessionRepository, statusNotifier)
    }

    /** Clears this session’s quiet ongoing status when it returns to the foreground. */
    fun clearBackgroundStatus(sessionId: SessionId) {
        statusNotifier.clear(sessionId)
    }

    /** Makes only subsequent stage transitions eligible for system alerts. */
    fun markScreenBackgrounded(
        sessionId: SessionId,
        wallClockMillis: Long = System.currentTimeMillis(),
    ) {
        notificationBoundaryStore?.markBackgrounded(sessionId.value, wallClockMillis)
    }

    /** Replays durable work and restores the current deadline prompt after startup. */
    suspend fun reconcileRecoverableSessions(useMonotonicClock: Boolean = false): List<BrewSessionOperationResult> {
        val results = coordinator.reconcileRecoverableSessions(useMonotonicClock)
        for (result in results) {
            val session = when (result) {
                is BrewSessionOperationResult.Active -> result.session
                is BrewSessionOperationResult.PendingEffect -> result.session
                else -> null
            }
            if (session != null) {
                enqueuePersistedDeadline(session)
                enqueuePersistedTimer(session)
            }
            val sessionId = when (result) {
                is BrewSessionOperationResult.Active -> result.session.runtime.sessionId
                is BrewSessionOperationResult.PendingEffect -> result.session.runtime.sessionId
                is BrewSessionOperationResult.Unavailable -> result.sessionId
                is BrewSessionOperationResult.NotFound -> result.sessionId
                is BrewSessionOperationResult.ConcurrentUpdate -> result.sessionId
            }
            publishBackgroundStatus(sessionId)
        }
        return results
    }

    private fun enqueuePersistedTimer(session: ActiveBrewSession) {
        val timer = session.runtime.userTimer ?: return
        val dueAt = timer.deadlineAtWallClockMillis ?: return
        if (session.runtime.status != BrewSessionStatus.RUNNING || timer.reached) return
        scheduler.schedule(session.runtime.sessionId, timer.stageInstanceId,
            timer.scheduleToken(session.runtime.sessionId), dueAt,
            SessionEffectId("${session.runtime.sessionId.value}:startup_timer:${timer.revision}:$dueAt"))
    }

    private suspend fun enqueuePersistedDeadline(session: ActiveBrewSession) {
        if (session.runtime.status != BrewSessionStatus.RUNNING) return
        val stage = session.runtime.currentStage
        val deadline = scheduledDeadline(session.runtime.sessionId)
        val matchesCurrentStage = deadline?.status == BrewSessionStatus.RUNNING.name &&
            deadline.stageInstanceKey == stage?.instanceId?.persistentKey
        val scheduleToken = deadline?.scheduleToken
        val dueAt = deadline?.dueAtWallClockMillis
        val hasCompleteSchedule = listOf(
            stage != null,
            matchesCurrentStage,
            scheduleToken != null,
            dueAt != null,
        ).all { it }
        if (hasCompleteSchedule) {
            scheduler.schedule(
                sessionId = session.runtime.sessionId,
                stageInstanceId = requireNotNull(stage).instanceId,
                scheduleToken = requireNotNull(scheduleToken),
                dueAtWallClockMillis = requireNotNull(dueAt),
                effectId = SessionEffectId(
                    "${session.runtime.sessionId.value}:startup_schedule:$scheduleToken",
                ),
            )
        }
    }

    companion object {
        /** Builds the production runtime from the process-wide Room and WorkManager instances. */
        fun create(context: Context): BrewSessionRuntime {
            val applicationContext = context.applicationContext
            return create(
                database = AppDatabase.getInstance(applicationContext),
                workManager = WorkManager.getInstance(applicationContext),
                monotonicClock = MonotonicClock { SystemClock.elapsedRealtime() },
                wallClock = WallClock { System.currentTimeMillis() },
                stageAlertNotifier = DurableBrewSessionStageNotifier(applicationContext),
                statusNotifier = DurableBrewSessionStatusNotifier(applicationContext),
                notificationBoundaryStore = BrewSessionNotificationBoundaryStore(applicationContext),
                schedulerOverride = com.adsamcik.starlitcoffee.data.work.AlarmBrewSessionScheduler(applicationContext),
            )
        }

        /**
         * Internal seam for deterministic integration tests. Production callers
         * should use [create] with a [Context].
         */
        internal fun create(
            database: AppDatabase,
            workManager: WorkManager,
            monotonicClock: MonotonicClock,
            wallClock: WallClock,
            stageAlertNotifier: BrewSessionStageAlertNotifier = NoOpBrewSessionStageAlertNotifier,
            statusNotifier: BrewSessionStatusNotifier = NoOpBrewSessionStatusNotifier,
            notificationBoundaryStore: BrewSessionNotificationBoundaryStore? = null,
            schedulerOverride: com.adsamcik.starlitcoffee.data.work.AlarmBrewSessionScheduler? = null,
        ): BrewSessionRuntime {
            val repository = ActiveBrewSessionRepository(database.activeBrewSessionDao())
            val scheduler = schedulerOverride ?: WorkManagerLongSessionScheduler(
                workManager = workManager,
                workerClass = LongBrewCompletionWorker::class.java,
                nowWallClockMillis = wallClock::nowMillis,
            )
            val finalizer = BrewSessionFinalizer(
                transactionRunner = TransactionRunner.room(database),
                sessionRepository = repository,
                brewLogDao = database.brewLogDao(),
                coffeeBagDao = database.coffeeBagDao(),
                nowWallClockMillis = wallClock::nowMillis,
            )
            val effectHandler = DefaultBrewSessionEffectHandler(
                scheduler = scheduler,
                finalizer = finalizer,
                stageAlertNotifier = stageAlertNotifier,
                statusNotifier = statusNotifier,
                workCanceller = scheduler as com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionWorkCanceller,
            )
            return BrewSessionRuntime(
                coordinator = BrewSessionCoordinator(
                    sessionRepository = repository,
                    clockedEngine = ClockedSessionEngine(monotonicClock, wallClock),
                    effectHandler = effectHandler,
                ),
                sessionRepository = repository,
                scheduler = scheduler,
                statusNotifier = statusNotifier,
                notificationBoundaryStore = notificationBoundaryStore,
            )
        }
    }
}

/** Indexed durable fields used to decide whether a deadline prompt is still current. */
data class ScheduledBrewSessionDeadline(
    val sessionId: String,
    val status: String,
    val stageInstanceKey: String?,
    val scheduleToken: String?,
    val dueAtWallClockMillis: Long?,
)
