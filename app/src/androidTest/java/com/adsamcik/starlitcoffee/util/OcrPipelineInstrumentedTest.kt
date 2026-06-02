package com.adsamcik.starlitcoffee.util

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test for the OCR + barcode portion of the bag-scan pipeline.
 *
 * Pre-requisite: push test images to emulator via Gradle:
 *   ./gradlew pushTestImages
 *
 * **Scope (post field-extractor removal):** This file validates that the
 * on-device OCR + barcode primitives produce SOMETHING usable for every
 * bag in the corpus. Field-level extraction (name / roaster / origin /
 * region / variety / process / roastLevel / weight / dates / tasting
 * notes) moved entirely to the LLM stage — see
 * [com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider]
 * and `CoffeeBagCorpusExtractionTest` for ground-truth validation of LLM
 * outputs.
 *
 * What we still assert here:
 *  - ML Kit text recognition returns non-blank text for every bag side.
 *  - Either ML Kit's barcode scanner or [OcrFieldExtractor.extractBarcodeFromText]
 *    recovers a barcode on bags that have one (Beansmith).
 *  - [ImagePreprocessor.preprocessForOcr] doesn't break OCR (still returns
 *    a readable bitmap).
 */
@RunWith(AndroidJUnit4::class)
class OcrPipelineInstrumentedTest {

    companion object {
        private const val TAG = "OcrPipelineTest"
        private const val BAGS_DIR = "/data/local/tmp/coffee-bags"
        private const val BEANSMITHS = "beansmiths_ethiopia_gedeb"
    }

    data class BagImageSet(
        val name: String,
        val frontPath: String?,
        val backPath: String?,
    )

    private fun discoverBags(): List<BagImageSet> {
        val dir = File(BAGS_DIR)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles() ?: return emptyList()
        val grouped = files
            .filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            .groupBy {
                it.nameWithoutExtension
                    .removeSuffix("_front")
                    .removeSuffix("_back")
            }

        return grouped.map { (name, bagFiles) ->
            BagImageSet(
                name = name,
                frontPath = bagFiles.find { "_front" in it.name }?.absolutePath,
                backPath = bagFiles.find { "_back" in it.name }?.absolutePath,
            )
        }.sortedBy { it.name }
    }

    private fun bagPath(filename: String) = "$BAGS_DIR/$filename"

    private suspend fun runOcr(path: String): String? {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        } ?: return null

        return result.text
    }

    private suspend fun runBarcodeScan(path: String): List<String> {
        val bitmap = BitmapFactory.decodeFile(path) ?: return emptyList()
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()

        val barcodes = suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(emptyList(), null) }
        }

        return barcodes?.mapNotNull { it.rawValue } ?: emptyList()
    }

    // --- Beansmith's Ethiopia Gedeb (corpus-anchored assertions) ---

    @Test
    fun frontOcrProducesTextForBeansmithBag() { runBlocking {
        val path = bagPath("${BEANSMITHS}_front.jpg")
        assumeTrue("Beansmith front image must exist", File(path).exists())

        val text = runOcr(path)
        assertNotNull("Front OCR should not fail", text)
        Log.d(TAG, "=== FRONT OCR RAW TEXT (${text!!.length} chars) ===\n$text\n=== END ===")
        assertTrue("Front should contain some text", text.isNotBlank())
    } }

    @Test
    fun backOcrProducesTextForBeansmithBag() { runBlocking {
        val path = bagPath("${BEANSMITHS}_back.jpg")
        assumeTrue("Beansmith back image must exist", File(path).exists())

        val text = runOcr(path)
        assertNotNull("Back OCR should not fail", text)
        Log.d(TAG, "=== BACK OCR RAW TEXT (${text!!.length} chars) ===\n$text\n=== END ===")
        assertTrue("Back should contain some text", text.isNotBlank())
    } }

    @Test
    fun barcodeDetectionFindsBarcodeOnBeansmithBackViaMlKitOrOcrFallback() { runBlocking {
        val path = bagPath("${BEANSMITHS}_back.jpg")
        assumeTrue("Beansmith back image must exist", File(path).exists())

        val mlKitBarcodes = runBarcodeScan(path)
        Log.d(TAG, "ML Kit found ${mlKitBarcodes.size} barcode(s): $mlKitBarcodes")

        val text = runOcr(path) ?: ""
        val ocrBarcode = OcrFieldExtractor.extractBarcodeFromText(text)
        Log.d(TAG, "OCR barcode fallback: $ocrBarcode")

        val foundBarcode = mlKitBarcodes.firstOrNull() ?: ocrBarcode
        assertNotNull("Should detect barcode via ML Kit or OCR fallback", foundBarcode)
    } }

    @Test
    fun preprocessingPreservesOcrReadabilityForBeansmithBag() { runBlocking {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        for ((label, suffix) in listOf("FRONT" to "front", "BACK" to "back")) {
            val path = bagPath("${BEANSMITHS}_$suffix.jpg")
            val bitmap = BitmapFactory.decodeFile(path) ?: continue

            // Preprocessed (CLAHE + unsharp mask) shouldn't break OCR
            val preprocessed = ImagePreprocessor.preprocessForOcr(bitmap)
            val ppImage = InputImage.fromBitmap(preprocessed, 0)
            val ppResult = suspendCancellableCoroutine { cont ->
                recognizer.process(ppImage)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            val ppText = ppResult?.text ?: ""
            Log.d(TAG, "=== $label PREPROCESSED OCR (${ppText.length} chars) ===")
            assertTrue("$label preprocessed image should still produce OCR text", ppText.isNotBlank())
        }
    } }

    // --- All bags (generic OCR coverage) ---

    @Test
    fun ocrProducesTextForEveryBagImage() { runBlocking {
        val bags = discoverBags()
        assumeTrue(
            "No bag images found at $BAGS_DIR — run ./gradlew pushTestImages first",
            bags.isNotEmpty()
        )

        Log.d(TAG, "=== DISCOVERED ${bags.size} BAG(S) ===")
        val failures = mutableListOf<String>()

        for (bag in bags) {
            Log.d(TAG, "--- ${bag.name} (front=${bag.frontPath != null}, back=${bag.backPath != null}) ---")

            for (path in listOfNotNull(bag.frontPath, bag.backPath)) {
                val text = runOcr(path)
                val fileLabel = "${bag.name} / ${File(path).name}"
                if (text.isNullOrBlank()) {
                    failures.add(fileLabel)
                    Log.w(TAG, "$fileLabel produced no OCR text")
                } else {
                    Log.d(TAG, "$fileLabel — ${text.length} chars OCR'd")
                }
            }
        }

        Log.d(TAG, "=== SUMMARY: ${failures.size} OCR failure(s) ===")
        assertTrue(
            "OCR produced no text for: ${failures.joinToString()}",
            failures.isEmpty()
        )
    } }
}
