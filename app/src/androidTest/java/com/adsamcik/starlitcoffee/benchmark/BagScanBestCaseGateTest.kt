package com.adsamcik.starlitcoffee.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.HierarchicalOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.test.corpus.BagFieldScorer
import com.adsamcik.starlitcoffee.test.corpus.BagScore
import com.adsamcik.starlitcoffee.test.corpus.GateResult
import com.adsamcik.starlitcoffee.test.corpus.QualityReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Q0 BEST-CASE GATE — the integration check that must pass 100%.
 *
 * For every studio-perfect (`Q0`) bag in the corpus this runs the FULL
 * production pipeline (3-pass OCR -> front/back fusion -> text-only LLM) and
 * asserts that every VISIBLE gate field
 * ([com.adsamcik.starlitcoffee.test.corpus.CorpusFields.gateFields]: name,
 * roaster, origin, process, roastLevel, weight) is extracted EXACTLY (after
 * field-specific canonicalization). Report-only fields (altitude, tasting
 * notes, producer, dates, ...) are scored into the report but never fail the
 * gate — those carry formatting/ordering variance that would make a 100% gate
 * flaky without adding signal.
 *
 * Unlike [LlmCorpusBenchmarkTest] (numbers, never fails on accuracy), THIS one
 * is pass/fail and is the contract the team commits to keeping green.
 *
 * # Skip vs hard-fail
 *
 * Missing corpus / Mindlayer cleanly skips by default so a casual device run
 * stays green. In the dedicated quality lane pass
 * `-e starlit.quality.required true` to turn those skips into failures — see
 * [QualityTestSupport].
 *
 * # Running
 *
 * ```
 * ./gradlew pushTestImages
 * ./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.BagScanBestCaseGateTest"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class BagScanBestCaseGateTest {

    @Test
    fun q0BestCaseBagsPassTheGate() = runBlocking {
        val corpus = CorpusFixture.load()
        QualityTestSupport.requireOrAssume(
            "Corpus sidecar metadata not present at ${CorpusFixture.CORPUS_DIR}/*.metadata.json — " +
                "run ./gradlew pushTestImages.",
            corpus != null,
        )

        val q0Bags = corpus!!.bags.filter { it.captureTier == "Q0" }
        assertTrue("Corpus must contain at least one Q0 best-case bag", q0Bags.isNotEmpty())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ocr: OcrService = HierarchicalOcrService(MindlayerOcrService(context))
        try {
            QualityTestSupport.requireOrAssume(
                "Mindlayer OCR not available — start the service and approve com.adsamcik.starlitcoffee " +
                    "in the Mindlayer dashboard, then retry.",
                QualityTestSupport.awaitTrueSuspending { ocr.isAvailable() },
            )
            val llm = MindlayerLlmInferenceProvider(context)
            QualityTestSupport.requireOrAssume(
                "Mindlayer LLM not available — start the service and approve com.adsamcik.starlitcoffee " +
                    "in the Mindlayer dashboard, then retry.",
                QualityTestSupport.awaitTrueSuspending { llm.isAvailable() },
            )

            val scores = mutableListOf<BagScore>()
            val gateResults = mutableListOf<GateResult>()

            for (bag in q0Bags) {
                Log.i(CorpusFixture.BENCHMARK_TAG, "----- GATE ${bag.id} -----")
                val pipeline = BagPipelineRunner.run(ocr, llm, bag)
                assertTrue(
                    "Q0 bag '${bag.id}' produced no OCR text — the pipeline must read a " +
                        "studio-perfect label. Check the pushed corpus images.",
                    pipeline.ocrText != null,
                )
                val score = BagFieldScorer.scoreBag(bag, BagPipelineRunner.extractedByField(pipeline.llmResult))
                val gate = BagFieldScorer.evaluateGate(score)
                scores.add(score)
                gateResults.add(gate)
                if (!gate.passed) {
                    Log.e(CorpusFixture.BENCHMARK_TAG, "  GATE FAIL ${bag.id}: ${describe(gate)}")
                }
            }

            val report = QualityReport.from("q0-best-case-gate", scores)
            QualityTestSupport.writeReport(context, report, "q0-best-case-gate-report")

            val failed = gateResults.filter { !it.passed }
            assertTrue(
                "Q0 best-case gate failed for ${failed.size}/${gateResults.size} bag(s):\n" +
                    failed.joinToString("\n") { "  ${it.bagId}: ${describe(it)}" },
                failed.isEmpty(),
            )
        } finally {
            ocr.close()
        }
    }

    private fun describe(gate: GateResult): String =
        gate.failures.joinToString("; ") {
            "${it.metadataKey} expected='${it.expected}' got='${it.actual}' (${it.outcome.label})"
        }
}
