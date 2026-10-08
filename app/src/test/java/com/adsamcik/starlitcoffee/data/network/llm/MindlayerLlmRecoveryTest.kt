package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.mindlayer.ModelReadinessItem
import com.adsamcik.mindlayer.ModelReadinessSnapshot
import com.adsamcik.mindlayer.sdk.Capabilities
import com.adsamcik.mindlayer.sdk.ConnectionState
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import com.adsamcik.starlitcoffee.data.network.ocr.FakeMindlayer
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.RecognitionOffer
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRecoveryAction
import com.adsamcik.starlitcoffee.util.RecognitionUiStateMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Recovery cases from the pinned SDK's connection and model-readiness contracts. */
class MindlayerLlmRecoveryTest {
    @Test
    fun `connection timeouts and service failures never ask an enabled user for consent`() = runTest {
        val failures = listOf(
            sdkError(MindlayerErrorCode.CONNECT_TIMEOUT),
            sdkError(MindlayerErrorCode.SERVICE_UNAVAILABLE),
            sdkError(MindlayerErrorCode.SERVICE_THROTTLED),
            sdkError(MindlayerErrorCode.INTERNAL),
            IllegalStateException("consent required is merely exception text"),
        )
        failures.forEach { error ->
            val client = RecoveryMindlayer(onAwaitConnected = { throw error })
            val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, result.capability)
            assertEquals(0, client.readinessChecks)
            val presentation = presentation(LlmEnrichmentStatus.UNAVAILABLE)
            assertNull(presentation.offer)
            assertEquals(RecognitionRecoveryAction.RETRY, presentation.recoveryAction)
        }
    }

    @Test
    fun `explicit consent-required and revoked approval retain the enable action`() = runTest {
        listOf(MindlayerErrorCode.CONSENT_REQUIRED, MindlayerErrorCode.ALLOWLIST_REVOKED).forEach { code ->
            val client = RecoveryMindlayer(onAwaitConnected = { throw sdkError(code) })
            val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

            assertEquals(RecognitionCapability.AUTHORIZATION_REQUIRED, result.capability)
            assertEquals(RecognitionOffer.ENABLE, presentation(LlmEnrichmentStatus.AUTHORIZATION_REQUIRED).offer)
        }
    }

    @Test
    fun `Android bind permission failure is distinct from an unapproved caller`() = runTest {
        val deniedBind = RecoveryMindlayer(
            state = ConnectionState.BIND_GAVE_UP,
            onAwaitConnected = { throw sdkError(MindlayerErrorCode.PERMISSION_DENIED) },
        )
        val unapproved = RecoveryMindlayer(
            state = ConnectionState.REJECTED_NOT_APPROVED,
            onAwaitConnected = { throw sdkError(MindlayerErrorCode.PERMISSION_DENIED) },
        )

        val deniedResult = unavailable(MindlayerLlmInferenceProvider(deniedBind).extractBagFields(request()))
        val unapprovedResult = unavailable(MindlayerLlmInferenceProvider(unapproved).extractBagFields(request()))

        assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, deniedResult.capability)
        assertEquals(RecognitionCapability.AUTHORIZATION_REQUIRED, unapprovedResult.capability)
    }

    @Test
    fun `denied consent and identity failures cannot be fixed by offering consent again`() = runTest {
        listOf(MindlayerErrorCode.CONSENT_DENIED, MindlayerErrorCode.IDENTITY_UNKNOWN).forEach { code ->
            val client = RecoveryMindlayer(
                state = ConnectionState.REJECTED_NOT_APPROVED,
                onAwaitConnected = { throw sdkError(code) },
            )
            val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, result.capability)
        }
    }

    @Test
    fun `plain SDK auth-gate exception and prefixed resource error remain distinct`() = runTest {
        val auth = RecoveryMindlayer(onAwaitConnected = { throw SecurityException("unprefixed SDK auth gate") })
        val resource = RecoveryMindlayer(onAwaitConnected = {
            throw SecurityException(MindlayerErrorCode.wireMessage(MindlayerErrorCode.RATE_LIMITED, "gate rate limit"))
        })

        assertEquals(
            RecognitionCapability.AUTHORIZATION_REQUIRED,
            unavailable(MindlayerLlmInferenceProvider(auth).extractBagFields(request())).capability,
        )
        assertEquals(
            RecognitionCapability.TEMPORARILY_UNAVAILABLE,
            unavailable(MindlayerLlmInferenceProvider(resource).extractBagFields(request())).capability,
        )
    }

    @Test
    fun `setup-required readiness offers model setup without repeating approval`() = runTest {
        val client = RecoveryMindlayer(onReadiness = {
            readiness(ModelReadinessItem.STATE_SETUP_REQUIRED, ModelReadinessItem.REASON_MODEL_MISSING)
        })

        val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

        assertEquals(RecognitionCapability.ASSET_SETUP_REQUIRED, result.capability)
        assertEquals(RecognitionOffer.FINISH_SETUP, presentation(LlmEnrichmentStatus.SETUP_REQUIRED).offer)
        assertEquals(0, client.prewarmCalls)
    }

    @Test
    fun `failed or downloading models remain temporary failures without consent`() = runTest {
        val states = listOf(
            ModelReadinessItem.STATE_FAILED to ModelReadinessItem.REASON_LOW_MEMORY,
            ModelReadinessItem.STATE_FAILED to ModelReadinessItem.REASON_INTEGRITY_MISMATCH,
            ModelReadinessItem.STATE_FAILED to ModelReadinessItem.REASON_BACKEND_UNAVAILABLE,
            ModelReadinessItem.STATE_FAILED to ModelReadinessItem.REASON_NATIVE_ERROR,
            ModelReadinessItem.STATE_IN_PROGRESS to ModelReadinessItem.REASON_MODEL_MISSING,
        )
        states.forEach { (state, reason) ->
            val client = RecoveryMindlayer(onReadiness = { readiness(state, reason) })
            val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, result.capability)
            assertEquals(0, client.prewarmCalls)
            assertNull(presentation(LlmEnrichmentStatus.UNAVAILABLE).offer)
        }
    }

    @Test
    fun `readiness API failures use the same typed recovery as connection failures`() = runTest {
        val client = RecoveryMindlayer(onReadiness = { throw sdkError(MindlayerErrorCode.MODEL_MISSING) })

        val result = unavailable(MindlayerLlmInferenceProvider(client).extractBagFields(request()))

        assertEquals(RecognitionCapability.ASSET_SETUP_REQUIRED, result.capability)
        assertEquals(1, client.connectionChecks)
        assertEquals(1, client.readinessChecks)
        assertEquals(0, client.prewarmCalls)
    }

    @Test
    fun `missing OCR text asks for capture recovery rather than consent`() = runTest {
        val client = RecoveryMindlayer()

        val result = MindlayerLlmInferenceProvider(client).extractBagFields(request(ocrText = " "))

        assertTrue(result is LlmExtractionResult.Failed)
        assertEquals(0, client.prewarmCalls)
        val presentation = presentation(LlmEnrichmentStatus.FAILED, hasValues = false)
        assertNull(presentation.offer)
        assertEquals(RecognitionRecoveryAction.RETAKE, presentation.recoveryAction)
    }

    @Test
    fun `old service readiness fallback is not mistaken for missing authorization`() = runTest {
        val client = RecoveryMindlayer(onReadiness = { ModelReadinessSnapshot.unsupported() })

        val result = MindlayerLlmInferenceProvider(client).extractBagFields(request(ocrText = null))

        assertTrue(result is LlmExtractionResult.Failed)
        assertEquals(0, client.prewarmCalls)
    }

    @Test
    fun `early provider availability preserves only an explicit approval rejection`() {
        val unavailableStates = listOf(
            ConnectionState.DISCONNECTED,
            ConnectionState.BIND_GAVE_UP,
        )
        unavailableStates.forEach { state ->
            val provider = MindlayerLlmInferenceProvider(RecoveryMindlayer(state = state))
            assertFalse(provider.isAvailable())
            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, provider.unavailableCapability())
        }
        val unapproved = MindlayerLlmInferenceProvider(RecoveryMindlayer(state = ConnectionState.REJECTED_NOT_APPROVED))
        assertFalse(unapproved.isAvailable())
        assertEquals(RecognitionCapability.AUTHORIZATION_REQUIRED, unapproved.unavailableCapability())
    }

    @Test
    fun `recoverable connections allow every enrichment pass while terminal states stay blocked`() {
        val recoverableStates = setOf(
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECOVERING,
            ConnectionState.SUSPENDED_IDLE,
        )
        ConnectionState.entries.forEach { state ->
            val provider = MindlayerLlmInferenceProvider(RecoveryMindlayer(state = state))
            val available = state in recoverableStates
            assertEquals("text in $state", available, provider.isAvailable())
            assertEquals("vision in $state", available, provider.supportsVision())
            assertEquals("combine in $state", available, provider.supportsCombine())
            assertEquals("refine in $state", available, provider.supportsRefine())
        }
    }

    @Test
    fun `idle and recovering clients reach readiness through a bounded connection wait without model warmup`() = runTest {
        listOf(ConnectionState.SUSPENDED_IDLE, ConnectionState.RECOVERING).forEach { state ->
            val client = RecoveryMindlayer(state = state)
            val provider = MindlayerLlmInferenceProvider(client)
            assertTrue(provider.isAvailable())

            // Missing text stops before inference, after the resumed connection/readiness checks.
            val result = provider.extractBagFields(request(ocrText = null))

            assertTrue(result is LlmExtractionResult.Failed)
            assertEquals(1, client.connectionChecks)
            assertEquals(1, client.readinessChecks)
            assertEquals(5.seconds, client.lastConnectionTimeout)
            assertEquals(0, client.prewarmCalls)
        }
    }

    @Test
    fun `failed idle resume offers retry without asking for consent`() = runTest {
        val client = RecoveryMindlayer(
            state = ConnectionState.SUSPENDED_IDLE,
            onAwaitConnected = { throw sdkError(MindlayerErrorCode.CONNECT_TIMEOUT) },
        )
        val provider = MindlayerLlmInferenceProvider(client)
        assertTrue(provider.isAvailable())

        val result = unavailable(provider.extractBagFields(request()))

        assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, result.capability)
        assertEquals(0, client.readinessChecks)
        assertNull(presentation(LlmEnrichmentStatus.UNAVAILABLE).offer)
        assertEquals(RecognitionRecoveryAction.RETRY, presentation(LlmEnrichmentStatus.UNAVAILABLE).recoveryAction)
    }

    @Test
    fun `caller cancellation during connection is never turned into a recovery result`() = runTest {
        val started = CompletableDeferred<Unit>()
        var waitCancelled = false
        val client = RecoveryMindlayer(state = ConnectionState.SUSPENDED_IDLE, onAwaitConnected = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                waitCancelled = true
            }
        })
        val scan = async { MindlayerLlmInferenceProvider(client).extractBagFields(request()) }

        started.await()
        scan.cancelAndJoin()

        assertTrue(scan.isCancelled)
        assertTrue(waitCancelled)
        assertEquals(0, client.readinessChecks)
        assertEquals(0, client.prewarmCalls)
    }

    @Test
    fun `generic model-load failures and forged code names cannot trigger consent or setup`() {
        val failures = listOf(
            MindlayerException("MODEL_MISSING consent required", code = MindlayerErrorCode.ENGINE_LOAD_FAILED),
            MindlayerException("message", codeName = "CONSENT_REQUIRED"),
            sdkError(MindlayerErrorCode.INTEGRITY_MISMATCH),
            sdkError(MindlayerErrorCode.LOW_MEMORY),
            sdkError(MindlayerErrorCode.BACKEND_UNAVAILABLE),
            sdkError(MindlayerErrorCode.NATIVE_ERROR),
        )
        failures.forEach { error ->
            val result = mindlayerFailureResult(error, ConnectionState.CONNECTED, "Inference failed", retryable = false)
            assertTrue(result is LlmExtractionResult.Failed)
            assertEquals(
                RecognitionCapability.TEMPORARILY_UNAVAILABLE,
                mindlayerRecoveryCapability(error, ConnectionState.CONNECTED),
            )
        }
    }

    @Test
    fun `inference approval and missing-model codes preserve actionable recovery`() {
        val cases = listOf(
            MindlayerErrorCode.CONSENT_REQUIRED to RecognitionCapability.AUTHORIZATION_REQUIRED,
            MindlayerErrorCode.ALLOWLIST_REVOKED to RecognitionCapability.AUTHORIZATION_REQUIRED,
            MindlayerErrorCode.MODEL_MISSING to RecognitionCapability.ASSET_SETUP_REQUIRED,
        )
        cases.forEach { (code, capability) ->
            val result = unavailable(
                mindlayerFailureResult(sdkError(code), ConnectionState.CONNECTED, "Inference failed", retryable = false),
            )
            assertEquals(capability, result.capability)
        }
    }

    @Test
    fun `ordinary busy inference retains its retry hint`() {
        val error = MindlayerException("busy retryAfterMs=500", code = MindlayerErrorCode.ENGINE_BUSY)

        val result = mindlayerFailureResult(error, ConnectionState.CONNECTED, "Inference failed", retryable = true)

        assertTrue(result is LlmExtractionResult.Failed)
        result as LlmExtractionResult.Failed
        assertTrue(result.retryable)
        assertEquals(500L, result.retryAfterMs)
    }

    private fun request(ocrText: String? = "coffee label") = LlmExtractionRequest(
        imageBytes = byteArrayOf(1),
        existingFields = emptyMap(),
        fieldsNeeded = setOf("name"),
        rawOcrText = ocrText,
    )

    private fun sdkError(code: Int) = MindlayerException("SDK failure", code = code)

    private fun unavailable(result: LlmExtractionResult): LlmExtractionResult.Unavailable {
        assertTrue("expected a typed unavailable result, got $result", result is LlmExtractionResult.Unavailable)
        return result as LlmExtractionResult.Unavailable
    }

    private fun presentation(status: LlmEnrichmentStatus, hasValues: Boolean = true) =
        RecognitionUiStateMapper.fromPipeline(
            pipelineStatus = status,
            isProcessing = false,
            hasValues = hasValues,
            unresolvedCount = 1,
            preference = RecognitionPreference.ENABLED,
            mindlayerSupported = true,
            mindlayerInstalled = true,
        )
}

private fun readiness(state: Int, reason: String? = null) = ModelReadinessSnapshot(
    capturedAtEpochMs = 1L,
    items = listOf(ModelReadinessItem(family = ModelReadinessItem.FAMILY_CHAT, state = state, reasonCode = reason)),
)

private class RecoveryMindlayer(
    state: ConnectionState = ConnectionState.CONNECTED,
    private val onAwaitConnected: suspend () -> Unit = {},
    private val onReadiness: suspend () -> ModelReadinessSnapshot = { readiness(ModelReadinessItem.STATE_READY) },
) : Mindlayer by FakeMindlayer(supportedFeatures = { emptySet() }) {
    override val connectionState = MutableStateFlow(state)
    var connectionChecks = 0
        private set
    var lastConnectionTimeout: Duration? = null
        private set
    var readinessChecks = 0
        private set
    var prewarmCalls = 0
        private set

    override suspend fun awaitConnected(timeout: Duration): Capabilities {
        connectionChecks++
        lastConnectionTimeout = timeout
        onAwaitConnected()
        return Capabilities(emptySet())
    }

    override suspend fun getModelReadiness(): ModelReadinessSnapshot {
        readinessChecks++
        return onReadiness()
    }

    override suspend fun prewarm(backend: InferenceBackend) {
        prewarmCalls++
        error("a blocked text pass must not start prewarming")
    }
}
