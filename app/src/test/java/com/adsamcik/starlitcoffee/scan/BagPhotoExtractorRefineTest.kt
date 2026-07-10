package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmRefineRequest
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabulary
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the post-translation refinement pass in
 * [BagPhotoExtractor.runRefineEnrichmentIfNeeded]: gating (provider support,
 * vocabulary present, something to suggest), authoritative-source exclusion, and
 * that close vocabulary candidates are offered to the LLM per field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BagPhotoExtractorRefineTest {

    private class RefineProvider(
        private val supports: Boolean = true,
        private val available: Boolean = true,
        private val response: List<BagFieldCandidate> = emptyList(),
    ) : LlmInferenceProvider {
        var lastRequest: LlmRefineRequest? = null
            private set
        var refineCalls = 0
            private set

        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult =
            LlmExtractionResult.Unavailable("stub")

        override fun isAvailable(): Boolean = available
        override fun supportsRefine(): Boolean = supports

        override suspend fun refineBagFields(request: LlmRefineRequest): LlmExtractionResult {
            refineCalls++
            lastRequest = request
            return LlmExtractionResult.Success(fieldCandidates = response)
        }
    }

    private val vocabulary = CoffeeFilterVocabulary(
        origins = listOf(
            CoffeeVocabularyEntry("Colombia"),
            CoffeeVocabularyEntry("Ethiopia"),
            CoffeeVocabularyEntry("Kenya"),
        ),
        processTypes = listOf(CoffeeVocabularyEntry("Washed", listOf("Wet Process"))),
        varieties = listOf(CoffeeVocabularyEntry("Geisha", listOf("Gesha"))),
        roastLevels = listOf(CoffeeVocabularyEntry("Light")),
    )

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
        BagPhotoExtractor(appContext = null, llmProvider = provider, vocabularyProvider = { vocabulary })

    @Test
    fun `refine offers close vocabulary candidates and folds the chosen value`() = runTest {
        val provider = RefineProvider(response = listOf(llmCandidate("origin", "Colombia")))

        val result = extractor(provider).runRefineEnrichmentIfNeeded(
            allCandidates = listOf(llmCandidate("origin", "Columbia")),
        )

        assertEquals(1, provider.refineCalls)
        assertEquals(setOf("origin"), provider.lastRequest?.fieldsNeeded)
        assertEquals(listOf("Colombia"), provider.lastRequest?.suggestionsByField?.get("origin"))
        assertEquals(listOf("Colombia"), result.map { it.value })
    }

    @Test
    fun `refine is skipped when the value is already canonical`() = runTest {
        val provider = RefineProvider(response = listOf(llmCandidate("origin", "Colombia")))

        val result = extractor(provider).runRefineEnrichmentIfNeeded(
            allCandidates = listOf(llmCandidate("origin", "Colombia")),
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.refineCalls)
    }

    @Test
    fun `refine is skipped when the provider does not support it`() = runTest {
        val provider = RefineProvider(supports = false, response = listOf(llmCandidate("origin", "Colombia")))

        val result = extractor(provider).runRefineEnrichmentIfNeeded(
            allCandidates = listOf(llmCandidate("origin", "Columbia")),
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.refineCalls)
    }

    @Test
    fun `refine is skipped without a vocabulary`() = runTest {
        val provider = RefineProvider(response = listOf(llmCandidate("origin", "Colombia")))
        val extractor = BagPhotoExtractor(
            appContext = null,
            llmProvider = provider,
            vocabularyProvider = { null },
        )

        val result = extractor.runRefineEnrichmentIfNeeded(
            allCandidates = listOf(llmCandidate("origin", "Columbia")),
        )

        assertTrue(result.isEmpty())
        assertEquals(0, provider.refineCalls)
    }

    @Test
    fun `refine excludes fields already pinned by an authoritative source`() = runTest {
        val provider = RefineProvider(response = emptyList())

        // variety is pinned by a barcode lookup as "Gesha" — a variant that WOULD
        // otherwise get the suggestion "Geisha"; being authoritative excludes it.
        extractor(provider).runRefineEnrichmentIfNeeded(
            allCandidates = listOf(lookupCandidate("variety", "Gesha")),
        )

        assertEquals(0, provider.refineCalls)
    }
}
