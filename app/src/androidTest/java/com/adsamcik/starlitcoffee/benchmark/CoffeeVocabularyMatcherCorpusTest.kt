package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabulary
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabularyLoader
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyMatcher
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyEntry
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-integration validation of the coffee-vocabulary grounding on a device.
 *
 * Runs the ACTUAL production path against real data:
 *  1. Loads the shipped `assets/coffee_filter_vocabulary.json` through the
 *     Android [android.content.res.AssetManager] via
 *     [CoffeeFilterVocabularyLoader.getInstance] — the exact code
 *     `BagPhotoExtractor` uses on device (not a JVM `File` read).
 *  2. Loads the committed synthetic corpus (143 labelled bags) pushed to the
 *     device by `./gradlew pushTestImages`.
 *  3. Feeds every bag its OCR text — the REAL captured OCR fixture when
 *     [OcrFixtureCaptureTest] has produced one, otherwise a label reconstructed
 *     from the bag's ground-truth fields — and measures how well
 *     [CoffeeVocabularyMatcher] surfaces each field's ground-truth value as an
 *     LLM hint (alias-aware per-field recall).
 *
 * ## Scope of the gate
 *
 * The curated vocabulary is intentionally English/Latin; non-English labels are
 * handled downstream by the LLM's translation step, not by these hints. So the
 * hard gate is measured on the **English subset** (`language` contains `"en"`),
 * where the vocabulary is expected to apply. Recall across ALL languages is
 * logged for information only (it is naturally lower and is not a defect).
 *
 * Missing corpus cleanly skips a casual run; pass
 * `-e starlit.quality.required true` to hard-fail instead.
 *
 * ```
 * ./gradlew pushTestImages
 * ./gradlew connectedDebugAndroidTest \
 *   "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.CoffeeVocabularyMatcherCorpusTest"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class CoffeeVocabularyMatcherCorpusTest {

    private class Recall {
        var expected = 0
        var covered = 0
        val ratio: Double get() = if (expected == 0) 1.0 else covered.toDouble() / expected
        fun summary() = "%.3f (%d/%d)".format(ratio, covered, expected)
    }

    private class FieldProbe(
        val label: String,
        val metadataKey: String,
        val hints: (KnownFieldValues) -> List<String>,
        val entries: (CoffeeFilterVocabulary) -> List<CoffeeVocabularyEntry>,
        val gate: Double,
    )

    @Test
    fun matcherRecallsEnglishCorpusFieldValuesOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // (1) Real AssetManager load — the production loading path.
        val vocabulary = CoffeeFilterVocabularyLoader.getInstance(context)
        assertFalse("Bundled coffee_filter_vocabulary.json failed to load via AssetManager", vocabulary.isEmpty)
        assertTrue("origins coverage", vocabulary.origins.size >= MIN_ORIGINS)
        assertTrue("varieties coverage", vocabulary.varieties.size >= MIN_VARIETIES)
        assertTrue("tastingNotes coverage", vocabulary.tastingNotes.size >= MIN_NOTES)

        // (2) Real corpus pushed to the device.
        val corpus = CorpusFixture.load()
        QualityTestSupport.requireOrAssume(
            "Corpus sidecar metadata not present at ${CorpusFixture.CORPUS_DIR}/*.metadata.json — " +
                "run ./gradlew pushTestImages.",
            corpus != null,
        )
        val bags = corpus!!.bags
        assertTrue("Corpus must contain bags", bags.isNotEmpty())

        val probes = listOf(
            FieldProbe("origin", "origin", { it.origins }, { it.origins }, ORIGIN_GATE),
            FieldProbe("process", "process", { it.processTypes }, { it.processTypes }, PROCESS_GATE),
            FieldProbe("roastLevel", "roastLevel", { it.roastLevels }, { it.roastLevels }, ROAST_GATE),
            FieldProbe("region", "region", { it.regions }, { it.regions }, REGION_GATE),
            FieldProbe("variety", "variety", { it.varieties }, { it.varieties }, VARIETY_GATE),
            FieldProbe("tastingNotes", "tastingNotes", { it.tastingNotes }, { it.tastingNotes }, NOTES_GATE),
        )
        val termsByValue = probes.associate { probe ->
            probe.label to probe.entries(vocabulary).associate { it.value to it.allTerms }
        }
        val recallAll = probes.associateWith { Recall() }
        val recallEn = probes.associateWith { Recall() }

        var englishBags = 0
        var englishBagsWithHint = 0
        var ocrFixtureBags = 0

        for (bag in bags) {
            val isEnglish = bag.language.contains("en")
            val fromFixture = realOcrText(context, bag)
            if (fromFixture != null) ocrFixtureBags++
            val ocrText = fromFixture ?: reconstructLabel(bag)

            val hints = CoffeeVocabularyMatcher.match(ocrText, vocabulary)
            val anyHint = probes.any { it.hints(hints).isNotEmpty() }
            if (isEnglish) {
                englishBags++
                if (anyHint) englishBagsWithHint++
            }

            probes.forEach { probe ->
                scoreField(
                    bag = bag,
                    probe = probe,
                    hints = probe.hints(hints),
                    terms = termsByValue.getValue(probe.label),
                    all = recallAll.getValue(probe),
                    english = recallEn.getValue(probe).takeIf { isEnglish },
                )
            }
        }

        writeAndLogReport(context, bags.size, englishBags, ocrFixtureBags, probes, recallAll, recallEn)

        // Run validity: the English subset must be non-trivial and every English
        // bag must produce at least one vocabulary hint.
        assertTrue("Corpus has too few English bags ($englishBags) to gate on", englishBags >= MIN_ENGLISH_BAGS)
        assertTrue(
            "Only $englishBagsWithHint/$englishBags English bags produced any hint",
            englishBagsWithHint >= (englishBags * MIN_HINT_RATIO).toInt(),
        )
        // Coverage gate on the English subset, per field.
        probes.forEach { probe ->
            val r = recallEn.getValue(probe)
            assertTrue(
                "[en] ${probe.label} recall ${r.summary()} below gate ${probe.gate}",
                r.ratio >= probe.gate,
            )
        }
    }

    private fun scoreField(
        bag: CoffeeBagFixture,
        probe: FieldProbe,
        hints: List<String>,
        terms: Map<String, List<String>>,
        all: Recall,
        english: Recall?,
    ) {
        if (bag.isNotVisible(probe.metadataKey)) return
        val groundTruth = bag.groundTruth(probe.metadataKey) ?: return
        splitValues(groundTruth).forEach { expected ->
            // Values our normalizer can't represent (non-Latin scripts) are out of
            // vocabulary scope — the LLM translates them — so don't score them.
            if (CoffeeMetadataNormalizer.normalizeSearch(expected).isBlank()) return@forEach
            val surfaced = valueSurfaced(expected, hints, terms)
            all.expected++
            if (surfaced) all.covered++
            english?.let {
                it.expected++
                if (surfaced) {
                    it.covered++
                } else {
                    Log.i(CorpusFixture.BENCHMARK_TAG, "  [en] miss ${bag.id} ${probe.label}='$expected' hints=$hints")
                }
            }
        }
    }

    /** Prefer real captured OCR (front+back) when [OcrFixtureCaptureTest] has run. */
    private fun realOcrText(context: Context, bag: CoffeeBagFixture): String? {
        val parts = listOf(
            CorpusFixture.frontOcrFixtureFile(context, bag),
            CorpusFixture.backOcrFixtureFile(context, bag),
        ).filter(File::isFile).map { it.readText() }.filter { it.isNotBlank() }
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Realistic multi-line label built from the bag's ground-truth fields. */
    private fun reconstructLabel(bag: CoffeeBagFixture): String = buildString {
        fun line(prefix: String, key: String) {
            bag.groundTruth(key)?.let { appendLine("$prefix$it") }
        }
        line("", "name")
        line("Roasted by ", "roaster")
        line("Origin: ", "origin")
        line("Region: ", "region")
        line("Farm: ", "farm")
        line("Variety: ", "variety")
        line("Process: ", "process")
        line("Roast: ", "roastLevel")
        line("Tasting notes: ", "tastingNotes")
        line("Net weight ", "weight")
    }

    /**
     * Alias-aware: an expected value counts as surfaced when a returned hint's
     * vocabulary entry (canonical value OR any alias) matches it, so a hint of
     * "Sidamo" credits the bag's "Sidama" and "East Timor" credits "Timor-Leste".
     */
    private fun valueSurfaced(expected: String, hints: List<String>, terms: Map<String, List<String>>): Boolean {
        val normalizedExpected = CoffeeMetadataNormalizer.normalizeSearch(expected)
        if (normalizedExpected.isBlank()) return false
        val paddedExpected = " $normalizedExpected "
        return hints.any { hint ->
            (terms[hint] ?: listOf(hint)).any { term ->
                val normalizedTerm = CoffeeMetadataNormalizer.normalizeSearch(term)
                normalizedTerm.isNotBlank() && (
                    normalizedTerm == normalizedExpected ||
                        paddedExpected.contains(" $normalizedTerm ") ||
                        " $normalizedTerm ".contains(paddedExpected)
                    )
            }
        }
    }

    private fun splitValues(value: String): List<String> =
        value.split(',', ';', '/').map(String::trim).filter(String::isNotBlank)

    private fun writeAndLogReport(
        context: Context,
        bagCount: Int,
        englishBags: Int,
        ocrFixtureBags: Int,
        probes: List<FieldProbe>,
        recallAll: Map<FieldProbe, Recall>,
        recallEn: Map<FieldProbe, Recall>,
    ) {
        val report = buildString {
            appendLine("=== Vocabulary matcher corpus recall ===")
            appendLine("bags=$bagCount  english=$englishBags  ocrFixtureBags=$ocrFixtureBags")
            appendLine("-- English subset (gated) --")
            probes.forEach { appendLine("  ${it.label.padEnd(14)} ${recallEn.getValue(it).summary()}") }
            appendLine("-- All languages (informational) --")
            probes.forEach { appendLine("  ${it.label.padEnd(14)} ${recallAll.getValue(it).summary()}") }
        }
        report.lineSequence().forEach { Log.i(CorpusFixture.BENCHMARK_TAG, it) }
        runCatching {
            File(CorpusFixture.fixturesDir(context), "vocab-matcher-corpus-report.txt").writeText(report)
        }
    }

    private companion object {
        private const val MIN_ORIGINS = 30
        private const val MIN_VARIETIES = 40
        private const val MIN_NOTES = 60
        private const val MIN_ENGLISH_BAGS = 10
        private const val MIN_HINT_RATIO = 0.95
        private const val ORIGIN_GATE = 0.95
        private const val PROCESS_GATE = 0.95
        private const val ROAST_GATE = 0.95
        private const val REGION_GATE = 0.90
        private const val VARIETY_GATE = 0.90
        private const val NOTES_GATE = 0.90
    }
}
