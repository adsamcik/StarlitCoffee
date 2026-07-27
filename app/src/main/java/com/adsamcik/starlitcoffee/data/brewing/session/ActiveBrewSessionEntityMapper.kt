package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotCodec
import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState

/** Fully restored durable state; callers never need to decode blobs themselves. */
data class RestoredActiveBrewSession(
    val entity: ActiveBrewSessionEntity,
    val recipe: BrewRecipeSnapshotV1,
    val runtime: SessionRuntimeState,
    val executionContext: SessionExecutionContextSnapshotV1,
)

/** A damaged or future session remains inspectable and is never silently reset. */
sealed interface ActiveBrewSessionRestoreResult {
    data class Restored(val value: RestoredActiveBrewSession) : ActiveBrewSessionRestoreResult

    data class UnsupportedRecipe(
        val schemaVersion: Int,
        val rawJson: String,
    ) : ActiveBrewSessionRestoreResult

    data class InvalidRecipe(
        val reason: String,
        val rawJson: String,
    ) : ActiveBrewSessionRestoreResult

    data class MissingExecutionContext(
        val sessionId: String,
    ) : ActiveBrewSessionRestoreResult

    data class UnsupportedStorage(
        val document: SessionStorageDocument,
        val schemaVersion: Int,
        val rawJson: String,
    ) : ActiveBrewSessionRestoreResult

    data class InvalidStorage(
        val document: SessionStorageDocument,
        val reason: String,
        val rawJson: String?,
    ) : ActiveBrewSessionRestoreResult

    data class InconsistentEntity(
        val sessionId: String,
        val reason: String,
    ) : ActiveBrewSessionRestoreResult
}

/**
 * Maps explicit versioned documents to indexed Room fields. Scheduling metadata
 * is preserved after its schedule effect is acknowledged, which lets startup
 * recovery repair missing WorkManager work without recreating a reducer event.
 */
object ActiveBrewSessionEntityMapper {

    fun create(
        recipe: BrewRecipeSnapshotV1,
        runtime: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
        nowWallClockMillis: Long,
    ): ActiveBrewSessionEntity = toEntity(
        previous = null,
        recipe = recipe,
        runtime = runtime,
        executionContext = executionContext,
        completedLogId = null,
        nowWallClockMillis = nowWallClockMillis,
    )

    fun update(
        previous: ActiveBrewSessionEntity,
        recipe: BrewRecipeSnapshotV1,
        runtime: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
        nowWallClockMillis: Long,
        completedLogId: Long? = previous.completedLogId,
    ): ActiveBrewSessionEntity = toEntity(
        previous = previous,
        recipe = recipe,
        runtime = runtime,
        executionContext = executionContext,
        completedLogId = completedLogId,
        nowWallClockMillis = nowWallClockMillis,
    )

    fun restore(entity: ActiveBrewSessionEntity): ActiveBrewSessionRestoreResult {
        val recipe = when (val decoded = BrewingSnapshotCodec.decodeRecipe(entity.recipeSnapshotJson)) {
            is SnapshotDecodeResult.Decoded -> decoded.value
            is SnapshotDecodeResult.Invalid -> {
                return ActiveBrewSessionRestoreResult.InvalidRecipe(decoded.reason, decoded.rawJson)
            }

            is SnapshotDecodeResult.UnsupportedVersion -> {
                return ActiveBrewSessionRestoreResult.UnsupportedRecipe(
                    decoded.schemaVersion,
                    decoded.rawJson,
                )
            }
        }
        if (entity.recipeSnapshotVersion != recipe.schemaVersion) {
            return inconsistent(entity, "Recipe schema column does not match the recipe document")
        }
        if (entity.executionContextSchemaVersion == null || entity.executionContextJson == null) {
            return ActiveBrewSessionRestoreResult.MissingExecutionContext(entity.sessionId)
        }

        return when (
            val restored = SessionStorageMapper.decodeAndRestore(
                compiledPlanJson = entity.compiledPlanJson,
                runtimeJson = entity.runtimeJson,
                executionContextJson = entity.executionContextJson,
            )
        ) {
            is SessionStorageRestoreResult.InvalidDocument -> ActiveBrewSessionRestoreResult.InvalidStorage(
                document = restored.document,
                reason = restored.reason,
                rawJson = restored.rawJson,
            )

            is SessionStorageRestoreResult.UnsupportedDocument -> {
                ActiveBrewSessionRestoreResult.UnsupportedStorage(
                    document = restored.document,
                    schemaVersion = restored.schemaVersion,
                    rawJson = restored.rawJson,
                )
            }

            is SessionStorageRestoreResult.Restored -> {
                validateRestored(entity, recipe, restored.value)
            }
        }
    }

    private fun toEntity(
        previous: ActiveBrewSessionEntity?,
        recipe: BrewRecipeSnapshotV1,
        runtime: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
        completedLogId: Long?,
        nowWallClockMillis: Long,
    ): ActiveBrewSessionEntity {
        val encoded = SessionStorageMapper.encode(runtime, executionContext)
        val schedule = scheduleMetadata(previous, runtime)
        return ActiveBrewSessionEntity(
            sessionId = runtime.sessionId.value,
            status = runtime.status.name,
            recipeSnapshotVersion = recipe.schemaVersion,
            recipeSnapshotJson = BrewingSnapshotCodec.encodeRecipe(recipe),
            compiledPlanSchemaVersion = encoded.compiledPlanSchemaVersion,
            compiledPlanJson = encoded.compiledPlanJson,
            runtimeSchemaVersion = encoded.runtimeSchemaVersion,
            runtimeJson = encoded.runtimeJson,
            executionContextSchemaVersion = encoded.executionContextSchemaVersion,
            executionContextJson = encoded.executionContextJson,
            currentStageId = runtime.currentStage?.instanceId?.persistentKey,
            currentStageIndex = runtime.currentStageIndex,
            startedAtWallClockMillis = runtime.startedAtWallClockMillis,
            pausedAtWallClockMillis = runtime.pausedAtWallClockMillis,
            deadlineAtWallClockMillis = schedule.deadlineAtWallClockMillis,
            scheduledEventToken = schedule.token,
            notificationStateJson = previous?.notificationStateJson,
            lastProcessedEventId = runtime.processedEventIds.lastOrNull()?.value,
            completedLogId = completedLogId,
            revision = runtime.revision,
            createdAt = previous?.createdAt ?: nowWallClockMillis,
            updatedAt = nowWallClockMillis,
        )
    }

    private fun scheduleMetadata(
        previous: ActiveBrewSessionEntity?,
        runtime: SessionRuntimeState,
    ): ScheduleMetadata {
        val schedule = runtime.pendingEffects.filterIsInstance<PendingSessionEffect.ScheduleStageDeadline>()
            .lastOrNull()
        if (schedule != null) {
            return ScheduleMetadata(schedule.dueAtWallClockMillis, schedule.scheduleToken)
        }

        val cancellationIsPending = runtime.pendingEffects.any { effect ->
            effect is PendingSessionEffect.CancelSessionWork ||
                (effect is PendingSessionEffect.CancelStageDeadline &&
                    effect.scheduleToken == previous?.scheduledEventToken)
        }
        val isSameRunningStage = runtime.status == BrewSessionStatus.RUNNING &&
            runtime.currentStage?.instanceId?.persistentKey == previous?.currentStageId
        return if (!cancellationIsPending && isSameRunningStage) {
            ScheduleMetadata(previous?.deadlineAtWallClockMillis, previous?.scheduledEventToken)
        } else {
            ScheduleMetadata(null, null)
        }
    }

    private fun validateRestored(
        entity: ActiveBrewSessionEntity,
        recipe: BrewRecipeSnapshotV1,
        restored: RestoredSessionStorage,
    ): ActiveBrewSessionRestoreResult {
        val runtime = restored.state
        val mismatch = when {
            entity.sessionId != runtime.sessionId.value -> "Session ID column does not match runtime"
            entity.status != runtime.status.name -> "Status column does not match runtime"
            entity.revision != runtime.revision -> "Revision column does not match runtime"
            entity.compiledPlanSchemaVersion != CompiledStagePlanSnapshotV1.SCHEMA_VERSION -> {
                "Compiled-plan schema column does not match the supported document"
            }

            entity.runtimeSchemaVersion != SessionRuntimeSnapshotV1.SCHEMA_VERSION -> {
                "Runtime schema column does not match the supported document"
            }

            entity.executionContextSchemaVersion != SessionExecutionContextSnapshotV1.SCHEMA_VERSION -> {
                "Execution-context schema column does not match the supported document"
            }

            entity.currentStageIndex != runtime.currentStageIndex -> {
                "Current-stage index column does not match runtime"
            }

            entity.currentStageId != runtime.currentStage?.instanceId?.persistentKey -> {
                "Current-stage ID column does not match runtime"
            }

            else -> null
        }
        return if (mismatch == null) {
            ActiveBrewSessionRestoreResult.Restored(
                RestoredActiveBrewSession(entity, recipe, runtime, restored.executionContext),
            )
        } else {
            inconsistent(entity, mismatch)
        }
    }

    private fun inconsistent(
        entity: ActiveBrewSessionEntity,
        reason: String,
    ): ActiveBrewSessionRestoreResult = ActiveBrewSessionRestoreResult.InconsistentEntity(
        sessionId = entity.sessionId,
        reason = reason,
    )

    private data class ScheduleMetadata(
        val deadlineAtWallClockMillis: Long?,
        val token: String?,
    )
}
