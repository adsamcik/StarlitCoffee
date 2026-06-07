package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the determinate progress plan/report logic without invoking the full
 * [BagPhotoExtractor.extract] pipeline (which needs ML Kit / Android graphics):
 * the planned stage list adapts to AI availability and the reporter stays
 * monotonic, dropping stages that aren't part of the plan.
 */
class BagPhotoExtractorProgressTest {

    private class StubProvider(
        private val available: Boolean,
        private val vision: Boolean,
    ) : LlmInferenceProvider {
        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult =
            LlmExtractionResult.Unavailable("stub")

        override fun supportsVision(): Boolean = vision
        override fun isAvailable(): Boolean = available
    }

    private fun extractor(available: Boolean, vision: Boolean) =
        BagPhotoExtractor(appContext = null, llmProvider = StubProvider(available, vision))

    @Test
    fun `plan is OCR barcode finalizing when AI unavailable`() {
        val plan = extractor(available = false, vision = false).planProgressStages(runLlm = true)
        assertEquals(
            listOf(ScanStage.OCR, ScanStage.BARCODE_LOOKUP, ScanStage.FINALIZING),
            plan,
        )
    }

    @Test
    fun `plan adds LLM stage when AI available without vision`() {
        val plan = extractor(available = true, vision = false).planProgressStages(runLlm = true)
        assertEquals(
            listOf(ScanStage.OCR, ScanStage.BARCODE_LOOKUP, ScanStage.LLM_EXTRACT, ScanStage.FINALIZING),
            plan,
        )
    }

    @Test
    fun `plan adds vision stage when provider supports vision`() {
        val plan = extractor(available = true, vision = true).planProgressStages(runLlm = true)
        assertEquals(
            listOf(
                ScanStage.OCR,
                ScanStage.BARCODE_LOOKUP,
                ScanStage.LLM_EXTRACT,
                ScanStage.VISION,
                ScanStage.FINALIZING,
            ),
            plan,
        )
    }

    @Test
    fun `skipping AI drops the LLM and vision stages from the plan`() {
        val plan = extractor(available = true, vision = true).planProgressStages(runLlm = false)
        assertEquals(
            listOf(ScanStage.OCR, ScanStage.BARCODE_LOOKUP, ScanStage.FINALIZING),
            plan,
        )
    }

    @Test
    fun `reporter emits planned stages with monotonic indices and full finish`() {
        val plan = listOf(
            ScanStage.OCR,
            ScanStage.BARCODE_LOOKUP,
            ScanStage.LLM_EXTRACT,
            ScanStage.FINALIZING,
        )
        val captured = mutableListOf<ScanProgress>()
        val reporter = ScanProgressReporter(plan, captured::add)

        plan.forEach(reporter::report)

        assertEquals(plan, captured.map { it.stage })
        assertEquals(listOf(1, 2, 3, 4), captured.map { it.stepIndex })
        assertTrue(captured.all { it.stepCount == 4 })
        assertEquals(1f, captured.last().fraction)
    }

    @Test
    fun `reporter drops stages absent from the plan`() {
        val plan = listOf(ScanStage.OCR, ScanStage.BARCODE_LOOKUP, ScanStage.FINALIZING)
        val captured = mutableListOf<ScanProgress>()
        val reporter = ScanProgressReporter(plan, captured::add)

        // VISION isn't planned this pass — it must be silently ignored.
        reporter.report(ScanStage.OCR)
        reporter.report(ScanStage.VISION)
        reporter.report(ScanStage.FINALIZING)

        assertEquals(listOf(ScanStage.OCR, ScanStage.FINALIZING), captured.map { it.stage })
    }

    @Test
    fun `fraction is zero for an empty plan and clamps within bounds`() {
        assertEquals(0f, ScanProgress(ScanStage.OCR, stepIndex = 1, stepCount = 0).fraction)
        assertEquals(0.5f, ScanProgress(ScanStage.LLM_EXTRACT, stepIndex = 2, stepCount = 4).fraction)
        assertEquals(1f, ScanProgress(ScanStage.FINALIZING, stepIndex = 9, stepCount = 4).fraction)
    }
}
