package com.adsamcik.starlitcoffee.data.network.llm

/**
 * Stub implementation for development and testing.
 * Returns [LlmExtractionResult.Unavailable] by default.
 * For tests, use [withCannedResponse] to configure specific responses.
 */
class StubLlmInferenceProvider : LlmInferenceProvider {
    private var cannedResult: LlmExtractionResult =
        LlmExtractionResult.Unavailable("LLM not configured")

    override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult {
        return cannedResult
    }

    override fun isAvailable(): Boolean = cannedResult is LlmExtractionResult.Success

    /** For testing: configure a canned response. */
    fun withCannedResponse(result: LlmExtractionResult): StubLlmInferenceProvider {
        cannedResult = result
        return this
    }
}
