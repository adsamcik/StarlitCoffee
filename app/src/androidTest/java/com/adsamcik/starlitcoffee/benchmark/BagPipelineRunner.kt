package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.scan.BagPhotoExtractor
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.test.corpus.CorpusFields
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor

/**
 * Runs the production extractor, including its scan deadline, translation, optional vision,
 * combine/refine passes, candidate resolution, and final prefill sanitization.
 *
 * This is a cold-start label-quality check: no saved collection or external barcode product
 * metadata supplies answers. It does not exercise WorkManager, draft recovery, review UI, or save.
 */
internal object BagPipelineRunner {

    val APP_FIELD_NAMES: List<String> = CorpusFields.appFieldNames

    suspend fun run(
        context: Context,
        ocr: OcrService,
        llm: LlmInferenceProvider,
        bag: CoffeeBagFixture,
    ): BagPhotoProcessingResult {
        val photos = buildList {
            add(CorpusFixture.frontPhotoFile(bag).absolutePath)
            CorpusFixture.backPhotoFile(bag)?.let { add(it.absolutePath) }
        }
        return BagPhotoExtractor(
            appContext = context,
            ocrService = ocr,
            llmProvider = llm,
            openFoodFactsLookup = { null },
        ).extract(
            photoUris = photos,
            knownFieldValues = KnownFieldValues.EMPTY,
        )
    }

    /** Score exactly the sanitized prefill returned to the production draft flow. */
    fun prefillByField(prefill: OcrFieldExtractor.OcrExtractionResult?): Map<String, String?> {
        if (prefill == null) return emptyMap()
        return mapOf(
            "name" to prefill.name,
            "roaster" to prefill.roaster,
            "origin" to prefill.origin,
            "region" to prefill.region,
            "farm" to prefill.farm,
            "variety" to prefill.variety,
            "processType" to prefill.processType,
            "altitude" to prefill.altitude,
            "tastingNotes" to prefill.tastingNotes,
            "roastLevel" to prefill.roastLevel,
            "roastDate" to prefill.roastDate,
            "expiryDate" to prefill.expiryDate,
            "weight" to prefill.weight,
            "isDecaf" to prefill.isDecaf?.toString(),
        )
    }

    /** Raw first candidates for report-only, isolated model experiments; not production output. */
    fun extractedByField(result: LlmExtractionResult?): Map<String, String?> {
        if (result !is LlmExtractionResult.Success) return emptyMap()
        return result.fieldCandidates
            .groupBy { it.fieldName }
            .mapValues { (_, candidates) -> candidates.firstOrNull()?.value }
    }
}
