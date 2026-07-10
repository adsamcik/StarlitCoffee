package com.adsamcik.starlitcoffee.data.network.llm

/**
 * Request payload for the post-translation **refinement** LLM pass.
 *
 * # Architecture
 *
 * This pass runs AFTER the text/vision/combine passes have produced canonical
 * English field values. For each field, the scan pipeline fuzzy-matches the
 * current English value against the curated (English-only) coffee vocabulary and
 * offers the model a short list of close known values ([suggestionsByField]).
 * The model decides, per field, whether to keep its current value or adopt a
 * suggestion that is a cleaner canonical form of the SAME thing — it MAY or MAY
 * NOT use them (advisory, never forced).
 *
 * Because it consumes already-English output, it works for every source
 * language with no multilingual vocabulary. Field keys are the pipeline's
 * internal field names (e.g. `origin`, `processType`, `roastLevel`).
 */
data class LlmRefineRequest(
    /** Internal field names the refine pass should reconsider and return. */
    val fieldsNeeded: Set<String>,
    /** Field → the current canonical-English value from the earlier passes. */
    val currentFields: Map<String, String>,
    /** Field → ranked close known values from the curated vocabulary (advisory). */
    val suggestionsByField: Map<String, List<String>>,
    /**
     * The merged OCR text, optional. Lets the model sanity-check a suggestion
     * against the original label instead of adopting one blindly.
     */
    val rawOcrText: String? = null,
)
