package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.mindlayer.CancelResult
import com.adsamcik.mindlayer.ModelReadinessItem
import com.adsamcik.mindlayer.ModelReadinessSnapshot
import com.adsamcik.mindlayer.sdk.Capabilities
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.InferenceEvent
import com.adsamcik.mindlayer.sdk.InferenceHandle
import com.adsamcik.mindlayer.sdk.InferenceRequest
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import com.adsamcik.starlitcoffee.data.network.ocr.FakeMindlayer
import com.adsamcik.starlitcoffee.domain.scanfield.FieldContext
import com.adsamcik.starlitcoffee.domain.scanfield.FieldSource
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

class MindlayerLlmContextBudgetTest {
    @Test
    fun `optional vocabulary budget counts encoded escaping and retains only complete values`() {
        val values = (1..20).map { "Roaster-$it-" + "\"\\".repeat(35) }
        val tooLong = "PRIVATE_LONG_NAME_".repeat(1000)

        val bounded = LlmContextBudget.referenceLists(
            linkedMapOf("roasters" to values, "farms" to listOf(tooLong, "Short farm")),
        )

        assertTrue(bounded.toString().length <= LlmContextBudget.VOCABULARY_JSON_CHARS)
        val kept = bounded.values.flatMap { it.jsonArray }.map { it.jsonPrimitive.content }
        assertTrue(kept.isNotEmpty())
        assertTrue(kept.all { it in values || it == "Short farm" })
        assertFalse(bounded.toString().contains("PRIVATE_LONG_NAME_"))
    }

    @Test
    fun `existing field context excludes oversized values without truncating normal ones`() {
        val oversized = "oversized".repeat(1000)
        val bounded = LlmContextBudget.objectEntries(
            linkedMapOf("roaster" to JsonPrimitive("Whole roaster"), "name" to JsonPrimitive(oversized)),
        )

        assertEquals("Whole roaster", bounded["roaster"]?.jsonPrimitive?.content)
        assertNull(bounded["name"])
        assertTrue(bounded.toString().length <= LlmContextBudget.EXISTING_FIELDS_JSON_CHARS)
    }

    @Test
    fun `text prompts preserve all OCR while compact prompt omits optional context`() {
        val ocr = "--- FRONT ---\nŽluté ovoce \"quoted\" 250g\n".repeat(400) + "--- BACK ---\nFinal source line"
        val request = textRequest(ocr).copy(
            existingFields = mapOf("roaster" to FieldContext("OPTIONAL_EXISTING_ROASTER", FieldSource.USER)),
            knownFieldValues = KnownFieldValues(roasters = listOf("OPTIONAL_REFERENCE_ROASTER")),
        )

        val normal = MindlayerLlmInferenceProvider.buildExtractionPrompt(request)
        val compact = MindlayerLlmInferenceProvider.buildExtractionPrompt(request, compact = true)

        assertTrue(JsonPrimitive(ocr).toString() in normal)
        assertTrue(JsonPrimitive(ocr).toString() in compact)
        assertTrue("OPTIONAL_EXISTING_ROASTER" in normal)
        assertTrue("OPTIONAL_REFERENCE_ROASTER" in normal)
        assertFalse("OPTIONAL_EXISTING_ROASTER" in compact)
        assertFalse("OPTIONAL_REFERENCE_ROASTER" in compact)
        assertTrue(compact.length < normal.length)
        assertEquals(ocr, request.rawOcrText)
    }

    @Test
    fun `refinement bounds suggestions while retaining current field values`() {
        val current = "Original current field".repeat(200)
        val request = refineRequest().copy(
            currentFields = mapOf("origin" to current),
            suggestionsByField = mapOf("origin" to listOf("OVERSIZED_SUGGESTION".repeat(1000), "Brazil")),
        )

        val normal = MindlayerLlmInferenceProvider.buildRefinePrompt(request)
        val compact = MindlayerLlmInferenceProvider.buildRefinePrompt(request, compact = true)

        assertTrue(JsonPrimitive(current).toString() in normal)
        assertTrue(JsonPrimitive(current).toString() in compact)
        assertFalse("OVERSIZED_SUGGESTION" in normal)
        assertTrue("Brazil" in normal)
        assertFalse("close_known_values" in compact)
        assertTrue(compact.length < normal.length)
    }

    @Test
    fun `vision context excludes oversized whole values and keeps normal grounding`() {
        val request = textRequest("Source OCR").copy(
            existingFields = linkedMapOf(
                "name" to FieldContext("PRIVATE_EXISTING_".repeat(1000), FieldSource.LLM),
                "roaster" to FieldContext("Whole roaster", FieldSource.USER),
            ),
            knownFieldValues = KnownFieldValues(
                names = listOf("PRIVATE_REFERENCE_".repeat(1000)), roasters = listOf("Known roaster"),
            ),
        )

        val prompt = MindlayerLlmInferenceProvider.buildVisionPrompt(request)

        assertFalse("PRIVATE_EXISTING_" in prompt)
        assertFalse("PRIVATE_REFERENCE_" in prompt)
        assertTrue(JsonPrimitive("Whole roaster").toString() in prompt)
        assertTrue(JsonPrimitive("Known roaster").toString() in prompt)
        assertTrue("label image" in prompt)
        assertTrue(prompt.length < 4000)
        assertEquals(1, request.imageBytes.size)
    }

    @Test
    fun `compact systems reduce total input without optional vocabulary and retain safety rules`() {
        val prompts = listOf(
            Triple(LlmPassDiagnostic.Pass.TEXT, MindlayerLlmInferenceProvider.buildSystemPrompt(extended = true),
                MindlayerLlmInferenceProvider.buildExtractionPrompt(textRequest("Complete source OCR"))),
            Triple(LlmPassDiagnostic.Pass.COMBINE, MindlayerLlmInferenceProvider.buildCombineSystemPrompt(),
                MindlayerLlmInferenceProvider.buildCombinePrompt(combineRequest().copy(knownFieldValues = null))),
            Triple(LlmPassDiagnostic.Pass.REFINE, MindlayerLlmInferenceProvider.buildRefineSystemPrompt(),
                MindlayerLlmInferenceProvider.buildRefinePrompt(refineRequest())),
        )
        prompts.forEach { (pass, normalSystem, prompt) ->
            val compactSystem = MindlayerLlmInferenceProvider.buildCompactSystemPrompt(pass)
            val attempt = LlmPromptAttempt(prompt, normalSystem) { prompt to compactSystem }

            assertTrue("$pass must reduce system content", compactSystem.length < normalSystem.length)
            assertTrue(attempt.compactAfter(overflow()))
            assertEquals(prompt, attempt.prompt)
            assertEquals(compactSystem, attempt.systemPrompt)
            assertTrue(attempt.prompt.length + attempt.systemPrompt.length < prompt.length + normalSystem.length)
            listOf("untrusted DATA", "never guess", "proper nouns", "to English", "found", "uncertain", "not_visible").forEach {
                assertTrue("$pass is missing $it", it in compactSystem)
            }
            assertFalse(attempt.compactAfter(overflow()))
        }
    }

    @Test
    fun `text overflow retries once with original OCR and records both actual prompt sizes`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val request = textRequest("Original source OCR").copy(knownFieldValues = vocabulary())
        val client = ContextMindlayer { call ->
            when (call) {
                1 -> "Normalized OCR"
                2 -> throw overflow()
                else -> "{\"name\": \"Coffee\"}"
            }
        }

        val result = MindlayerLlmInferenceProvider(client, records::add).extractBagFields(request)

        assertEquals("Coffee", (result as LlmExtractionResult.Success).fieldCandidates.single().value)
        assertEquals(3, client.inferCalls)
        val attempts = records.filter { it.pass == LlmPassDiagnostic.Pass.TEXT }
        assertEquals(listOf(LlmPassDiagnostic.Status.ERROR, LlmPassDiagnostic.Status.SUCCESS), attempts.map { it.status })
        assertEquals(MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT, attempts.first().errorCode)
        assertEquals(MindlayerLlmInferenceProvider.buildExtractionPrompt(request.copy(rawOcrText = "Normalized OCR")).length,
            attempts.first().promptCharLen)
        assertEquals(MindlayerLlmInferenceProvider.buildExtractionPrompt(request, compact = true).length,
            attempts.last().promptCharLen)
        assertNull(attempts.last().errorCode)
        assertEquals("Original source OCR", request.rawOcrText)
    }

    @Test
    fun `combine overflow removes optional vocabulary for its one recovery attempt`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val request = combineRequest()
        val client = ContextMindlayer { call -> if (call == 1) throw overflow() else "{\"name\": \"Coffee\"}" }

        val result = MindlayerLlmInferenceProvider(client, records::add).combineBagFields(request)

        assertTrue(result is LlmExtractionResult.Success)
        assertEquals(2, client.inferCalls)
        assertEquals(MindlayerLlmInferenceProvider.buildCombinePrompt(request).length, records.first().promptCharLen)
        assertEquals(MindlayerLlmInferenceProvider.buildCombinePrompt(request, compact = true).length, records.last().promptCharLen)
        assertEquals(LlmPassDiagnostic.Status.SUCCESS, records.last().status)
    }

    @Test
    fun `refine overflow keeps core fields but drops suggestions for one recovery attempt`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val request = refineRequest()
        val client = ContextMindlayer { call -> if (call == 1) throw overflow() else "{\"origin\": \"Brazil\"}" }

        val result = MindlayerLlmInferenceProvider(client, records::add).refineBagFields(request)

        assertTrue(result is LlmExtractionResult.Success)
        assertEquals(2, client.inferCalls)
        assertEquals(MindlayerLlmInferenceProvider.buildRefinePrompt(request).length, records.first().promptCharLen)
        assertEquals(MindlayerLlmInferenceProvider.buildRefinePrompt(request, compact = true).length, records.last().promptCharLen)
        assertEquals(LlmPassDiagnostic.Status.SUCCESS, records.last().status)
    }

    @Test
    fun `repeated overflow stops after compact attempt and records no false success`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val client = ContextMindlayer { throw overflow() }

        val result = MindlayerLlmInferenceProvider(client, records::add).combineBagFields(combineRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        assertFalse((result as LlmExtractionResult.Failed).retryable)
        assertEquals(2, client.inferCalls)
        assertEquals(2, records.size)
        assertTrue(records.all { it.status == LlmPassDiagnostic.Status.ERROR && it.errorCode == MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT })
        assertTrue(records.last().promptCharLen < records.first().promptCharLen)
    }

    @Test
    fun `ordinary errors and forged overflow code names never cause compact retry`() = runTest {
        listOf(
            MindlayerException("INPUT_EXCEEDS_CONTEXT", code = MindlayerErrorCode.INTERNAL, codeName = "INPUT_EXCEEDS_CONTEXT"),
            IllegalStateException("INPUT_EXCEEDS_CONTEXT"),
        ).forEach { failure ->
            val records = mutableListOf<LlmPassDiagnostic>()
            val client = ContextMindlayer { throw failure }

            val result = MindlayerLlmInferenceProvider(client, records::add).combineBagFields(combineRequest())

            assertTrue(result is LlmExtractionResult.Failed)
            assertEquals(1, client.inferCalls)
            assertEquals(1, records.size)
        }
    }

    @Test
    fun `combine without optional vocabulary can recover through smaller system content`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val request = combineRequest().copy(knownFieldValues = null)
        val client = ContextMindlayer { call -> if (call == 1) throw overflow() else "{\"name\": \"Coffee\"}" }

        val result = MindlayerLlmInferenceProvider(client, records::add).combineBagFields(request)

        assertTrue(result is LlmExtractionResult.Success)
        assertEquals(2, client.inferCalls)
        assertEquals(2, records.size)
        assertEquals(records.first().promptCharLen, records.last().promptCharLen)
    }

    @Test
    fun `equal or larger combined input never repeats the oversized request`() {
        listOf("12345" to "12345", "123456" to "12345", "1234" to "1234567").forEach { compact ->
            val attempt = LlmPromptAttempt("12345", "12345") { compact }

            assertFalse(attempt.compactAfter(overflow()))
            assertEquals("12345", attempt.prompt)
            assertEquals("12345", attempt.systemPrompt)
        }
    }

    @Test
    fun `cancellation after first overflow record prevents compact inference from starting`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val client = ContextMindlayer { throw overflow() }
        lateinit var extraction: Job
        val recorder = LlmDiagnosticsRecorder { record -> records += record; extraction.cancel() }
        val work = async(start = CoroutineStart.LAZY) {
            MindlayerLlmInferenceProvider(client, recorder).combineBagFields(combineRequest())
        }
        extraction = work

        work.start()
        work.join()

        assertTrue(work.isCancelled)
        assertEquals(1, client.inferCalls)
        assertEquals(1, records.size)
        assertEquals(MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT, records.single().errorCode)
    }

    @Test
    fun `caller cancellation during compact attempt retains only the completed overflow diagnosis`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val compactStarted = CompletableDeferred<Unit>()
        val client = ContextMindlayer { call ->
            if (call == 1) throw overflow()
            compactStarted.complete(Unit)
            awaitCancellation()
        }
        val work = async { MindlayerLlmInferenceProvider(client, records::add).combineBagFields(combineRequest()) }

        compactStarted.await()
        work.cancelAndJoin()

        assertTrue(work.isCancelled)
        assertEquals(2, client.inferCalls)
        assertEquals(1, records.size)
        assertEquals(MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT, records.single().errorCode)
    }

    @Test
    fun `caller deadline also covers compact recovery without a new timeout window`() = runTest {
        val records = mutableListOf<LlmPassDiagnostic>()
        val client = ContextMindlayer { call -> if (call == 1) throw overflow() else awaitCancellation() }

        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(1000) {
                MindlayerLlmInferenceProvider(client, records::add).combineBagFields(combineRequest())
            }
        }

        assertNull(result)
        assertEquals(2, client.inferCalls)
        assertEquals(1, records.size)
        assertEquals(MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT, records.single().errorCode)
    }

    private fun overflow() = MindlayerException("private overflow detail", code = MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT)
    private fun vocabulary() = KnownFieldValues(roasters = listOf("Optional known roaster", "Another roaster"))
    private fun textRequest(ocr: String) = LlmExtractionRequest(
        imageBytes = byteArrayOf(1), existingFields = emptyMap(), fieldsNeeded = setOf("name"), rawOcrText = ocr,
    )
    private fun combineRequest() = LlmCombineRequest(
        fieldsNeeded = setOf("name"), textPassFields = mapOf("name" to "Coffee"), visionPassFields = emptyMap(),
        knownFieldValues = vocabulary(), rawOcrText = "Source coffee label",
    )
    private fun refineRequest() = LlmRefineRequest(
        fieldsNeeded = setOf("origin"), currentFields = mapOf("origin" to "Brasil"),
        suggestionsByField = mapOf("origin" to listOf("Brazil")), rawOcrText = "Source coffee label",
    )
}

private class ContextMindlayer(private val response: suspend (Int) -> String) :
    Mindlayer by FakeMindlayer(supportedFeatures = { emptySet() }) {
    var inferCalls = 0
        private set
    override suspend fun awaitConnected(timeout: Duration) = Capabilities(emptySet())
    override suspend fun getModelReadiness() = ModelReadinessSnapshot(
        capturedAtEpochMs = 1L,
        items = listOf(ModelReadinessItem(family = ModelReadinessItem.FAMILY_CHAT, state = ModelReadinessItem.STATE_READY)),
    )
    override suspend fun prewarm(backend: InferenceBackend) = Unit
    override suspend fun infer(build: InferenceRequest.Builder.() -> Unit): InferenceHandle {
        val call = ++inferCalls
        return object : InferenceHandle.Text {
            override val requestId = "request-$call"
            override val sessionId = "session-$call"
            override val events = emptyFlow<InferenceEvent>()
            override suspend fun cancel() = CancelResult(outcome = CancelResult.UNKNOWN)
            override suspend fun awaitText(): String = response(call)
        }
    }
}
