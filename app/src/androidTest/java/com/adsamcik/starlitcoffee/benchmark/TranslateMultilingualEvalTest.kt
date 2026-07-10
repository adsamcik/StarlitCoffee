package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.test.corpus.FieldComparators
import com.adsamcik.starlitcoffee.test.corpus.TranslateEvalLang
import com.adsamcik.starlitcoffee.test.corpus.TranslateEvalSeed
import com.adsamcik.starlitcoffee.test.corpus.TranslateEvalSeedLoader
import com.adsamcik.starlitcoffee.test.corpus.TranslateEvalTerm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device translate-pass quality measurement — does **Gemma 4 E2B** (the
 * on-device model behind [MindlayerLlmInferenceProvider]) translate localized
 * coffee vocabulary to English *by itself*, without a glossary?
 *
 * ## Why this test
 *
 * The scan pipeline is English-focused: a translate pass normalizes OCR to
 * English before extraction. Before investing in a foreign→English glossary we
 * want a number: how well does the model already handle the localized terms
 * scraped into `docs/coffee-filter-vocabulary.json`?
 *
 * The corpus benchmark can't answer this cleanly because its origin/process/
 * roast comparators *alias* untranslated foreign terms (so they score correct
 * even when the model doesn't translate). This test scores translation
 * directly: English present + source token NOT leaked.
 *
 * ## What it does
 *
 * Loads `translate-eval-seed.json` (per-language localized term banks with
 * canonical-English targets), batches terms into realistic synthetic
 * source-language labels, feeds each straight into
 * [MindlayerLlmInferenceProvider.extractBagFields] (empty image → text-only
 * translate+extract, no OCR, no vision), and per (language, field) tallies:
 *  - **coverage** = English target present in the output (recall of translation)
 *  - **leak**     = the source-language token survived untranslated (the bug)
 *
 * `tastingNotes` is the sharp signal (heavily localized, no alias forgiveness);
 * `process`/`roastLevel`/`origin` are secondary. It writes a per-language report
 * (`translate-eval-report.{txt,json}`) and logs it under
 * [CorpusFixture.BENCHMARK_TAG]. NOT pass/fail on accuracy — it asserts only that
 * the run was valid (seed present, Mindlayer produced output).
 *
 * ## Running
 *
 * Needs a reachable, approved Mindlayer service (skips cleanly otherwise). The
 * seed ships in the test APK's assets; a `/data/local/tmp` copy is used as a
 * fallback if present.
 *
 * ```
 * ./gradlew.bat installDebug installDebugAndroidTest
 * adb shell am instrument -w -e class \
 *   com.adsamcik.starlitcoffee.benchmark.TranslateMultilingualEvalTest \
 *   com.adsamcik.starlitcoffee.debug.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.adsamcik.starlitcoffee.debug/files/coffee-bags-fixtures/translate-eval-report.txt
 * ```
 *
 * Optional instrumentation arg `-e translate.eval.langs cs,it` limits languages.
 */
@RunWith(AndroidJUnit4::class)
class TranslateMultilingualEvalTest {

    private data class Probe(
        val lang: TranslateEvalLang,
        val label: String,
        val notes: List<TranslateEvalTerm>,
        val process: TranslateEvalTerm?,
        val roast: TranslateEvalTerm?,
        val origin: TranslateEvalTerm?,
    )

    private class Acc {
        var coverageTotal = 0
        var coverageHit = 0
        var leakTotal = 0
        var leakHit = 0
        val misses = ArrayList<String>()
        val leaks = ArrayList<String>()
    }

    @Test
    fun evaluateTranslationQuality() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext

        val seed = loadSeed(testContext)
        assumeTrue(
            "translate-eval-seed.json not found in test assets or ${DATA_LOCAL_TMP_SEED}.",
            seed != null,
        )

        val langFilter = InstrumentationRegistry.getArguments()
            .getString("translate.eval.langs")
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
        val languages = seed!!.languages.filter { langFilter == null || it.lang.lowercase() in langFilter }
        assumeTrue("No languages selected for the eval.", languages.isNotEmpty())

        val llm = MindlayerLlmInferenceProvider(targetContext)
        assumeTrue(
            "Mindlayer LLM not available — start the service and approve com.adsamcik.starlitcoffee, then retry.",
            QualityTestSupport.awaitTrue { llm.isAvailable() },
        )

        // field key -> per-language accumulator
        val acc = LinkedHashMap<String, LinkedHashMap<String, Acc>>()
        FIELDS.forEach { acc[it] = LinkedHashMap() }
        var probesRun = 0
        var probesOk = 0

        for (lang in languages) {
            val probes = buildProbes(lang, seed.chunkSize)
            Log.i(CorpusFixture.BENCHMARK_TAG, "===== ${lang.lang} (${lang.name}) — ${probes.size} probes =====")
            for (probe in probes) {
                probesRun++
                val extracted = runCatching {
                    val request = LlmExtractionRequest(
                        imageBytes = ByteArray(0),
                        existingFields = emptyMap(),
                        fieldsNeeded = BagPipelineRunner.APP_FIELD_NAMES.toSet(),
                        rawOcrText = probe.label,
                        knownFieldValues = null,
                    )
                    BagPipelineRunner.extractedByField(llm.extractBagFields(request))
                }.getOrElse {
                    Log.w(CorpusFixture.BENCHMARK_TAG, "  probe failed: ${it.message}")
                    emptyMap()
                }
                if (extracted.isNotEmpty()) probesOk++

                scoreProbe(probe, extracted, acc)
            }
        }

        val report = buildReport(seed, languages, acc)
        writeReport(targetContext, report)

        assertTrue("No probes were run — check the seed.", probesRun > 0)
        assertTrue(
            "Mindlayer produced zero successful extractions — verify the service is healthy.",
            probesOk > 0,
        )
    }

    // --- probe construction ---

    private fun buildProbes(lang: TranslateEvalLang, chunkSize: Int): List<Probe> {
        val probes = ArrayList<Probe>()
        val chunks = lang.tastingNotes.chunked(chunkSize.coerceAtLeast(1))
            .ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { i, chunk ->
            probes.add(
                Probe(
                    lang = lang,
                    label = buildLabel(
                        lang, chunk,
                        lang.process.getOrNull(i), lang.roastLevel.getOrNull(i), lang.origin.getOrNull(i),
                    ),
                    notes = chunk,
                    process = lang.process.getOrNull(i),
                    roast = lang.roastLevel.getOrNull(i),
                    origin = lang.origin.getOrNull(i),
                ),
            )
        }
        // Cover leftover process/roast terms (the meaningful concept fields) with
        // a few extra minimal labels; origin is left to base-probe cycling.
        val filler = lang.tastingNotes.take(2)
        leftover(lang.process, chunks.size).forEach { p ->
            probes.add(Probe(lang, buildLabel(lang, filler, p, null, null), emptyList(), p, null, null))
        }
        leftover(lang.roastLevel, chunks.size).forEach { r ->
            probes.add(Probe(lang, buildLabel(lang, filler, null, r, null), emptyList(), null, r, null))
        }
        return probes
    }

    private fun leftover(list: List<TranslateEvalTerm>, baseCount: Int): List<TranslateEvalTerm> =
        if (list.size > baseCount) list.subList(baseCount, minOf(list.size, baseCount + EXTRA_PROBE_CAP))
        else emptyList()

    private fun buildLabel(
        lang: TranslateEvalLang,
        notes: List<TranslateEvalTerm>,
        process: TranslateEvalTerm?,
        roast: TranslateEvalTerm?,
        origin: TranslateEvalTerm?,
    ): String = buildString {
        append("--- FRONT ---\n")
        append(lang.sampleName).append('\n')
        append(lang.sampleRoaster).append('\n')
        origin?.let { append(lang.labels.origin).append(": ").append(it.src).append('\n') }
        if (notes.isNotEmpty()) {
            append(lang.labels.notes).append(": ")
                .append(notes.joinToString(", ") { it.src }).append('\n')
        }
        process?.let { append(lang.labels.process).append(": ").append(it.src).append('\n') }
        roast?.let { append(lang.labels.roast).append(": ").append(it.src).append('\n') }
        append("250 g\n")
    }

    // --- scoring ---

    private fun scoreProbe(probe: Probe, extracted: Map<String, String?>, acc: Map<String, LinkedHashMap<String, Acc>>) {
        val notesOut = FieldComparators.normalize(extracted["tastingNotes"].orEmpty())
        val processOut = FieldComparators.normalize(extracted["processType"].orEmpty())
        val roastOut = FieldComparators.normalize(extracted["roastLevel"].orEmpty())
        val originOut = FieldComparators.normalize(extracted["origin"].orEmpty())

        probe.notes.forEach { score("tastingNotes", probe.lang.lang, it, notesOut, acc) }
        probe.process?.let { score("process", probe.lang.lang, it, processOut, acc) }
        probe.roast?.let { score("roastLevel", probe.lang.lang, it, roastOut, acc) }
        probe.origin?.let { score("origin", probe.lang.lang, it, originOut, acc) }
    }

    private fun score(
        field: String,
        lang: String,
        term: TranslateEvalTerm,
        outputNorm: String,
        acc: Map<String, LinkedHashMap<String, Acc>>,
    ) {
        val a = acc.getValue(field).getOrPut(lang) { Acc() }
        a.coverageTotal++
        if (englishHit(outputNorm, term.en)) {
            a.coverageHit++
        } else if (a.misses.size < MAX_EXAMPLES) {
            a.misses.add("${term.src} → ${term.en.firstOrNull() ?: "?"} | got=\"${outputNorm.take(48)}\"")
        }
        if (!term.loanword) {
            a.leakTotal++
            if (sourceLeaked(outputNorm, term.src)) {
                a.leakHit++
                if (a.leaks.size < MAX_EXAMPLES) a.leaks.add("${term.src} in \"${outputNorm.take(48)}\"")
            }
        }
    }

    /** Whole-word / whole-phrase containment on already-normalized text. */
    private fun wholeContains(haystackNorm: String, needleRaw: String): Boolean {
        val n = FieldComparators.normalize(needleRaw)
        if (n.isBlank()) return false
        return " $haystackNorm ".contains(" $n ")
    }

    private fun englishHit(outputNorm: String, en: List<String>): Boolean =
        en.any { wholeContains(outputNorm, it) }

    private fun sourceLeaked(outputNorm: String, src: String): Boolean =
        wholeContains(outputNorm, src)

    // --- report ---

    private fun buildReport(
        seed: TranslateEvalSeed,
        languages: List<TranslateEvalLang>,
        acc: Map<String, LinkedHashMap<String, Acc>>,
    ): String = buildString {
        appendLine("===== TRANSLATE-EVAL: Gemma multilingual translation (no glossary) =====")
        appendLine("Seed v${seed.version}, ${languages.size} languages, chunkSize=${seed.chunkSize}")
        appendLine("coverage = English target present (translation recall); leak = source token survived.")
        appendLine()

        FIELDS.forEach { field ->
            val perLang = acc.getValue(field)
            if (perLang.isEmpty()) return@forEach
            val totals = Acc()
            appendLine("── $field ──")
            perLang.forEach { (lang, a) ->
                totals.coverageTotal += a.coverageTotal; totals.coverageHit += a.coverageHit
                totals.leakTotal += a.leakTotal; totals.leakHit += a.leakHit
                appendLine(
                    "  ${lang.padEnd(3)} coverage=${pct(a.coverageHit, a.coverageTotal)} " +
                        "(${a.coverageHit}/${a.coverageTotal})  " +
                        "leak=${pct(a.leakHit, a.leakTotal)} (${a.leakHit}/${a.leakTotal})",
                )
            }
            appendLine(
                "  ALL coverage=${pct(totals.coverageHit, totals.coverageTotal)} " +
                    "(${totals.coverageHit}/${totals.coverageTotal})  " +
                    "leak=${pct(totals.leakHit, totals.leakTotal)} (${totals.leakHit}/${totals.leakTotal})",
            )
            appendLine()
        }

        appendLine("── examples: missed translations (source → expected | model output) ──")
        FIELDS.forEach { field ->
            acc.getValue(field).forEach { (lang, a) ->
                a.misses.forEach { appendLine("  [$lang/$field] $it") }
            }
        }
        appendLine()
        appendLine("── examples: source-token leaks ──")
        FIELDS.forEach { field ->
            acc.getValue(field).forEach { (lang, a) ->
                a.leaks.forEach { appendLine("  [$lang/$field] $it") }
            }
        }
    }

    private fun writeReport(context: Context, report: String) {
        val dir = CorpusFixture.fixturesDir(context)
        File(dir, "translate-eval-report.txt").writeText(report)
        report.lineSequence().forEach { Log.i(CorpusFixture.BENCHMARK_TAG, it) }
        Log.i(CorpusFixture.BENCHMARK_TAG, "Report written: ${File(dir, "translate-eval-report.txt").absolutePath}")
    }

    private fun pct(hit: Int, total: Int): String =
        if (total <= 0) "n/a" else "${(hit * 100) / total}%"

    // --- seed loading ---

    private fun loadSeed(testContext: Context): TranslateEvalSeed? {
        runCatching {
            testContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { return runCatching { TranslateEvalSeedLoader.parse(it) }.getOrNull() }

        val fallback = File(DATA_LOCAL_TMP_SEED)
        if (fallback.isFile) {
            return runCatching { TranslateEvalSeedLoader.parse(fallback.readText()) }.getOrNull()
        }
        return null
    }

    private companion object {
        private const val ASSET_NAME = "translate-eval-seed.json"
        private const val DATA_LOCAL_TMP_SEED = "/data/local/tmp/translate-eval-seed.json"
        private const val EXTRA_PROBE_CAP = 3
        private const val MAX_EXAMPLES = 8
        private val FIELDS = listOf("tastingNotes", "process", "roastLevel", "origin")
    }
}
