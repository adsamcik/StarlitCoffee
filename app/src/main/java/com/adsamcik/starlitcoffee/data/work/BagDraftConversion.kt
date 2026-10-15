package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.RecognitionRunState

/** Keep the editable snapshot, but revoke all work that belonged to the former target. */
internal fun BagScanDraft.convertRescanToNewBag(
    expectedGenerationId: String?,
    newGenerationId: String,
    nowMillis: Long,
): BagScanDraft {
    if (!isActive || reviewContext.mode != BagReviewMode.RESCAN || generationId != expectedGenerationId) return this
    require(newGenerationId != generationId) { "Conversion must revoke the rescan generation" }
    val result = resultJson?.let { runCatching { decodeBagExtractionResult(it) }.getOrNull() }
    return copy(
        generationId = newGenerationId,
        updatedAtMillis = nowMillis.coerceAtLeast(updatedAtMillis + 1L),
        phase = BagDraftPhase.REVIEWING,
        recognitionCapability = RecognitionCapability.READY,
        recognitionRunState = RecognitionRunState.COMPLETE,
        workId = null,
        reviewContext = BagReviewContext.addNew(),
        resultJson = result?.copy(llmStatus = LlmEnrichmentStatus.NOT_RUN)?.encodeToStoredJson(),
        pendingExternalSetupReason = null,
    )
}
