package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import com.mindlayer.sdk.ConnectionState
import com.mindlayer.sdk.Mindlayer
import com.mindlayer.sdk.MindlayerEvent
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
                mindlayer.awaitConnected()
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
        var sessionId: String? = null
        return try {
            val connected = withTimeoutOrNull(10_000L) {
                mindlayer.awaitConnected()
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

            sessionId = mindlayer.createSession {
                systemPrompt("You are a helpful assistant. Be very brief.")
                maxTokens(256)
            }

            val prompt = "What is the capital of Ethiopia? Reply in one word."
            val responseText = StringBuilder()
            var tokenCount = 0
            val startMs = System.currentTimeMillis()

            mindlayer.chat(sessionId, prompt).events.collect { event ->
                when (event) {
                    is MindlayerEvent.TextDelta -> {
                        responseText.append(event.text)
                        tokenCount++
                    }
                    // Surface the SDK error message via a generic
                    // RuntimeException; the diagnostic harness then catches
                    // it and reports the failure to the user. Wrapping in
                    // a domain-specific exception type would just be ceremony.
                    is MindlayerEvent.Error -> {
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
            sessionId?.let {
                try { mindlayer.destroySession(it) } catch (_: Exception) {}
            }
            mindlayer.disconnect()
        }
    }
}
