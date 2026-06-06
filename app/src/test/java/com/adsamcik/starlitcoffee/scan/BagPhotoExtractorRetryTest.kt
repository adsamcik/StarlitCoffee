package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the silent auto-recovery for transient bag-photo LLM failures: a
 * retryable failure is retried (up to the attempt cap) before the user ever sees
 * a result, a non-retryable failure is not, and a recovered run reports success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BagPhotoExtractorRetryTest {

    private class SequencedProvider(
        private val results: List<LlmExtractionResult>,
    ) : LlmInferenceProvider {
        var calls = 0
            private set

        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult {
            val result = results[minOf(calls, results.size - 1)]
            calls++
            return result
        }

        override fun isAvailable() = true
    }

    private fun extractor(provider: LlmInferenceProvider) =
        BagPhotoExtractor(appContext = null, llmProvider = provider)

    private fun request() = LlmExtractionRequest(
        imageBytes = ByteArray(0),
        existingFields = emptyMap(),
        fieldsNeeded = setOf("name"),
        rawOcrText = "Some OCR text",
    )

    private fun failure(retryable: Boolean) = LlmExtractionResult.Failed("boom", retryable = retryable)

    private fun success() = LlmExtractionResult.Success(fieldCandidates = emptyList<BagFieldCandidate>())

    @Test
    fun `retryable failure then success recovers silently`() = runTest {
        val provider = SequencedProvider(listOf(failure(retryable = true), success()))

        val result = extractor(provider).runLlmExtractionWithRetry(request())

        assertTrue(result is LlmExtractionResult.Success)
        assertEquals(2, provider.calls)
    }

    @Test
    fun `gives up after the attempt cap when every retry fails`() = runTest {
        val provider = SequencedProvider(listOf(failure(retryable = true)))

        val result = extractor(provider).runLlmExtractionWithRetry(request())

        assertTrue(result is LlmExtractionResult.Failed)
        assertEquals(3, provider.calls)
    }

    @Test
    fun `non-retryable failure is not retried`() = runTest {
        val provider = SequencedProvider(listOf(failure(retryable = false), success()))

        val result = extractor(provider).runLlmExtractionWithRetry(request())

        assertTrue(result is LlmExtractionResult.Failed)
        assertEquals(1, provider.calls)
    }
}
