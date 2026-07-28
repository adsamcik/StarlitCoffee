package com.adsamcik.starlitcoffee.data.brewing.session

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkManager
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
) {
    /**
     * Reads only indexed scheduling metadata. The worker intentionally does
     * not decode a recipe or runtime document before deciding whether an old
     * WorkManager prompt is stale.
     */
    suspend fun scheduledDeadline(sessionId: SessionId): ScheduledBrewSessionDeadline? =
        sessionRepository.getSession(sessionId.value)?.let { entity ->
            ScheduledBrewSessionDeadline(
                sessionId = entity.sessionId,
                status = entity.status,
                stageInstanceKey = entity.currentStageId,
                scheduleToken = entity.scheduledEventToken,
                dueAtWallClockMillis = entity.deadlineAtWallClockMillis,
            )
        }

    /** Replays durable work and restores the current deadline prompt after startup. */
    suspend fun reconcileRecoverableSessions(): List<BrewSessionOperationResult> {
        val results = coordinator.reconcileRecoverableSessions()
        for (result in results) {
            val active = result as? BrewSessionOperationResult.Active ?: continue
            enqueuePersistedDeadline(active.session)
        }
        return results
    }

    private suspend fun enqueuePersistedDeadline(session: ActiveBrewSession) {
        if (session.runtime.status != BrewSessionStatus.RUNNING) return
        val stage = session.runtime.currentStage ?: return
        val deadline = scheduledDeadline(session.runtime.sessionId) ?: return
        val token = deadline.scheduleToken ?: return
        val dueAt = deadline.dueAtWallClockMillis ?: return
        if (
            deadline.status != BrewSessionStatus.RUNNING.name ||
                deadline.stageInstanceKey != stage.instanceId.persistentKey
        ) return
        scheduler.schedule(
            sessionId = session.runtime.sessionId,
            stageInstanceId = stage.instanceId,
            scheduleToken = token,
            dueAtWallClockMillis = dueAt,
            effectId = SessionEffectId(
                "${session.runtime.sessionId.value}:startup_schedule:$token",
            ),
        )
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
        ): BrewSessionRuntime {
            val repository = ActiveBrewSessionRepository(database.activeBrewSessionDao())
            val scheduler = WorkManagerLongSessionScheduler(
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
                workCanceller = scheduler,
            )
            return BrewSessionRuntime(
                coordinator = BrewSessionCoordinator(
                    sessionRepository = repository,
                    clockedEngine = ClockedSessionEngine(monotonicClock, wallClock),
                    effectHandler = effectHandler,
                ),
                sessionRepository = repository,
                scheduler = scheduler,
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
