package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveScanViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Evidence flow wiring ---

    @Test
    fun `evidence flow stays stable across start`() {
        val viewModel = LiveScanViewModel(testConfig())

        val initialFlow = viewModel.evidence
        viewModel.start(KnownFieldValues.EMPTY)

        assertSame(initialFlow, viewModel.evidence)

        viewModel.stop()
    }

    // --- Best-frame windowing ---

    @Test
    fun `fresh OCR frame survives later non OCR frames until flush`() {
        val viewModel = LiveScanViewModel(testConfig())
        viewModel.start(KnownFieldValues.EMPTY)

        viewModel.onOcrResult(
            ocrResult = OcrExtractionResult(
                origin = "Ethiopia",
                fieldConfidence = mapOf("origin" to BagFieldConfidence.HIGH),
            ),
            quality = quality(blurScore = 18f),
            lumaGrid = null,
        )

        Thread.sleep(80L)

        viewModel.onRawFrame(
            quality = quality(blurScore = 40f),
            lumaGrid = null,
        )

        Thread.sleep(200L)

        assertTrue(viewModel.evidence.value.fields.containsKey("origin"))

        viewModel.stop()
    }

    private fun testConfig(): AccumulatorConfig {
        return AccumulatorConfig.DEFAULT.copy(
            consensusIntervalMs = 20L,
            throttleFastMs = 50L,
            throttleHysteresisMs = 0L,
            allFields = setOf("origin"),
            coreFields = setOf("origin"),
            draftTriggerCoreFields = 1,
            resolveThreshold = 0.01f,
            lockCycles = 1,
        )
    }

    private fun quality(blurScore: Float): BagCaptureQuality {
        return BagCaptureQuality(
            blurScore = blurScore,
            glarePercent = 0.01f,
            overexposedPercent = 0f,
            underexposedPercent = 0f,
            textBlockCount = 3,
            textDetected = true,
        )
    }
}
