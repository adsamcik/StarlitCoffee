package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the final combine/reconciliation pass orchestration in
 * [BagPhotoExtractor.runCombineEnrichmentIfNeeded]: gating (both passes must
 * contribute, provider must support it), authoritative-source exclusion, and
 * the sourceCount tie-breaker on the returned candidates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BagPhotoExtractorCombineTest {

    private class CombineProvider(
        private val supports: Boolean = true,
        private val available: Boolean = true,
        private val response: List<BagFieldCandidate> = emptyList(),
    ) : LlmInferenceProvider {
        var lastRequest: LlmCombineRequest? = null
            private set
        var combineCalls = 0
            private set

        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult =
            LlmExtractionResult.Unavailable("stub")

        override fun isAvailable(): Boolean = available
        override fun supportsCombine(): Boolean = supports

        override suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult {
            combineCalls++
            lastRequest = request
            return LlmExtractionResult.Success(fieldCandidates = response)
        }
    }

    private fun llmCandidate(field: String, value: String) = BagFieldCandidate(
        fieldName = field,
        value = value,
        sourceType = BagFieldSourceType.LLM,
        confidenceHint = BagFieldConfidence.HIGH,
    )

    private fun lookupCandidate(field: String, value: String) = BagFieldCandidate(
        fieldName = field,
        value = value,
        sourceType = BagFieldSourceType.BARCODE_LOOKUP,
        confidenceHint = BagFieldConfidence.HIGH,
    )

    private fun extractor(provider: LlmInferenceProvider) =
        BagPhotoExtractor(appContext = null, llmProvider = provider)

    @Test
    fun `combine reconciles both passes and tags result with sourceCount tie-breaker`() = runTest {
        val provider = CombineProvider(response = listOf(llmCandidate("name", "Tumbaga")))
        val text = listOf(llmCandidate("name", "Tunbaga"))
        val vision = listOf(llmCandidate("name", "Tumbaga"))

        val result = extractor(provider).runCombineEnrichmentIfNeeded(
            textPassCandidates = text,
            visionPassCandidates = vision,
            allCandidates = text + vision,
            knownFieldValues = KnownFieldValues.EMPTY,
        )

        assertEquals(1, provider.combineCalls)
        assertEquals(listOf("Tumbaga"), result.map { it.value })
        assertTrue("Combine candidate must reinforce its group", result.all { it.sourceCount == 2 })
        assertEquals(setOf("name"), provider.lastRequest?.fieldsNeeded)
    }

    @Test
    fun `combine is skipped when the vision pass produced nothing`() = runTest {
        val provider = CombineProvider(response = listOf(llmCandidate("name", "Tumbaga")))

        val result = extractor(provider).runCombineEnrichmentIfNeeded(
            textPassCandidates = listOf(llmCandidate("name", "Tumbaga")),
            visionPassCandidates = emptyList(),
            allCandidates = listOf(llmCandidate("name", "Tumbaga")),
            knownFieldValues = KnownFieldValues.EMPTY,
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.combineCalls)
    }

    @Test
    fun `combine is skipped when the provider does not support it`() = runTest {
        val provider = CombineProvider(supports = false, response = listOf(llmCandidate("name", "X")))

        val result = extractor(provider).runCombineEnrichmentIfNeeded(
            textPassCandidates = listOf(llmCandidate("name", "A")),
            visionPassCandidates = listOf(llmCandidate("name", "B")),
            allCandidates = listOf(llmCandidate("name", "A")),
            knownFieldValues = KnownFieldValues.EMPTY,
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.combineCalls)
    }

    @Test
    fun `combine excludes fields already pinned by an authoritative source`() = runTest {
        val provider = CombineProvider(response = listOf(llmCandidate("name", "Tumbaga")))
        // name DISAGREES across passes (so it needs reconciling); origin agrees
        // with a barcode lookup (authoritatively settled -> must be excluded).
        val text = listOf(llmCandidate("name", "Tunbaga"), llmCandidate("origin", "Kolumbie"))
        val vision = listOf(llmCandidate("name", "Tumbaga"), llmCandidate("origin", "Colombia"))
        val all = text + vision + lookupCandidate("origin", "Colombia")

        extractor(provider).runCombineEnrichmentIfNeeded(
            textPassCandidates = text,
            visionPassCandidates = vision,
            allCandidates = all,
            knownFieldValues = KnownFieldValues.EMPTY,
        )

        val requested = provider.lastRequest?.fieldsNeeded
        assertEquals(setOf("name"), requested)
        assertNull("origin must not be sent to combine", requested?.firstOrNull { it == "origin" })
    }

    @Test
    fun `combine skips fields where both passes already agree`() = runTest {
        val provider = CombineProvider(response = emptyList())
        // Both passes agree on name -> consensus -> nothing to reconcile -> skip.
        val text = listOf(llmCandidate("name", "Tumbaga"))
        val vision = listOf(llmCandidate("name", "Tumbaga"))

        val result = extractor(provider).runCombineEnrichmentIfNeeded(
            textPassCandidates = text,
            visionPassCandidates = vision,
            allCandidates = text + vision,
            knownFieldValues = KnownFieldValues.EMPTY,
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.combineCalls)
    }
}
