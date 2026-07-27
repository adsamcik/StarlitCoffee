package com.adsamcik.starlitcoffee.domain.brewing.session

/**
 * Session IDs may be UUIDs supplied by persistence, so unlike catalogue IDs
 * they intentionally accept hyphens. They still cannot be blank.
 */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Session IDs cannot be blank" }
    }
}

/** Caller-provided keys make retried user and scheduler events idempotent. */
@JvmInline
value class SessionEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "Event IDs cannot be blank" }
    }
}

/** Stable keys for effects that can outlive a process. */
@JvmInline
value class SessionEffectId(val value: String) {
    init {
        require(value.isNotBlank()) { "Effect IDs cannot be blank" }
    }
}

enum class BrewSessionStatus {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

enum class StageRunStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    SKIPPED,
    CANCELLED,
}

enum class StageCompletionKind {
    AUTOMATIC,
    MANUAL,
    MEASURED,
    OBSERVED,
    EXTERNAL_MARKER,
    SKIPPED,
}

/** Values recorded while a stage is active. They are retained in the session snapshot. */
data class StageActuals(
    val addedAmountGrams: Double? = null,
    val cumulativeAmountGrams: Double? = null,
    val beverageYieldGrams: Double? = null,
    val observations: Set<StageObservationId> = emptySet(),
    val markers: Set<StageMarkerId> = emptySet(),
)

sealed interface StageActualValue {
    data class AddedAmount(val grams: Double) : StageActualValue

    data class CumulativeAmount(val grams: Double) : StageActualValue

    data class BeverageYield(val grams: Double) : StageActualValue
}

data class StageRuntimeProgress(
    val status: StageRunStatus,
    val elapsedActiveMillis: Long = 0L,
    val startedAtWallClockMillis: Long? = null,
    val completedAtWallClockMillis: Long? = null,
    val completionKind: StageCompletionKind? = null,
    val actuals: StageActuals = StageActuals(),
)

/**
 * Monotonic time is meaningful only in the owning process. The wall-clock
 * anchor is persisted and becomes authoritative when [SessionEvent.Restore]
 * or [SessionEvent.Reconcile] is processed.
 */
data class ActiveClockAnchor(
    val monotonicMillis: Long?,
    val wallClockMillis: Long,
)

enum class ClockReconciliationKind {
    MONOTONIC_TICK,
    RESTORE_WALL_CLOCK,
    WALL_CLOCK_FORWARD,
    WALL_CLOCK_BACKWARD_CLAMPED,
}

data class ClockReconciliation(
    val kind: ClockReconciliationKind,
    val observedDeltaMillis: Long,
    val appliedDeltaMillis: Long,
)

/**
 * Durable, Android-free session snapshot. The Room layer can serialize this
 * object later without changing reducer semantics.
 */
data class SessionRuntimeState(
    val sessionId: SessionId,
    val stagePlan: CompiledStagePlan,
    val status: BrewSessionStatus,
    val currentStageIndex: Int?,
    val stageProgress: List<StageRuntimeProgress>,
    val totalActiveElapsedMillis: Long = 0L,
    val activeClockAnchor: ActiveClockAnchor? = null,
    val startedAtWallClockMillis: Long? = null,
    val pausedAtWallClockMillis: Long? = null,
    val endedAtWallClockMillis: Long? = null,
    val updatedAtWallClockMillis: Long? = null,
    val revision: Long = 0L,
    /** A bounded history is sufficient because durable effect keys protect output after eviction. */
    val processedEventIds: List<SessionEventId> = emptyList(),
    /** Persist this outbox before delivering any externally visible effect. */
    val pendingEffects: List<PendingSessionEffect> = emptyList(),
    /** Delivery acknowledgements make restoration/retry safe. */
    val acknowledgedEffectIds: List<SessionEffectId> = emptyList(),
    val lastClockReconciliation: ClockReconciliation? = null,
) {
    val currentStage: CompiledBrewStage?
        get() = currentStageIndex?.let(stagePlan.stages::getOrNull)

    val currentProgress: StageRuntimeProgress?
        get() = currentStageIndex?.let(stageProgress::getOrNull)

    companion object {
        fun create(
            sessionId: SessionId,
            stagePlan: CompiledStagePlan,
        ): SessionRuntimeState {
            require(stagePlan.stages.isNotEmpty()) { "A brew session needs at least one compiled stage" }
            return SessionRuntimeState(
                sessionId = sessionId,
                stagePlan = stagePlan,
                status = BrewSessionStatus.READY,
                currentStageIndex = 0,
                stageProgress = stagePlan.stages.map { StageRuntimeProgress(StageRunStatus.PENDING) },
            )
        }
    }
}

enum class StageAlertKind {
    STARTED,
    COMPLETED,
}

/**
 * Every pending effect carries the stable idempotency key consumed by its
 * adapter. A crash after delivery but before acknowledgement is therefore safe
 * to retry: notification, scheduler, log, and inventory adapters must use the
 * same key as their idempotency/deduplication token.
 */
sealed interface SessionEffect {
    val effectId: SessionEffectId

    data class PersistRuntime(
        override val effectId: SessionEffectId,
        val snapshot: SessionRuntimeState,
    ) : SessionEffect
}

sealed interface PendingSessionEffect : SessionEffect {
    data class StageAlert(
        override val effectId: SessionEffectId,
        val sessionId: SessionId,
        val stageInstanceId: StageInstanceId,
        val kind: StageAlertKind,
    ) : PendingSessionEffect

    data class ScheduleStageDeadline(
        override val effectId: SessionEffectId,
        val sessionId: SessionId,
        val stageInstanceId: StageInstanceId,
        val scheduleToken: String,
        val dueAtWallClockMillis: Long,
    ) : PendingSessionEffect

    data class CancelStageDeadline(
        override val effectId: SessionEffectId,
        val sessionId: SessionId,
        val scheduleToken: String,
    ) : PendingSessionEffect

    /** A repository adapter must make this one transaction with inventory mutation. */
    data class FinalizeBrewLog(
        override val effectId: SessionEffectId,
        val sessionId: SessionId,
    ) : PendingSessionEffect

    data class CancelSessionWork(
        override val effectId: SessionEffectId,
        val sessionId: SessionId,
    ) : PendingSessionEffect
}

/** Android adapters implement this later; the domain model never imports WorkManager. */
interface LongSessionScheduler {
    fun schedule(
        sessionId: SessionId,
        stageInstanceId: StageInstanceId,
        scheduleToken: String,
        dueAtWallClockMillis: Long,
        effectId: SessionEffectId,
    )

    fun cancel(
        sessionId: SessionId,
        scheduleToken: String,
        effectId: SessionEffectId,
    )
}

sealed interface SessionEvent {
    val eventId: SessionEventId?

    data class Start(override val eventId: SessionEventId? = null) : SessionEvent

    data class Pause(override val eventId: SessionEventId? = null) : SessionEvent

    data class Resume(override val eventId: SessionEventId? = null) : SessionEvent

    data class Tick(override val eventId: SessionEventId? = null) : SessionEvent

    /** Explicitly reconcile persisted wall time after recovery or a clock change. */
    data class Reconcile(override val eventId: SessionEventId? = null) : SessionEvent

    /** Rebuilds the in-process monotonic anchor from a persisted runtime snapshot. */
    data class Restore(override val eventId: SessionEventId? = null) : SessionEvent

    data class ManualAdvance(override val eventId: SessionEventId? = null) : SessionEvent

    data class RecordActual(
        val value: StageActualValue,
        override val eventId: SessionEventId? = null,
    ) : SessionEvent

    data class RecordObservation(
        val observationId: StageObservationId,
        override val eventId: SessionEventId? = null,
    ) : SessionEvent

    data class RecordMarker(
        val markerId: StageMarkerId,
        override val eventId: SessionEventId? = null,
    ) : SessionEvent

    data class Skip(override val eventId: SessionEventId? = null) : SessionEvent

    data class Cancel(override val eventId: SessionEventId? = null) : SessionEvent

    /** Completes the eligible final stage and emits one durable log request. */
    data class Finish(override val eventId: SessionEventId? = null) : SessionEvent

    data class AcknowledgeEffect(
        val effectIdToAcknowledge: SessionEffectId,
        override val eventId: SessionEventId? = null,
    ) : SessionEvent
}

data class SessionTransition(
    val state: SessionRuntimeState,
    /** PersistRuntime is always first when state changes. */
    val effects: List<SessionEffect>,
    val wasIgnored: Boolean = false,
) {
    companion object {
        fun unchanged(state: SessionRuntimeState): SessionTransition = SessionTransition(
            state = state,
            effects = emptyList(),
            wasIgnored = true,
        )
    }
}
