package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmRefineRequest
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabulary
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyEntry
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BagPhotoExtractorAvailabilityTest {
    @Test
    fun `skipped unavailable provider preserves explicit recovery capability`() = runTest {
        availabilityCases.forEach { (capability, status) ->
            val provider = UnavailableProvider(capability, available = false)
            val partialStatuses = mutableListOf<LlmEnrichmentStatus>()
            val result = extractor(provider).extract(
                photoUris = listOf("file:///missing-photo.jpg"),
                knownFieldValues = KnownFieldValues.EMPTY,
                onPartialResult = { partialStatuses += it.llmStatus },
            )

            assertEquals(status, result.llmStatus)
            assertTrue(partialStatuses.contains(status))
            assertEquals(0, provider.calls)
            assertFalse(result.shouldSuggestRetake)
        }
    }

    @Test
    fun `text attempt preserves classified unavailable result without retrying`() = runTest {
        availabilityCases.forEach { (capability, status) ->
            val provider = UnavailableProvider(capability)
            val outcome = extractor(provider).tryLlmEnrichment(
                photoBytes = byteArrayOf(1),
                existingFields = emptyMap(),
                fieldsNeeded = setOf("name"),
                rawOcrText = "Label text",
                knownFieldValues = KnownFieldValues.EMPTY,
            )

            assertEquals(status, outcome.status)
            assertTrue(outcome.candidates.isEmpty())
            assertEquals(1, provider.calls)
        }
    }

    @Test
    fun `vision attempt preserves authorization setup and transient outcomes`() = runTest {
        availabilityCases.forEach { (capability, status) ->
            val provider = UnavailableProvider(capability)
            val outcome = extractor(provider).tryVisionLlmEnrichment(
                photoBytes = byteArrayOf(1),
                existingFields = emptyMap(),
                fieldsNeeded = setOf("name"),
                knownFieldValues = KnownFieldValues.EMPTY,
            )

            assertEquals(status, outcome.status)
            assertTrue(outcome.candidates.isEmpty())
            assertEquals(1, provider.calls)
        }
    }

    @Test
    fun `combine retains explicit blockers after earlier passes produced values`() = runTest {
        availabilityCases.forEach { (capability, status) ->
            val provider = UnavailableProvider(capability)
            val text = listOf(candidate("name", "First reading"))
            val vision = listOf(candidate("name", "Other reading"))
            val outcome = extractor(provider).runCombineEnrichmentOutcomeIfNeeded(
                textPassCandidates = text,
                visionPassCandidates = vision,
                allCandidates = text + vision,
                knownFieldValues = KnownFieldValues.EMPTY,
            )

            assertEquals(status, outcome.status)
            assertEquals(capability != RecognitionCapability.TEMPORARILY_UNAVAILABLE, outcome.requiresUserAction)
            assertTrue(outcome.candidates.isEmpty())
            assertEquals(1, provider.calls)
        }
    }

    @Test
    fun `refine retains explicit blockers after earlier passes produced values`() = runTest {
        availabilityCases.forEach { (capability, status) ->
            val provider = UnavailableProvider(capability)
            val outcome = extractor(provider).runRefineEnrichmentOutcomeIfNeeded(
                allCandidates = listOf(candidate("origin", "Columbia")),
            )

            assertEquals(status, outcome.status)
            assertEquals(capability != RecognitionCapability.TEMPORARILY_UNAVAILABLE, outcome.requiresUserAction)
            assertTrue(outcome.candidates.isEmpty())
            assertEquals(1, provider.calls)
        }
    }

    private fun extractor(provider: LlmInferenceProvider) = BagPhotoExtractor(
        appContext = null,
        llmProvider = provider,
        vocabularyProvider = {
            CoffeeFilterVocabulary(origins = listOf(CoffeeVocabularyEntry("Colombia")))
        },
    )

    private fun candidate(field: String, value: String) = BagFieldCandidate(
        fieldName = field,
        value = value,
        sourceType = BagFieldSourceType.LLM,
        confidenceHint = BagFieldConfidence.HIGH,
    )

    private class UnavailableProvider(
        private val capability: RecognitionCapability,
        private val available: Boolean = true,
    ) : LlmInferenceProvider {
        var calls = 0
            private set

        override fun isAvailable(): Boolean = available
        override fun unavailableCapability(): RecognitionCapability = capability
        override fun supportsVision(): Boolean = true
        override fun supportsCombine(): Boolean = true
        override fun supportsRefine(): Boolean = true
        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult = unavailable()
        override suspend fun extractBagFieldsWithVision(request: LlmExtractionRequest): LlmExtractionResult = unavailable()
        override suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult = unavailable()
        override suspend fun refineBagFields(request: LlmRefineRequest): LlmExtractionResult = unavailable()

        private fun unavailable(): LlmExtractionResult {
            calls += 1
            return LlmExtractionResult.Unavailable("Diagnostic detail", capability = capability)
        }
    }

    private companion object {
        val availabilityCases = listOf(
            RecognitionCapability.AUTHORIZATION_REQUIRED to LlmEnrichmentStatus.AUTHORIZATION_REQUIRED,
            RecognitionCapability.ASSET_SETUP_REQUIRED to LlmEnrichmentStatus.SETUP_REQUIRED,
            RecognitionCapability.TEMPORARILY_UNAVAILABLE to LlmEnrichmentStatus.UNAVAILABLE,
        )
    }
}
