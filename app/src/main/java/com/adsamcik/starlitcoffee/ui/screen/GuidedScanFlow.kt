package com.adsamcik.starlitcoffee.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagAnalysisProgressScreen
import com.adsamcik.starlitcoffee.ui.component.ConsentOutcome
import com.adsamcik.starlitcoffee.ui.component.RescanDeltaDialog
import com.adsamcik.starlitcoffee.ui.component.messageRes
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerConsentFlow
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagThumbnailWriter
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import com.adsamcik.starlitcoffee.viewmodel.BagScanPhase
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THUMBNAIL_TARGET_PX = 512

/** Snapshot of the extraction result the review surfaces present to the user. */
data class ScanReviewData(
    val ocrPrefill: OcrFieldExtractor.OcrExtractionResult? = null,
    val capturedPhotoUris: String? = null,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
    val offLookupName: String? = null,
    val offLookupRoaster: String? = null,
    val fieldEvidence: Map<String, BagFieldEvidence> = emptyMap(),
    val reviewHints: List<BagPhotoReviewHint> = emptyList(),
    val llmStatus: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
    val thumbnailFocus: BagPhotoRect? = null,
)

/** Callbacks a review surface uses to drive the surrounding scan flow. */
data class ScanReviewCallbacks(
    /** Return to the camera to capture additional photos. */
    val onScanMore: () -> Unit,
    /** Leave the scan flow (after saving, or on discard). */
    val onExit: () -> Unit,
)

/**
 * Hosts the guided bag-scan flow on a single nav route: camera capture and the
 * review/action surface, switched by [BagScanCaptureViewModel] phase so "scan
 * more photos" keeps the session intact. Extraction requests emitted by the
 * capture VM are forwarded to [BrewViewModel.processNewBagPhotos] (which cancels
 * any earlier pass), so extraction starts after the first photo and refines as
 * more arrive. While a pass runs in the review phase the user sees the
 * analyzing screen (with Skip AI / Continue in background); otherwise the
 * provided [reviewContent] renders the prefilled, editable result.
 */
@Composable
fun GuidedScanFlow(
    captureViewModel: BagScanCaptureViewModel,
    brewViewModel: BrewViewModel,
    onExit: () -> Unit,
    reviewContent: @Composable (data: ScanReviewData, callbacks: ScanReviewCallbacks) -> Unit,
) {
    val state by captureViewModel.uiState.collectAsStateWithLifecycle()
    val bagPhotoResult by brewViewModel.bagPhotoResult.collectAsStateWithLifecycle()

    var reviewData by remember { mutableStateOf(ScanReviewData()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Start each scan session from a clean slate so a stale result from a prior
    // scan can't pre-fill this one.
    LaunchedEffect(Unit) { brewViewModel.clearBagPhotoResult() }

    // Forward debounced/finished capture events to the extraction pipeline.
    LaunchedEffect(Unit) {
        captureViewModel.extractionRequests.collect { csv ->
            isProcessing = true
            brewViewModel.processNewBagPhotos(csv)
        }
    }

    LaunchedEffect(bagPhotoResult) {
        val result = bagPhotoResult
        if (result != null) {
            reviewData = ScanReviewData(
                ocrPrefill = result.ocrPrefill,
                capturedPhotoUris = result.capturedPhotoUris,
                detectedBarcode = result.detectedBarcode,
                detectedQrUrl = result.detectedQrUrl,
                offLookupName = result.offLookupName,
                offLookupRoaster = result.offLookupRoaster,
                fieldEvidence = result.fieldEvidence,
                reviewHints = result.reviewHints,
                llmStatus = result.llmStatus,
                thumbnailFocus = result.thumbnailFocus,
            )
            isProcessing = false
        }
    }

    fun cleanupCaches() {
        captureViewModel.uiState.value.photos.forEach { ScanPhotoStorage.deleteCapture(it.uri) }
    }

    fun discardAndExit() {
        brewViewModel.cancelBagPhotoProcessing()
        cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    fun finishAndExit() {
        // Photos were already copied to permanent storage by the save action.
        brewViewModel.cancelBagPhotoProcessing()
        cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    when (state.phase) {
        BagScanPhase.CAPTURING -> GuidedCaptureScreen(
            captureViewModel = captureViewModel,
            onBack = ::discardAndExit,
        )

        BagScanPhase.REVIEWING -> {
            // Show the analyzing screen until the first extraction result lands
            // (or a later pass is running), so we never flash an empty form.
            if (state.hasPhotos && (isProcessing || bagPhotoResult == null)) {
                BagAnalysisProgressScreen(
                    onSkip = { brewViewModel.skipBagPhotoLlm() },
                    onRunInBackground = {
                        // Leave extraction running; it delivers via notification.
                        // Do NOT delete the cache photos — the background pass
                        // still reads them.
                        brewViewModel.continueBagAnalysisInBackground()
                        captureViewModel.reset()
                        onExit()
                    },
                )
            } else {
                reviewContent(
                    reviewData,
                    ScanReviewCallbacks(
                        onScanMore = { captureViewModel.backToCapture() },
                        onExit = ::finishAndExit,
                    ),
                )
            }
        }
    }
}

/**
 * Review surface for adding a NEW bag from a guided scan: the editable,
 * prefilled [AddBagSheet] plus the save pipeline (copy photos to permanent
 * storage, build a focused thumbnail, persist the bag).
 */
@Composable
fun ScanAddBagReview(
    brewViewModel: BrewViewModel,
    data: ScanReviewData,
    callbacks: ScanReviewCallbacks,
    existingBags: List<CoffeeBagEntity>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiConsentFlow = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> scope.launch {
                (context.applicationContext as? StarlitCoffeeApp)?.reconnectMindlayer()
                brewViewModel.retryBagPhotoLlm()
            }
            else -> Toast.makeText(context, context.getString(outcome.messageRes()), Toast.LENGTH_LONG).show()
        }
    }
    val savedLabel = stringResource(R.string.msg_bag_saved)

    AddBagSheet(
        initialBarcode = data.detectedBarcode,
        ocrPrefill = data.ocrPrefill,
        initialName = data.offLookupName,
        initialRoaster = data.offLookupRoaster,
        traceabilityUrl = data.detectedQrUrl,
        capturedPhotoUris = data.capturedPhotoUris,
        fieldEvidence = data.fieldEvidence,
        reviewHints = data.reviewHints,
        llmStatus = data.llmStatus,
        isProcessing = false,
        existingBags = existingBags,
        onScanMorePhotos = callbacks.onScanMore,
        onExploreQrUrl = { url, callback -> brewViewModel.exploreApprovedQrLink(url, callback) },
        onRetryLlmEnrichment = { brewViewModel.retryBagPhotoLlm() },
        onEnableAi = aiConsentFlow.request,
        onDismiss = callbacks.onExit,
        onSave = { name, roaster, origin, region, roastLevel, barcode, weightG, notes,
                   variety, processType, tastingNotes, isDecaf, decafProcess, roastDateMillis, expiryDateMillis ->
            val rawPhotoUris = data.capturedPhotoUris
            val qrUrl = data.detectedQrUrl
            val scanFocus = data.thumbnailFocus
            scope.launch {
                val permanentUris = rawPhotoUris?.let { uris ->
                    withContext(Dispatchers.IO) { ScanPhotoStorage.copyPhotosToPermanentStorage(context, uris) }
                }
                val frontPhotoUri = permanentUris?.split(",")?.firstOrNull()
                val thumbnailUri = if (frontPhotoUri != null && scanFocus != null) {
                    withContext(Dispatchers.IO) {
                        BagThumbnailWriter.createFocusedThumbnail(
                            context = context,
                            sourceUri = frontPhotoUri,
                            focus = scanFocus,
                            targetSizePx = THUMBNAIL_TARGET_PX,
                        )
                    }
                } else {
                    null
                }
                brewViewModel.addCoffeeBag(
                    input = BrewViewModel.CoffeeBagInput(
                        name = name,
                        roaster = roaster,
                        origin = origin,
                        region = region,
                        roastLevel = roastLevel,
                        barcode = barcode,
                        weightG = weightG,
                        notes = notes,
                        variety = variety,
                        processType = processType,
                        tastingNotes = tastingNotes,
                        isDecaf = isDecaf,
                        decafProcess = decafProcess,
                        roastDate = roastDateMillis,
                        expiryDate = expiryDateMillis,
                        photoUri = thumbnailUri ?: frontPhotoUri,
                        photoUris = permanentUris,
                        traceabilityUrl = qrUrl,
                    ),
                )
                Toast.makeText(context, savedLabel, Toast.LENGTH_SHORT).show()
                callbacks.onExit()
            }
        },
    )
}

/**
 * Review surface for RE-scanning an existing bag: presents the scan deltas so
 * the user can update the existing bag, fork a new one, or discard.
 */
@Composable
fun ScanRescanReview(
    brewViewModel: BrewViewModel,
    bag: CoffeeBagEntity,
    data: ScanReviewData,
    callbacks: ScanReviewCallbacks,
    onNewBag: (Map<String, String>) -> Unit,
) {
    val resolvedFields = remember(data.fieldEvidence) {
        data.fieldEvidence.mapValues { (_, evidence) -> evidence.value }
    }
    RescanDeltaDialog(
        bag = bag,
        resolvedFields = resolvedFields,
        onUpdateBag = { updated ->
            brewViewModel.updateCoffeeBag(updated)
            callbacks.onExit()
        },
        onNewBag = { fields ->
            onNewBag(fields)
            callbacks.onExit()
        },
        onDismiss = callbacks.onExit,
    )
}
