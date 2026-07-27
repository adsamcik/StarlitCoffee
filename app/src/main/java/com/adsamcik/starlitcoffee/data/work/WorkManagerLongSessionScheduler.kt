package com.adsamcik.starlitcoffee.data.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionScheduler
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import java.util.concurrent.TimeUnit

/**
 * Stable WorkManager contract for a deferred brew-stage deadline.
 *
 * The eventual worker must treat [KEY_EFFECT_ID] and [KEY_SCHEDULE_TOKEN] as
 * idempotency and staleness guards; WorkManager timing is deliberately
 * inexact, so it is only a prompt to reconcile the durable session state.
 */
object LongSessionWork {
    const val UNIQUE_WORK_PREFIX = "starlitcoffee.brewing.stage_deadline"
    const val WORK_TAG = "starlitcoffee.brewing.stage_deadline"
    const val INPUT_SCHEMA_VERSION = 1

    const val KEY_SCHEMA_VERSION = "brew_session_schema_version"
    const val KEY_SESSION_ID = "brew_session_id"
    const val KEY_STAGE_SOURCE_ID = "brew_stage_source_id"
    const val KEY_STAGE_OCCURRENCE = "brew_stage_occurrence"
    const val KEY_STAGE_INSTANCE_KEY = "brew_stage_instance_key"
    const val KEY_SCHEDULE_TOKEN = "brew_schedule_token"
    const val KEY_DUE_AT_WALL_CLOCK_MILLIS = "brew_due_at_wall_clock_millis"
    const val KEY_EFFECT_ID = "brew_effect_id"

    /**
     * The name is deliberately length-delimited so distinct session/token
     * pairs cannot collide when either value contains a separator character.
     */
    fun uniqueWorkName(sessionId: SessionId, scheduleToken: String): String =
        "$UNIQUE_WORK_PREFIX/${nameComponent(sessionId.value)}/${nameComponent(requireScheduleToken(scheduleToken))}"

    /** Tag for inspecting all deadline prompts belonging to one session. */
    fun sessionTag(sessionId: SessionId): String =
        "$UNIQUE_WORK_PREFIX.session/${nameComponent(sessionId.value)}"

    /**
     * Returns a non-negative delay derived from the durable wall-clock due
     * time. A past due deadline runs at the next WorkManager opportunity.
     */
    fun initialDelayMillis(
        dueAtWallClockMillis: Long,
        nowWallClockMillis: Long,
    ): Long {
        if (dueAtWallClockMillis <= nowWallClockMillis) return 0L
        val difference = dueAtWallClockMillis - nowWallClockMillis
        return if (difference < 0L) Long.MAX_VALUE else difference
    }

    /** Input data consumed by the later deadline worker. */
    fun inputData(
        sessionId: SessionId,
        stageInstanceId: StageInstanceId,
        scheduleToken: String,
        dueAtWallClockMillis: Long,
        effectId: SessionEffectId,
    ): Data = workDataOf(
        KEY_SCHEMA_VERSION to INPUT_SCHEMA_VERSION,
        KEY_SESSION_ID to sessionId.value,
        KEY_STAGE_SOURCE_ID to stageInstanceId.sourceStageId.value,
        KEY_STAGE_OCCURRENCE to stageInstanceId.occurrence,
        KEY_STAGE_INSTANCE_KEY to stageInstanceId.persistentKey,
        KEY_SCHEDULE_TOKEN to requireScheduleToken(scheduleToken),
        KEY_DUE_AT_WALL_CLOCK_MILLIS to dueAtWallClockMillis,
        KEY_EFFECT_ID to effectId.value,
    )

    /**
     * Builds a normal one-time WorkManager request. This intentionally avoids
     * exact alarms: the worker must reconcile state when it eventually runs.
     */
    fun buildRequest(
        workerClass: Class<out ListenableWorker>,
        sessionId: SessionId,
        stageInstanceId: StageInstanceId,
        scheduleToken: String,
        dueAtWallClockMillis: Long,
        effectId: SessionEffectId,
        nowWallClockMillis: Long,
    ): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(workerClass)
            .setInputData(
                inputData(
                    sessionId = sessionId,
                    stageInstanceId = stageInstanceId,
                    scheduleToken = scheduleToken,
                    dueAtWallClockMillis = dueAtWallClockMillis,
                    effectId = effectId,
                ),
            )
            .setInitialDelay(
                initialDelayMillis(
                    dueAtWallClockMillis = dueAtWallClockMillis,
                    nowWallClockMillis = nowWallClockMillis,
                ),
                TimeUnit.MILLISECONDS,
            )
            .addTag(WORK_TAG)
            .addTag(sessionTag(sessionId))
            .build()

    private fun nameComponent(value: String): String = "${value.length}:$value"

    private fun requireScheduleToken(scheduleToken: String): String =
        requireNotNull(scheduleToken.takeIf(String::isNotBlank)) {
            "Schedule tokens cannot be blank"
        }
}

/**
 * Android implementation of the pure-domain [LongSessionScheduler].
 *
 * A later worker type is injected so this adapter can stay independent of the
 * worker's notification and persistence dependencies. Replacing work for the
 * same session/token makes pause-resume and retry reschedules deterministic.
 */
class WorkManagerLongSessionScheduler(
    private val workManager: WorkManager,
    private val workerClass: Class<out ListenableWorker>,
    private val nowWallClockMillis: () -> Long = System::currentTimeMillis,
) : LongSessionScheduler {

    override fun schedule(
        sessionId: SessionId,
        stageInstanceId: StageInstanceId,
        scheduleToken: String,
        dueAtWallClockMillis: Long,
        effectId: SessionEffectId,
    ) {
        workManager.enqueueUniqueWork(
            LongSessionWork.uniqueWorkName(sessionId, scheduleToken),
            ExistingWorkPolicy.REPLACE,
            LongSessionWork.buildRequest(
                workerClass = workerClass,
                sessionId = sessionId,
                stageInstanceId = stageInstanceId,
                scheduleToken = scheduleToken,
                dueAtWallClockMillis = dueAtWallClockMillis,
                effectId = effectId,
                nowWallClockMillis = nowWallClockMillis(),
            ),
        )
    }

    override fun cancel(
        sessionId: SessionId,
        scheduleToken: String,
        effectId: SessionEffectId,
    ) {
        workManager.cancelUniqueWork(LongSessionWork.uniqueWorkName(sessionId, scheduleToken))
    }
}
