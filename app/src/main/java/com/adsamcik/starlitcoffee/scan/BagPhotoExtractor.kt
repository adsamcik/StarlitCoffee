package com.adsamcik.starlitcoffee.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.adsamcik.starlitcoffee.data.db.dao.UserBarcodeStemDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.data.network.ProductResult
import com.adsamcik.starlitcoffee.data.network.QrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.llm.LlmCacheKey
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmCallGate
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.data.network.ocr.RecognizedText
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.domain.scanfield.FieldContext
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import com.adsamcik.starlitcoffee.util.BagPhotoAnalysis
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.BagThumbnailFocus
import com.adsamcik.starlitcoffee.util.BarcodeInsights
import com.adsamcik.starlitcoffee.util.CoffeeCountryDictionaries
import com.adsamcik.starlitcoffee.util.CoffeeMetadataMatchStrategy
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.NormalizedCoffeeField
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

data class OffLookupSummary(val name: String?, val brand: String?)

private object BagPhotoVisionPlanner {
    private val VISION_WORTHY_FIELDS: Set<String> = setOf("roastLevel", "isDecaf")

    private val AUTHORITATIVE_SOURCES: Set<BagFieldSourceType> = setOf(
        BagFieldSourceType.BARCODE_LOOKUP,
        BagFieldSourceType.LOCAL_BARCODE_MATCH,
        BagFieldSourceType.QR_LINK_LOOKUP,
        BagFieldSourceType.OBSERVED_BARCODE_STEM,
        BagFieldSourceType.CONSENSUS,
    )

    fun selectVisionFields(resolved: Map<String, BagFieldEvidence>): Set<String> =
        VISION_WORTHY_FIELDS.filterNot { field -> isSettledForVision(resolved[field]) }.toSet()

    fun filterVisionCandidates(
        visionCandidates: List<BagFieldCandidate>,
        resolved: Map<String, BagFieldEvidence>,
    ): List<BagFieldCandidate> =
        visionCandidates.filterNot { candidate -> isSettledForVision(resolved[candidate.fieldName]) }

    private fun isSettledForVision(evidence: BagFieldEvidence?): Boolean {
        if (evidence == null) return false
        if (evidence.sourceType in AUTHORITATIVE_SOURCES) return true
        return evidence.sourceType == BagFieldSourceType.OCR &&
            evidence.confidence == BagFieldConfidence.HIGH
    }
}

@Suppress("LargeClass", "TooManyFunctions", "UnusedPrivateProperty")
class BagPhotoExtractor @Suppress("LongParameterList") constructor(
    private val appContext: Context?,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
    private val qrLinkMetadataExplorer: QrLinkMetadataExplorer = SafeQrLinkMetadataExplorer(),
    private val llmProvider: LlmInferenceProvider = StubLlmInferenceProvider(),
    private val ocrService: OcrService? = null,
    private val userBarcodeStemDao: UserBarcodeStemDao? = null,
    private val openFoodFactsLookup: (barcode: String) -> ProductResult? = {
        OpenFoodFactsClient.lookupBarcode(it)
    },
    private val llmCache: LlmResultCache = LlmResultCache(),
) {
    private var currentKnownValues: KnownFieldValues = KnownFieldValues.EMPTY

    private companion object {
        private const val BAG_PHOTO_TAG = "BagPhotoProcessing"
        private const val BAG_PHOTO_LLM_TIMEOUT_MS = 95_000L
        // Auto-recover from transient LLM failures (timeouts, one-off inference
        // errors) without bugging the user: retry a retryable failure up to this
        // many TOTAL attempts before giving up, with a short backoff between.
        private const val LLM_MAX_ATTEMPTS = 3
        private const val LLM_RETRY_BACKOFF_MS = 600L
        private const val BAG_PHOTO_VISION_TIMEOUT_MS = 305_000L
        private const val MAX_LLM_PHOTO_BYTES = 20 * 1024 * 1024
        private const val LLM_READ_BUFFER_SIZE = 8 * 1024

        private val CANONICAL_METADATA_FIELDS = setOf(
            "origin",
            "region",
            "variety",
            "processType",
            "tastingNotes",
            "roastLevel",
        )
        private val BAG_PHOTO_FIELD_NAMES = listOf(
            "name",
            "roaster",
            "origin",
            "region",
            "farm",
            "variety",
            "processType",
            "altitude",
            "tastingNotes",
            "roastLevel",
            "roastDate",
            "expiryDate",
            "isDecaf",
            "weight",
        )
    }

    suspend fun extract(
        photoUris: List<String>,
        knownFieldValues: KnownFieldValues,
        runLlm: Boolean = true,
    ): BagPhotoProcessingResult {
        currentKnownValues = knownFieldValues
        if (photoUris.isEmpty()) {
            return BagPhotoProcessingResult(capturedPhotoUris = "")
        }

        val barcodeScanner = BarcodeScanning.getClient()
        return try {
            val processedPhotos = photoUris.mapIndexedNotNull { index, uriStr ->
                processBagPhoto(
                    uriStr = uriStr,
                    side = if (index == 0) BagCaptureSide.FRONT else BagCaptureSide.BACK,
                    barcodeScanner = barcodeScanner,
                )
            }
            emitBagPhotoResult(
                photoUriList = photoUris,
                processedPhotos = processedPhotos,
                runLlm = runLlm,
            )
        } finally {
            barcodeScanner.close()
        }
    }

    private suspend fun emitBagPhotoResult(
        photoUriList: List<String>,
        processedPhotos: List<ProcessedBagPhoto>,
        runLlm: Boolean,
    ): BagPhotoProcessingResult {
        val photoAnalyses = processedPhotos.map { photo ->
            BagPhotoAnalysis(
                uri = photo.uri,
                side = photo.side,
                quality = photo.quality,
                extractedText = photo.fullText,
            )
        }
        val combinedOcrText = combineOcrTextBySide(processedPhotos)
        val allCandidates = processedPhotos
            .flatMap { photo -> buildFieldCandidates(photo) }
            .toMutableList()
        val rawDetectedQrUrl = processedPhotos.firstNotNullOfOrNull { it.detectedQrUrl }
        val detectedBarcode = resolveDetectedBarcode(processedPhotos, combinedOcrText, allCandidates)

        val matchedBagByBarcode = findLocalBagByBarcode(detectedBarcode)
        val inferredLocale = inferLocaleFromBarcode(detectedBarcode)

        addBarcodeMatchAndStemCandidates(
            allCandidates = allCandidates,
            detectedBarcode = detectedBarcode,
            matchedBag = matchedBagByBarcode,
            inferredLocale = inferredLocale,
        )
        boostKnownFieldValuesForBarcode(detectedBarcode)

        val offSummary = addOpenFoodFactsCandidates(allCandidates, detectedBarcode)
        val qrEnrichment = buildQrLinkEnrichment(rawDetectedQrUrl)
        allCandidates += qrEnrichment.candidates

        val baseCandidates = allCandidates.toList()
        val llmOutcome = if (runLlm) {
            tryLlmEnrichment(
                photoUriList = photoUriList,
                processedPhotos = processedPhotos,
                allCandidates = baseCandidates,
                combinedOcrText = combinedOcrText,
                knownFieldValues = currentKnownValues,
            )
        } else {
            LlmEnrichmentOutcome(status = LlmEnrichmentStatus.NOT_RUN)
        }
        allCandidates += llmOutcome.candidates

        if (runLlm) {
            runVisionEnrichmentIfNeeded(photoUriList, processedPhotos, allCandidates)
        }

        val fieldEvidence = resolveBagPhotoFieldEvidence(allCandidates)
        val reviewHints = BagPhotoScanSupport.buildReviewHints(
            photoAnalyses = photoAnalyses,
            resolvedFields = fieldEvidence,
            additionalHints = BarcodeInsights.buildBarcodeReviewHints(
                barcode = detectedBarcode,
                matchedBag = matchedBagByBarcode,
                observedStemMatch = BarcodeInsights.findObservedStemMatch(detectedBarcode),
            ) + qrEnrichment.reviewHints,
            scanServiceUnavailable = llmOutcome.status == LlmEnrichmentStatus.UNAVAILABLE,
        )
        val ocrPrefill = fieldEvidence.takeIf { it.isNotEmpty() }?.let(BagPhotoScanSupport::buildPrefill)

        return BagPhotoProcessingResult(
            ocrPrefill = ocrPrefill,
            capturedPhotoUris = photoUriList.joinToString(","),
            detectedBarcode = detectedBarcode,
            detectedQrUrl = qrEnrichment.safeUrl,
            offLookupName = offSummary.name,
            offLookupRoaster = offSummary.brand,
            fieldEvidence = fieldEvidence,
            photoAnalyses = photoAnalyses,
            reviewHints = reviewHints,
            llmStatus = llmOutcome.status,
            thumbnailFocus = computeThumbnailFocus(processedPhotos),
        )
    }

    private fun computeThumbnailFocus(processedPhotos: List<ProcessedBagPhoto>): BagPhotoRect? {
        val frontPhoto = processedPhotos.firstOrNull { it.side == BagCaptureSide.FRONT }
            ?: processedPhotos.firstOrNull()
            ?: return null
        val originalBlocks = frontPhoto.passes.firstOrNull { it.label == "original" }?.blocks
            ?: frontPhoto.passes.firstOrNull()?.blocks
            ?: return null
        return BagThumbnailFocus.labelRegion(originalBlocks.mapNotNull { it.normalizedBounds() })
    }

    private suspend fun resolveDetectedBarcode(
        processedPhotos: List<ProcessedBagPhoto>,
        combinedOcrText: String,
        allCandidates: MutableList<BagFieldCandidate>,
    ): String? {
        var detectedBarcode = processedPhotos.firstNotNullOfOrNull { it.detectedBarcode }
        if (detectedBarcode == null && combinedOcrText.isNotBlank()) {
            detectedBarcode = OcrFieldExtractor.extractBarcodeFromText(combinedOcrText)
        }
        val rawDetectedBarcode = detectedBarcode
        detectedBarcode = BarcodeInsights.normalizeBarcode(detectedBarcode)
            ?: detectedBarcode?.trim()?.takeIf { it.isNotBlank() }

        if (BarcodeInsights.normalizeBarcode(rawDetectedBarcode) == null && rawDetectedBarcode != null) {
            val partialResult = BarcodeInsights.recoverPartialBarcode(rawDetectedBarcode, userBarcodeStemDao)
            if (partialResult.isPartial && partialResult.candidates.isNotEmpty()) {
                allCandidates += partialResult.candidates
                detectedBarcode = null
            }
        }
        return detectedBarcode
    }

    private fun inferLocaleFromBarcode(detectedBarcode: String?): Locale? =
        CoffeeCountryDictionaries.localeFromBarcode(detectedBarcode)
            ?.let { locale -> CoffeeCountryDictionaries.ALL.firstOrNull { it.locale == locale } }
            ?.locale

    private suspend fun addBarcodeMatchAndStemCandidates(
        allCandidates: MutableList<BagFieldCandidate>,
        detectedBarcode: String?,
        matchedBag: CoffeeBagEntity?,
        inferredLocale: Locale?,
    ) {
        matchedBag?.let {
            allCandidates += BarcodeInsights.buildLocalMatchCandidates(
                it,
                locale = inferredLocale ?: Locale.getDefault(),
            )
        }
        val observedStemMatch = BarcodeInsights.findObservedStemMatch(detectedBarcode)
        allCandidates += BarcodeInsights.buildObservedStemCandidates(observedStemMatch)

        val stemDao = userBarcodeStemDao
        if (stemDao != null && detectedBarcode != null) {
            allCandidates += BarcodeInsights.findUserStemMatch(detectedBarcode, stemDao)
        }
    }

    private fun boostKnownFieldValuesForBarcode(detectedBarcode: String?) {
        if (detectedBarcode == null) return
        val boostedKnownValues = BarcodeInsights.buildBarcodeOcrBoost(
            barcode = detectedBarcode,
            currentKnownValues = currentKnownValues,
            userStemDao = userBarcodeStemDao,
        )
        if (boostedKnownValues !== currentKnownValues) {
            currentKnownValues = boostedKnownValues
        }
    }

    suspend fun addOpenFoodFactsCandidates(
        candidates: MutableList<BagFieldCandidate>,
        barcode: String?,
    ): OffLookupSummary {
        if (barcode == null) return OffLookupSummary(name = null, brand = null)
        return try {
            val lookup = openFoodFactsLookup(barcode)
                ?: return OffLookupSummary(name = null, brand = null)
            val lookupSupportingText = listOfNotNull(
                lookup.name?.takeIf { it.isNotBlank() },
                lookup.brand?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").takeIf { it.isNotBlank() }

            if (!lookup.name.isNullOrBlank()) {
                candidates += BagFieldCandidate(
                    fieldName = "name",
                    value = lookup.name,
                    sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                    confidenceHint = BagFieldConfidence.HIGH,
                )
            }
            if (!lookup.brand.isNullOrBlank()) {
                candidates += BagFieldCandidate(
                    fieldName = "roaster",
                    value = lookup.brand,
                    sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                    confidenceHint = BagFieldConfidence.HIGH,
                )
            }
            candidates.addDecafCandidate(
                isDecaf = lookupSupportingText
                    ?.let(CoffeeMetadataNormalizer::containsDecafMarker)
                    ?.takeIf { it },
                provenance = BagCandidateProvenance(
                    sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                    confidenceHint = BagFieldConfidence.HIGH,
                    supportingText = lookupSupportingText,
                ),
                rawValue = lookupSupportingText,
            )

            if (!lookup.origins.isNullOrBlank()) {
                candidates += BagFieldCandidate(
                    fieldName = "origin",
                    value = lookup.origins,
                    sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                    confidenceHint = BagFieldConfidence.MEDIUM,
                    supportingText = "OFF origins field",
                )
            }
            lookup.countriesTags
                ?.firstNotNullOfOrNull(::coffeeProducingCountryFromTag)
                ?.let { originName ->
                    candidates += BagFieldCandidate(
                        fieldName = "origin",
                        value = originName,
                        sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                        confidenceHint = BagFieldConfidence.LOW,
                        supportingText = "OFF countries_tags (may be country of sale)",
                    )
                }
            OffLookupSummary(name = lookup.name, brand = lookup.brand)
        } catch (e: Exception) {
            Log.w(BAG_PHOTO_TAG, "Failed to fetch product info from OpenFoodFacts", e)
            OffLookupSummary(name = null, brand = null)
        }
    }

    private fun resolveBagPhotoFieldEvidence(
        candidates: List<BagFieldCandidate>,
    ): Map<String, BagFieldEvidence> = buildMap {
        for (fieldName in BAG_PHOTO_FIELD_NAMES) {
            val resolved = BagPhotoScanSupport.resolveField(
                fieldName = fieldName,
                candidates = candidates.filter { it.fieldName == fieldName },
            )
            if (resolved != null) {
                put(fieldName, resolved)
            }
        }
    }

    private suspend fun processBagPhoto(
        uriStr: String,
        side: BagCaptureSide,
        barcodeScanner: BarcodeScanner,
    ): ProcessedBagPhoto? {
        return try {
            val bitmap = decodeBagPhotoBitmap(uriStr) ?: return null

            val originalText = recognizeText(bitmap)
            val alignedBitmap = if (originalText != null && originalText.blocks.isNotEmpty()) {
                val alignment = ImagePreprocessor.computeAlignment(originalText.blocks)
                ImagePreprocessor.applyAlignment(bitmap, alignment)
            } else {
                bitmap
            }
            val alignedText = recognizeText(alignedBitmap)
            val enhancedBitmap = ImagePreprocessor.preprocessForOcr(alignedBitmap)
            val enhancedText = recognizeText(enhancedBitmap)

            val passes = listOfNotNull(
                buildScanPass("original", bitmap, originalText),
                buildScanPass("aligned", alignedBitmap, alignedText),
                buildScanPass("enhanced", enhancedBitmap, enhancedText),
            )

            val mergedText = passes.joinToString("\n") { it.fullText }.trim()
            val textBlockCount = passes.maxOfOrNull { it.blocks.size } ?: 0
            val quality = BagCaptureQualityAnalyzer.analyzeBitmap(
                bitmap = bitmap,
                textBlockCount = textBlockCount,
                textDetected = textBlockCount > 0,
            )

            val ocrBarcode = mergedText.takeIf { it.isNotBlank() }
                ?.let { OcrFieldExtractor.extractBarcodeFromText(it) }
            val detection = detectBarcodeAndQrUrl(
                scannedCodes = scanBarcodes(barcodeScanner, bitmap),
                ocrBarcode = ocrBarcode,
            )

            ProcessedBagPhoto(
                uri = uriStr,
                side = side,
                quality = quality,
                passes = passes,
                fullText = mergedText,
                detectedBarcode = detection.barcode,
                detectedQrUrl = detection.qrUrl,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(BAG_PHOTO_TAG, "Failed to process bag photo for OCR and barcode extraction", e)
            null
        }
    }

    private data class BagPhotoBarcodeDetection(val barcode: String?, val qrUrl: String?)

    private fun detectBarcodeAndQrUrl(
        scannedCodes: List<Barcode>?,
        ocrBarcode: String?,
    ): BagPhotoBarcodeDetection {
        var barcode = ocrBarcode
        var qrUrl: String? = null
        scannedCodes?.forEach { code ->
            val rawValue = code.rawValue ?: return@forEach
            val isHttpUrl = rawValue.startsWith("http://") || rawValue.startsWith("https://")
            when {
                isHttpUrl && qrUrl == null -> qrUrl = rawValue
                !isHttpUrl && barcode == null -> barcode = rawValue
            }
        }
        return BagPhotoBarcodeDetection(barcode = barcode, qrUrl = qrUrl)
    }

    private fun decodeBagPhotoBitmap(uriStr: String): Bitmap? {
        val uri = uriStr.toUri()
        return if (uri.scheme == "content") {
            val resolver = appContext?.contentResolver ?: run {
                Log.w(BAG_PHOTO_TAG, "Cannot decode content URI: appContext is null in BagPhotoExtractor")
                return null
            }
            val orientation = try {
                resolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } catch (e: Exception) {
                Log.w(BAG_PHOTO_TAG, "Failed to read content URI EXIF orientation", e)
                null
            } ?: ExifInterface.ORIENTATION_NORMAL
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }?.let { bitmap -> ImagePreprocessor.applyExifOrientation(bitmap, orientation) }
        } else {
            val file = java.io.File(uri.path ?: uriStr)
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            ImagePreprocessor.applyExifRotation(rawBitmap, file.absolutePath)
        }
    }

    private fun buildScanPass(
        label: String,
        bitmap: Bitmap,
        text: RecognizedText?,
    ): ScanPass? {
        val ocrText = text ?: return null
        val blocks = ocrText.blocks.map { block ->
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
        return ScanPass(
            label = label,
            result = OcrFieldExtractor.OcrExtractionResult(rawText = ocrText.fullText),
            blocks = blocks,
            fullText = ocrText.fullText,
        )
    }

    private fun buildFieldCandidates(
        photo: ProcessedBagPhoto,
        inferredLocale: Locale? = null,
    ): List<BagFieldCandidate> = buildList {
        val locale = inferredLocale ?: Locale.getDefault()
        photo.passes.forEach { pass ->
            val confidenceHint = confidenceHintForPass(photo.quality, pass.blocks.size)
            addFieldCandidate("name", pass.result.name, photo, pass, confidenceHint, locale)
            addFieldCandidate("roaster", pass.result.roaster, photo, pass, confidenceHint, locale)
            addFieldCandidate("origin", pass.result.origin, photo, pass, confidenceHint, locale)
            addFieldCandidate("region", pass.result.region, photo, pass, confidenceHint, locale)
            addFieldCandidate("farm", pass.result.farm, photo, pass, confidenceHint, locale)
            addFieldCandidate("variety", pass.result.variety, photo, pass, confidenceHint, locale)
            addFieldCandidate("processType", pass.result.processType, photo, pass, confidenceHint, locale)
            addFieldCandidate("altitude", pass.result.altitude, photo, pass, confidenceHint, locale)
            addFieldCandidate("tastingNotes", pass.result.tastingNotes, photo, pass, confidenceHint, locale)
            addFieldCandidate("roastLevel", pass.result.roastLevel, photo, pass, confidenceHint, locale)
            addFieldCandidate("roastDate", pass.result.roastDate, photo, pass, confidenceHint, locale)
            addFieldCandidate("expiryDate", pass.result.expiryDate, photo, pass, confidenceHint, locale)
            addFieldCandidate("weight", pass.result.weight, photo, pass, confidenceHint, locale)
            val decafSupportingBlock = pass.blocks.firstOrNull { block ->
                CoffeeMetadataNormalizer.containsDecafMarker(block.text)
            }
            val decafSupportingText = decafSupportingBlock?.text
                ?: pass.fullText.lineSequence().map(String::trim).firstOrNull { line ->
                    CoffeeMetadataNormalizer.containsDecafMarker(line)
                }
            addDecafCandidate(
                isDecaf = pass.result.isDecaf,
                provenance = BagCandidateProvenance(
                    sourceType = BagFieldSourceType.OCR,
                    confidenceHint = pass.result.fieldConfidence["isDecaf"] ?: confidenceHint,
                    supportingText = decafSupportingText,
                    side = photo.side,
                    previewUri = photo.uri,
                    previewRect = decafSupportingBlock?.normalizedBounds(),
                ),
                rawValue = decafSupportingText,
            )
        }
    }

    private fun MutableList<BagFieldCandidate>.addFieldCandidate(
        fieldName: String,
        value: String?,
        photo: ProcessedBagPhoto,
        pass: ScanPass,
        confidenceHint: BagFieldConfidence,
        locale: Locale,
    ) {
        val cleanValue = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val supportingBlock = findSupportingBlock(pass.blocks, cleanValue)
        val normalized = normalizeMetadataField(fieldName, cleanValue, locale)
        add(
            BagFieldCandidate(
                fieldName = fieldName,
                value = normalized?.value ?: cleanValue,
                rawValue = cleanValue,
                canonicalKey = normalized?.canonicalKey,
                sourceType = BagFieldSourceType.OCR,
                side = photo.side,
                confidenceHint = confidenceHint,
                matchStrategy = normalized?.matchStrategy,
                supportingText = supportingBlock?.text ?: pass.fullText.lineSequence().firstOrNull { line ->
                    line.contains(cleanValue, ignoreCase = true)
                },
                previewUri = photo.uri,
                previewRect = supportingBlock?.normalizedBounds(),
            ),
        )
        addInferredMetadataCandidates(
            normalized = normalized,
            rawValue = cleanValue,
            locale = locale,
            provenance = BagCandidateProvenance(
                sourceType = BagFieldSourceType.OCR,
                confidenceHint = confidenceHint,
                side = photo.side,
                supportingText = supportingBlock?.text ?: pass.fullText.lineSequence().firstOrNull { line ->
                    line.contains(cleanValue, ignoreCase = true)
                },
                previewUri = photo.uri,
                previewRect = supportingBlock?.normalizedBounds(),
            ),
        )
    }

    private suspend fun buildQrLinkEnrichment(rawUrl: String?): QrLinkEnrichment {
        val safeUrl = SafeQrLinkMetadataExplorer.sanitizePublicWebUrl(rawUrl)
        if (rawUrl != null && safeUrl == null) {
            return QrLinkEnrichment(
                reviewHints = listOf(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "Ignored an unsafe QR link. Only public HTTPS pages are supported.",
                    ),
                ),
            )
        }
        if (safeUrl == null) return QrLinkEnrichment()

        return QrLinkEnrichment(
            safeUrl = safeUrl,
            reviewHints = listOf(
                BagPhotoReviewHint(
                    severity = BagReviewSeverity.INFO,
                    message = "QR link found. Approve exploration to extract coffee details from the website.",
                ),
            ),
        )
    }

    private data class BagCandidateProvenance(
        val sourceType: BagFieldSourceType,
        val confidenceHint: BagFieldConfidence,
        val supportingText: String? = null,
        val side: BagCaptureSide? = null,
        val previewUri: String? = null,
        val previewRect: BagPhotoRect? = null,
    )

    private fun MutableList<BagFieldCandidate>.addDecafCandidate(
        isDecaf: Boolean?,
        provenance: BagCandidateProvenance,
        rawValue: String? = null,
    ) {
        if (isDecaf != true) return
        val cleanRawValue = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: "decaf"
        add(
            BagFieldCandidate(
                fieldName = "isDecaf",
                value = "Decaf",
                rawValue = cleanRawValue,
                canonicalKey = "true",
                sourceType = provenance.sourceType,
                side = provenance.side,
                confidenceHint = provenance.confidenceHint,
                supportingText = provenance.supportingText,
                previewUri = provenance.previewUri,
                previewRect = provenance.previewRect,
            ),
        )
    }

    private fun normalizeMetadataField(
        fieldName: String,
        rawValue: String,
        locale: Locale,
    ): NormalizedCoffeeField? =
        rawValue.takeIf { fieldName in CANONICAL_METADATA_FIELDS }
            ?.let { CoffeeMetadataNormalizer.normalizeField(fieldName, it, locale) }

    private fun MutableList<BagFieldCandidate>.addInferredMetadataCandidates(
        normalized: NormalizedCoffeeField?,
        rawValue: String,
        locale: Locale,
        provenance: BagCandidateProvenance,
    ) {
        normalized?.relatedCanonicalKeys
            ?.filterKeys { it in BAG_PHOTO_FIELD_NAMES }
            ?.forEach { (fieldName, canonicalKey) ->
                val localizedValue = CoffeeMetadataNormalizer.displayField(
                    fieldName = fieldName,
                    canonicalKey = canonicalKey,
                    fallbackRaw = rawValue,
                    locale = locale,
                ) ?: return@forEach
                add(
                    BagFieldCandidate(
                        fieldName = fieldName,
                        value = localizedValue,
                        rawValue = rawValue,
                        canonicalKey = canonicalKey,
                        sourceType = provenance.sourceType,
                        side = provenance.side,
                        confidenceHint = inferredConfidence(provenance.confidenceHint),
                        matchStrategy = CoffeeMetadataMatchStrategy.RELATION_INFERENCE,
                        supportingText = provenance.supportingText,
                        previewUri = provenance.previewUri,
                        previewRect = provenance.previewRect,
                    ),
                )
            }
    }

    private fun inferredConfidence(confidence: BagFieldConfidence): BagFieldConfidence = when (confidence) {
        BagFieldConfidence.HIGH -> BagFieldConfidence.MEDIUM
        BagFieldConfidence.MEDIUM -> BagFieldConfidence.LOW
        BagFieldConfidence.LOW -> BagFieldConfidence.LOW
        BagFieldConfidence.NEEDS_REVIEW -> BagFieldConfidence.NEEDS_REVIEW
    }

    private fun coffeeProducingCountryFromTag(tag: String): String? {
        val countryPart = tag.substringAfter(":", tag)
            .replace("-", " ")
            .trim()
            .lowercase()
        return CoffeeOrigin.Known.entries.firstOrNull { origin ->
            origin.displayName.lowercase() == countryPart
        }?.displayName
    }

    private suspend fun findLocalBagByBarcode(barcode: String?): CoffeeBagEntity? {
        val repository = coffeeBagRepository ?: return null
        val rawBarcode = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedBarcode = BarcodeInsights.normalizeBarcode(rawBarcode)

        return when {
            normalizedBarcode != null && normalizedBarcode != rawBarcode -> {
                repository.findByBarcode(normalizedBarcode)
                    ?: repository.findByBarcode(rawBarcode)
            }
            normalizedBarcode != null -> repository.findByBarcode(normalizedBarcode)
            else -> repository.findByBarcode(rawBarcode)
        }
    }

    private suspend fun readPhotoBytesForLlm(uriStr: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val uri = uriStr.toUri()
            val input = if (uri.scheme == "content") {
                appContext?.contentResolver?.openInputStream(uri)
            } else {
                val file = java.io.File(uri.path ?: uriStr)
                file.inputStream()
            } ?: return@withContext null

            input.use { stream -> readBytesCapped(stream, MAX_LLM_PHOTO_BYTES) }
        }

    private fun readBytesCapped(input: InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(LLM_READ_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                Log.w(BAG_PHOTO_TAG, "Skipping LLM enrichment: photo exceeds ${maxBytes / (1024 * 1024)}MB")
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private suspend fun tryLlmEnrichment(
        photoUriList: List<String>,
        processedPhotos: List<ProcessedBagPhoto>,
        allCandidates: List<BagFieldCandidate>,
        combinedOcrText: String,
        knownFieldValues: KnownFieldValues,
    ): LlmEnrichmentOutcome {
        if (!llmProvider.isAvailable()) {
            return LlmEnrichmentOutcome(status = LlmEnrichmentStatus.UNAVAILABLE)
        }

        val fieldsNeeded = BAG_PHOTO_FIELD_NAMES.toSet()
        val existingFields = BagFieldContextMapper.buildExistingFieldsContext(allCandidates)
        val bestPhotoUri = processedPhotos.maxByOrNull { it.quality.blurScore }?.uri
            ?: photoUriList.firstOrNull()
        val photoBytes = bestPhotoUri?.let { uri ->
            try {
                readPhotoBytesForLlm(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(BAG_PHOTO_TAG, "Failed to read photo bytes for LLM enrichment", e)
                null
            }
        } ?: return LlmEnrichmentOutcome(status = LlmEnrichmentStatus.FAILED)

        return tryLlmEnrichment(
            photoBytes = photoBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = combinedOcrText.takeIf { it.isNotBlank() },
            knownFieldValues = knownFieldValues,
        )
    }

    private suspend fun tryLlmEnrichment(
        photoBytes: ByteArray,
        existingFields: Map<String, FieldContext>,
        fieldsNeeded: Set<String>,
        rawOcrText: String?,
        knownFieldValues: KnownFieldValues?,
    ): LlmEnrichmentOutcome {
        if (!llmProvider.isAvailable()) {
            return LlmEnrichmentOutcome(status = LlmEnrichmentStatus.UNAVAILABLE)
        }

        val imageHash = LlmCacheKey.compute(
            imageBytes = photoBytes,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = rawOcrText,
            existingFields = existingFields,
        )
        llmCache.get(imageHash)?.let { cached ->
            return LlmEnrichmentOutcome(
                candidates = cached.fieldCandidates,
                status = LlmEnrichmentStatus.SUCCEEDED,
            )
        }

        val request = LlmExtractionRequest(
            imageBytes = photoBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = rawOcrText,
            knownFieldValues = knownFieldValues,
        )
        val result = runLlmExtractionWithRetry(request)

        return when (result) {
            is LlmExtractionResult.Success -> {
                llmCache.put(imageHash, result)
                LlmEnrichmentOutcome(
                    candidates = result.fieldCandidates,
                    status = LlmEnrichmentStatus.SUCCEEDED,
                )
            }
            is LlmExtractionResult.Unavailable -> {
                Log.w(BAG_PHOTO_TAG, "LLM enrichment unavailable: ${result.reason}")
                LlmEnrichmentOutcome(status = LlmEnrichmentStatus.UNAVAILABLE)
            }
            is LlmExtractionResult.Failed -> {
                Log.w(BAG_PHOTO_TAG, "LLM enrichment failed: ${result.error}")
                LlmEnrichmentOutcome(status = LlmEnrichmentStatus.FAILED)
            }
        }
    }

    /**
     * Runs the LLM extraction and silently auto-retries transient (retryable)
     * failures — timeouts and one-off inference errors — up to [LLM_MAX_ATTEMPTS]
     * times with a short backoff. The user only ever sees the final outcome,
     * never the in-between hiccups. A non-retryable failure or a success returns
     * immediately; cancellation always propagates.
     */
    @VisibleForTesting
    internal suspend fun runLlmExtractionWithRetry(
        request: LlmExtractionRequest,
    ): LlmExtractionResult {
        var attempt = 1
        while (true) {
            val result = try {
                MindlayerLlmCallGate.withPermit {
                    withTimeout(BAG_PHOTO_LLM_TIMEOUT_MS) {
                        llmProvider.extractBagFields(request)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                LlmExtractionResult.Failed(
                    "Brew photo LLM enrichment timed out after ${BAG_PHOTO_LLM_TIMEOUT_MS / 1000}s",
                    retryable = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                LlmExtractionResult.Failed("Brew photo LLM enrichment threw: ${e.message}", retryable = true)
            }

            if (result !is LlmExtractionResult.Failed || !result.retryable || attempt >= LLM_MAX_ATTEMPTS) {
                return result
            }
            Log.w(
                BAG_PHOTO_TAG,
                "LLM enrichment attempt $attempt failed (retryable): ${result.error}; auto-retrying",
            )
            delay(LLM_RETRY_BACKOFF_MS * attempt)
            attempt++
        }
    }

    private suspend fun runVisionEnrichmentIfNeeded(
        photoUriList: List<String>,
        processedPhotos: List<ProcessedBagPhoto>,
        allCandidates: MutableList<BagFieldCandidate>,
    ) {
        if (!llmProvider.supportsVision() || !llmProvider.isAvailable()) return
        val preVisionEvidence = resolveBagPhotoFieldEvidence(allCandidates)
        val visionFields = BagPhotoVisionPlanner.selectVisionFields(preVisionEvidence)
        if (visionFields.isEmpty()) return

        val visionOutcome = tryVisionLlmEnrichment(
            photoUriList = photoUriList,
            processedPhotos = processedPhotos,
            existingCandidates = allCandidates.toList(),
            fieldsNeeded = visionFields,
            knownFieldValues = currentKnownValues,
        )
        allCandidates += BagPhotoVisionPlanner.filterVisionCandidates(visionOutcome.candidates, preVisionEvidence)
    }

    private suspend fun tryVisionLlmEnrichment(
        photoUriList: List<String>,
        processedPhotos: List<ProcessedBagPhoto>,
        existingCandidates: List<BagFieldCandidate>,
        fieldsNeeded: Set<String>,
        knownFieldValues: KnownFieldValues,
    ): LlmEnrichmentOutcome {
        val existingFields = BagFieldContextMapper.buildExistingFieldsContext(existingCandidates)
        val bestPhotoUri = processedPhotos.maxByOrNull { it.quality.blurScore }?.uri
            ?: photoUriList.firstOrNull()
        val photoBytes = bestPhotoUri?.let { uri ->
            try {
                readPhotoBytesForLlm(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(BAG_PHOTO_TAG, "Failed to read photo bytes for vision pass", e)
                null
            }
        } ?: return LlmEnrichmentOutcome(status = LlmEnrichmentStatus.FAILED)

        val cacheKey = LlmCacheKey.compute(
            imageBytes = photoBytes,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = null,
            existingFields = existingFields,
            mode = "vision",
        )
        llmCache.get(cacheKey)?.let { cached ->
            return LlmEnrichmentOutcome(
                candidates = cached.fieldCandidates,
                status = LlmEnrichmentStatus.SUCCEEDED,
            )
        }

        val request = LlmExtractionRequest(
            imageBytes = photoBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = null,
            knownFieldValues = knownFieldValues,
        )
        val result = try {
            MindlayerLlmCallGate.withPermit {
                withTimeout(BAG_PHOTO_VISION_TIMEOUT_MS) {
                    llmProvider.extractBagFieldsWithVision(request)
                }
            }
        } catch (_: TimeoutCancellationException) {
            LlmExtractionResult.Failed("Vision enrichment timed out", retryable = false)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            LlmExtractionResult.Failed("Vision enrichment threw: ${e.message}", retryable = false)
        }

        return when (result) {
            is LlmExtractionResult.Success -> {
                llmCache.put(cacheKey, result)
                LlmEnrichmentOutcome(
                    candidates = result.fieldCandidates,
                    status = LlmEnrichmentStatus.SUCCEEDED,
                )
            }
            is LlmExtractionResult.Unavailable -> {
                Log.w(BAG_PHOTO_TAG, "Vision enrichment unavailable: ${result.reason}")
                LlmEnrichmentOutcome(status = LlmEnrichmentStatus.UNAVAILABLE)
            }
            is LlmExtractionResult.Failed -> {
                Log.w(BAG_PHOTO_TAG, "Vision enrichment failed: ${result.error}")
                LlmEnrichmentOutcome(status = LlmEnrichmentStatus.FAILED)
            }
        }
    }

    private fun confidenceHintForPass(
        quality: BagCaptureQuality,
        blockCount: Int,
    ): BagFieldConfidence = when {
        quality.readyForCapture && blockCount >= 2 -> BagFieldConfidence.MEDIUM
        quality.textDetected -> BagFieldConfidence.LOW
        else -> BagFieldConfidence.NEEDS_REVIEW
    }

    private fun findSupportingBlock(
        blocks: List<OcrFieldExtractor.OcrTextBlock>,
        value: String,
    ): OcrFieldExtractor.OcrTextBlock? {
        val normalizedValue = normalizeMatchText(value)
        if (normalizedValue.isBlank()) return null

        blocks.firstOrNull { block ->
            normalizeMatchText(block.text).contains(normalizedValue)
        }?.let { return it }

        val targetTokens = normalizedValue.split(" ").filter { it.length >= 3 }
        if (targetTokens.isEmpty()) return null

        return blocks.maxByOrNull { block ->
            val normalizedBlock = normalizeMatchText(block.text)
            targetTokens.count { token -> normalizedBlock.contains(token) }
        }?.takeIf { block ->
            val normalizedBlock = normalizeMatchText(block.text)
            targetTokens.any { token -> normalizedBlock.contains(token) }
        }
    }

    private fun normalizeMatchText(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private suspend fun recognizeText(bitmap: Bitmap): RecognizedText? {
        val service = ocrService ?: return null
        return service.recognize(bitmap)
    }

    private fun combineOcrTextBySide(photos: List<ProcessedBagPhoto>): String =
        BagOcrTextMerger.combineBySide(photos.map { it.side to it.fullText })

    private suspend fun scanBarcodes(
        scanner: BarcodeScanner,
        bitmap: Bitmap,
    ): List<Barcode>? = suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes -> cont.resume(codes) }
            .addOnFailureListener { cont.resume(null) }
    }

    private data class ProcessedBagPhoto(
        val uri: String,
        val side: BagCaptureSide,
        val quality: BagCaptureQuality,
        val passes: List<ScanPass>,
        val fullText: String,
        val detectedBarcode: String?,
        val detectedQrUrl: String?,
    )

    private data class QrLinkEnrichment(
        val safeUrl: String? = null,
        val candidates: List<BagFieldCandidate> = emptyList(),
        val reviewHints: List<BagPhotoReviewHint> = emptyList(),
    )

    private data class LlmEnrichmentOutcome(
        val candidates: List<BagFieldCandidate> = emptyList(),
        val status: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
    )

    private data class ScanPass(
        val label: String,
        val result: OcrFieldExtractor.OcrExtractionResult,
        val blocks: List<OcrFieldExtractor.OcrTextBlock>,
        val fullText: String,
    )
}
