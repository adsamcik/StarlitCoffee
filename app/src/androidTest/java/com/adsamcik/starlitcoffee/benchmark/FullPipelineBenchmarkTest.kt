package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.test.corpus.BagFieldScorer
import com.adsamcik.starlitcoffee.test.corpus.BagScore
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One serialized full-pipeline record per corpus bag: the text-only score and
 * the combined (text -> vision -> combine) score, side by side. Appended one
 * line at a time by [FullPipelineBenchmarkTest] and read back by
 * [FullPipelineAggregateTest].
 */
@Serializable
data class FullPipelineBagRecord(
    val bagId: String,
    val tier: String?,
    val textScore: BagScore,
    val combinedScore: BagScore,
)

/**
 * Full-pipeline (text -> vision -> combine) quality benchmark for ONE corpus
 * bag per test process.
 *
 * # Why one bag per process
 *
 * The on-device vision pass has a HARD one-multimodal-inference-per-process
 * budget (`MindlayerLlmInferenceProvider`: a second Gemma-4 image inference
 * SIGSEGVs in `liblitertlm_jni.so`, LiteRT-LM #2028). A loop over the corpus in
 * a single process could therefore only ever vision-score the first bag. So the
 * benchmark is driven ONE bag per `am instrument` invocation — each a fresh
 * process with a fresh vision budget — and the per-bag scores are persisted to
 * a JSONL file that [FullPipelineAggregateTest] reduces into the comparative
 * report. The `scanBenchmark -PfullPipeline` Gradle task orchestrates the loop.
 *
 * # What it measures
 *
 * For the bag named by the `bagId` instrumentation argument it runs:
 *  1. TEXT pass — `extractBagFields` over the cached OCR fixtures (identical to
 *     [LlmCorpusBenchmarkTest]).
 *  2. VISION pass — `extractBagFieldsWithVision` over the front label image, on
 *     the full field set (independent read; not primed with the text result, so
 *     vision's standalone contribution is isolated).
 *  3. COMBINE pass — `combineBagFields` reconciling the text + vision values.
 *
 * It then scores TWO extractions against ground truth: text-only, and the
 * pipeline's final merged output (text, overlaid by vision, overlaid by
 * combine). Both land in one [FullPipelineBagRecord] appended to the JSONL.
 *
 * # Running
 *
 * ```
 * .\gradlew.bat scanBenchmark -PfullPipeline                 # curated subset, all phases
 * .\gradlew.bat scanBenchmark -PfullPipeline -PbagIds=scb-001-en-q0,scb-003-cs-q2
 * ```
 *
 * Needs the corpus IMAGES on device (`pushTestImages`) and the OCR fixtures
 * captured once (`OcrFixtureCaptureTest`); vision reads pixels, the text pass
 * reuses fixtures.
 */
@RunWith(AndroidJUnit4::class)
class FullPipelineBenchmarkTest {

    @Test
    fun benchmarkFullPipelineForOneBag() = runBlocking {
        val bagId = InstrumentationRegistry.getArguments().getString(ARG_BAG_ID)?.trim()
        assumeTrue(
            "No '$ARG_BAG_ID' instrumentation argument — run via `gradlew scanBenchmark -PfullPipeline` " +
                "which loops one bag per process.",
            !bagId.isNullOrBlank(),
        )

        val corpus = CorpusFixture.load()
        assumeTrue("Corpus not present at ${CorpusFixture.CORPUS_DIR} — run ./gradlew pushTestImages.", corpus != null)
        val bag = corpus!!.bags.firstOrNull { it.id == bagId }
        assumeTrue("Bag '$bagId' not in the automation-ready corpus.", bag != null)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = MindlayerLlmInferenceProvider(context)
        assumeTrue(
            "Mindlayer LLM not available — start the service and approve the app, then retry.",
            QualityTestSupport.awaitTrue { llm.isAvailable() },
        )

        val ocrText = loadCombinedOcrFor(context, bag!!)
        assumeTrue("No OCR fixtures for '$bagId' — run OcrFixtureCaptureTest first.", ocrText != null)

        // 1) TEXT pass — identical request shape to LlmCorpusBenchmarkTest.
        val textResult = llm.extractBagFields(
            LlmExtractionRequest(
                imageBytes = ByteArray(0),
                existingFields = emptyMap(),
                fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
                rawOcrText = ocrText,
                knownFieldValues = null,
            ),
        )
        val textFields = nonBlank(BagPipelineRunner.extractedByField(textResult))
        Log.i(CorpusFixture.BENCHMARK_TAG, "[$bagId] text pass -> ${textFields.size} fields")

        // 2) VISION pass — front label image, full field set, independent read.
        val imageBytes = frontImageBytes(bag)
        val visionResult = if (imageBytes == null) {
            Log.w(CorpusFixture.BENCHMARK_TAG, "[$bagId] no front image — skipping vision pass")
            LlmExtractionResult.Unavailable("No front image")
        } else {
            llm.extractBagFieldsWithVision(
                LlmExtractionRequest(
                    imageBytes = imageBytes,
                    existingFields = emptyMap(),
                    fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
                    rawOcrText = null,
                    knownFieldValues = null,
                ),
            )
        }
        val visionFields = nonBlank(BagPipelineRunner.extractedByField(visionResult))
        // Idea #7 — abstention calibration: only let vision contribute fields it
        // is CONFIDENT about ("found" => HIGH). An "uncertain" (LOW) vision read
        // is a guess; letting it feed combine or overwrite text is how a vision
        // hallucination (e.g. a roastLevel guessed from style) sneaks into the
        // final result. Uncertain vision values are dropped here.
        val visionFound = confidentValues(visionResult)
        Log.i(
            CorpusFixture.BENCHMARK_TAG,
            "[$bagId] vision pass -> ${visionFields.size} fields (${visionFound.size} confident) ($visionResult)",
        )

        // 3) COMBINE pass — reconcile text + CONFIDENT vision (only when both have values).
        val combineFields = if (textFields.isNotEmpty() && visionFound.isNotEmpty()) {
            val combineResult = llm.combineBagFields(
                LlmCombineRequest(
                    fieldsNeeded = (textFields.keys + visionFound.keys),
                    textPassFields = textFields,
                    visionPassFields = visionFound,
                    knownFieldValues = null,
                ),
            )
            Log.i(CorpusFixture.BENCHMARK_TAG, "[$bagId] combine pass ($combineResult)")
            nonBlank(BagPipelineRunner.extractedByField(combineResult))
        } else {
            emptyMap()
        }

        // Final pipeline output: text, overlaid by CONFIDENT vision, overlaid by combine.
        val combinedExtraction = buildMap<String, String?> {
            putAll(textFields)
            putAll(visionFound)
            putAll(combineFields)
        }

        val record = FullPipelineBagRecord(
            bagId = bag.id,
            tier = bag.captureTier,
            textScore = BagFieldScorer.scoreBag(bag, textFields),
            combinedScore = BagFieldScorer.scoreBag(bag, combinedExtraction),
        )
        appendRecord(context, record)

        // Infra assertion only (never accuracy): the text pass must have produced output.
        assertTrue(
            "Text pass produced no fields for '$bagId' — verify OCR fixtures + Mindlayer health.",
            textFields.isNotEmpty(),
        )
    }

    private fun frontImageBytes(bag: CoffeeBagFixture): ByteArray? {
        val front = CorpusFixture.frontPhotoFile(bag).takeIf { it.isFile }
        val file = front ?: CorpusFixture.backPhotoFile(bag)?.takeIf { it.isFile }
        return file?.readBytes()
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

    private fun nonBlank(fields: Map<String, String?>): Map<String, String> =
        fields.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

    /**
     * Field -> value for only the HIGH-confidence ("found") candidates of [result].
     * Drops "uncertain" (LOW) reads — the basis of idea #7's vision abstention
     * calibration.
     */
    private fun confidentValues(result: LlmExtractionResult?): Map<String, String> {
        if (result !is LlmExtractionResult.Success) return emptyMap()
        return result.fieldCandidates
            .filter { it.confidenceHint == BagFieldConfidence.HIGH }
            .groupBy { it.fieldName }
            .mapNotNull { (field, candidates) ->
                candidates.firstOrNull()?.value?.takeIf { it.isNotBlank() }?.let { field to it }
            }
            .toMap()
    }

    private fun appendRecord(context: Context, record: FullPipelineBagRecord) {
        val file = recordsFile(context)
        file.appendText(json.encodeToString(record) + "\n")
        Log.i(CorpusFixture.BENCHMARK_TAG, "[${record.bagId}] record appended -> ${file.absolutePath}")
    }

    companion object {
        const val ARG_BAG_ID = "bagId"
        private val json = Json { ignoreUnknownKeys = true }

        /** Cross-process JSONL accumulator (one [FullPipelineBagRecord] per line). */
        fun recordsFile(context: Context): File =
            File(CorpusFixture.fixturesDir(context), "full-pipeline-records.jsonl")
    }
}
