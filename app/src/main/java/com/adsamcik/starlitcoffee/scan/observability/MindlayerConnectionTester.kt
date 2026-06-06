package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import com.adsamcik.mindlayer.sdk.ConnectionState
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.InferenceEvent
import com.adsamcik.mindlayer.sdk.Mindlayer
import kotlinx.coroutines.withTimeoutOrNull

data class ConnectionTestResult(
    val status: ConnectionStatus,
    val engineInfo: EngineInfoSnapshot?,
    val testResult: TestResult?,
    val errorMessage: String?,
)

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class EngineInfoSnapshot(
    val modelId: String,
    val modelSizeBytes: Long,
    val backend: String,
    val maxTokens: Int,
    val initTimeSeconds: Float,
    val prefillToksPerSec: Float,
    val decodeToksPerSec: Float,
)

data class TestResult(
    val prompt: String,
    val response: String,
    val latencyMs: Long,
    val tokenCount: Int,
)

/**
 * Debug/settings-screen diagnostic only.
 *
 * Production LLM paths must use the app-scoped provider singleton plus
 * LlmCallGate; this tester intentionally creates a short-lived raw Mindlayer
 * connection so users can verify the service independently from scan state.
 */
object MindlayerConnectionTester {

    suspend fun testConnection(context: Context): ConnectionTestResult {
        val mindlayer = Mindlayer.connect(context.applicationContext)
        return try {
            val connected = withTimeoutOrNull(10_000L) {
                mindlayer.awaitConnected(kotlin.time.Duration.INFINITE)
                true
            }
            if (connected != true) {
                return ConnectionTestResult(
                    status = ConnectionStatus.DISCONNECTED,
                    engineInfo = null,
                    testResult = null,
                    errorMessage = "Connection timed out after 10s",
                )
            }

            val engineInfo = try {
                val info = mindlayer.getEngineInfo()
                EngineInfoSnapshot(
                    modelId = info.modelId,
                    modelSizeBytes = info.modelSizeBytes,
                    backend = info.backend,
                    maxTokens = info.maxTokens,
                    initTimeSeconds = info.initTimeSeconds,
                    prefillToksPerSec = info.lastPrefillToksPerSec,
                    decodeToksPerSec = info.lastDecodeToksPerSec,
                )
            } catch (_: Exception) {
                null
            }

            ConnectionTestResult(
                status = ConnectionStatus.CONNECTED,
                engineInfo = engineInfo,
                testResult = null,
                errorMessage = null,
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                status = ConnectionStatus.ERROR,
                engineInfo = null,
                testResult = null,
                errorMessage = e.message ?: "Unknown error",
            )
        } finally {
            mindlayer.disconnect()
        }
    }

    suspend fun runTestPrompt(context: Context): ConnectionTestResult {
        val mindlayer = Mindlayer.connect(context.applicationContext)
        return try {
            val connected = withTimeoutOrNull(10_000L) {
                mindlayer.awaitConnected(kotlin.time.Duration.INFINITE)
                true
            }
            if (connected != true) {
                return ConnectionTestResult(
                    status = ConnectionStatus.DISCONNECTED,
                    engineInfo = null,
                    testResult = null,
                    errorMessage = "Connection timed out after 10s",
                )
            }

            val engineInfo = try {
                val info = mindlayer.getEngineInfo()
                EngineInfoSnapshot(
                    modelId = info.modelId,
                    modelSizeBytes = info.modelSizeBytes,
                    backend = info.backend,
                    maxTokens = info.maxTokens,
                    initTimeSeconds = info.initTimeSeconds,
                    prefillToksPerSec = info.lastPrefillToksPerSec,
                    decodeToksPerSec = info.lastDecodeToksPerSec,
                )
            } catch (_: Exception) {
                null
            }

            val prompt = "What is the capital of Ethiopia? Reply in one word."
            val responseText = StringBuilder()
            var tokenCount = 0
            val startMs = System.currentTimeMillis()

            // Pin the safe CPU backend before infer{} triggers engine creation
            // (see [prewarmCpuBestEffort]).
            mindlayer.prewarmCpuBestEffort()

            // v1 canonical ephemeral one-shot. The infer{} bridge replays a
            // Started → TextDelta → Done stream (token-level streaming is not
            // exposed through this path in alpha), so tokenCount reflects the
            // number of delta frames rather than true decode tokens.
            val handle = mindlayer.infer {
                ephemeralSession {
                    systemPrompt = "You are a helpful assistant. Be very brief."
                    maxTokens = 256
                }
                text(prompt)
                outputText()
            }
            handle.events.collect { event ->
                when (event) {
                    is InferenceEvent.TextDelta -> {
                        responseText.append(event.text)
                        tokenCount++
                    }
                    // Surface the SDK error message via a generic
                    // RuntimeException; the diagnostic harness then catches
                    // it and reports the failure to the user. Wrapping in
                    // a domain-specific exception type would just be ceremony.
                    is InferenceEvent.Error -> {
                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException(event.message)
                    }
                    else -> { /* Started, Done, Metrics — no action needed */ }
                }
            }

            val latencyMs = System.currentTimeMillis() - startMs

            ConnectionTestResult(
                status = ConnectionStatus.CONNECTED,
                engineInfo = engineInfo,
                testResult = TestResult(
                    prompt = prompt,
                    response = responseText.toString().trim(),
                    latencyMs = latencyMs,
                    tokenCount = tokenCount,
                ),
                errorMessage = null,
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                status = ConnectionStatus.ERROR,
                engineInfo = null,
                testResult = null,
                errorMessage = e.message ?: "Unknown error",
            )
        } finally {
            mindlayer.disconnect()
        }
    }

    /**
     * Pin the safe CPU backend before an `infer{}` call triggers engine
     * creation. Without this, a cold service process initialises the engine
     * with its default backend (GPU), and LiteRT-LM's `nativeCreateEngine`
     * SIGSEGVs while log-formatting the engine config on the emulator's
     * software GPU (tombstone: `strlen` <- `vsnprintf` <- `__android_log_print`
     * in `liblitertlm_jni.so`; tracked as LiteRT-LM #1686 / #2028). The
     * production extraction path does the same prewarm; this diagnostic must
     * mirror it or it reintroduces the very crash that guard avoids.
     *
     * Best-effort: a prewarm failure is non-fatal — the subsequent infer call
     * surfaces a clean error if the service is genuinely down.
     */
    private suspend fun Mindlayer.prewarmCpuBestEffort() {
        @Suppress("TooGenericExceptionCaught")
        try {
            prewarm(InferenceBackend.CPU)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to infer; it reports a meaningful failure.
        }
    }
}
