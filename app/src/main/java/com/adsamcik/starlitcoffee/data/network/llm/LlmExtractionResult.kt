package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.RecognitionCapability

/**
 * Sealed result type for LLM extraction — forces callers to handle all cases.
 */
sealed interface LlmExtractionResult {
    /** LLM successfully extracted field candidates. */
    data class Success(
        /** Field candidates that plug directly into the consensus engine via submitEnrichment(). */
        val fieldCandidates: List<BagFieldCandidate>,
        /** Tokens consumed for budget tracking (0 if unknown). */
        val tokensUsed: Int = 0,
    ) : LlmExtractionResult

    /** A typed recovery route; diagnostic text must never determine consent. */
    data class Unavailable(
        val reason: String,
        val capability: RecognitionCapability = RecognitionCapability.TEMPORARILY_UNAVAILABLE,
    ) : LlmExtractionResult

    /** LLM call failed (network error, rate limit, etc.). */
    data class Failed(
        val error: String,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
    ) : LlmExtractionResult
}
