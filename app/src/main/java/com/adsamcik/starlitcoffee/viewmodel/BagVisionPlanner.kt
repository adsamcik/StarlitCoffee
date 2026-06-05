package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType

/**
 * Pure decision logic for the bag-scan **vision second pass**.
 *
 * After the OCR + text-LLM pass resolves field evidence, this planner decides
 * (a) whether a multimodal vision pass is worth running and for which fields,
 * and (b) how to fold the vision results back in without letting a best-effort
 * visual guess override an authoritative value (barcode/QR lookup, consensus,
 * or a confident OCR read).
 *
 * Kept Android-free and side-effect-free so it is unit-testable in isolation.
 */
internal object BagVisionPlanner {

    /**
     * Fields whose value is frequently **visual** on a label (e.g. a roast
     * level shown as a filled-dot scale, or a decaf icon) and which OCR text
     * therefore often can't capture. v1 deliberately scopes the (slow, and —
     * per MindlayerLlmInferenceProvider — crash-risky) multimodal call to these
     * fields only, rather than re-running vision for every commonly-absent
     * field on a normal label.
     */
    val VISION_WORTHY_FIELDS: Set<String> = setOf("roastLevel", "isDecaf")

    /** Sources whose value must never be replaced by a best-effort vision guess. */
    private val AUTHORITATIVE_SOURCES: Set<BagFieldSourceType> = setOf(
        BagFieldSourceType.BARCODE_LOOKUP,
        BagFieldSourceType.LOCAL_BARCODE_MATCH,
        BagFieldSourceType.QR_LINK_LOOKUP,
        BagFieldSourceType.OBSERVED_BARCODE_STEM,
        BagFieldSourceType.CONSENSUS,
    )

    /**
     * Which [VISION_WORTHY_FIELDS] still warrant a vision pass.
     *
     * A field is considered already settled (no vision needed) only when it is
     * resolved by an authoritative source, or by a confident OCR read. A
     * text-LLM guess about a *visual* field is NOT trusted — the resolver
     * weights LLM candidates highly, so a hallucinated roast level can resolve
     * to HIGH; for visual fields we re-verify against the image rather than
     * trust text reasoning. Returns the still-uncertain fields (empty = skip
     * the vision pass entirely).
     */
    fun selectVisionFields(resolved: Map<String, BagFieldEvidence>): Set<String> =
        VISION_WORTHY_FIELDS.filterNot { field -> isSettledForVision(resolved[field]) }.toSet()

    /**
     * Drop any vision candidate that would override an already-settled field;
     * keep the ones that fill a gap or refine a weak/text-only value. This is
     * the safety net that stops a vision hallucination from clobbering a
     * barcode/OCR-resolved value even though vision candidates carry the
     * (highly weighted) LLM source type.
     */
    fun filterVisionCandidates(
        visionCandidates: List<BagFieldCandidate>,
        resolved: Map<String, BagFieldEvidence>,
    ): List<BagFieldCandidate> =
        visionCandidates.filterNot { candidate -> isSettledForVision(resolved[candidate.fieldName]) }

    private fun isSettledForVision(evidence: BagFieldEvidence?): Boolean {
        if (evidence == null) return false
        if (evidence.sourceType in AUTHORITATIVE_SOURCES) return true
        return evidence.sourceType == BagFieldSourceType.OCR &&
            evidence.confidence == BagFieldConfidence.HIGH
    }
}
