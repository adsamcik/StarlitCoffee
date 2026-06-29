package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.util.KnownFieldValues

/**
 * Request payload for the final **combine / reconciliation** LLM pass.
 *
 * # Architecture
 *
 * The combine pass is the last stage of the bag-scan pipeline. Per the design,
 * it consumes ONLY the structured OUTPUTS of the earlier LLM stages — the
 * text-extraction pass ([textPassFields]) and the vision pass
 * ([visionPassFields]) — plus the user's known-value vocabulary for grounding.
 * It deliberately does **not** re-read the raw OCR text: the prior passes have
 * already interpreted it, and the combine model's job is to pick the single
 * best value per field (especially proper-noun identity fields like
 * `name` / `roaster`) when the two passes disagree.
 *
 * Because it is text-only it is NOT subject to the one-multimodal-inference
 * per-process budget that constrains the vision pass.
 *
 * Field keys in [textPassFields] / [visionPassFields] are the pipeline's
 * internal field names (e.g. `name`, `roaster`, `processType`, `roastLevel`).
 */
data class LlmCombineRequest(
    /** Internal field names the combine pass should reconcile and return. */
    val fieldsNeeded: Set<String>,
    /** Field -> value produced by the first (OCR-grounded) text LLM pass. */
    val textPassFields: Map<String, String>,
    /** Field -> value produced by the second (vision) LLM pass. */
    val visionPassFields: Map<String, String>,
    /** User vocabulary (roasters, origins, varieties, …) for grounding/disambiguation. */
    val knownFieldValues: KnownFieldValues? = null,
    /**
     * The merged OCR text the text pass read from. Optional context so the
     * combine model can break proper-noun ties (name vs roaster) by consulting
     * the original tokens instead of guessing between the two passes' values.
     */
    val rawOcrText: String? = null,
)
