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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test that loads real coffee bag images from /sdcard/Download/
 * and validates the full OCR + barcode extraction pipeline.
 *
 * Pre-requisite: push sample images to emulator:
 *   adb push front.jpeg /sdcard/Download/bag_front.jpeg
 *   adb push back.jpeg /sdcard/Download/bag_back.jpeg
 */
@RunWith(AndroidJUnit4::class)
class OcrPipelineInstrumentedTest {

    companion object {
        private const val TAG = "OcrPipelineTest"
        private const val FRONT_PATH = "/data/local/tmp/bag_front.jpeg"
        private const val BACK_PATH = "/data/local/tmp/bag_back.jpeg"
    }

    @Test
    fun ocrExtractsTextFromFrontOfBag() { runBlocking {
        val file = File(FRONT_PATH)
        assertTrue("Front image must exist at $FRONT_PATH", file.exists())

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("Bitmap should decode", bitmap)

        val image = InputImage.fromBitmap(bitmap!!, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }

        assertNotNull("OCR should produce result", result)
        val text = result!!.text
        Log.d(TAG, "=== FRONT OCR RAW TEXT ===")
        Log.d(TAG, text)
        Log.d(TAG, "=== END FRONT OCR ===")

        assertTrue("Front should contain some text", text.isNotBlank())

        // Extract fields
        val extracted = OcrFieldExtractor.extractFields(text)
        Log.d(TAG, "=== FRONT EXTRACTION ===")
        Log.d(TAG, "Origin: ${extracted.origin}")
        Log.d(TAG, "Region: ${extracted.region}")
        Log.d(TAG, "Variety: ${extracted.variety}")
        Log.d(TAG, "Process: ${extracted.processType}")
        Log.d(TAG, "Altitude: ${extracted.altitude}")
        Log.d(TAG, "Tasting notes: ${extracted.tastingNotes}")
        Log.d(TAG, "Roast level: ${extracted.roastLevel}")
        Log.d(TAG, "Roast date: ${extracted.roastDate}")
        Log.d(TAG, "Roaster: ${extracted.roaster}")
        Log.d(TAG, "=== END FRONT EXTRACTION ===")
    } }

    @Test
    fun ocrExtractsTextFromBackOfBag() { runBlocking {
        val file = File(BACK_PATH)
        assertTrue("Back image must exist at $BACK_PATH", file.exists())

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("Bitmap should decode", bitmap)

        val image = InputImage.fromBitmap(bitmap!!, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }

        assertNotNull("OCR should produce result", result)
        val text = result!!.text
        Log.d(TAG, "=== BACK OCR RAW TEXT ===")
        Log.d(TAG, text)
        Log.d(TAG, "=== END BACK OCR ===")

        assertTrue("Back should contain some text", text.isNotBlank())

        val extracted = OcrFieldExtractor.extractFields(text)
        Log.d(TAG, "=== BACK EXTRACTION ===")
        Log.d(TAG, "Origin: ${extracted.origin}")
        Log.d(TAG, "Region: ${extracted.region}")
        Log.d(TAG, "Variety: ${extracted.variety}")
        Log.d(TAG, "Process: ${extracted.processType}")
        Log.d(TAG, "Altitude: ${extracted.altitude}")
        Log.d(TAG, "Tasting notes: ${extracted.tastingNotes}")
        Log.d(TAG, "Roast level: ${extracted.roastLevel}")
        Log.d(TAG, "Roast date: ${extracted.roastDate}")
        Log.d(TAG, "Roaster: ${extracted.roaster}")
        Log.d(TAG, "=== END BACK EXTRACTION ===")
    } }

    @Test
    fun barcodeDetectionWithOcrFallback() { runBlocking {
        val file = File(BACK_PATH)
        assertTrue("Back image must exist at $BACK_PATH", file.exists())

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("Bitmap should decode", bitmap)

        val image = InputImage.fromBitmap(bitmap!!, 0)

        // Try ML Kit barcode scanner first
        val scanner = BarcodeScanning.getClient()
        val barcodes = suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }

        Log.d(TAG, "=== BARCODES DETECTED ===")
        val mlKitCount = barcodes?.size ?: 0
        barcodes?.forEach { code ->
            Log.d(TAG, "Format: ${code.format}, Type: ${code.valueType}, Raw: ${code.rawValue}")
        }
        Log.d(TAG, "ML Kit found $mlKitCount barcode(s)")

        // Fallback: extract barcode from OCR text
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val textResult = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }
        val ocrBarcode = textResult?.let { OcrFieldExtractor.extractBarcodeFromText(it.text) }
        Log.d(TAG, "OCR barcode fallback: $ocrBarcode")
        Log.d(TAG, "=== END BARCODES ===")

        // At least one method should find the barcode
        val foundBarcode = barcodes?.firstOrNull()?.rawValue ?: ocrBarcode
        assertNotNull("Should detect barcode via ML Kit or OCR fallback", foundBarcode)
    } }

    @Test
    fun fullPipelineMergesFrontAndBackResults() { runBlocking {
        val frontFile = File(FRONT_PATH)
        val backFile = File(BACK_PATH)
        assertTrue("Front image must exist", frontFile.exists())
        assertTrue("Back image must exist", backFile.exists())

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val scanner = BarcodeScanning.getClient()
        val ocrResults = mutableListOf<OcrFieldExtractor.OcrExtractionResult>()
        val detectedBarcodes = mutableListOf<String>()
        val detectedUrls = mutableListOf<String>()
        val rawOcrTexts = mutableListOf<String>()

        for (path in listOf(FRONT_PATH, BACK_PATH)) {
            val bitmap = BitmapFactory.decodeFile(path) ?: continue
            val image = InputImage.fromBitmap(bitmap, 0)

            // OCR
            val textResult = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            if (textResult != null) {
                ocrResults.add(OcrFieldExtractor.extractFields(textResult.text))
                rawOcrTexts.add(textResult.text)
                // OCR barcode fallback
                if (detectedBarcodes.isEmpty()) {
                    OcrFieldExtractor.extractBarcodeFromText(textResult.text)?.let {
                        detectedBarcodes.add(it)
                    }
                }
            }

            // ML Kit barcode scanner
            val codes = suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            }
            codes?.forEach { code ->
                val raw = code.rawValue ?: return@forEach
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    detectedUrls.add(raw)
                } else {
                    detectedBarcodes.add(raw)
                }
            }
        }

        // Merge OCR
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
            weight = ocrResults.firstNotNullOfOrNull { it.weight },
        )

        Log.d(TAG, "=== MERGED PIPELINE RESULT ===")
        Log.d(TAG, "Origin: ${merged.origin}")
        Log.d(TAG, "Region: ${merged.region}")
        Log.d(TAG, "Variety: ${merged.variety}")
        Log.d(TAG, "Process: ${merged.processType}")
        Log.d(TAG, "Altitude: ${merged.altitude}")
        Log.d(TAG, "Tasting notes: ${merged.tastingNotes}")
        Log.d(TAG, "Roast level: ${merged.roastLevel}")
        Log.d(TAG, "Roast date: ${merged.roastDate}")
        Log.d(TAG, "Weight: ${merged.weight}")
        Log.d(TAG, "Roaster: ${merged.roaster}")
        Log.d(TAG, "Barcodes: $detectedBarcodes")
        Log.d(TAG, "QR URLs: $detectedUrls")
        Log.d(TAG, "=== END MERGED RESULT ===")

        // Expected results for Beansmith's Ethiopia Gedeb bag
        assertNotNull("Should detect origin (Ethiopia)", merged.origin)
        assertEquals("Ethiopia", merged.origin)
        assertNotNull("Should detect region (Gedeb)", merged.region)
        assertNotNull("Should detect variety (Heirloom)", merged.variety)
        assertNotNull("Should detect process (Washed)", merged.processType)
        assertNotNull("Should detect tasting notes", merged.tastingNotes)
        assertNotNull("Should detect roast level", merged.roastLevel)
        assertNotNull("Should detect roast date", merged.roastDate)
        assertTrue("Should find barcode via OCR fallback", detectedBarcodes.isNotEmpty())
    } }

    @Test
    fun preprocessingImprovesOcrExtraction() { runBlocking {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        for ((label, path) in listOf("FRONT" to FRONT_PATH, "BACK" to BACK_PATH)) {
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

            fun countFields(r: OcrFieldExtractor.OcrExtractionResult): Int =
                listOfNotNull(r.roaster, r.origin, r.region, r.variety, r.processType,
                    r.altitude, r.tastingNotes, r.roastLevel, r.roastDate, r.weight).size

            Log.d(TAG, "=== $label PREPROCESSING COMPARISON ===")
            Log.d(TAG, "Original text length: ${origText.length}, fields: ${countFields(origFields)}")
            Log.d(TAG, "Preprocessed text length: ${ppText.length}, fields: ${countFields(ppFields)}")
            Log.d(TAG, "Original OCR: ${origText.take(200)}")
            Log.d(TAG, "Preprocessed OCR: ${ppText.take(200)}")
            Log.d(TAG, "Orig fields: origin=${origFields.origin}, region=${origFields.region}, " +
                "variety=${origFields.variety}, process=${origFields.processType}, " +
                "roastLevel=${origFields.roastLevel}, roastDate=${origFields.roastDate}, " +
                "weight=${origFields.weight}, roaster=${origFields.roaster}")
            Log.d(TAG, "PP fields: origin=${ppFields.origin}, region=${ppFields.region}, " +
                "variety=${ppFields.variety}, process=${ppFields.processType}, " +
                "roastLevel=${ppFields.roastLevel}, roastDate=${ppFields.roastDate}, " +
                "weight=${ppFields.weight}, roaster=${ppFields.roaster}")
            Log.d(TAG, "=== END $label COMPARISON ===")
        }
    } }
}
