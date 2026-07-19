package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
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
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.data.work.bagReviewContextsMatch
import com.adsamcik.starlitcoffee.navigation.ScanDraftTransfer
import com.adsamcik.starlitcoffee.navigation.ScanThumbnailFocus
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagAnalysisProgressScreen
import com.adsamcik.starlitcoffee.ui.component.ConsentOutcome
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.RescanDeltaDialog
import com.adsamcik.starlitcoffee.ui.component.ScannedBagSaveResult
import com.adsamcik.starlitcoffee.ui.component.messageRes
import com.adsamcik.starlitcoffee.ui.component.persistRescannedBagUpdate
import com.adsamcik.starlitcoffee.ui.component.persistScannedBag
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerConsentFlow
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import com.adsamcik.starlitcoffee.viewmodel.BagScanPhase
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val THUMBNAIL_TARGET_PX = 512

/** Snapshot of the extraction result the review surfaces present to the user. */
data class ScanReviewData(
    val sessionId: String? = null,
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
    val generationId: String? = null,
)

/** Callbacks a review surface uses to drive the surrounding scan flow. */
data class ScanReviewCallbacks(
    /** Return to the camera to capture additional photos. */
    val onScanMore: () -> Unit,
    /** Leave the scan flow (after saving, or on discard). */
    val onExit: () -> Unit,
    /** Transfer staged-photo ownership to another review form before leaving. */
    val onTransfer: () -> Unit,
)

/**
 * Hosts the guided bag-scan flow on a single nav route: camera capture and the
 * review/action surface, switched by [BagScanCaptureViewModel] phase so "scan
 * more photos" keeps the session intact. Extraction requests emitted by the
 * capture VM are forwarded to [BrewViewModel.processNewBagPhotos], so extraction
 * starts after the first photo and refines as
 * more arrive. While a pass runs in the review phase the user sees the
 * analyzing screen (with Skip AI / Continue in background); otherwise the
 * provided [reviewContent] renders the prefilled, editable result.
 */
@Composable
fun GuidedScanFlow(
    captureViewModel: BagScanCaptureViewModel,
    brewViewModel: BrewViewModel,
    onExit: () -> Unit,
    reviewContext: BagReviewContext = BagReviewContext.addNew(),
    reviewContent: @Composable (data: ScanReviewData, callbacks: ScanReviewCallbacks) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val state by captureViewModel.uiState.collectAsStateWithLifecycle()
    val bagPhotoResult by brewViewModel.bagPhotoResult.collectAsStateWithLifecycle()
    val bagPhotoRetryResult by brewViewModel.bagPhotoRetryResult.collectAsStateWithLifecycle()
    val pendingScanReview by brewViewModel.pendingScanReview.collectAsStateWithLifecycle()
    val bagPhotoProgress by brewViewModel.bagPhotoProgress.collectAsStateWithLifecycle()
    val scanSessionId = state.sessionId

    var reviewData by remember(scanSessionId) {
        mutableStateOf(initialScanReviewData(scanSessionId))
    }
    // Forward debounced/finished capture events to the extraction pipeline.
    LaunchedEffect(scanSessionId, reviewContext) {
        captureViewModel.extractionRequests.collect { csv ->
            brewViewModel.processNewBagPhotos(
                photosCsv = csv,
                sessionId = scanSessionId,
                reviewContext = reviewContext,
            )
        }
    }

    LaunchedEffect(pendingScanReview, reviewContext) {
        val pending = pendingScanReview ?: return@LaunchedEffect
        if (!bagReviewContextsMatch(reviewContext, pending.reviewContext)) return@LaunchedEffect
        captureViewModel.resumeReview(pending.sessionId, pending.result.capturedPhotoUris)
        brewViewModel.promotePendingScanReviewToForeground(pending.sessionId)
    }

    LaunchedEffect(bagPhotoResult, reviewContext, scanSessionId) {
        val sessionResult = bagPhotoResult ?: return@LaunchedEffect
        if (!bagReviewContextsMatch(reviewContext, sessionResult.reviewContext)) {
            return@LaunchedEffect
        }
        if (sessionResult.sessionId != scanSessionId) {
            captureViewModel.resumeReview(
                sessionResult.sessionId,
                sessionResult.result.capturedPhotoUris,
            )
        } else {
            val result = sessionResult.result
            reviewData = ScanReviewData(
                sessionId = scanSessionId,
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
                generationId = sessionResult.generationId.takeIf(String::isNotBlank),
            )
        }
    }

    LaunchedEffect(bagPhotoRetryResult) {
        val sessionResult = bagPhotoRetryResult ?: return@LaunchedEffect
        if (sessionResult.sessionId != scanSessionId) return@LaunchedEffect
        if (!bagReviewContextsMatch(reviewContext, sessionResult.reviewContext)) return@LaunchedEffect
        val result = sessionResult.result
        reviewData = reviewData.copy(
            ocrPrefill = result.ocrPrefill ?: reviewData.ocrPrefill,
            fieldEvidence = result.fieldEvidence.ifEmpty { reviewData.fieldEvidence },
            reviewHints = result.reviewHints.ifEmpty { reviewData.reviewHints },
            llmStatus = result.llmStatus,
            thumbnailFocus = result.thumbnailFocus ?: reviewData.thumbnailFocus,
            generationId = sessionResult.generationId.takeIf(String::isNotBlank) ?: reviewData.generationId,
        )
    }

    fun cleanupCaches() {
        captureViewModel.uiState.value.photos.forEach {
            ScanPhotoStorage.deleteStagedCapture(context, it.uri)
        }
    }

    fun discardAndExit() {
        brewViewModel.completeBagPhotoReview(scanSessionId)
        brewViewModel.cancelBagPhotoProcessing(scanSessionId)
        cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    fun finishAndExit() {
        // Photos were already copied to permanent storage by the save action.
        brewViewModel.completeBagPhotoReview(scanSessionId)
        brewViewModel.cancelBagPhotoProcessing(scanSessionId)
        cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    fun transferAndExit() {
        if (shouldDeleteStagedPhotosOnExit(ownershipTransferred = true)) cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    val hasCompletedResult =
        bagPhotoResult?.let { result ->
            result.sessionId == scanSessionId &&
                bagReviewContextsMatch(reviewContext, result.reviewContext)
        } == true ||
            bagPhotoRetryResult?.let { result ->
                result.sessionId == scanSessionId &&
                    bagReviewContextsMatch(reviewContext, result.reviewContext)
            } == true
    val scanMore = {
        captureViewModel.backToCapture()
    }
    if (shouldShowCompletedScanReview(hasCompletedResult, state.phase == BagScanPhase.REVIEWING)) {
        reviewContent(
            reviewData,
            ScanReviewCallbacks(
                onScanMore = scanMore,
                onExit = ::finishAndExit,
                onTransfer = ::transferAndExit,
            ),
        )
    } else {
        when (state.phase) {
            BagScanPhase.CAPTURING -> GuidedCaptureScreen(
                captureViewModel = captureViewModel,
                onBack = ::discardAndExit,
            )

            BagScanPhase.REVIEWING -> {
                // Show the analyzing screen until the first extraction result lands
                // (or a later pass is running), so we never flash an empty form.
                if (state.hasPhotos) {
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
                        progress = bagPhotoProgress,
                    )
                } else {
                    reviewContent(
                        reviewData,
                        ScanReviewCallbacks(
                            onScanMore = scanMore,
                            onExit = ::finishAndExit,
                            onTransfer = ::transferAndExit,
                        ),
                    )
                }
            }
        }
    }
}

internal fun shouldShowCompletedScanReview(
    hasCompletedResult: Boolean,
    isReviewing: Boolean,
): Boolean = hasCompletedResult && isReviewing

internal fun initialScanReviewData(sessionId: String): ScanReviewData =
    ScanReviewData(sessionId = sessionId)

internal fun shouldDeleteStagedPhotosOnExit(ownershipTransferred: Boolean): Boolean =
    !ownershipTransferred

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
    val sanitizedFieldEvidence = remember(data.fieldEvidence) {
        BagPhotoScanSupport.sanitizeFieldEvidence(data.fieldEvidence)
    }
    val sanitizedPrefill = remember(data.ocrPrefill, sanitizedFieldEvidence) {
        sanitizedFieldEvidence
            .takeIf { it.isNotEmpty() }
            ?.let(BagPhotoScanSupport::buildPrefill)
            ?: data.ocrPrefill
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedLabel = stringResource(R.string.msg_bag_saved)
    val couldNotReadLabel = stringResource(R.string.msg_could_not_read_label)
    val couldNotSaveBag = stringResource(R.string.msg_could_not_save_bag)
    val consentMessages = ConsentOutcome.entries.associateWith { outcome ->
        stringResource(outcome.messageRes())
    }
    var isRetryingLlm by remember { mutableStateOf(false) }
    LaunchedEffect(data.llmStatus, data.fieldEvidence, data.generationId) {
        isRetryingLlm = false
    }
    val aiConsentFlow = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> scope.launch {
                (context.applicationContext as? StarlitCoffeeApp)?.reconnectMindlayer()
                isRetryingLlm = data.sessionId?.let(brewViewModel::retryBagPhotoLlm) == true
            }
            else -> Toast.makeText(context, consentMessages.getValue(outcome), Toast.LENGTH_LONG).show()
        }
    }
    var isSavingBag by remember { mutableStateOf(false) }

    AddBagSheet(
        initialBarcode = data.detectedBarcode,
        ocrPrefill = sanitizedPrefill,
        initialName = data.offLookupName,
        initialRoaster = data.offLookupRoaster,
        traceabilityUrl = data.detectedQrUrl,
        capturedPhotoUris = data.capturedPhotoUris,
        fieldEvidence = sanitizedFieldEvidence,
        reviewHints = data.reviewHints,
        llmStatus = data.llmStatus,
        isProcessing = isRetryingLlm,
        isSaving = isSavingBag,
        existingBags = existingBags,
        onScanMorePhotos = callbacks.onScanMore,
        onExploreQrUrl = { url, callback -> brewViewModel.exploreApprovedQrLink(url, callback) },
        onRetryLlmEnrichment = {
            isRetryingLlm = data.sessionId?.let(brewViewModel::retryBagPhotoLlm) == true
        },
        onEnableAi = aiConsentFlow.request,
        onDismiss = callbacks.onExit,
        onSave = save@{ name, roaster, origin, region, farm, altitude, roastLevel, barcode, weightG, notes,
                   variety, processType, tastingNotes, isDecaf, decafProcess, roastDateMillis, expiryDateMillis ->
            val saveSessionId = checkNotNull(data.sessionId) { "Scan review is missing its session ID" }
            if (isSavingBag || !brewViewModel.beginScannedBagSave(saveSessionId)) return@save
            isSavingBag = true
            val rawPhotoUris = data.capturedPhotoUris
            val qrUrl = data.detectedQrUrl
            val scanFocus = data.thumbnailFocus
            scope.launch {
                try {
                    when (
                        val saveResult = persistScannedBag(
                            context = context,
                            brewViewModel = brewViewModel,
                            rawPhotoUris = rawPhotoUris,
                            scanSessionId = saveSessionId,
                            thumbnailFocus = scanFocus,
                            thumbnailTargetPx = THUMBNAIL_TARGET_PX,
                        ) { photoUri, photoUris ->
                            BrewViewModel.CoffeeBagInput(
                                name = name,
                                roaster = roaster,
                                origin = origin,
                                region = region,
                                farm = farm,
                                altitude = altitude,
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
                                photoUri = photoUri,
                                photoUris = photoUris,
                                traceabilityUrl = qrUrl,
                            )
                        }
                    ) {
                        ScannedBagSaveResult.PhotoCopyFailed -> {
                            Toast.makeText(context, couldNotReadLabel, Toast.LENGTH_LONG).show()
                        }
                        is ScannedBagSaveResult.Failed -> {
                            Log.e("ScanAddBagReview", "Failed to save scanned coffee bag", saveResult.error)
                            Toast.makeText(context, couldNotSaveBag, Toast.LENGTH_LONG).show()
                        }
                        is ScannedBagSaveResult.Saved -> {
                            brewViewModel.cancelBagPhotoRetry(data.sessionId)
                            Toast.makeText(context, savedLabel, Toast.LENGTH_SHORT).show()
                            callbacks.onExit()
                        }
                    }
                } finally {
                    brewViewModel.finishScannedBagSave(saveSessionId)
                    isSavingBag = false
                }
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
    onNewBag: (ScanDraftTransfer) -> Unit,
) {
    val resolvedFields = remember(data.fieldEvidence) {
        resolveRescanFieldEvidence(data.fieldEvidence)
            .mapValues { (_, evidence) -> evidence.value }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isUpdatingBag by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        DestructiveActionDialog(
            titleRes = R.string.dialog_discard_scan_title,
            messageRes = R.string.msg_discard_scan_body,
            confirmLabelRes = R.string.action_discard,
            onConfirm = {
                showDiscardDialog = false
                callbacks.onExit()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    RescanDeltaDialog(
        bag = bag,
        resolvedFields = resolvedFields,
        reviewedPhotoUris = data.capturedPhotoUris,
        isUpdating = isUpdatingBag,
        onUpdateBag = { updated ->
            if (isUpdatingBag) return@RescanDeltaDialog
            val saveSessionId = checkNotNull(data.sessionId) {
                "Rescan review is missing its session ID"
            }
            if (!brewViewModel.beginScannedBagSave(saveSessionId)) return@RescanDeltaDialog
            isUpdatingBag = true
            scope.launch {
                try {
                    when (
                        val updateResult = persistRescannedBagUpdate(
                            context = context,
                            brewViewModel = brewViewModel,
                            originalBag = bag,
                            updatedBag = updated,
                            rawPhotoUris = data.capturedPhotoUris,
                            scanSessionId = saveSessionId,
                            thumbnailFocus = data.thumbnailFocus,
                            thumbnailTargetPx = THUMBNAIL_TARGET_PX,
                        )
                    ) {
                        ScannedBagSaveResult.PhotoCopyFailed -> {
                            Toast.makeText(context, R.string.msg_could_not_read_label, Toast.LENGTH_LONG).show()
                        }
                        is ScannedBagSaveResult.Failed -> {
                            Log.e(
                                "ScanRescanReview",
                                "Failed to update rescanned coffee bag",
                                updateResult.error,
                            )
                            Toast.makeText(
                                context,
                                R.string.msg_could_not_save_changes,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        is ScannedBagSaveResult.Saved -> callbacks.onExit()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    Log.e("ScanRescanReview", "Failed to update rescanned coffee bag", error)
                    Toast.makeText(context, R.string.msg_could_not_save_changes, Toast.LENGTH_LONG).show()
                } finally {
                    brewViewModel.finishScannedBagSave(saveSessionId)
                    isUpdatingBag = false
                }
            }
        },
        onNewBag = { fields ->
            onNewBag(
                ScanDraftTransfer(
                    fields = fields,
                    capturedPhotoUris = data.capturedPhotoUris,
                    scanSessionId = checkNotNull(data.sessionId) {
                        "Rescan review is missing its session ID"
                    },
                    generationId = data.generationId,
                    thumbnailFocus = ScanThumbnailFocus.from(data.thumbnailFocus),
                    detectedBarcode = data.detectedBarcode,
                    detectedQrUrl = data.detectedQrUrl,
                ),
            )
            callbacks.onTransfer()
        },
        onDismiss = { showDiscardDialog = true },
    )
}

internal fun resolveRescanFieldEvidence(
    fieldEvidence: Map<String, BagFieldEvidence>,
): Map<String, BagFieldEvidence> = BagPhotoScanSupport.sanitizeFieldEvidence(fieldEvidence)
