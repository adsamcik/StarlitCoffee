package com.adsamcik.starlitcoffee.data.network.llm

/**
 * API-agnostic contract for LLM-based bag field extraction.
 *
 * Implementations wrap a specific LLM API (OpenAI, Gemini, local, …).
 * The scan pipeline calls [extractBagFields] and folds the returned
 * candidates into the consensus engine like any other source.
 *
 * ## Threading contract
 *
 * Implementations **must be safe to call from any dispatcher, including
 * [kotlinx.coroutines.Dispatchers.Main]**. The scan pipeline collects an
 * escalation flow on `viewModelScope` (Main by default), so an implementation
 * that hands off to a blocking SDK is responsible for switching to a
 * background dispatcher internally — typically `withContext(Dispatchers.IO)`
 * around the entire body.
 *
 * Failing to honour this caused a real production regression: the on-device
 * Mindlayer SDK does not switch dispatchers internally, and its bitmap
 * marshalling + Binder transactions ran on Main, which delayed CameraX
 * `image.close()` and visibly froze the camera preview during inference.
 * See `MindlayerLlmInferenceProvider.extractBagFields` for the reference
 * pattern.
 *
 * ## Cancellation contract
 *
 * Implementations **must let `CancellationException` propagate**. Catching
 * it (e.g. via a generic `catch (e: Exception)`) and returning a normal
 * `Failed`/`Unavailable` result lets cancelled work mutate state in the
 * caller after the session has been torn down — see
 * `LlmEscalationCoordinator` for the consumer that relies on this.
 */
interface LlmInferenceProvider {
    /**
     * Send a bag label image + context to the LLM for field extraction.
     * Returns structured field candidates that plug into the consensus engine.
     *
     * Must be safe to call from any dispatcher — see the class-level
     * "Threading contract" section. Must let `CancellationException`
     * propagate — see "Cancellation contract".
     */
    suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult

    /**
     * Whether this provider can run a multimodal (vision) second pass over the
     * label image via [extractBagFieldsWithVision]. Default `false` so callers
     * skip vision for text-only providers. Should be cheap/non-blocking.
     */
    fun supportsVision(): Boolean = false

    /**
     * Second-pass extraction that looks at the label **image** (not just OCR
     * text) to recover visual-only details — e.g. a roast-level dot scale that
     * has no textual form — and to correct earlier mistakes, given the fields
     * already known (`request.existingFields`) and the ones still needed
     * (`request.fieldsNeeded`). Honours the same threading and cancellation
     * contracts as [extractBagFields].
     *
     * Default returns [LlmExtractionResult.Unavailable] for providers without
     * vision support; the orchestration only calls this when [supportsVision]
     * is true.
     */
    suspend fun extractBagFieldsWithVision(request: LlmExtractionRequest): LlmExtractionResult =
        LlmExtractionResult.Unavailable("Vision extraction not supported by this provider")

    /**
     * Whether this provider can run the final text-only **combine** pass via
     * [combineBagFields]. Default `false`. Unlike vision, combine is text-only
     * and so is NOT limited by the per-process multimodal-inference budget.
     * Should be cheap/non-blocking.
     */
    fun supportsCombine(): Boolean = false

    /**
     * Final reconciliation pass: given the structured OUTPUTS of the earlier
     * text and vision passes (and known-value grounding), select the single
     * best value per requested field — especially proper-noun identity fields
     * (`name` / `roaster`) where the passes disagree. Returns the chosen values
     * as [BagFieldCandidate]s that fold back into the consensus engine.
     *
     * Honours the same threading and cancellation contracts as
     * [extractBagFields]. Default returns [LlmExtractionResult.Unavailable];
     * the orchestration only calls this when [supportsCombine] is true.
     */
    suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult =
        LlmExtractionResult.Unavailable("Combine pass not supported by this provider")

    /**
     * Whether the LLM provider is configured and available.
     * If false, callers should skip LLM enrichment gracefully.
     *
     * Should be cheap and non-blocking — typically reads a cached
     * connection state. Callers may invoke this from Main.
     */
    fun isAvailable(): Boolean

    /**
     * Optionally pre-warm the provider so the first [extractBagFields] call
     * doesn't pay the full SDK initialization cost (binder bind, model load,
     * GPU shader compilation, etc.). Default is a no-op for stateless or
     * cheap-to-init providers; overrides should return as soon as the
     * provider is ready.
     *
     * Called once per scan session. Must honour the same threading and
     * cancellation contracts as [extractBagFields].
     */
    suspend fun prewarm() {
        // No-op default — override for providers with non-trivial init cost.
    }
}
