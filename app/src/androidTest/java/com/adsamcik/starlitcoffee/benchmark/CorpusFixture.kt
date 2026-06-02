package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * Shared utilities for the bag-scan corpus benchmark.
 *
 * # Paths on device
 *
 *  - **Corpus input** (read-only for the test): `/data/local/tmp/coffee-bags/`.
 *    Push the JPEGs and `corpus_metadata.json` here via
 *    `adb push ...` or `./gradlew pushTestImages`. `/data/local/tmp/` is the
 *    standard ADB-pushable, instrumented-test-readable location on Android
 *    (same as the existing
 *    [com.adsamcik.starlitcoffee.util.OcrPipelineInstrumentedTest]).
 *
 *  - **OCR fixture output** (written by the tests):
 *    `targetContext.getExternalFilesDir("coffee-bags-fixtures")` which
 *    resolves to
 *    `/sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/`.
 *    This is app-owned external storage — writable by the app UID without
 *    any permission, persisted across reinstalls (well, until the user
 *    uninstalls the app), and pullable via
 *    `adb pull /sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/`.
 *    SELinux on API 30+ blocks app UIDs from writing to `/data/local/tmp/`
 *    regardless of POSIX permissions, so that path can only host the
 *    read-side corpus, not the write-side fixtures.
 *
 * # Two tests consume this
 *
 *  - [com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest] runs once
 *    when the OCR pipeline changes and writes the captured OCR text.
 *  - [com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest] reads
 *    those fixtures + the metadata, calls the real LLM through Mindlayer,
 *    and logs per-field accuracy without an emulator UI loop.
 *
 * Photos themselves stay off-repo by policy.
 */
internal object CorpusFixture {

    /**
     * Read-only corpus input directory on the device. ADB-pushable.
     * Not used for fixture output (SELinux blocks app writes here).
     */
    const val CORPUS_DIR: String = "/data/local/tmp/coffee-bags"

    /** Tag used for all benchmark logcat output. Grep on this. */
    const val BENCHMARK_TAG: String = "StarlitBagBenchmark"

    /** Sub-folder name for OCR fixtures inside the app's external files dir. */
    private const val FIXTURES_SUBDIR: String = "coffee-bags-fixtures"

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Returns the corpus loaded from `corpus_metadata.json`, or `null`
     * when the file is missing (callers should skip cleanly via JUnit
     * `Assume.assumeTrue`).
     */
    fun load(): CoffeeBagCorpus? {
        val metadataFile = File(CORPUS_DIR, "corpus_metadata.json")
        if (!metadataFile.isFile) return null
        return runCatching {
            lenientJson.decodeFromString(CoffeeBagCorpus.serializer(), metadataFile.readText())
        }.getOrNull()
    }

    fun frontPhotoFile(fixture: CoffeeBagFixture): File =
        File(CORPUS_DIR, fixture.photos.front)

    fun backPhotoFile(fixture: CoffeeBagFixture): File? =
        fixture.photos.back?.let { File(CORPUS_DIR, it) }

    /**
     * App-owned writable fixtures dir, derived from the under-test app's
     * external files dir. Caller must pass `targetContext`, not the test
     * APK's context — the LLM benchmark needs both tests' outputs to land
     * in the same place.
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

@Serializable
internal data class CoffeeBagCorpus(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val bags: List<CoffeeBagFixture>,
)

@Serializable
internal data class CoffeeBagFixture(
    val id: String,
    val photos: CoffeeBagPhotos,
    val language: List<String> = emptyList(),
    val fields: Map<String, JsonElement> = emptyMap(),
    val extras: Map<String, JsonElement> = emptyMap(),
    val notes: String? = null,
)

@Serializable
internal data class CoffeeBagPhotos(
    val front: String,
    val back: String? = null,
)

