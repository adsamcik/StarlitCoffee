package com.adsamcik.starlitcoffee.util

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.benchmark.CorpusFixture
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the OCR + barcode portion of the bag-scan pipeline,
 * driven entirely by the committed synthetic corpus.
 *
 * Pre-requisite: push the corpus to the emulator via `./gradlew pushTestImages`.
 *
 * **Scope (post field-extractor removal):** validates that the on-device OCR +
 * barcode primitives produce SOMETHING usable for every bag in the corpus.
 * Field-level extraction moved entirely to the LLM stage — see
 * [com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider]
 * and `CoffeeBagCorpusExtractionTest` for ground-truth validation of LLM
 * outputs, and the `benchmark` package for the quality report + Q0 gate.
 *
 * What we still assert here:
 *  - ML Kit text recognition returns non-blank text for every bag side.
 *  - For bags whose metadata declares a barcode, ML Kit's scanner or
 *    [OcrFieldExtractor.extractBarcodeFromText] recovers a barcode on at least
 *    one of them.
 *  - [ImagePreprocessor.preprocessForOcr] doesn't break OCR.
 */
@RunWith(AndroidJUnit4::class)
class OcrPipelineInstrumentedTest {

    private companion object {
        const val TAG = "OcrPipelineTest"
    }

    private fun loadBags(): List<CoffeeBagFixture> = CorpusFixture.load()?.bags.orEmpty()

    private suspend fun runOcr(path: String): String? {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return null
        return result.text
    }

    private suspend fun runBarcodeScan(path: String): List<String> {
        val bitmap = BitmapFactory.decodeFile(path) ?: return emptyList()
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        val barcodes = suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
        return barcodes?.mapNotNull { it.rawValue } ?: emptyList()
    }

    private fun declaredBarcode(bag: CoffeeBagFixture): String? =
        (bag.extras["barcode"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    @Test
    fun ocrProducesTextForEveryBagImage() = runBlocking {
        val bags = loadBags()
        assumeTrue(
            "No corpus at ${CorpusFixture.CORPUS_DIR} — run ./gradlew pushTestImages first.",
            bags.isNotEmpty(),
        )

        val failures = mutableListOf<String>()
        for (bag in bags) {
            val sides = listOfNotNull(
                CorpusFixture.frontPhotoFile(bag),
                CorpusFixture.backPhotoFile(bag),
            ).filter { it.isFile }
            for (file in sides) {
                val text = runOcr(file.absolutePath)
                val label = "${bag.id} / ${file.name}"
                if (text.isNullOrBlank()) {
                    failures.add(label)
                    Log.w(TAG, "$label produced no OCR text")
                } else {
                    Log.d(TAG, "$label — ${text.length} chars OCR'd")
                }
            }
        }
        assertTrue("OCR produced no text for: ${failures.joinToString()}", failures.isEmpty())
    }

    @Test
    fun barcodeDetectionFindsAtLeastOneDeclaredBarcode() = runBlocking {
        val bags = loadBags()
        assumeTrue("No corpus — run ./gradlew pushTestImages first.", bags.isNotEmpty())

        val barcodeBags = bags.filter { declaredBarcode(it) != null }
        assumeTrue(
            "No corpus bag declares a barcode in its metadata extras — nothing to validate.",
            barcodeBags.isNotEmpty(),
        )

        var found = false
        for (bag in barcodeBags) {
            val back = CorpusFixture.backPhotoFile(bag)?.takeIf { it.isFile } ?: continue
            val mlKit = runBarcodeScan(back.absolutePath)
            val ocrFallback = OcrFieldExtractor.extractBarcodeFromText(runOcr(back.absolutePath) ?: "")
            Log.d(TAG, "${bag.id}: mlkit=$mlKit ocrFallback=$ocrFallback declared=${declaredBarcode(bag)}")
            if (mlKit.isNotEmpty() || ocrFallback != null) {
                found = true
                break
            }
        }
        assertTrue(
            "No barcode recovered (ML Kit or OCR fallback) on any of ${barcodeBags.size} bag(s) " +
                "that declare one.",
            found,
        )
    }

    @Test
    fun preprocessingPreservesOcrReadability() = runBlocking {
        val bags = loadBags()
        assumeTrue("No corpus — run ./gradlew pushTestImages first.", bags.isNotEmpty())

        val firstWithImage = bags.firstOrNull { CorpusFixture.frontPhotoFile(it).isFile }
        assumeTrue("No readable front image in corpus", firstWithImage != null)

        val front = CorpusFixture.frontPhotoFile(firstWithImage!!)
        val bitmap = BitmapFactory.decodeFile(front.absolutePath)
        assertNotNull("Front image must decode: ${front.path}", bitmap)

        val preprocessed = ImagePreprocessor.preprocessForOcr(bitmap!!)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val ppImage = InputImage.fromBitmap(preprocessed, 0)
        val ppResult = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            recognizer.process(ppImage)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        val ppText = ppResult?.text ?: ""
        Log.d(TAG, "${firstWithImage.id} preprocessed OCR: ${ppText.length} chars")
        assertTrue("Preprocessed image should still produce OCR text", ppText.isNotBlank())
    }
}
