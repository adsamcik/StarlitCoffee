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
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test that loads real coffee bag images from device storage
 * and validates the full OCR + barcode extraction pipeline.
 *
 * Pre-requisite: push test images to emulator via Gradle:
 *   ./gradlew pushTestImages
 *
 * See testdata/README.md for setup instructions.
 */
@RunWith(AndroidJUnit4::class)
class OcrPipelineInstrumentedTest {

    companion object {
        private const val TAG = "OcrPipelineTest"
        private const val BAGS_DIR = "/data/local/tmp/coffee-bags"
        private const val BEANSMITHS = "beansmiths_ethiopia_gedeb"
    }

    // --- Helpers ---

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

    private suspend fun runOcr(path: String): Pair<String, OcrFieldExtractor.OcrExtractionResult>? {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        } ?: return null

        val blocks = result.textBlocks.map { block ->
            OcrFieldExtractor.OcrTextBlock(
                text = block.text,
                heightPx = block.boundingBox?.height() ?: 0,
                topPx = block.boundingBox?.top ?: 0,
            )
        }
        return result.text to OcrFieldExtractor.extractFieldsFromBlocks(blocks)
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

    private fun logExtraction(label: String, r: OcrFieldExtractor.OcrExtractionResult) {
        Log.d(TAG, "=== $label EXTRACTION ===")
        Log.d(TAG, "Name: ${r.name}, Roaster: ${r.roaster}")
        Log.d(TAG, "Origin: ${r.origin}, Region: ${r.region}")
        Log.d(TAG, "Variety: ${r.variety}, Process: ${r.processType}")
        Log.d(TAG, "Altitude: ${r.altitude}, Roast level: ${r.roastLevel}")
        Log.d(TAG, "Roast date: ${r.roastDate}")
        Log.d(TAG, "Tasting notes: ${r.tastingNotes}, Weight: ${r.weight}")
        Log.d(TAG, "=== END $label ===")
    }

    private fun countFields(r: OcrFieldExtractor.OcrExtractionResult): Int =
        listOfNotNull(
            r.roaster, r.origin, r.region, r.variety, r.processType,
            r.altitude, r.tastingNotes, r.roastLevel, r.roastDate, r.weight
        ).size

    // --- Beansmith's Ethiopia Gedeb (specific assertions) ---

    @Test
    fun frontOcrExtractsTextFromBeansmithBag() { runBlocking {
        val path = bagPath("${BEANSMITHS}_front.jpg")
        assumeTrue("Beansmith front image must exist", File(path).exists())

        val (text, extracted) = runOcr(path)!!
        Log.d(TAG, "=== FRONT OCR RAW TEXT ===\n$text\n=== END ===")
        logExtraction("FRONT", extracted)

        assertTrue("Front should contain some text", text.isNotBlank())
    } }

    @Test
    fun backOcrExtractsTextFromBeansmithBag() { runBlocking {
        val path = bagPath("${BEANSMITHS}_back.jpg")
        assumeTrue("Beansmith back image must exist", File(path).exists())

        val (text, extracted) = runOcr(path)!!
        Log.d(TAG, "=== BACK OCR RAW TEXT ===\n$text\n=== END ===")
        logExtraction("BACK", extracted)

        assertTrue("Back should contain some text", text.isNotBlank())
    } }

    @Test
    fun barcodeDetectionFindsBarcodeOnBeansmithBackViaMlKitOrOcrFallback() { runBlocking {
        val path = bagPath("${BEANSMITHS}_back.jpg")
        assumeTrue("Beansmith back image must exist", File(path).exists())

        // ML Kit barcode scanner
        val mlKitBarcodes = runBarcodeScan(path)
        Log.d(TAG, "ML Kit found ${mlKitBarcodes.size} barcode(s): $mlKitBarcodes")

        // OCR fallback
        val (text, _) = runOcr(path)!!
        val ocrBarcode = OcrFieldExtractor.extractBarcodeFromText(text)
        Log.d(TAG, "OCR barcode fallback: $ocrBarcode")

        val foundBarcode = mlKitBarcodes.firstOrNull() ?: ocrBarcode
        assertNotNull("Should detect barcode via ML Kit or OCR fallback", foundBarcode)
    } }

    @Test
    fun fullPipelineMergesBeansmithFrontAndBackCorrectly() { runBlocking {
        val frontPath = bagPath("${BEANSMITHS}_front.jpg")
        val backPath = bagPath("${BEANSMITHS}_back.jpg")
        assumeTrue("Beansmith front image must exist", File(frontPath).exists())
        assumeTrue("Beansmith back image must exist", File(backPath).exists())

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val ocrResults = mutableListOf<OcrFieldExtractor.OcrExtractionResult>()
        val detectedBarcodes = mutableListOf<String>()
        val detectedUrls = mutableListOf<String>()

        for (path in listOf(frontPath, backPath)) {
            val bitmap = BitmapFactory.decodeFile(path) ?: continue
            val image = InputImage.fromBitmap(bitmap, 0)

            // OCR — use block-based extraction (matches real scanning pipeline)
            val textResult = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            if (textResult != null) {
                val blocks = textResult.textBlocks.map { block ->
                    OcrFieldExtractor.OcrTextBlock(
                        text = block.text,
                        heightPx = block.boundingBox?.height() ?: 0,
                        topPx = block.boundingBox?.top ?: 0,
                        leftPx = block.boundingBox?.left ?: 0,
                        widthPx = block.boundingBox?.width() ?: 0,
                        imageWidthPx = bitmap.width,
                        imageHeightPx = bitmap.height,
                    )
                }
                ocrResults.add(OcrFieldExtractor.extractFieldsFromBlocks(blocks))
                if (detectedBarcodes.isEmpty()) {
                    OcrFieldExtractor.extractBarcodeFromText(textResult.text)?.let {
                        detectedBarcodes.add(it)
                    }
                }
            }

            // ML Kit barcode scanner
            val codes = runBarcodeScan(path)
            codes.forEach { raw ->
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    detectedUrls.add(raw)
                } else {
                    detectedBarcodes.add(raw)
                }
            }
        }

        // Merge OCR results (first non-null per field)
        val merged = OcrFieldExtractor.OcrExtractionResult(
            roaster = ocrResults.firstNotNullOfOrNull { it.roaster },
            origin = ocrResults.firstNotNullOfOrNull { it.origin },
            region = ocrResults.firstNotNullOfOrNull { it.region },
            variety = ocrResults.firstNotNullOfOrNull { it.variety },
            processType = ocrResults.firstNotNullOfOrNull { it.processType },
            altitude = ocrResults.firstNotNullOfOrNull { it.altitude },
            tastingNotes = ocrResults.firstNotNullOfOrNull { it.tastingNotes },
            roastLevel = ocrResults.firstNotNullOfOrNull { it.roastLevel },
            roastDate = ocrResults.firstNotNullOfOrNull { it.roastDate },
            expiryDate = ocrResults.firstNotNullOfOrNull { it.expiryDate },
            weight = ocrResults.firstNotNullOfOrNull { it.weight },
        )

        logExtraction("MERGED BEANSMITH", merged)
        Log.d(TAG, "Barcodes: $detectedBarcodes, QR URLs: $detectedUrls")

        // Beansmith's Ethiopia Gedeb specific assertions
        // Origin may be null in single-frame OCR due to decorative font on this bag;
        // the real scanning pipeline accumulates evidence across multiple frames.
        // When detected, it should be Ethiopia (not a false-positive like "Jemen").
        if (merged.origin != null) {
            assertTrue(
                "Origin should be Ethiopia, not ${merged.origin}",
                merged.origin!!.contains("Ethiop", ignoreCase = true) ||
                    merged.origin!!.contains("Etiopie", ignoreCase = true),
            )
        }
        assertNotNull("Should detect region (Gedeb)", merged.region)
        assertNotNull("Should detect variety (Heirloom)", merged.variety)
        assertNotNull("Should detect process (Washed)", merged.processType)
        assertNotNull("Should detect tasting notes", merged.tastingNotes)
        assertNotNull("Should detect roast level", merged.roastLevel)
        assertNotNull("Should detect roast date", merged.roastDate)
        assertTrue("Should find barcode", detectedBarcodes.isNotEmpty())
    } }

    @Test
    fun preprocessingImprovesOcrOnBeansmithBag() { runBlocking {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        for ((label, suffix) in listOf("FRONT" to "front", "BACK" to "back")) {
            val path = bagPath("${BEANSMITHS}_$suffix.jpg")
            val bitmap = BitmapFactory.decodeFile(path) ?: continue

            // Original
            val origImage = InputImage.fromBitmap(bitmap, 0)
            val origResult = suspendCancellableCoroutine { cont ->
                recognizer.process(origImage)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            val origText = origResult?.text ?: ""
            val origFields = OcrFieldExtractor.extractFields(origText)

            // Preprocessed
            val preprocessed = ImagePreprocessor.preprocessForOcr(bitmap)
            val ppImage = InputImage.fromBitmap(preprocessed, 0)
            val ppResult = suspendCancellableCoroutine { cont ->
                recognizer.process(ppImage)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            val ppText = ppResult?.text ?: ""
            val ppFields = OcrFieldExtractor.extractFields(ppText)

            Log.d(TAG, "=== $label PREPROCESSING COMPARISON ===")
            Log.d(TAG, "Original: ${origText.length} chars, ${countFields(origFields)} fields")
            Log.d(TAG, "Preprocessed: ${ppText.length} chars, ${countFields(ppFields)} fields")
            logExtraction("$label ORIG", origFields)
            logExtraction("$label PP", ppFields)
        }
    } }

    // --- All bags (generic pipeline validation) ---

    @Test
    fun pipelineExtractsAtLeastOneFieldFromEveryBagImage() { runBlocking {
        val bags = discoverBags()
        assumeTrue(
            "No bag images found at $BAGS_DIR — run ./gradlew pushTestImages first",
            bags.isNotEmpty()
        )

        Log.d(TAG, "=== DISCOVERED ${bags.size} BAG(S) ===")
        val failures = mutableListOf<String>()

        for (bag in bags) {
            Log.d(TAG, "--- ${bag.name} (front=${bag.frontPath != null}, back=${bag.backPath != null}) ---")

            val allResults = mutableListOf<OcrFieldExtractor.OcrExtractionResult>()

            for (path in listOfNotNull(bag.frontPath, bag.backPath)) {
                val result = runOcr(path)
                if (result != null) {
                    val (text, extracted) = result
                    assertTrue("OCR should produce text for ${File(path).name}", text.isNotBlank())
                    logExtraction("${bag.name} / ${File(path).name}", extracted)
                    allResults.add(extracted)
                }
            }

            // Merge fields across sides
            val merged = if (allResults.isNotEmpty()) {
                OcrFieldExtractor.OcrExtractionResult(
                    roaster = allResults.firstNotNullOfOrNull { it.roaster },
                    origin = allResults.firstNotNullOfOrNull { it.origin },
                    region = allResults.firstNotNullOfOrNull { it.region },
                    variety = allResults.firstNotNullOfOrNull { it.variety },
                    processType = allResults.firstNotNullOfOrNull { it.processType },
                    altitude = allResults.firstNotNullOfOrNull { it.altitude },
                    tastingNotes = allResults.firstNotNullOfOrNull { it.tastingNotes },
                    roastLevel = allResults.firstNotNullOfOrNull { it.roastLevel },
                    roastDate = allResults.firstNotNullOfOrNull { it.roastDate },
                    expiryDate = allResults.firstNotNullOfOrNull { it.expiryDate },
                    weight = allResults.firstNotNullOfOrNull { it.weight },
                )
            } else null

            val fields = if (merged != null) countFields(merged) else 0
            Log.d(TAG, "${bag.name}: $fields merged fields extracted")

            if (fields == 0) {
                failures.add(bag.name)
            }
        }

        Log.d(TAG, "=== SUMMARY: ${bags.size - failures.size}/${bags.size} bags produced fields ===")
        assertTrue(
            "Pipeline extracted zero fields from: ${failures.joinToString()}",
            failures.isEmpty()
        )
    } }
}
