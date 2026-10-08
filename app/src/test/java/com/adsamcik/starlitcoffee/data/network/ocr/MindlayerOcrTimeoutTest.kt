package com.adsamcik.starlitcoffee.data.network.ocr

import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.Metrics
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.sdk.OcrHandle
import com.adsamcik.mindlayer.sdk.OcrLine
import com.adsamcik.mindlayer.sdk.OcrResult
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import com.adsamcik.starlitcoffee.scan.ScanDeadline
import com.adsamcik.starlitcoffee.scan.ScanDeadlineExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class MindlayerOcrTimeoutTest {
    private val imageBytes = byteArrayOf(1)
    private val bundledText = RecognizedText("bundled coffee label", emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one-shot result timeout cancels primary and returns bundled text`() = runTest {
        var primaryCancelled = false
        var fallbackCalls = 0
        val service = service {
            handle {
                try {
                    awaitCancellation()
                } finally {
                    primaryCancelled = true
                }
            }
        }

        val result = runWithFallback(
            primaryAvailable = service::isAvailable,
            primaryCall = { service.recognizePng(imageBytes) },
            fallbackCall = {
                assertTrue("primary cleanup must finish before fallback starts", primaryCancelled)
                fallbackCalls++
                bundledText
            },
        )

        assertSame(bundledText, result)
        assertEquals(1, fallbackCalls)
        assertTrue(primaryCancelled)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(MindlayerOcrService.SESSION_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `timeout while submitting one-shot also returns bundled text`() = runTest {
        val service = service { awaitCancellation() }
        var fallbackCalls = 0

        val result = runWithFallback(
            primaryAvailable = service::isAvailable,
            primaryCall = { service.recognizePng(imageBytes) },
            fallbackCall = { fallbackCalls++; bundledText },
        )

        assertSame(bundledText, result)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `successful one-shot retains recognized text without fallback`() = runTest {
        val service = service {
            handle {
                OcrResult(
                    lines = listOf(OcrLine("Mindlayer coffee label")),
                    fullJson = JsonObject(emptyMap()),
                    extractionJson = null,
                    metrics = Metrics.EMPTY,
                )
            }
        }

        val result = runWithFallback(
            primaryAvailable = service::isAvailable,
            primaryCall = { service.recognizePng(imageBytes) },
            fallbackCall = { fail("successful primary must not invoke fallback"); null },
        )

        assertEquals("Mindlayer coffee label", result?.fullText)
    }

    @Test
    fun `caller cancellation stops in-flight OCR without fallback`() = runTest {
        val primaryStarted = CompletableDeferred<Unit>()
        var primaryCancelled = false
        var fallbackCalls = 0
        val service = service {
            handle {
                primaryStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    primaryCancelled = true
                }
            }
        }
        val scan = async {
            runWithFallback(
                primaryAvailable = service::isAvailable,
                primaryCall = { service.recognizePng(imageBytes) },
                fallbackCall = { fallbackCalls++; bundledText },
            )
        }

        primaryStarted.await()
        scan.cancelAndJoin()

        assertTrue(scan.isCancelled)
        assertTrue(primaryCancelled)
        assertEquals(0, fallbackCalls)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `scan deadline expiring during OCR stops without fallback`() = runTest {
        val service = service { handle { awaitCancellation() } }
        val deadline = ScanDeadline.startingNow(
            budgetMillis = 1_000L,
            nowEpochMs = { 1_000L + testScheduler.currentTime },
        )
        var fallbackCalls = 0

        try {
            deadline.run {
                runWithFallback(
                    primaryAvailable = service::isAvailable,
                    primaryCall = { service.recognizePng(imageBytes) },
                    fallbackCall = { fallbackCalls++; bundledText },
                ) ?: error("expected scan deadline")
            }
            fail("expected scan deadline")
        } catch (_: ScanDeadlineExceededException) {
            assertEquals(0, fallbackCalls)
            assertEquals(1_000L, testScheduler.currentTime)
        }
    }

    @Test
    fun `independently nested timeout is not mistaken for the OCR budget`() = runTest {
        val service = service {
            handle { withTimeout(1_000L) { awaitCancellation() } }
        }
        var fallbackCalls = 0

        try {
            runWithFallback(
                primaryAvailable = service::isAvailable,
                primaryCall = { service.recognizePng(imageBytes) },
                fallbackCall = { fallbackCalls++; bundledText },
            )
            fail("expected nested cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, fallbackCalls)
            assertEquals(1_000L, testScheduler.currentTime)
            assertTrue(currentCoroutineContext().isActive)
        }
    }

    @Test
    fun `fallback after OCR timeout shares the original five-minute deadline`() = runTest {
        val service = service { handle { awaitCancellation() } }
        val deadline = ScanDeadline.startingNow(
            nowEpochMs = { 1_000L + testScheduler.currentTime },
        )
        var fallbackCalls = 0

        try {
            deadline.run {
                runWithFallback(
                    primaryAvailable = service::isAvailable,
                    primaryCall = { service.recognizePng(imageBytes) },
                    fallbackCall = {
                        fallbackCalls++
                        assertEquals(240_000L, deadline.remainingMillis)
                        delay(ScanDeadline.DEFAULT_BUDGET_MS)
                        bundledText
                    },
                ) ?: error("expected scan deadline")
            }
            fail("expected scan deadline")
        } catch (_: ScanDeadlineExceededException) {
            assertEquals(1, fallbackCalls)
            assertEquals(ScanDeadline.DEFAULT_BUDGET_MS, testScheduler.currentTime)
        }
    }

    @Test
    fun `connection wait timeout uses bundled fallback`() = runTest {
        val service = MindlayerOcrService(
            FakeMindlayer(
                onAwaitConnected = { timeout -> withTimeout(timeout) { awaitCancellation() } },
                supportedFeatures = { emptySet() },
            ),
        )

        val result = runWithFallback(
            primaryAvailable = service::isAvailable,
            primaryCall = { fail("unconnected primary must not run"); null },
            fallbackCall = { bundledText },
        )

        assertSame(bundledText, result)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `caller timeout during connection check remains cancellation`() = runTest {
        val service = MindlayerOcrService(
            FakeMindlayer(
                onAwaitConnected = { timeout -> withTimeout(timeout) { awaitCancellation() } },
                supportedFeatures = { emptySet() },
            ),
        )
        var fallbackCalls = 0

        try {
            withTimeout(1_000L) {
                runWithFallback(
                    primaryAvailable = service::isAvailable,
                    primaryCall = { error("unconnected primary must not run") },
                    fallbackCall = { fallbackCalls++; bundledText },
                )
            }
            fail("expected caller cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, fallbackCalls)
            assertEquals(1_000L, testScheduler.currentTime)
        }
    }

    @Test
    fun `SDK typed connection timeout still falls back when caller is active`() = runTest {
        val service = MindlayerOcrService(
            FakeMindlayer(
                onAwaitConnected = ::translatedConnectionWait,
                supportedFeatures = { emptySet() },
            ),
        )

        val result = runWithFallback(
            primaryAvailable = service::isAvailable,
            primaryCall = { fail("unconnected primary must not run"); null },
            fallbackCall = { bundledText },
        )

        assertSame(bundledText, result)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `SDK translated caller timeout during connection cannot start fallback`() = runTest {
        val service = MindlayerOcrService(
            FakeMindlayer(
                onAwaitConnected = ::translatedConnectionWait,
                supportedFeatures = { emptySet() },
            ),
        )
        var fallbackCalls = 0

        try {
            withTimeout(1_000L) {
                runWithFallback(
                    primaryAvailable = service::isAvailable,
                    primaryCall = { error("unconnected primary must not run") },
                    fallbackCall = { fallbackCalls++; bundledText },
                )
            }
            fail("expected caller cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, fallbackCalls)
            assertEquals(1_000L, testScheduler.currentTime)
        }
    }

    @Test
    fun `SDK translated caller timeout during OCR cannot start fallback`() = runTest {
        val service = service {
            handle {
                try {
                    awaitCancellation()
                } catch (error: TimeoutCancellationException) {
                    throw connectionTimeout(error)
                }
            }
        }
        var fallbackCalls = 0

        try {
            withTimeout(1_000L) {
                runWithFallback(
                    primaryAvailable = service::isAvailable,
                    primaryCall = { service.recognizePng(imageBytes) },
                    fallbackCall = { fallbackCalls++; bundledText },
                )
            }
            fail("expected caller cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, fallbackCalls)
            assertEquals(1_000L, testScheduler.currentTime)
        }
    }

    // Model the published SDK ConnectionManager's timeout-to-error translation.
    private suspend fun translatedConnectionWait(timeout: Duration) {
        try {
            withTimeout(timeout) { awaitCancellation() }
        } catch (error: TimeoutCancellationException) {
            throw connectionTimeout(error)
        }
    }

    private fun connectionTimeout(cause: TimeoutCancellationException): MindlayerException =
        MindlayerException("connection timed out", code = MindlayerErrorCode.CONNECT_TIMEOUT, cause = cause)

    private fun service(onOcr: suspend () -> OcrHandle.OneShot): MindlayerOcrService =
        MindlayerOcrService(
            FakeMindlayer(
                onOcr = onOcr,
                supportedFeatures = { setOf(ServiceCapabilities.FEATURE_OCR_IMAGE_ONESHOT) },
            ),
        )

    private fun handle(awaitResult: suspend () -> OcrResult): OcrHandle.OneShot =
        object : OcrHandle.OneShot {
            override val events = emptyFlow<com.adsamcik.mindlayer.sdk.OcrEvent>()
            override suspend fun awaitResult(): OcrResult = awaitResult.invoke()
        }
}
