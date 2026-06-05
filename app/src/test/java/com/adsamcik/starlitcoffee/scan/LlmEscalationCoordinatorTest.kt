package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldContext
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import com.adsamcik.starlitcoffee.scan.model.LlmUiStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlmEscalationCoordinatorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Session generation ---

    @Test
    fun `stale extract result from prior session does not write state`() = runTest {
        val provider = FakeLlmProvider(delayMs = 100)
        val coordinator = LlmEscalationCoordinator(provider)
        val firstAccumulator = FrameEvidenceAccumulator(config = testConfig())
        coordinator.start(this, firstAccumulator, { KnownFieldValues.EMPTY })

        val stale = async { coordinator.extract(request()) }
        delay(10)
        coordinator.stopAndSnapshot()
        coordinator.start(this, FrameEvidenceAccumulator(config = testConfig()), { KnownFieldValues.EMPTY })

        val result = stale.await()

        assertTrue(result is LlmExtractionResult.Success)
        assertNotEquals(LlmUiStatus.COMPLETED, coordinator.state.value.status)
        coordinator.stopAndSnapshot()
    }

    @Test
    fun `mutex serializes overlapping extract and auto escalation`() = runTest {
        val provider = FakeLlmProvider(delayMs = 50)
        val coordinator = LlmEscalationCoordinator(provider)
        val accumulator = FrameEvidenceAccumulator(config = testConfig())
        coordinator.start(this, accumulator, { KnownFieldValues.EMPTY })
        accumulator.start()

        val manual = async(start = CoroutineStart.UNDISPATCHED) { coordinator.extract(request()) }
        triggerFirstEscalation(accumulator)
        // The auto-escalation flows from the accumulator (Dispatchers.Default)
        // through the coordinator; keep advancing the coordinator's virtual
        // time and yielding to the Default threads until both extracts have
        // run, rather than racing a single advanceUntilIdle under full-suite
        // thread contention.
        val deadline = System.currentTimeMillis() + 5000L
        while (provider.extractCalls < 2 && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(5)
        }
        manual.await()
        assertEquals(2, provider.extractCalls)
        assertEquals(1, provider.maxActiveExtracts)
        accumulator.stop()
        coordinator.stopAndSnapshot()
    }

    @Test
    fun `prewarm failure flips state to unavailable`() = runTest {
        val coordinator = LlmEscalationCoordinator(FakeLlmProvider(prewarmFailure = true))

        coordinator.start(this, FrameEvidenceAccumulator(config = testConfig()), { KnownFieldValues.EMPTY })
        advanceUntilIdle()

        assertEquals(LlmUiStatus.UNAVAILABLE, coordinator.state.value.status)
        coordinator.stopAndSnapshot()
    }

    @Test
    fun `cancellation propagates from provider through coordinator`() = runTest {
        val coordinator = LlmEscalationCoordinator(FakeLlmProvider(cancelExtraction = true))
        coordinator.start(this, FrameEvidenceAccumulator(config = testConfig()), { KnownFieldValues.EMPTY })

        try {
            coordinator.extract(request())
            fail("Expected cancellation to propagate")
        } catch (_: CancellationException) {
            // Expected.
        } finally {
            coordinator.stopAndSnapshot()
        }
    }

    @Test
    fun `extract before any session leaves state idle`() = runTest {
        val coordinator = LlmEscalationCoordinator(FakeLlmProvider())

        val result = coordinator.extract(request())

        assertTrue(result is LlmExtractionResult.Unavailable)
        assertEquals(LlmUiStatus.IDLE, coordinator.state.value.status)
    }

    private fun triggerFirstEscalation(accumulator: FrameEvidenceAccumulator) {
        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))
        repeat(5) { index ->
            accumulator.processGoldenFrameForTest(
                FrameResult(
                    ocrResult = OcrExtractionResult(
                        name = "Test Coffee",
                        roaster = "Test Roaster",
                        fieldConfidence = mapOf(
                            "name" to BagFieldConfidence.HIGH,
                            "roaster" to BagFieldConfidence.HIGH,
                        ),
                    ),
                    quality = quality(),
                    frameIndex = index,
                    timestampMs = System.currentTimeMillis(),
                    isGoldenFrame = true,
                ),
            )
        }
        accumulator.forceConsensusForTest()
    }

    private fun request(): LlmExtractionRequest = LlmExtractionRequest(
        imageBytes = byteArrayOf(1, 2, 3),
        existingFields = mapOf(
            "name" to FieldContext(
                value = "Test Coffee",
                source = FieldSource.OCR,
                confidence = "HIGH",
            ),
        ),
        fieldsNeeded = setOf("origin"),
    )

    private fun testConfig(): AccumulatorConfig = AccumulatorConfig.DEFAULT.copy(
        coreFields = setOf("name", "roaster", "origin"),
        allFields = setOf("name", "roaster", "origin"),
        resolveThreshold = 0.3f,
        lockCycles = 1,
    )

    private fun quality(): BagCaptureQuality = BagCaptureQuality(
        blurScore = 25f,
        glarePercent = 0.02f,
        overexposedPercent = 0.1f,
        underexposedPercent = 0.1f,
        textBlockCount = 3,
        textDetected = true,
    )

    private class FakeLlmProvider(
        private val delayMs: Long = 0L,
        private val prewarmFailure: Boolean = false,
        private val cancelExtraction: Boolean = false,
    ) : LlmInferenceProvider {
        var extractCalls = 0
        var maxActiveExtracts = 0
        private var activeExtracts = 0

        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult {
            if (cancelExtraction) throw CancellationException("cancelled")
            extractCalls++
            activeExtracts++
            maxActiveExtracts = maxOf(maxActiveExtracts, activeExtracts)
            delay(delayMs)
            activeExtracts--
            return LlmExtractionResult.Success(
                listOf(
                    BagFieldCandidate(
                        fieldName = "origin",
                        value = "Ethiopia",
                        sourceType = BagFieldSourceType.LLM,
                        confidenceHint = BagFieldConfidence.HIGH,
                    ),
                ),
            )
        }

        override fun isAvailable(): Boolean = true

        override suspend fun prewarm() {
            if (prewarmFailure) error("prewarm failed")
        }
    }
}
