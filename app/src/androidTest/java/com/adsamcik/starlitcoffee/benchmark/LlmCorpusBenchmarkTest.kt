package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.test.corpus.BagFieldScorer
import com.adsamcik.starlitcoffee.test.corpus.BagScore
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.test.corpus.QualityReport
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LLM quality REPORT — feeds captured OCR fixtures to the real Mindlayer LLM
 * and scores per-field extraction against the corpus ground truth.
 *
 * This is the **daily-driver iteration loop** for prompt + extraction changes.
 * It skips the OCR pipeline (using fixtures captured once by
 * [OcrFixtureCaptureTest]) so each bag costs only one LLM call instead of full
 * OCR + 3 preprocessing passes + LLM.
 *
 * # Not pass/fail on accuracy
 *
 * This test produces NUMBERS, not a verdict. It writes a structured
 * [QualityReport] (`llm-fixture-quality-report.json` + `.txt`) under the app's
 * external fixtures dir and logs the table to logcat (tag
 * [CorpusFixture.BENCHMARK_TAG]). It asserts only that the run was VALID —
 * fixtures existed and the LLM actually produced output — never a minimum
 * accuracy. Accuracy gating lives in [BagScanBestCaseGateTest] (Q0 only).
 *
 * # Running
 *
 * ```
 * ./gradlew pushTestImages   # corpus + metadata to the device
 * ./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"   # once
 * ./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest"
 * adb pull /sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LlmCorpusBenchmarkTest {

    @Test
    fun benchmarkLlmAgainstCorpus() = runBlocking {
        val corpus = CorpusFixture.load()
        assumeTrue(
            "Corpus sidecar metadata not present at ${CorpusFixture.CORPUS_DIR}/*.metadata.json — " +
                "run ./gradlew pushTestImages.",
            corpus != null,
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = MindlayerLlmInferenceProvider(context)
        assumeTrue(
            "Mindlayer LLM not available — start the service and approve com.adsamcik.starlitcoffee " +
                "in the Mindlayer dashboard, then retry.",
            QualityTestSupport.awaitTrue { llm.isAvailable() },
        )

        val scores = mutableListOf<BagScore>()
        var bagsWithFixtures = 0
        var bagsWithLlmSuccess = 0

        for (bag in corpus!!.bags) {
            Log.i(CorpusFixture.BENCHMARK_TAG, "----- ${bag.id} -----")
            val ocrText = loadCombinedOcrFor(context, bag)
            if (ocrText == null) {
                Log.w(CorpusFixture.BENCHMARK_TAG, "  Skipped — no OCR fixtures. Run OcrFixtureCaptureTest first.")
                continue
            }
            bagsWithFixtures++

            val request = LlmExtractionRequest(
                imageBytes = ByteArray(0),
                existingFields = emptyMap(),
                fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
                rawOcrText = ocrText,
                knownFieldValues = null,
            )
            val result = llm.extractBagFields(request)
            if (result is LlmExtractionResult.Success) {
                bagsWithLlmSuccess++
            } else {
                Log.w(CorpusFixture.BENCHMARK_TAG, "  Non-success LLM result: $result")
            }

            val score = BagFieldScorer.scoreBag(bag, BagPipelineRunner.extractedByField(result))
            scores.add(score)
            logBagScore(score)
        }

        val report = QualityReport.from("llm-fixture-quality-report", scores)
        QualityTestSupport.writeReport(context, report, "llm-fixture-quality-report")

        // Infra assertions only (never accuracy): the run must have been valid.
        assertTrue(
            "No OCR fixtures found for any bag. Run OcrFixtureCaptureTest first, then re-run.",
            bagsWithFixtures > 0,
        )
        assertTrue(
            "OCR fixtures existed but the LLM produced zero successful extractions — " +
                "verify Mindlayer is healthy.",
            bagsWithLlmSuccess > 0,
        )
    }

    private fun loadCombinedOcrFor(context: Context, bag: CoffeeBagFixture): String? {
        val front = CorpusFixture.frontOcrFixtureFile(context, bag).takeIf { it.isFile }?.readText()
        val back = CorpusFixture.backOcrFixtureFile(context, bag).takeIf { it.isFile }?.readText()
        if (front.isNullOrBlank() && back.isNullOrBlank()) return null
        val pairs = buildList {
            if (!front.isNullOrBlank()) add(BagCaptureSide.FRONT to front)
            if (!back.isNullOrBlank()) add(BagCaptureSide.BACK to back)
        }
        return BagOcrTextMerger.combineBySide(pairs)
    }

    private fun logBagScore(score: BagScore) {
        for (field in score.fields) {
            Log.i(
                CorpusFixture.BENCHMARK_TAG,
                "  ${field.outcome.symbol} ${field.metadataKey.padEnd(13)} " +
                    "got=${field.actual?.take(48)} expected=${field.expected?.take(48)}",
            )
        }
    }
}
