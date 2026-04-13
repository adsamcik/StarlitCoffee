package com.adsamcik.starlitcoffee.data.network.llm

/**
 * API-agnostic contract for LLM-based bag field extraction.
 *
 * Implementations wrap a specific LLM API (OpenAI, Gemini, local, …).
 * The scan pipeline calls [extractBagFields] and folds the returned
 * candidates into the consensus engine like any other source.
 */
interface LlmInferenceProvider {
    /**
     * Send a bag label image + context to the LLM for field extraction.
     * Returns structured field candidates that plug into the consensus engine.
     */
    suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult

    /**
     * Whether the LLM provider is configured and available.
     * If false, callers should skip LLM enrichment gracefully.
     */
    fun isAvailable(): Boolean
}
