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
