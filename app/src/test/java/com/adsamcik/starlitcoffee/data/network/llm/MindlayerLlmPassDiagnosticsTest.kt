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
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic.FailureReason
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic.Pass
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic.ReadinessCode
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic.Status
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import kotlin.time.Duration

/** Covers the provider's validation/recording boundary without Android bitmap fixtures. */
class MindlayerLlmPassDiagnosticsTest {
    @Test
    fun `text pass records malformed generated text as error after translation`() = runTest {
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onResponse = { call -> if (call == 1) "coffee label" else "not JSON" })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        assertEquals(listOf(Pass.TRANSLATE, Pass.TEXT), diagnostics.records.map { it.pass })
        assertEquals(Status.SUCCESS, diagnostics.records.first().status)
        assertInvalidResponse(diagnostics.records.last(), Pass.TEXT, "not JSON")
        assertEquals(2, client.inferCalls)
    }

    @Test
    fun `combine records one error for malformed output instead of a success`() = runTest {
        val diagnostics = PassDiagnostics()
        val response = "{\"fields\": []}"
        val client = PassMindlayer(onResponse = { response })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        assertFalse((result as LlmExtractionResult.Failed).retryable)
        assertInvalidResponse(diagnostics.records.single(), Pass.COMBINE, response)
    }

    @Test
    fun `refine records one error for malformed output instead of a success`() = runTest {
        val diagnostics = PassDiagnostics()
        val response = "{\"origin\": {\"value\": []}}"
        val client = PassMindlayer(onResponse = { response })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).refineBagFields(refineRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        assertInvalidResponse(diagnostics.records.single(), Pass.REFINE, response)
    }

    @Test
    fun `all extraction passes reject malformed JSON and invalid field structures`() = runTest {
        val responses = listOf(
            "not JSON",
            "[]",
            "{\"fields\": []}",
            "{\"name\": []}",
            "{\"name\": {\"value\": {}}}",
            "{\"name\": {\"value\": \"Coffee\", \"status\": {}}}",
        )
        extractionPasses.forEach { pass ->
            responses.forEach { response ->
                val diagnostics = PassDiagnostics()
                val result = validationProvider(diagnostics).parseAndRecordResponse(
                    pass, response, setOf("name"), System.currentTimeMillis(), 100,
                )

                assertTrue("$pass accepted $response", result is LlmExtractionResult.Failed)
                assertInvalidResponse(diagnostics.records.single(), pass, response)
            }
        }
    }

    @Test
    fun `structural errors retain the existing text-only retry policy`() = runTest {
        extractionPasses.forEach { pass ->
            val result = validationProvider(PassDiagnostics()).parseAndRecordResponse(
                pass, "{\"fields\": []}", setOf("name"), System.currentTimeMillis(), 100,
            ) as LlmExtractionResult.Failed

            assertEquals(pass == Pass.TEXT, result.retryable)
        }
    }

    @Test
    fun `usable candidates are recorded as success for every extraction pass`() = runTest {
        extractionPasses.forEach { pass ->
            val diagnostics = PassDiagnostics()
            val result = validationProvider(diagnostics).parseAndRecordResponse(
                pass, "{\"name\": \"Coffee\"}", setOf("name"), System.currentTimeMillis(), 100,
            ) as LlmExtractionResult.Success

            assertEquals("Coffee", result.fieldCandidates.single().value)
            assertEquals(Status.SUCCESS, diagnostics.records.single().status)
            assertNull(diagnostics.records.single().failureReason)
        }
    }

    @Test
    fun `valid replies without usable requested fields remain empty results`() = runTest {
        val responses = listOf(
            "{}",
            "{\"fields\": {\"name\": {\"value\": null, \"status\": \"not_visible\"}}}",
            "{\"name\": \"   \"}",
            "{\"name\": \"not_visible\"}",
            "{\"unexpected\": \"Coffee\"}",
            "{\"origin\": \"Brazil\"}",
        )
        extractionPasses.forEach { pass ->
            responses.forEach { response ->
                val diagnostics = PassDiagnostics()
                val result = validationProvider(diagnostics).parseAndRecordResponse(
                    pass, response, setOf("name"), System.currentTimeMillis(), 100,
                ) as LlmExtractionResult.Success

                assertTrue(result.fieldCandidates.isEmpty())
                assertEquals(Status.NO_RESULT, diagnostics.records.single().status)
                assertNull(diagnostics.records.single().failureReason)
                assertEquals(response.length, diagnostics.records.single().outputCharLen)
            }
        }
    }

    @Test
    fun `vision evidence rejection is no result and invalid evidence structure is error`() = runTest {
        listOf(
            "{\"roastLevel\": {\"value\": \"Dark\", \"status\": \"found\"}}" to Status.NO_RESULT,
            "{\"roastLevel\": {\"value\": \"Dark\", \"status\": \"found\", \"evidence\": []}}" to Status.ERROR,
            "{\"roastLevel\": {\"value\": \"Dark\", \"status\": \"found\", \"evidence\": \"DARK ROAST\"}}" to Status.SUCCESS,
        ).forEach { (response, expectedStatus) ->
            val diagnostics = PassDiagnostics()
            validationProvider(diagnostics).parseAndRecordResponse(
                Pass.VISION, response, setOf("roastLevel"), System.currentTimeMillis(), 100,
                requireEvidenceFor = setOf("roastLevel"),
            )

            assertEquals(expectedStatus, diagnostics.records.single().status)
            assertEquals(
                if (expectedStatus == Status.ERROR) FailureReason.INVALID_RESPONSE else null,
                diagnostics.records.single().failureReason,
            )
        }
    }

    @Test
    fun `blank translation counts emitted characters and still permits text extraction`() = runTest {
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onResponse = { call -> if (call == 1) "   " else "{\"name\": \"Coffee\"}" })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest())

        assertEquals("Coffee", (result as LlmExtractionResult.Success).fieldCandidates.single().value)
        val translation = diagnostics.records.first()
        assertEquals(Pass.TRANSLATE, translation.pass)
        assertEquals(Status.NO_RESULT, translation.status)
        assertEquals(3, translation.outputCharLen)
        assertEquals(Status.SUCCESS, diagnostics.records.last().status)
    }

    @Test
    fun `blocked model readiness records bounded unavailable reasons before inference`() = runTest {
        listOf(
            ModelReadinessItem.STATE_SETUP_REQUIRED to FailureReason.MODEL_SETUP_REQUIRED,
            ModelReadinessItem.STATE_IN_PROGRESS to FailureReason.MODEL_IN_PROGRESS,
            ModelReadinessItem.STATE_FAILED to FailureReason.MODEL_FAILED,
        ).forEach { (state, expectedReason) ->
            val diagnostics = PassDiagnostics()
            val client = PassMindlayer(onReadiness = {
                passReadiness(state, reason = "PRIVATE_MODEL_REASON")
            })

            val result = MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest())

            assertTrue(result is LlmExtractionResult.Unavailable)
            val record = diagnostics.records.single()
            assertEquals(Pass.TEXT, record.pass)
            assertEquals(Status.UNAVAILABLE, record.status)
            assertEquals(expectedReason, record.failureReason)
            assertNull(record.errorCode)
            assertNull(record.readinessCode)
            assertFalse(record.toString().contains("PRIVATE_MODEL_REASON"))
            assertEquals(0, record.promptCharLen)
            assertEquals(0, record.outputCharLen)
            assertEquals(0, client.inferCalls)
        }
    }

    @Test
    fun `known readiness failures retain their closed code before inference`() = runTest {
        val knownReasons = listOf(
            ModelReadinessItem.REASON_MODEL_MISSING to ReadinessCode.MODEL_MISSING,
            ModelReadinessItem.REASON_LOW_MEMORY to ReadinessCode.LOW_MEMORY,
            ModelReadinessItem.REASON_INTEGRITY_MISMATCH to ReadinessCode.INTEGRITY_MISMATCH,
            ModelReadinessItem.REASON_BACKEND_UNAVAILABLE to ReadinessCode.BACKEND_UNAVAILABLE,
            ModelReadinessItem.REASON_NATIVE_ERROR to ReadinessCode.NATIVE_ERROR,
            ModelReadinessItem.REASON_OLD_SERVICE to ReadinessCode.OLD_SERVICE,
        )
        listOf(
            ModelReadinessItem.STATE_SETUP_REQUIRED to FailureReason.MODEL_SETUP_REQUIRED,
            ModelReadinessItem.STATE_FAILED to FailureReason.MODEL_FAILED,
            ModelReadinessItem.STATE_IN_PROGRESS to FailureReason.MODEL_IN_PROGRESS,
        ).forEach { (state, expectedFailure) ->
            knownReasons.forEach { (reason, expectedCode) ->
                val diagnostics = PassDiagnostics()
                val client = PassMindlayer(onReadiness = { passReadiness(state, reason) })

                val result = MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest())

                assertTrue(result is LlmExtractionResult.Unavailable)
                val record = diagnostics.records.single()
                assertEquals(Pass.TEXT, record.pass)
                assertEquals(Status.UNAVAILABLE, record.status)
                assertEquals(expectedFailure, record.failureReason)
                assertEquals(expectedCode, record.readinessCode)
                assertNull(record.errorCode)
                assertEquals(0, record.promptCharLen)
                assertEquals(0, record.outputCharLen)
                assertEquals(0, client.inferCalls)
            }
        }
    }

    @Test
    fun `readiness reasons with private suffixes cannot become recognized diagnostic codes`() = runTest {
        val privateReason = "LOW_MEMORY PRIVATE_MODEL_DETAIL"
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onReadiness = { passReadiness(ModelReadinessItem.STATE_FAILED, privateReason) })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest())

        assertTrue(result is LlmExtractionResult.Unavailable)
        val record = diagnostics.records.single()
        assertEquals(FailureReason.MODEL_FAILED, record.failureReason)
        assertNull(record.readinessCode)
        assertNull(record.errorCode)
        assertFalse(record.toString().contains(privateReason))
        assertEquals(0, client.inferCalls)
    }

    @Test
    fun `preflight SDK failures retain only known wire codes and bounded recovery reasons`() = runTest {
        listOf(
            MindlayerErrorCode.CONSENT_REQUIRED to FailureReason.AUTHORIZATION_REQUIRED,
            MindlayerErrorCode.MODEL_MISSING to FailureReason.MODEL_SETUP_REQUIRED,
            MindlayerErrorCode.CONNECT_TIMEOUT to FailureReason.CONNECTION_UNAVAILABLE,
        ).forEach { (code, expectedReason) ->
            val diagnostics = PassDiagnostics()
            val client = PassMindlayer(onAwaitConnected = {
                throw MindlayerException("PRIVATE_CONNECTION_DETAIL", code = code)
            })

            val result = MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest())

            assertTrue(result is LlmExtractionResult.Unavailable)
            val record = diagnostics.records.single()
            assertEquals(Pass.COMBINE, record.pass)
            assertEquals(Status.UNAVAILABLE, record.status)
            assertEquals(code, record.errorCode)
            assertEquals(expectedReason, record.failureReason)
            assertEquals(0, record.promptCharLen)
            assertEquals(0, record.outputCharLen)
            assertEquals(0, client.inferCalls)
        }
    }

    @Test
    fun `inference failure differs from invalid generated JSON`() = runTest {
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onResponse = {
            throw MindlayerException("PRIVATE_INFERENCE_DETAIL", code = MindlayerErrorCode.INTERNAL)
        })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        val record = diagnostics.records.single()
        assertEquals(Status.ERROR, record.status)
        assertEquals(FailureReason.INFERENCE_FAILED, record.failureReason)
        assertEquals(MindlayerErrorCode.INTERNAL, record.errorCode)
        assertEquals(0, record.outputCharLen)
    }

    @Test
    fun `owned inference timeout records timeout without invalid-response diagnosis`() = runTest {
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onResponse = { withTimeout(1) { awaitCancellation() } })

        val result = MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest())

        assertTrue(result is LlmExtractionResult.Failed)
        val record = diagnostics.records.single()
        assertEquals(Status.TIMEOUT, record.status)
        assertEquals(FailureReason.TIMEOUT, record.failureReason)
        assertNull(record.errorCode)
    }

    @Test
    fun `caller cancellation never emits a terminal pass outcome`() = runTest {
        val diagnostics = PassDiagnostics()
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val client = PassMindlayer(onResponse = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        val extraction = async { MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest()) }

        started.await()
        extraction.cancelAndJoin()

        assertTrue(extraction.isCancelled)
        assertTrue(cancelled)
        assertTrue(diagnostics.records.isEmpty())
    }

    @Test
    fun `SDK cancellation translated to an ordinary exception still produces no terminal outcome`() = runTest {
        val diagnostics = PassDiagnostics()
        val started = CompletableDeferred<Unit>()
        val client = PassMindlayer(onAwaitConnected = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw MindlayerException("cancelled wait", code = MindlayerErrorCode.CONNECT_TIMEOUT)
            }
        })
        val extraction = async { MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest()) }

        started.await()
        extraction.cancelAndJoin()

        assertTrue(extraction.isCancelled)
        assertTrue(diagnostics.records.isEmpty())
    }

    @Test
    fun `inference cancellation translated to an ordinary exception does not record an error`() = runTest {
        val diagnostics = PassDiagnostics()
        val started = CompletableDeferred<Unit>()
        val client = PassMindlayer(onResponse = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw MindlayerException("cancelled inference", code = MindlayerErrorCode.SERVICE_UNAVAILABLE)
            }
        })
        val extraction = async { MindlayerLlmInferenceProvider(client, diagnostics).extractBagFields(textRequest()) }

        started.await()
        extraction.cancelAndJoin()

        assertTrue(extraction.isCancelled)
        assertEquals(1, client.inferCalls)
        assertTrue(diagnostics.records.isEmpty())
    }

    @Test
    fun `caller deadline remains cancellation rather than an owned inference timeout`() = runTest {
        val diagnostics = PassDiagnostics()
        val client = PassMindlayer(onResponse = { awaitCancellation() })

        // The provider uses IO, so keep the caller's deadline on that same real-time dispatcher.
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_000) {
                MindlayerLlmInferenceProvider(client, diagnostics).combineBagFields(combineRequest())
            }
        }

        assertNull(result)
        assertEquals(1, client.inferCalls)
        assertTrue(diagnostics.records.isEmpty())
    }

    @Test
    fun `concurrent scans keep their own immutable diagnostic correlation`() = runTest {
        val diagnostics = PassDiagnostics()
        val firstContext = ScanDiagnosticContext(11L, 12L, 13L, 2, ScanDiagnosticMode.ADD_NEW)
        val secondContext = ScanDiagnosticContext(21L, 22L, 23L, 1, ScanDiagnosticMode.RESCAN)
        val first = async(firstContext) {
            MindlayerLlmInferenceProvider(PassMindlayer(onResponse = { "{\"name\": \"First\"}" }), diagnostics)
                .combineBagFields(combineRequest())
        }
        val second = async(secondContext) {
            MindlayerLlmInferenceProvider(PassMindlayer(onResponse = { "{}" }), diagnostics)
                .combineBagFields(combineRequest())
        }

        first.await()
        second.await()

        val firstRecord = diagnostics.records.single { it.sessionKey == 11L }
        assertEquals(12L, firstRecord.generationKey)
        assertEquals(13L, firstRecord.workKey)
        assertEquals(2, firstRecord.photoCount)
        assertEquals(ScanDiagnosticMode.ADD_NEW, firstRecord.mode)
        assertEquals(Status.SUCCESS, firstRecord.status)
        val secondRecord = diagnostics.records.single { it.sessionKey == 21L }
        assertEquals(22L, secondRecord.generationKey)
        assertEquals(23L, secondRecord.workKey)
        assertEquals(1, secondRecord.photoCount)
        assertEquals(ScanDiagnosticMode.RESCAN, secondRecord.mode)
        assertEquals(Status.NO_RESULT, secondRecord.status)
    }

    @Test
    fun `validation without scan context does not fabricate correlation`() = runTest {
        val diagnostics = PassDiagnostics()

        validationProvider(diagnostics).parseAndRecordResponse(
            Pass.VISION, "{\"name\": \"Coffee\"}", setOf("name"), System.currentTimeMillis(), 100,
        )

        val record = diagnostics.records.single()
        assertNull(record.sessionKey)
        assertNull(record.generationKey)
        assertNull(record.workKey)
        assertNull(record.photoCount)
        assertNull(record.mode)
    }

    private fun validationProvider(diagnostics: LlmDiagnosticsRecorder) =
        MindlayerLlmInferenceProvider(PassMindlayer(), diagnostics)

    private fun assertInvalidResponse(record: LlmPassDiagnostic, pass: Pass, response: String) {
        assertEquals(pass, record.pass)
        assertEquals(Status.ERROR, record.status)
        assertEquals(FailureReason.INVALID_RESPONSE, record.failureReason)
        assertEquals(response.length, record.outputCharLen)
        assertNull(record.errorCode)
    }

    private fun textRequest() = LlmExtractionRequest(
        imageBytes = byteArrayOf(1), existingFields = emptyMap(), fieldsNeeded = setOf("name"),
        rawOcrText = "raw coffee label",
    )

    private fun combineRequest() = LlmCombineRequest(
        fieldsNeeded = setOf("name"), textPassFields = mapOf("name" to "Coffee"), visionPassFields = emptyMap(),
    )

    private fun refineRequest() = LlmRefineRequest(
        fieldsNeeded = setOf("origin"), currentFields = mapOf("origin" to "Brasil"),
        suggestionsByField = mapOf("origin" to listOf("Brazil")),
    )

    private val extractionPasses = listOf(Pass.TEXT, Pass.VISION, Pass.COMBINE, Pass.REFINE)
}

private class PassDiagnostics : LlmDiagnosticsRecorder {
    val records: MutableList<LlmPassDiagnostic> = Collections.synchronizedList(mutableListOf())
    override fun record(diagnostic: LlmPassDiagnostic) {
        records += diagnostic
    }
}

private class PassMindlayer(
    private val onAwaitConnected: suspend () -> Unit = {},
    private val onReadiness: suspend () -> ModelReadinessSnapshot = { passReadiness(ModelReadinessItem.STATE_READY) },
    private val onResponse: suspend (Int) -> String = { error("inference not expected") },
) : Mindlayer by FakeMindlayer(supportedFeatures = { emptySet() }) {
    var inferCalls = 0
        private set

    override suspend fun awaitConnected(timeout: Duration): Capabilities {
        onAwaitConnected()
        return Capabilities(emptySet())
    }

    override suspend fun getModelReadiness(): ModelReadinessSnapshot = onReadiness()

    override suspend fun prewarm(backend: InferenceBackend) = Unit

    override suspend fun infer(build: InferenceRequest.Builder.() -> Unit): InferenceHandle {
        val call = ++inferCalls
        return object : InferenceHandle.Text {
            override val requestId = "request-$call"
            override val sessionId = "session-$call"
            override val events = emptyFlow<InferenceEvent>()
            override suspend fun cancel() = CancelResult(outcome = CancelResult.UNKNOWN)
            override suspend fun awaitText(): String = onResponse(call)
        }
    }
}

private fun passReadiness(state: Int, reason: String? = null) = ModelReadinessSnapshot(
    capturedAtEpochMs = 1L,
    items = listOf(ModelReadinessItem(family = ModelReadinessItem.FAMILY_CHAT, state = state, reasonCode = reason)),
)
