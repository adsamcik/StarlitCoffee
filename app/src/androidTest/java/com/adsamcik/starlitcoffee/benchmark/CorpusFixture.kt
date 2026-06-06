package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagCorpus
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagCorpusLoader
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import java.io.File

/**
 * Shared device-side utilities for the bag-scan corpus benchmark and quality
 * tests. The corpus schema/model itself lives in the Android-free shared
 * harness ([com.adsamcik.starlitcoffee.test.corpus.CoffeeBagCorpus]); this
 * object only knows about device paths.
 *
 * # Paths on device
 *
 *  - **Corpus input** (read-only for the test): `/data/local/tmp/coffee-bags/`.
 *    Push the committed sidecar corpus (`*.metadata.json` + sibling images)
 *    here via
 *    `./gradlew pushTestImages`. Photo paths in the metadata are relative to
 *    this root (e.g. `scb-001-en-q0_moonlit_orchard.front.webp`).
 *
 *  - **OCR fixture + report output** (written by the tests):
 *    `targetContext.getExternalFilesDir("coffee-bags-fixtures")` →
 *    `/sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/`.
 *    App-owned external storage, writable without permission and pullable via
 *    `adb pull`. SELinux on API 30+ blocks app writes to `/data/local/tmp/`,
 *    so that path hosts only the read-side corpus.
 *
 * # Consumers
 *
 *  - [OcrFixtureCaptureTest] captures OCR fixtures once when the OCR pipeline
 *    changes.
 *  - [LlmCorpusBenchmarkTest] reads those fixtures + the metadata, calls the
 *    real LLM, and writes a structured
 *    [com.adsamcik.starlitcoffee.test.corpus.QualityReport].
 *  - [BagScanBestCaseGateTest] runs the full OCR+LLM pipeline on the Q0
 *    subset and asserts the best-case gate.
 */
internal object CorpusFixture {

    /** Read-only corpus input directory on the device. ADB-pushable. */
    const val CORPUS_DIR: String = "/data/local/tmp/coffee-bags"

    /** Tag used for all benchmark logcat output. Grep on this. */
    const val BENCHMARK_TAG: String = "StarlitBagBenchmark"

    /** Sub-folder name for OCR fixtures + reports inside the app's external files dir. */
    private const val FIXTURES_SUBDIR: String = "coffee-bags-fixtures"

    /**
     * Returns the automation-ready corpus loaded from sidecar metadata, or
     * `null` when the corpus is missing (callers should skip cleanly via JUnit
     * `Assume.assumeTrue`).
     */
    fun load(): CoffeeBagCorpus? {
        val corpusDir = File(CORPUS_DIR)
        if (!CoffeeBagCorpusLoader.looksLikeCorpusDir(corpusDir)) return null
        return runCatching {
            CoffeeBagCorpusLoader.loadAutomationReady(corpusDir)
        }.getOrNull()
    }

    fun frontPhotoFile(fixture: CoffeeBagFixture): File =
        File(CORPUS_DIR, requireNotNull(fixture.photos.front) { "Fixture '${fixture.id}' has no front photo" })

    fun backPhotoFile(fixture: CoffeeBagFixture): File? =
        fixture.photos.back?.let { File(CORPUS_DIR, it) }

    /**
     * App-owned writable fixtures/report dir, derived from the under-test
     * app's external files dir. Caller must pass `targetContext`, not the test
     * APK's context.
     */
    fun fixturesDir(targetContext: Context): File {
        val external = targetContext.getExternalFilesDir(FIXTURES_SUBDIR)
            ?: error("getExternalFilesDir returned null — emulator external storage not mounted?")
        external.mkdirs()
        return external
    }

    fun frontOcrFixtureFile(targetContext: Context, fixture: CoffeeBagFixture): File =
        File(fixturesDir(targetContext), "${fixture.id}.front.ocr.txt")

    fun backOcrFixtureFile(targetContext: Context, fixture: CoffeeBagFixture): File =
        File(fixturesDir(targetContext), "${fixture.id}.back.ocr.txt")
}
