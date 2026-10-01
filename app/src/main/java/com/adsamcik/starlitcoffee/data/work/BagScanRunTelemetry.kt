package com.adsamcik.starlitcoffee.data.work

import android.os.SystemClock
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticFailure
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticOutcome
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticStage
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleEvent
import com.adsamcik.starlitcoffee.domain.scandiagnostics.scanCorrelationKey
import com.adsamcik.starlitcoffee.scan.observability.ScanAnalyticsTracker
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoReviewUris
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.ScanProgress

/** One execution attempt; replay is explicit and no diagnostic state is stored in the draft. */
internal class BagScanRunTelemetry(
    val identity: ScanDiagnosticContext,
    private val runAttempt: Int = 0,
    private val aiRequested: Boolean? = null,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val emit: (ScanLifecycleDiagnostic) -> Unit = ScanAnalyticsTracker::trackLifecycle,
) {
    private val startedAt = clock()
    private val stages = mutableSetOf<ScanDiagnosticStage>()
    private var stagesPlanned = 0
    private var fieldsResolved = 0
    private var completed = false

    fun start(queueMs: Long = 0L) {
        if (!completed) record(ScanLifecycleEvent.STARTED, queueMs = queueMs)
    }

    fun progress(progress: ScanProgress) {
        if (completed) return
        stagesPlanned = maxOf(stagesPlanned, progress.stepCount)
        val stage = ScanDiagnosticStage.entries.firstOrNull { it.name == progress.stage.name } ?: return
        if (stages.add(stage)) record(ScanLifecycleEvent.STAGE_STARTED, stage = stage)
    }

    fun partial(result: BagPhotoProcessingResult) {
        if (completed) return
        val count = recognizedFieldCount(result)
        if (count != fieldsResolved) {
            fieldsResolved = count
            record(ScanLifecycleEvent.PARTIAL_RESULTS)
        }
    }

    fun finish(result: BagPhotoProcessingResult, successful: Boolean, replayed: Boolean = false) {
        if (completed) return
        completed = true
        fieldsResolved = recognizedFieldCount(result)
        record(
            event = if (replayed) ScanLifecycleEvent.RESULT_REPLAYED else ScanLifecycleEvent.COMPLETED,
            outcome = bagScanDiagnosticOutcome(result, successful),
            failure = bagScanDiagnosticFailure(result, successful),
        )
    }

    fun cancel() {
        if (completed) return
        completed = true
        record(ScanLifecycleEvent.CANCELLED, outcome = ScanDiagnosticOutcome.CANCELLED)
    }

    private fun record(
        event: ScanLifecycleEvent,
        queueMs: Long = 0L,
        stage: ScanDiagnosticStage? = null,
        outcome: ScanDiagnosticOutcome? = null,
        failure: ScanDiagnosticFailure? = null,
    ) {
        recordBagScanLifecycle(
            ScanLifecycleDiagnostic(
                context = identity,
                event = event,
                elapsedMs = (clock() - startedAt).coerceAtLeast(0L),
                queueMs = queueMs.coerceAtLeast(0L),
                fieldsResolved = fieldsResolved,
                stage = stage,
                stagesObserved = stages.size,
                stagesPlanned = stagesPlanned,
                runAttempt = runAttempt,
                aiRequested = aiRequested,
                outcome = outcome,
                failure = failure,
            ),
            emit,
        )
    }
}

internal fun bagScanDiagnosticContext(
    sessionId: String?,
    generationId: String?,
    workId: String?,
    photoUrisCsv: String?,
    reviewContext: BagReviewContext?,
): ScanDiagnosticContext = ScanDiagnosticContext(
    sessionKey = scanCorrelationKey(sessionId),
    generationKey = scanCorrelationKey(generationId),
    workKey = scanCorrelationKey(workId),
    photoCount = BagPhotoReviewUris.parse(photoUrisCsv).size,
    mode = when (reviewContext?.mode) {
        BagReviewMode.ADD_NEW -> ScanDiagnosticMode.ADD_NEW
        BagReviewMode.RESCAN -> ScanDiagnosticMode.RESCAN
        null -> ScanDiagnosticMode.UNKNOWN
    },
)

internal fun recognizedFieldCount(result: BagPhotoProcessingResult): Int =
    BagPhotoScanSupport.sanitizeFieldEvidence(result.fieldEvidence)
        .count { (key, _) -> BagDraftField.entries.any { it.wireName == key } }

internal fun bagScanDiagnosticOutcome(
    result: BagPhotoProcessingResult,
    successful: Boolean,
): ScanDiagnosticOutcome = when {
    !successful -> ScanDiagnosticOutcome.ERROR
    recognizedFieldCount(result) == 0 -> ScanDiagnosticOutcome.NO_RESULT
    result.llmStatus == LlmEnrichmentStatus.SUCCEEDED || result.llmStatus == LlmEnrichmentStatus.NOT_RUN ->
        ScanDiagnosticOutcome.COMPLETE
    else -> ScanDiagnosticOutcome.PARTIAL
}

internal fun bagScanDiagnosticFailure(
    result: BagPhotoProcessingResult,
    successful: Boolean,
): ScanDiagnosticFailure? = if (!successful) ScanDiagnosticFailure.WORKER_FAILED else when (result.llmStatus) {
    LlmEnrichmentStatus.TIMED_OUT -> ScanDiagnosticFailure.TIMED_OUT
    LlmEnrichmentStatus.AUTHORIZATION_REQUIRED -> ScanDiagnosticFailure.AUTHORIZATION_REQUIRED
    LlmEnrichmentStatus.SETUP_REQUIRED -> ScanDiagnosticFailure.MODEL_SETUP_REQUIRED
    LlmEnrichmentStatus.UNAVAILABLE -> ScanDiagnosticFailure.RECOGNITION_UNAVAILABLE
    LlmEnrichmentStatus.FAILED -> ScanDiagnosticFailure.INFERENCE_FAILED
    LlmEnrichmentStatus.SUCCEEDED, LlmEnrichmentStatus.NOT_RUN -> null
}

/** A diagnostics sink failure must not fail an enqueue, draft write, or extraction. */
internal fun recordBagScanLifecycle(
    diagnostic: ScanLifecycleDiagnostic,
    emit: (ScanLifecycleDiagnostic) -> Unit = ScanAnalyticsTracker::trackLifecycle,
) {
    runCatching { emit(diagnostic) }
}

internal fun bagDraftPhaseDiagnostic(
    before: BagScanDraft,
    after: BagScanDraft,
): ScanLifecycleDiagnostic? {
    if (!before.isActive || before.phase == after.phase) return null
    val event = when (after.phase) {
        BagDraftPhase.REVIEWING -> ScanLifecycleEvent.REVIEW_OPENED
        BagDraftPhase.BACKGROUND -> ScanLifecycleEvent.BACKGROUNDED
        BagDraftPhase.SAVED -> ScanLifecycleEvent.SAVED
        BagDraftPhase.DISCARDED -> ScanLifecycleEvent.DISCARDED
        BagDraftPhase.CAPTURING -> return null
    }
    return ScanLifecycleDiagnostic(
        context = bagScanDiagnosticContext(
            before.sessionId, before.generationId, before.workId,
            before.photoUris.joinToString(","), before.reviewContext,
        ),
        event = event,
        elapsedMs = (after.updatedAtMillis - before.createdAtMillis).coerceAtLeast(0L),
        fieldsResolved = before.fields.values.count { !it.value.isNullOrBlank() },
    )
}
