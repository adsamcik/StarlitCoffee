package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Bundled, deterministic Latin-script OCR used as the always-available label
 * recognition baseline. It does not require Mindlayer, authorization, a model
 * download, or network access.
 */
class MlKitOcrService : OcrService {
    private val recognizerDelegate = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val recognizer get() = recognizerDelegate.value

    override fun close() {
        if (recognizerDelegate.isInitialized()) recognizer.close()
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun recognize(bitmap: Bitmap): RecognizedText? = try {
        val result = suspendCancellableCoroutine<Text?> { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    if (continuation.isActive) continuation.resume(text)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.cancel()
                }
        } ?: return null
        result.toRecognizedText()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private fun Text.toRecognizedText(): RecognizedText = RecognizedText(
    fullText = text,
    blocks = textBlocks.map { block ->
        RecognizedTextBlock(
            text = block.text,
            boundingBox = block.boundingBox,
            lines = block.lines.map { line ->
                RecognizedTextLine(
                    text = line.text,
                    boundingBox = line.boundingBox,
                    cornerPoints = line.cornerPoints,
                )
            },
        )
    },
)
