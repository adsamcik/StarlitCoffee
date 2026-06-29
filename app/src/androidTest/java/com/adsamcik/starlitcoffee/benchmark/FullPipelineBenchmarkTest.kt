package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
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

        // Idea #4 — known-vocabulary grounding. With -PknownVocab the benchmark
        // simulates a returning user whose collection already contains this
        // roaster/origin/variety among many: build that vocabulary from the whole
        // corpus's ground truth and pass it to every pass. Off by default so the
        // standard run still measures the cold-start case.
        val useKnownVocab =
            InstrumentationRegistry.getArguments().getString(ARG_KNOWN_VOCAB)?.equals("true", true) == true
        val vocab = if (useKnownVocab) buildCollectionVocab(corpus.bags) else null
        Log.i(CorpusFixture.BENCHMARK_TAG, "[$bagId] knownVocab=$useKnownVocab")

        // 1) TEXT pass. With -PselfConsistency the (cheap, re-runnable) text pass
        // is sampled several times and the per-field MODE is taken (idea #8) — a
        // targeted defence against the low-temperature run-to-run wobble. Vision
        // is never re-sampled (one-shot per process). Default: a single pass.
        val selfConsistencyRuns =
            InstrumentationRegistry.getArguments().getString(ARG_SELF_CONSISTENCY)?.toIntOrNull()
                ?.coerceIn(1, MAX_SELF_CONSISTENCY) ?: 1
        val textRequest = LlmExtractionRequest(
            imageBytes = ByteArray(0),
            existingFields = emptyMap(),
            fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
            rawOcrText = ocrText,
            knownFieldValues = vocab,
        )
        val textFields = if (selfConsistencyRuns > 1) {
            selfConsistentTextFields(llm, textRequest, selfConsistencyRuns)
        } else {
            nonBlank(BagPipelineRunner.extractedByField(llm.extractBagFields(textRequest)))
        }
        Log.i(
            CorpusFixture.BENCHMARK_TAG,
            "[$bagId] text pass -> ${textFields.size} fields (selfConsistency=$selfConsistencyRuns)",
        )

        // 2) VISION pass — full field set, independent read. With -Pstitch the
        // front and back are composited into ONE image so the single (one-shot,
        // budget-limited) vision inference can see both faces; otherwise the
        // front alone is used. Whether stitching helps (it halves per-panel
        // resolution) is exactly what this opt-in measures.
        val useStitch =
            InstrumentationRegistry.getArguments().getString(ARG_STITCH)?.equals("true", true) == true
        val imageBytes = if (useStitch) stitchedImageBytes(bag) else frontImageBytes(bag)
        Log.i(CorpusFixture.BENCHMARK_TAG, "[$bagId] stitch=$useStitch image=${imageBytes?.size ?: 0}B")
        val visionResult = if (imageBytes == null) {
            Log.w(CorpusFixture.BENCHMARK_TAG, "[$bagId] no image — skipping vision pass")
            LlmExtractionResult.Unavailable("No image")
        } else {
            llm.extractBagFieldsWithVision(
                LlmExtractionRequest(
                    imageBytes = imageBytes,
                    existingFields = emptyMap(),
                    fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
                    rawOcrText = null,
                    knownFieldValues = vocab,
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
                    knownFieldValues = vocab,
                    rawOcrText = ocrText,
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

    /**
     * Composite the front and back label images into ONE portrait image (front
     * on top, back below, scaled to a common width with a thin separator) and
     * encode it as JPEG. Falls back to the front alone when there is no back.
     * This lets the single one-shot vision inference see both faces (idea #5).
     */
    private fun stitchedImageBytes(bag: CoffeeBagFixture): ByteArray? {
        val frontFile = CorpusFixture.frontPhotoFile(bag).takeIf { it.isFile }
        val backFile = CorpusFixture.backPhotoFile(bag)?.takeIf { it.isFile }
        val front = frontFile?.let { BitmapFactory.decodeFile(it.absolutePath) } ?: return frontImageBytes(bag)
        val back = backFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: return front.toJpegBytes().also { front.recycle() }

        val width = maxOf(front.width, back.width).coerceAtLeast(1)
        val gap = (width * STITCH_GAP_RATIO).toInt().coerceAtLeast(1)
        val frontScaled = scaleToWidth(front, width)
        val backScaled = scaleToWidth(back, width)
        val height = frontScaled.height + gap + backScaled.height

        val composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(composite).apply {
            drawColor(Color.WHITE)
            drawBitmap(frontScaled, 0f, 0f, null)
            drawBitmap(backScaled, 0f, (frontScaled.height + gap).toFloat(), null)
        }
        val bytes = composite.toJpegBytes()
        listOf(front, back, frontScaled, backScaled, composite).forEach { it.recycle() }
        return bytes
    }

    private fun scaleToWidth(bitmap: Bitmap, width: Int): Bitmap {
        if (bitmap.width == width) return bitmap
        val height = (bitmap.height.toLong() * width / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, STITCH_JPEG_QUALITY, out)
        out.toByteArray()
    }

    /**
     * Build a realistic returning-user vocabulary from the whole corpus's ground
     * truth (idea #4). The current bag's roaster/origin/etc. appear among ~28
     * others — exactly the collection a user who has logged a few bags would have.
     */
    private fun buildCollectionVocab(bags: List<CoffeeBagFixture>): KnownFieldValues {
        fun distinct(key: String) = bags.mapNotNull { it.groundTruth(key) }.distinct()
        return KnownFieldValues(
            names = distinct("name"),
            roasters = distinct("roaster"),
            origins = distinct("origin"),
            regions = distinct("region"),
            varieties = distinct("variety"),
            processTypes = distinct("process"),
            farms = distinct("farm"),
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

    private fun nonBlank(fields: Map<String, String?>): Map<String, String> =
        fields.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

    /**
     * Run the text pass [runs] times and take the per-field MODE (idea #8). For
     * each field the most frequently produced value wins; ties keep the value
     * from the earliest run. Fields absent in a run simply don't vote, so a
     * field most runs agree on stays stable even if one run drops or garbles it.
     */
    private suspend fun selfConsistentTextFields(
        llm: MindlayerLlmInferenceProvider,
        request: LlmExtractionRequest,
        runs: Int,
    ): Map<String, String> {
        val perField = linkedMapOf<String, MutableList<String>>()
        repeat(runs) {
            nonBlank(BagPipelineRunner.extractedByField(llm.extractBagFields(request)))
                .forEach { (field, value) -> perField.getOrPut(field) { mutableListOf() }.add(value) }
        }
        return perField.mapValues { (_, values) ->
            values.groupingBy { it }.eachCount().entries
                .maxWithOrNull(compareBy({ it.value }, { -values.indexOf(it.key) }))!!.key
        }
    }

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
        const val ARG_KNOWN_VOCAB = "knownVocab"
        const val ARG_STITCH = "stitch"
        const val ARG_SELF_CONSISTENCY = "selfConsistency"
        private const val MAX_SELF_CONSISTENCY = 7
        private const val STITCH_GAP_RATIO = 0.02
        private const val STITCH_JPEG_QUALITY = 90
        private val json = Json { ignoreUnknownKeys = true }

        /** Cross-process JSONL accumulator (one [FullPipelineBagRecord] per line). */
        fun recordsFile(context: Context): File =
            File(CorpusFixture.fixturesDir(context), "full-pipeline-records.jsonl")
    }
}
