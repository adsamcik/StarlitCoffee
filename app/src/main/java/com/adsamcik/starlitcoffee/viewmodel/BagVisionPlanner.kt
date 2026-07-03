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
     * Fields the vision pass reads from the cropped label image.
     *
     * Beyond visual-only details (a roast-level filled-dot scale, a decaf icon),
     * the cropped-label vision pass also re-reads proper-noun and concept fields
     * that OCR garbled, so the combine pass has a genuine second reading to
     * reconcile against (especially `name` / `roaster`). Structural numeric/date
     * fields (weight, altitude, dates) are left to OCR, which is more reliable on
     * exact digits. The [selectVisionFields] gate still skips any field already
     * settled authoritatively or by a confident OCR read, so a typical scan where
     * OCR + text-LLM already agree won't waste the (slow, budgeted) vision call.
     */
    val VISION_WORTHY_FIELDS: Set<String> = setOf(
        "name",
        "roaster",
        "origin",
        "region",
        "farm",
        "variety",
        "processType",
        "roastLevel",
        "tastingNotes",
        "isDecaf",
    )

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
     * Whether a field is already pinned by an authoritative non-OCR/LLM source
     * (barcode / QR / local-match / multi-source consensus). The combine pass
     * skips such fields so it can't override a trusted database/consensus value
     * with a reconciliation of the AI passes.
     */
    fun isAuthoritativelySettled(evidence: BagFieldEvidence?): Boolean =
        evidence != null && evidence.sourceType in AUTHORITATIVE_SOURCES

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
        visionCandidates.filterNot { candidate ->
            isSettledForVision(resolved[candidate.fieldName]) ||
                overridesPresentValueWithGuess(candidate, resolved[candidate.fieldName])
        }

    /**
     * Idea #7 — abstention calibration. A LOW-confidence ("uncertain") vision
     * read is a guess; don't let it overwrite a value an earlier pass already
     * produced. It may still FILL a gap (no prior value), but it can't clobber
     * one. This is what stops, e.g., a roast level guessed from style from
     * replacing a value the text pass already read.
     */
    private fun overridesPresentValueWithGuess(
        candidate: BagFieldCandidate,
        evidence: BagFieldEvidence?,
    ): Boolean =
        candidate.confidenceHint == BagFieldConfidence.LOW && evidence != null && evidence.value.isNotBlank()

    private fun isSettledForVision(evidence: BagFieldEvidence?): Boolean {
        if (evidence == null) return false
        if (evidence.sourceType in AUTHORITATIVE_SOURCES) return true
        return evidence.sourceType == BagFieldSourceType.OCR &&
            evidence.confidence == BagFieldConfidence.HIGH
    }
}
