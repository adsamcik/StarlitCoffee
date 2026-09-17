package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.RecognizedText
import com.adsamcik.starlitcoffee.data.network.ocr.runWithFallback
import com.adsamcik.starlitcoffee.test.corpus.BagFieldScorer
import com.adsamcik.starlitcoffee.test.corpus.BagScore
import com.adsamcik.starlitcoffee.test.corpus.FieldOutcome
import com.adsamcik.starlitcoffee.test.corpus.FieldScore
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-only characterization probes for revision 2c75a3f.
 * Passing confirms the documented defect; these are NOT desired-behavior tests.
 * Kept outside normal test sources so fixes do not have to preserve defects.
 * See the accompanying evaluation for reproduction instructions and limitations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiIntegrationAuditProbeTest {
    @Test
    fun `provider-local timeout bypasses fallback and escapes unexpired scan deadline`() = runTest {
        var fallbackCalls = 0
        val deadline = ScanDeadline.startingNow(
            budgetMillis = 1_000,
            nowEpochMs = { 1_000 + testScheduler.currentTime },
        )
        val failure = runCatching {
            deadline.run {
                runWithFallback(
                    primaryAvailable = { true },
                    primaryCall = { withTimeout(10) { delay(20); "primary" } },
                    fallbackCall = { fallbackCalls++; "bundled" },
                ).orEmpty()
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals(0, fallbackCalls)
        assertTrue(deadline.remainingMillis > 0)
        currentCoroutineContext().ensureActive()
    }

    @Test
    fun `empty primary OCR prevents usable bundled OCR from running`() = runTest {
        var fallbackCalls = 0
        val result = runWithFallback(
            primaryAvailable = { true },
            primaryCall = { RecognizedText("", emptyList()) },
            fallbackCall = { fallbackCalls++; RecognizedText("Coffee 250g", emptyList()) },
        )
        assertEquals("", result?.fullText)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `one array-valued field throws away the valid sibling instead of returning a typed result`() {
        val failure = runCatching {
            MindlayerLlmInferenceProvider.parseResponse(
                """{"fields":{"name":{"value":"Good Coffee","status":"found"},"origin":{"value":["Colombia"],"status":"found"}}}""",
                setOf("name", "origin"),
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `error-shaped JSON is accepted as a successful empty extraction`() {
        val result = MindlayerLlmInferenceProvider.parseResponse(
            """{"error":"Unable to process this label"}""",
            setOf("name", "origin"),
        )
        assertTrue(result is LlmExtractionResult.Success)
        assertTrue((result as LlmExtractionResult.Success).fieldCandidates.isEmpty())
    }

    @Test
    fun `combine reuses old answer when original OCR context changes`() = runTest {
        val provider = ContextSensitiveProvider()
        val extractor = BagPhotoExtractor(appContext = null, llmProvider = provider)
        val first = combine(extractor, "Old Beans", KnownFieldValues.EMPTY)
        val second = combine(extractor, "New Beans", KnownFieldValues.EMPTY)
        assertEquals("Old Beans", first.single().value)
        assertEquals("Old Beans", second.single().value)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `combine reuses old answer when known vocabulary changes`() = runTest {
        val provider = ContextSensitiveProvider()
        val extractor = BagPhotoExtractor(appContext = null, llmProvider = provider)
        val first = combine(extractor, null, KnownFieldValues(roasters = listOf("Old Beans")))
        val second = combine(extractor, null, KnownFieldValues(roasters = listOf("New Beans")))
        assertEquals("Old Beans", first.single().value)
        assertEquals("Old Beans", second.single().value)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `Q0 gate accepts hallucinated roast level when ground truth is absent`() {
        val score = BagScore(
            bagId = "audit-synthetic",
            tier = "Q0",
            fields = listOf(
                FieldScore("name", "Coffee", "Coffee", FieldOutcome.EXACT),
                FieldScore("roastLevel", null, "Dark", FieldOutcome.HALLUCINATED),
            ),
        )
        assertTrue(BagFieldScorer.evaluateGate(score).passed)
    }

    private suspend fun combine(
        extractor: BagPhotoExtractor,
        ocr: String?,
        known: KnownFieldValues,
    ): List<BagFieldCandidate> {
        val text = listOf(candidate("Old Beans"))
        val vision = listOf(candidate("New Beans"))
        return extractor.runCombineEnrichmentIfNeeded(
            textPassCandidates = text,
            visionPassCandidates = vision,
            allCandidates = text + vision,
            knownFieldValues = known,
            combinedOcrText = ocr,
        )
    }

    private class ContextSensitiveProvider : LlmInferenceProvider {
        var calls = 0
        override fun isAvailable() = true
        override fun supportsCombine() = true
        override suspend fun extractBagFields(request: LlmExtractionRequest) =
            LlmExtractionResult.Unavailable("unused")

        override suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult {
            calls++
            val answer = request.rawOcrText ?: request.knownFieldValues!!.roasters.first()
            return LlmExtractionResult.Success(listOf(candidate(answer)))
        }
    }

    companion object {
        private fun candidate(value: String) = BagFieldCandidate(
            fieldName = "roaster",
            value = value,
            sourceType = BagFieldSourceType.LLM,
            confidenceHint = BagFieldConfidence.HIGH,
        )
    }
}
