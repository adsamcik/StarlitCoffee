package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.scan.model.LlmEscalationRequest
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrameEvidenceAccumulatorEscalationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- First trigger ---

    @Test
    fun `first trigger requires two provisional core fields and viable frame`() = runTest {
        val requests = mutableListOf<LlmEscalationRequest>()
        val accumulator = FrameEvidenceAccumulator(config = escalationConfig())
        val collector = collectEscalations(this, accumulator, requests)
        accumulator.start()

        repeat(5) { accumulator.processGoldenFrameForTest(frame(name = "Coffee", frameIndex = it)) }
        accumulator.forceConsensusForTest()
        assertTrue(requests.isEmpty())

        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))
        repeat(5) {
            accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = 10 + it))
        }
        accumulator.forceConsensusForTest()

        assertEquals(1, requests.size)
        accumulator.stop()
        collector.cancel()
    }

    @Test
    fun `stale golden frame prevents escalation`() = runTest {
        val requests = mutableListOf<LlmEscalationRequest>()
        val accumulator = FrameEvidenceAccumulator(
            config = escalationConfig().copy(llmGoldenFrameMaxAgeMs = 1L),
        )
        val collector = collectEscalations(this, accumulator, requests)
        accumulator.start()
        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))
        accumulator.setGoldenFrameTimestampForTest(System.currentTimeMillis() - 20L)

        repeat(5) { accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = it)) }
        accumulator.forceConsensusForTest()

        assertTrue(requests.isEmpty())
        accumulator.stop()
        collector.cancel()
    }

    @Test
    fun `fields needed empty does not spend escalation budget`() = runTest {
        val requests = mutableListOf<LlmEscalationRequest>()
        val accumulator = FrameEvidenceAccumulator(
            config = escalationConfig().copy(coreFields = setOf("name", "roaster"), allFields = setOf("name", "roaster")),
        )
        val collector = collectEscalations(this, accumulator, requests)
        accumulator.start()
        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))

        repeat(5) { accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = it)) }
        accumulator.forceConsensusForTest()

        assertTrue(requests.isEmpty())
        accumulator.stop()
        collector.cancel()
    }

    // --- Second trigger and budget ---

    @Test
    fun `second trigger fires on side flip or ocr change and budget prevents third call`() = runTest {
        val requests = mutableListOf<LlmEscalationRequest>()
        val accumulator = FrameEvidenceAccumulator(config = escalationConfig())
        val collector = collectEscalations(this, accumulator, requests)
        accumulator.start()
        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))

        repeat(5) { accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = it)) }
        accumulator.forceConsensusForTest()
        assertEquals(1, requests.size)

        accumulator.setGoldenFrameBytes(byteArrayOf(4, 5, 6))
        accumulator.notifyPotentialSideFlip()
        accumulator.processGoldenFrameForTest(frame(origin = "Ethiopia", frameIndex = 100, textBlockCount = 3))
        accumulator.forceConsensusForTest()
        assertEquals(2, requests.size)

        accumulator.updateRawOcrText("substantially new label text")
        accumulator.setGoldenFrameBytes(byteArrayOf(7, 8, 9))
        repeat(5) {
            accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = 200 + it))
        }
        accumulator.forceConsensusForTest()

        assertEquals(2, requests.size)
        accumulator.stop()
        collector.cancel()
    }

    @Test
    fun `second trigger fires on ocr text change`() = runTest {
        val requests = mutableListOf<LlmEscalationRequest>()
        val accumulator = FrameEvidenceAccumulator(config = escalationConfig())
        val collector = collectEscalations(this, accumulator, requests)
        accumulator.start()
        accumulator.updateRawOcrText("front label")
        accumulator.setGoldenFrameBytes(byteArrayOf(1, 2, 3))

        repeat(5) { accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = it)) }
        accumulator.forceConsensusForTest()
        assertEquals(1, requests.size)

        accumulator.updateRawOcrText("back label with process and altitude")
        accumulator.setGoldenFrameBytes(byteArrayOf(4, 5, 6))
        repeat(5) {
            accumulator.processGoldenFrameForTest(frame(name = "Coffee", roaster = "Roaster", frameIndex = 20 + it))
        }
        accumulator.forceConsensusForTest()

        assertEquals(2, requests.size)
        accumulator.stop()
        collector.cancel()
    }

    private fun collectEscalations(
        scope: CoroutineScope,
        accumulator: FrameEvidenceAccumulator,
        requests: MutableList<LlmEscalationRequest>,
    ): Job = scope.launch(testDispatcher) {
        accumulator.llmEscalation.collect { requests += it }
    }

    private fun frame(
        name: String? = null,
        roaster: String? = null,
        origin: String? = null,
        frameIndex: Int = 0,
        textBlockCount: Int = 3,
    ): FrameResult {
        val confidence = mutableMapOf<String, BagFieldConfidence>()
        name?.let { confidence["name"] = BagFieldConfidence.HIGH }
        roaster?.let { confidence["roaster"] = BagFieldConfidence.HIGH }
        origin?.let { confidence["origin"] = BagFieldConfidence.HIGH }
        return FrameResult(
            ocrResult = OcrExtractionResult(
                name = name,
                roaster = roaster,
                origin = origin,
                fieldConfidence = confidence,
                rawText = listOfNotNull(name, roaster, origin).joinToString("\n"),
            ),
            quality = BagCaptureQuality(
                blurScore = 25f,
                glarePercent = 0.02f,
                overexposedPercent = 0.1f,
                underexposedPercent = 0.1f,
                textBlockCount = textBlockCount,
                textDetected = textBlockCount > 0,
            ),
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
            isGoldenFrame = true,
        )
    }

    private fun escalationConfig(): AccumulatorConfig = AccumulatorConfig.DEFAULT.copy(
        coreFields = setOf("name", "roaster", "origin"),
        allFields = setOf("name", "roaster", "origin", "processType"),
        resolveThreshold = 0.3f,
        lockCycles = 2,
        llmFirstTriggerCoreFields = 2,
        llmMaxCallsPerSession = 2,
        llmGoldenFrameMaxAgeMs = 10_000L,
    )

}
