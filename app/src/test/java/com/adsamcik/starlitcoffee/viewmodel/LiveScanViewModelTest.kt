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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `scan ui state stays steady after start`() {
        val viewModel = LiveScanViewModel(testConfig())
        viewModel.start(KnownFieldValues.EMPTY)

        val initialUiState = viewModel.liveScanUiState.value
        assertTrue(initialUiState.isScanning)
        assertTrue(initialUiState.scanStartTimeMs > 0L)

        Thread.sleep(80L)

        val uiState = viewModel.liveScanUiState.value
        assertTrue(uiState.isScanning)
        assertEquals(initialUiState.scanStartTimeMs, uiState.scanStartTimeMs)
        assertFalse(uiState.sideFlipDetected)
        assertEquals(0, uiState.goldenFrameCount)
        assertNull(uiState.lastRejectionReason)

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

    @Test
    fun `default accumulator config includes decaf and expiry fields`() {
        assertTrue(AccumulatorConfig.DEFAULT.allFields.contains("isDecaf"))
        assertTrue(AccumulatorConfig.DEFAULT.allFields.contains("expiryDate"))
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
