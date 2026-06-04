package com.adsamcik.starlitcoffee.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LLM benchmark — feeds captured OCR fixtures to the real Mindlayer LLM and
 * compares per-field extraction against the ground truth in
 * `corpus_metadata.json`.
 *
 * This is the **daily-driver iteration loop** for prompt + extraction
 * changes. It skips the OCR pipeline entirely (using fixtures captured
 * once by [OcrFixtureCaptureTest]) so each bag costs only one LLM call
 * (~30-60s) instead of full OCR + 3 preprocessing passes + LLM
 * (~90s+). Eight bags in ~5 minutes vs. ~20 minutes of manual scanning.
 *
 * # Reading the output
 *
 * Logcat tag: [CorpusFixture.BENCHMARK_TAG] = "StarlitBagBenchmark".
 * Per-bag per-field hit/miss lines, plus a summary table at the end.
 *
 * # What this test asserts
 *
 * Only that the pipeline doesn't crash and at least one bag produces
 * parseable LLM output. **Field accuracy is logged, not asserted** —
 * single-run LLM output has variance, and pinning expectations would
 * turn this into a brittle pass/fail rather than a calibration tool.
 * Read the logcat to see how a prompt change moved the needle.
 *
 * # Running
 *
 * ```
 * ./gradlew connectedDebugAndroidTest --tests '*LlmCorpusBenchmarkTest*'
 * ```
 *
 * Requires:
 *  - bag photos pushed to `/sdcard/Download/coffee-bags/`
 *  - `corpus_metadata.json` pushed alongside
 *  - OCR fixtures pre-captured by [OcrFixtureCaptureTest] in
 *    `/sdcard/Download/coffee-bags-fixtures/`
 *  - Mindlayer service installed + ready
 */
@RunWith(AndroidJUnit4::class)
class LlmCorpusBenchmarkTest {

    @Test
    fun benchmarkLlmAgainstCorpus() = runBlocking {
        val corpus = CorpusFixture.load()
        assumeTrue(
            "Corpus metadata not present at ${CorpusFixture.CORPUS_DIR}/corpus_metadata.json",
            corpus != null,
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = MindlayerLlmInferenceProvider(context)
        assumeTrue(
            "Mindlayer LLM not available — install/start the service and retry.",
            llm.isAvailable(),
        )

        val perBagResults = mutableListOf<BagBenchmarkResult>()
        var bagsWithLlmSuccess = 0

        for (bag in corpus!!.bags) {
            Log.i(CorpusFixture.BENCHMARK_TAG, "----- ${bag.id} -----")
            val ocrText = loadCombinedOcrFor(context, bag)
            if (ocrText == null) {
                Log.w(
                    CorpusFixture.BENCHMARK_TAG,
                    "  Skipped — no OCR fixtures. Run OcrFixtureCaptureTest first.",
                )
                perBagResults.add(BagBenchmarkResult(bag.id, "no-ocr-fixture", emptyMap()))
                continue
            }

            val request = LlmExtractionRequest(
                imageBytes = ByteArray(0),
                existingFields = emptyMap(),
                fieldsNeeded = BAG_FIELD_NAMES.toSet(),
                rawOcrText = ocrText,
                knownFieldValues = null,
            )

            val result = llm.extractBagFields(request)
            val perField = scoreResult(result, bag)
            perBagResults.add(
                BagBenchmarkResult(
                    bagId = bag.id,
                    status = result::class.java.simpleName,
                    perField = perField,
                ),
            )
            if (result is LlmExtractionResult.Success) {
                bagsWithLlmSuccess++
            }
        }

        logSummary(perBagResults)

        assertTrue(
            "Zero bags produced any LLM output. Verify Mindlayer is up and OCR fixtures exist.",
            bagsWithLlmSuccess > 0 || perBagResults.all { it.status == "no-ocr-fixture" },
        )
    }

    // --- Comparison utilities ---

    private fun loadCombinedOcrFor(context: android.content.Context, bag: CoffeeBagFixture): String? {
        val frontFile = CorpusFixture.frontOcrFixtureFile(context, bag)
        val backFile = CorpusFixture.backOcrFixtureFile(context, bag)
        val front = frontFile.takeIf { it.isFile }?.readText()
        val back = backFile.takeIf { it.isFile }?.readText()
        if (front.isNullOrBlank() && back.isNullOrBlank()) return null

        val pairs = buildList<Pair<BagCaptureSide, String>> {
            if (!front.isNullOrBlank()) add(BagCaptureSide.FRONT to front)
            if (!back.isNullOrBlank()) add(BagCaptureSide.BACK to back)
        }
        return BagOcrTextMerger.combineBySide(pairs)
    }

    private fun scoreResult(
        result: LlmExtractionResult,
        bag: CoffeeBagFixture,
    ): Map<String, FieldOutcome> {
        if (result !is LlmExtractionResult.Success) {
            val why = when (result) {
                is LlmExtractionResult.Unavailable -> "UNAVAILABLE: ${result.reason}"
                is LlmExtractionResult.Failed -> "FAILED: ${result.error}"
            }
            Log.w(CorpusFixture.BENCHMARK_TAG, "  $why")
            return emptyMap()
        }
        val extractedByField = result.fieldCandidates.groupBy { it.fieldName }
        val out = mutableMapOf<String, FieldOutcome>()
        for (fieldName in BAG_FIELD_NAMES) {
            val gtValue = bag.fields[fieldName]?.toComparableValue()
            val gotValue = extractedByField[fieldName]?.firstOrNull()?.value
            val outcome = when {
                gtValue == null && gotValue.isNullOrBlank() -> FieldOutcome.BOTH_BLANK
                gtValue == null -> FieldOutcome.EXTRACTED_EXTRA
                gotValue.isNullOrBlank() -> FieldOutcome.MISSING
                normalizeForCompare(gotValue) == normalizeForCompare(gtValue) -> FieldOutcome.EXACT
                normalizeForCompare(gotValue).contains(normalizeForCompare(gtValue)) ||
                    normalizeForCompare(gtValue).contains(normalizeForCompare(gotValue)) ->
                    FieldOutcome.PARTIAL
                else -> FieldOutcome.WRONG
            }
            out[fieldName] = outcome
            Log.i(
                CorpusFixture.BENCHMARK_TAG,
                "  ${outcome.symbol} ${fieldName.padEnd(14)} got=${gotValue?.format()} " +
                    "expected=${gtValue?.format()}",
            )
        }
        return out
    }

    private fun logSummary(results: List<BagBenchmarkResult>) {
        Log.i(CorpusFixture.BENCHMARK_TAG, "")
        Log.i(CorpusFixture.BENCHMARK_TAG, "=========== BENCHMARK SUMMARY ===========")
        val perFieldTotals = mutableMapOf<String, MutableMap<FieldOutcome, Int>>()
        for (r in results) {
            for ((field, outcome) in r.perField) {
                perFieldTotals.getOrPut(field) { mutableMapOf() }
                    .merge(outcome, 1) { a, b -> a + b }
            }
        }
        Log.i(
            CorpusFixture.BENCHMARK_TAG,
            "Bags evaluated: ${results.size} " +
                "(${results.count { it.status == "Success" }} LLM successes, " +
                "${results.count { it.status == "no-ocr-fixture" }} skipped no-OCR)",
        )
        Log.i(CorpusFixture.BENCHMARK_TAG, "")
        Log.i(
            CorpusFixture.BENCHMARK_TAG,
            "Per-field breakdown (✓ exact, ≈ partial, ✗ wrong, ? missing, · both-blank):",
        )
        for (field in BAG_FIELD_NAMES) {
            val totals = perFieldTotals[field] ?: continue
            val parts = listOf(
                FieldOutcome.EXACT, FieldOutcome.PARTIAL, FieldOutcome.WRONG,
                FieldOutcome.MISSING, FieldOutcome.BOTH_BLANK,
            ).joinToString("  ") { o -> "${o.symbol}${totals[o] ?: 0}" }
            Log.i(CorpusFixture.BENCHMARK_TAG, "  ${field.padEnd(14)} $parts")
        }
    }

    private fun String.format(): String = if (length > 60) substring(0, 57) + "..." else this

    private fun normalizeForCompare(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun kotlinx.serialization.json.JsonElement.toComparableValue(): String? {
        if (this is JsonNull) return null
        return runCatching {
            val prim = jsonPrimitive
            // Booleans surface as "true" / "false" — that matches what
            // the LLM emits for `isDecaf` and round-trips correctly.
            prim.booleanOrNull?.toString() ?: prim.contentOrNull
        }.getOrNull()
    }

    private data class BagBenchmarkResult(
        val bagId: String,
        val status: String,
        val perField: Map<String, FieldOutcome>,
    )

    private enum class FieldOutcome(val symbol: String) {
        EXACT("✓"),
        PARTIAL("≈"),
        WRONG("✗"),
        MISSING("?"),
        EXTRACTED_EXTRA("+"),
        BOTH_BLANK("·"),
    }

    companion object {
        private val BAG_FIELD_NAMES = listOf(
            "name", "roaster", "origin", "region", "farm",
            "variety", "processType", "altitude", "tastingNotes",
            "roastLevel", "roastDate", "expiryDate", "weight", "isDecaf",
        )
    }
}
