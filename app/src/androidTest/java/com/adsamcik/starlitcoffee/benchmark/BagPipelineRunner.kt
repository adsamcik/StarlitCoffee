package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.test.corpus.CorpusFields
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import java.io.File

/**
 * Runs the production bag-scan pipeline (full 3-pass OCR per side -> front/back
 * fusion -> text-only LLM extraction) against one corpus bag, end to end.
 *
 * This mirrors the production flow in `BrewViewModel.processBagPhoto` so the
 * Q0 best-case gate ([BagScanBestCaseGateTest]) validates the real code path a
 * user hits, not pre-captured fixtures. The cheaper fixture-based loop lives in
 * [OcrFixtureCaptureTest] + [LlmCorpusBenchmarkTest].
 */
internal object BagPipelineRunner {

    /** App-internal candidate field names scored by the corpus harness. */
    val APP_FIELD_NAMES: List<String> = CorpusFields.appFieldNames

    data class PipelineResult(
        val ocrText: String?,
        val llmResult: LlmExtractionResult?,
    )

    /**
     * OCR both sides of [bag], merge, and run the LLM. Returns a null
     * `ocrText` when no side produced text (so callers can record a skip
     * rather than feeding the LLM an empty prompt).
     */
    suspend fun run(
        ocr: OcrService,
        llm: LlmInferenceProvider,
        bag: CoffeeBagFixture,
    ): PipelineResult {
        val sides = buildList {
            ocrSide(ocr, CorpusFixture.frontPhotoFile(bag))?.let { add(BagCaptureSide.FRONT to it) }
            CorpusFixture.backPhotoFile(bag)?.let { backFile ->
                ocrSide(ocr, backFile)?.let { add(BagCaptureSide.BACK to it) }
            }
        }
        if (sides.isEmpty()) return PipelineResult(ocrText = null, llmResult = null)

        val merged = BagOcrTextMerger.combineBySide(sides)
        val request = LlmExtractionRequest(
            imageBytes = ByteArray(0),
            existingFields = emptyMap(),
            fieldsNeeded = APP_FIELD_NAMES.toSet(),
            rawOcrText = merged,
            knownFieldValues = null,
        )
        return PipelineResult(ocrText = merged, llmResult = llm.extractBagFields(request))
    }

    /**
     * Reduce a successful [LlmExtractionResult] to `appFieldName -> value`,
     * keeping the first candidate per field. Non-success results map to an
     * empty map (the scorer then records every visible field as MISSING).
     */
    fun extractedByField(result: LlmExtractionResult?): Map<String, String?> {
        if (result !is LlmExtractionResult.Success) return emptyMap()
        return result.fieldCandidates
            .groupBy { it.fieldName }
            .mapValues { (_, candidates) -> candidates.firstOrNull()?.value }
    }

    /**
     * Full production OCR for one image: original -> aligned (deskew) ->
     * enhanced (CLAHE + unsharp), merged. Returns null if the photo can't be
     * decoded or every pass is empty.
     */
    private suspend fun ocrSide(ocr: OcrService, photoFile: File): String? {
        if (!photoFile.isFile) {
            Log.w(CorpusFixture.BENCHMARK_TAG, "Missing photo: ${photoFile.path}")
            return null
        }
        val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: run {
            Log.w(CorpusFixture.BENCHMARK_TAG, "Failed to decode: ${photoFile.path}")
            return null
        }
        val bitmap = ImagePreprocessor.applyExifRotation(rawBitmap, photoFile.absolutePath)

        val originalText = ocr.recognize(bitmap)
        val alignedBitmap = if (originalText != null && originalText.blocks.isNotEmpty()) {
            val alignment = ImagePreprocessor.computeAlignment(originalText.blocks)
            ImagePreprocessor.applyAlignment(bitmap, alignment)
        } else {
            bitmap
        }
        val alignedText = ocr.recognize(alignedBitmap)
        val enhancedBitmap = ImagePreprocessor.preprocessForOcr(alignedBitmap)
        val enhancedText = ocr.recognize(enhancedBitmap)

        return listOfNotNull(
            originalText?.fullText?.takeIf { it.isNotBlank() },
            alignedText?.fullText?.takeIf { it.isNotBlank() },
            enhancedText?.fullText?.takeIf { it.isNotBlank() },
        ).joinToString("\n").trim().takeIf { it.isNotBlank() }
    }
}
