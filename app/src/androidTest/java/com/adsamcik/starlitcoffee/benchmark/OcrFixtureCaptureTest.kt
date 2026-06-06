package com.adsamcik.starlitcoffee.benchmark

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.ocr.HierarchicalOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One-time OCR fixture capture for the bag-scan benchmark.
 *
 * For every bag in the corpus this test runs the full OCR pipeline
 * (same code path the production bag scan uses — [HierarchicalOcrService]
 * over [MindlayerOcrService], with the [ImagePreprocessor] alignment +
 * enhancement passes) and writes the resulting `fullText` to the app's
 * external files dir (`CorpusFixture.fixturesDir`), one file per
 * `{bagId}.{side}.ocr.txt`.
 *
 * The fixtures are then consumed by [LlmCorpusBenchmarkTest] — that test
 * is the daily-driver iteration loop and only needs OCR text, not bitmaps,
 * which lets us re-run the LLM benchmark in seconds instead of re-running
 * the full pipeline (~90s per bag).
 *
 * # When to re-run
 *
 * - Any change to [MindlayerOcrService], [HierarchicalOcrService], or
 *   [ImagePreprocessor] potentially changes the OCR output — re-capture.
 * - Any change to PaddleOCR model packs in Mindlayer — re-capture.
 *
 * # When NOT to re-run
 *
 * - Changes to the LLM prompt, [com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider],
 *   or anything downstream of OCR — the fixtures stay valid; just re-run
 *   the LLM benchmark.
 *
 * # Running
 *
 * ```
 * ./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"
 * ```
 *
 * Requires the corpus pushed via `./gradlew pushTestImages` (to
 * `/data/local/tmp/coffee-bags/`) and Mindlayer's OCR service installed + ready.
 */
@RunWith(AndroidJUnit4::class)
class OcrFixtureCaptureTest {

    @Test
    fun captureOcrForEveryBagInCorpus() = runBlocking {
        val corpus = CorpusFixture.load()
        assumeTrue(
            "Corpus metadata not present at ${CorpusFixture.CORPUS_DIR}/corpus_metadata.json. " +
                "Push the corpus and re-run.",
            corpus != null,
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixturesDir = CorpusFixture.fixturesDir(context)
        assertTrue("Fixtures dir must be creatable", fixturesDir.isDirectory)

        val ocr: OcrService = HierarchicalOcrService(MindlayerOcrService(context))

        assumeTrue(
            "Mindlayer OCR not available — start the service and approve com.adsamcik.starlitcoffee " +
                "in the Mindlayer dashboard, then retry.",
            QualityTestSupport.awaitTrueSuspending { ocr.isAvailable() },
        )

        try {
            val captured = mutableListOf<String>()
            val skipped = mutableListOf<String>()

            for (bag in corpus!!.bags) {
                val frontFile = CorpusFixture.frontPhotoFile(bag)
                val backFile = CorpusFixture.backPhotoFile(bag)
                Log.i(
                    CorpusFixture.BENCHMARK_TAG,
                    "Capturing OCR for ${bag.id} " +
                        "(front=${frontFile.exists()}, back=${backFile?.exists() == true})",
                )
                captureSide(ocr, bag, frontFile, CorpusFixture.frontOcrFixtureFile(context, bag))?.let {
                    captured.add("${bag.id}.front (${it.length} chars)")
                } ?: skipped.add("${bag.id}.front")

                if (backFile != null) {
                    captureSide(ocr, bag, backFile, CorpusFixture.backOcrFixtureFile(context, bag))?.let {
                        captured.add("${bag.id}.back (${it.length} chars)")
                    } ?: skipped.add("${bag.id}.back")
                }
            }

            Log.i(CorpusFixture.BENCHMARK_TAG, "=== OCR CAPTURE SUMMARY ===")
            Log.i(CorpusFixture.BENCHMARK_TAG, "Captured ${captured.size} fixture(s):")
            captured.forEach { Log.i(CorpusFixture.BENCHMARK_TAG, "  ✓ $it") }
            if (skipped.isNotEmpty()) {
                Log.w(CorpusFixture.BENCHMARK_TAG, "Skipped ${skipped.size} side(s):")
                skipped.forEach { Log.w(CorpusFixture.BENCHMARK_TAG, "  ✗ $it") }
            }
            assertTrue(
                "Captured zero OCR fixtures — verify bag photos are pushed",
                captured.isNotEmpty(),
            )
        } finally {
            ocr.close()
        }
    }

    /**
     * Run the full production OCR pipeline on one side of one bag and
     * persist the merged text. Mirrors the 3-pass dance in
     * [com.adsamcik.starlitcoffee.viewmodel.BrewViewModel.processBagPhoto]:
     *  - pass 1: original bitmap
     *  - pass 2: aligned (deskewed using pass-1 block geometry)
     *  - pass 3: enhanced (CLAHE + unsharp mask on top of aligned)
     * The merged text feeds the LLM in production, so the fixture has to
     * carry the same 3-pass content for the benchmark to be representative.
     *
     * Returns the merged fullText written to [outputFile], or `null` when
     * the photo couldn't be decoded or every pass returned nothing.
     */
    private suspend fun captureSide(
        ocr: OcrService,
        bag: CoffeeBagFixture,
        photoFile: File,
        outputFile: File,
    ): String? {
        if (!photoFile.isFile) {
            Log.w(CorpusFixture.BENCHMARK_TAG, "Missing photo for ${bag.id}: $photoFile")
            return null
        }
        val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        if (rawBitmap == null) {
            Log.w(CorpusFixture.BENCHMARK_TAG, "Failed to decode bitmap: $photoFile")
            return null
        }
        val bitmap = ImagePreprocessor.applyExifRotation(rawBitmap, photoFile.absolutePath)

        // Pass 1: original
        val originalText = ocr.recognize(bitmap)

        // Pass 2: aligned (deskew + crop based on pass-1 block geometry)
        val alignedBitmap = if (originalText != null && originalText.blocks.isNotEmpty()) {
            val alignment = ImagePreprocessor.computeAlignment(originalText.blocks)
            ImagePreprocessor.applyAlignment(bitmap, alignment)
        } else {
            bitmap
        }
        val alignedText = ocr.recognize(alignedBitmap)

        // Pass 3: enhanced (CLAHE + unsharp mask)
        val enhancedBitmap = ImagePreprocessor.preprocessForOcr(alignedBitmap)
        val enhancedText = ocr.recognize(enhancedBitmap)

        val merged = listOfNotNull(
            originalText?.fullText?.takeIf { it.isNotBlank() },
            alignedText?.fullText?.takeIf { it.isNotBlank() },
            enhancedText?.fullText?.takeIf { it.isNotBlank() },
        ).joinToString("\n").trim()

        if (merged.isBlank()) {
            Log.w(
                CorpusFixture.BENCHMARK_TAG,
                "All 3 OCR passes returned empty for ${bag.id} (${photoFile.name})",
            )
            return null
        }
        outputFile.writeText(merged)
        Log.i(
            CorpusFixture.BENCHMARK_TAG,
            "  ${photoFile.name}: ${originalText?.fullText?.length ?: 0}+" +
                "${alignedText?.fullText?.length ?: 0}+" +
                "${enhancedText?.fullText?.length ?: 0}=${merged.length} chars merged",
        )
        return merged
    }
}
