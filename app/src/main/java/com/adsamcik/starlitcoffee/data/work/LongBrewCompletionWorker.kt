package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.data.brewing.session.ScheduledBrewSessionDeadline
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * WorkManager is an inexact wake-up prompt, not the source of truth for a
 * brew timer. Before changing a session this worker checks the durable token,
 * stage, and wall-clock due time that caused it to be scheduled. A late or
 * replaced request is therefore harmless and simply succeeds.
 */
class LongBrewCompletionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val request = when (val decoded = decodeLongBrewCompletionWork(inputData)) {
            is LongBrewCompletionWorkInput.Invalid -> {
                Log.w(TAG, "Ignoring malformed brew deadline work: ${decoded.reason}")
                return Result.failure()
            }

            is LongBrewCompletionWorkInput.Valid -> decoded.request
        }

        return try {
            val runtime = BrewSessionRuntime.create(applicationContext)
            val durableDeadline = runtime.scheduledDeadline(request.sessionId)
            if (deadlineWorkDisposition(request, durableDeadline) == DeadlineWorkDisposition.STALE) {
                return Result.success()
            }

            // The event ID derives from the durable scheduling effect, so a
            // process death after persistence cannot advance this deadline twice.
            runtime.coordinator.dispatch(
                sessionId = request.sessionId,
                event = SessionEvent.Reconcile(
                    eventId = SessionEventId("work:${request.effectId.value}"),
                ),
            )
            // A current session can become unavailable or concurrently move
            // between the indexed check and dispatch. Both are stale work, not
            // a reason to retry an otherwise valid prompt.
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Brew deadline reconciliation failed", error)
            if (isRetryableLongBrewCompletionFailure(error)) Result.retry() else Result.failure()
        }
    }

    private companion object {
        private const val TAG = "LongBrewCompletionWorker"
    }
}

/** Strict, versioned WorkManager input decoded before opening Room. */
internal data class LongBrewCompletionWorkRequest(
    val sessionId: SessionId,
    val stageInstanceId: StageInstanceId,
    val scheduleToken: String,
    val dueAtWallClockMillis: Long,
    val effectId: SessionEffectId,
)

internal sealed interface LongBrewCompletionWorkInput {
    data class Valid(val request: LongBrewCompletionWorkRequest) : LongBrewCompletionWorkInput

    data class Invalid(val reason: String) : LongBrewCompletionWorkInput
}

/**
 * Decodes [LongSessionWork]'s stable data contract without accepting partial
 * input. A schema bump will deliberately fail rather than guessing how to
 * reconcile a future worker payload.
 */
internal fun decodeLongBrewCompletionWork(input: Data): LongBrewCompletionWorkInput {
    val schemaVersion = input.getInt(LongSessionWork.KEY_SCHEMA_VERSION, MISSING_SCHEMA_VERSION)
    if (schemaVersion != LongSessionWork.INPUT_SCHEMA_VERSION) {
        return LongBrewCompletionWorkInput.Invalid(
            "Unsupported deadline-work schema version: $schemaVersion",
        )
    }

    return try {
        val sessionId = SessionId(input.requiredString(LongSessionWork.KEY_SESSION_ID))
        val stageSourceId = StageId(input.requiredString(LongSessionWork.KEY_STAGE_SOURCE_ID))
        val occurrence = input.getInt(LongSessionWork.KEY_STAGE_OCCURRENCE, MISSING_OCCURRENCE)
        val stageInstanceId = StageInstanceId(stageSourceId, occurrence)
        val inputStageKey = input.requiredString(LongSessionWork.KEY_STAGE_INSTANCE_KEY)
        require(inputStageKey == stageInstanceId.persistentKey) {
            "Stage instance key does not match its source ID and occurrence"
        }
        val scheduleToken = input.requiredString(LongSessionWork.KEY_SCHEDULE_TOKEN)
        val dueAtWallClockMillis = input.getLong(
            LongSessionWork.KEY_DUE_AT_WALL_CLOCK_MILLIS,
            MISSING_DUE_AT_WALL_CLOCK_MILLIS,
        )
        require(dueAtWallClockMillis >= 0L) { "Deadline due time must be a wall-clock epoch value" }
        val effectId = SessionEffectId(input.requiredString(LongSessionWork.KEY_EFFECT_ID))
        LongBrewCompletionWorkInput.Valid(
            LongBrewCompletionWorkRequest(
                sessionId = sessionId,
                stageInstanceId = stageInstanceId,
                scheduleToken = scheduleToken,
                dueAtWallClockMillis = dueAtWallClockMillis,
                effectId = effectId,
            ),
        )
    } catch (error: IllegalArgumentException) {
        LongBrewCompletionWorkInput.Invalid(error.message ?: "Invalid deadline-work input")
    }
}

/** Whether the indexed Room state still belongs to this unique work request. */
internal enum class DeadlineWorkDisposition {
    CURRENT,
    STALE,
}

internal fun deadlineWorkDisposition(
    request: LongBrewCompletionWorkRequest,
    durableDeadline: ScheduledBrewSessionDeadline?,
): DeadlineWorkDisposition = when {
    durableDeadline == null -> DeadlineWorkDisposition.STALE
    durableDeadline.sessionId != request.sessionId.value -> DeadlineWorkDisposition.STALE
    durableDeadline.status != BrewSessionStatus.RUNNING.name -> DeadlineWorkDisposition.STALE
    durableDeadline.stageInstanceKey != request.stageInstanceId.persistentKey -> DeadlineWorkDisposition.STALE
    durableDeadline.scheduleToken != request.scheduleToken -> DeadlineWorkDisposition.STALE
    durableDeadline.dueAtWallClockMillis != request.dueAtWallClockMillis -> DeadlineWorkDisposition.STALE
    else -> DeadlineWorkDisposition.CURRENT
}

/** Only infrastructure failures deserve WorkManager's retry/backoff behaviour. */
internal fun isRetryableLongBrewCompletionFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { cause -> cause is SQLiteException || cause is IOException }

private fun Data.requiredString(key: String): String = requireNotNull(getString(key)?.takeIf(String::isNotBlank)) {
    "Missing $key"
}

private const val MISSING_SCHEMA_VERSION = Int.MIN_VALUE
private const val MISSING_OCCURRENCE = Int.MIN_VALUE
private const val MISSING_DUE_AT_WALL_CLOCK_MILLIS = Long.MIN_VALUE
private const val MAX_CAUSE_DEPTH = 16
