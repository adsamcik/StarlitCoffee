package com.adsamcik.starlitcoffee.data.network.ocr

import com.adsamcik.mindlayer.DiagnosticsSnapshot
import com.adsamcik.mindlayer.EngineInfo
import com.adsamcik.mindlayer.HealthCheck
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.ServiceStatus
import com.adsamcik.mindlayer.SessionInfo
import com.adsamcik.mindlayer.sdk.Capabilities
import com.adsamcik.mindlayer.sdk.ConnectionState
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.Mindlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

/**
 * Fake [Mindlayer] exposing only what [MindlayerOcrService] actually calls
 * ([connectionState], [awaitConnected]). The project has no mocking library,
 * so every other member throws — this fake fails loudly if the code under
 * test ever starts depending on something new instead of silently returning
 * a meaningless stub.
 */
private class FakeMindlayer(
    private val supportedFeatures: () -> Set<String>,
) : Mindlayer {

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.CONNECTED)

    override suspend fun awaitConnected(timeout: Duration): Capabilities =
        Capabilities(supportedFeatures())

    override fun disconnect(): Unit = error("not used in test")

    override suspend fun prewarm(backend: InferenceBackend): Unit = error("not used in test")

    override suspend fun getEngineInfo(): EngineInfo = error("not used in test")

    override suspend fun getCapabilities(forceRefresh: Boolean): ServiceCapabilities =
        error("not used in test")

    override suspend fun getStatus(): ServiceStatus = error("not used in test")

    override suspend fun ping(): HealthCheck = error("not used in test")

    override suspend fun getDiagnosticsTyped(): DiagnosticsSnapshot? = error("not used in test")

    override suspend fun listSessions(): List<SessionInfo> = error("not used in test")

    override suspend fun destroySession(sessionId: String): Unit = error("not used in test")
}

/**
 * Regression coverage for the capability-caching bug: a transient negative
 * capability check (e.g. PaddleOCR still warming up right after the client
 * binds) must NOT be cached forever — the app-scoped [MindlayerOcrService]
 * singleton would otherwise report OCR unavailable for the rest of the
 * process even after the Mindlayer service becomes ready.
 */
class MindlayerOcrServiceTest {

    @Test
    fun `isAvailable re-checks after an initial false instead of caching it forever`() = runTest {
        var engineReady = false
        val service = MindlayerOcrService(
            FakeMindlayer {
                if (engineReady) setOf(ServiceCapabilities.FEATURE_OCR_IMAGE_ONESHOT) else emptySet()
            },
        )

        assertFalse(
            "capability should read as unavailable while the engine is still warming up",
            service.isAvailable(),
        )

        engineReady = true

        assertTrue(
            "capability must be re-checked (not permanently cached as false) once the " +
                "service actually becomes ready",
            service.isAvailable(),
        )
    }

    @Test
    fun `isAvailable short-circuits once capability has been observed true`() = runTest {
        var checks = 0
        val service = MindlayerOcrService(
            FakeMindlayer {
                checks++
                setOf(ServiceCapabilities.FEATURE_OCR_IMAGE_ONESHOT)
            },
        )

        assertTrue(service.isAvailable())
        assertTrue(service.isAvailable())

        assertEquals("a positive result should be cached, avoiding repeat round-trips", 1, checks)
    }

    @Test
    fun `isAvailable stays false when the service never advertises the feature`() = runTest {
        val service = MindlayerOcrService(FakeMindlayer { emptySet() })

        assertFalse(service.isAvailable())
        assertFalse(service.isAvailable())
    }
}
