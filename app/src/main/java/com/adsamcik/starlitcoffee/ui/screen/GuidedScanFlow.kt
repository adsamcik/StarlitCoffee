package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.data.work.BagReviewMode
import com.adsamcik.starlitcoffee.data.work.BagDraftField
import com.adsamcik.starlitcoffee.data.work.BagDraftReviewState
import com.adsamcik.starlitcoffee.data.work.BagScanDraft
import com.adsamcik.starlitcoffee.data.work.bagReviewContextsMatch
import com.adsamcik.starlitcoffee.data.work.decodeBagExtractionResult
import com.adsamcik.starlitcoffee.navigation.ScanDraftTransfer
import com.adsamcik.starlitcoffee.navigation.ScanThumbnailFocus
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagFormSnapshot
import com.adsamcik.starlitcoffee.ui.component.ConsentOutcome
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.RescanDeltaDialog
import com.adsamcik.starlitcoffee.ui.component.ScannedBagSaveResult
import com.adsamcik.starlitcoffee.ui.component.messageRes
import com.adsamcik.starlitcoffee.ui.component.persistRescannedBagUpdate
import com.adsamcik.starlitcoffee.ui.component.persistScannedBag
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerConsentFlow
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerInstalled
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.MindlayerAvailability
import com.adsamcik.starlitcoffee.util.MindlayerInstallLink
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionUiStateMapper
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import com.adsamcik.starlitcoffee.viewmodel.BagScanDraftViewModel
import com.adsamcik.starlitcoffee.viewmodel.BagScanPhase
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    val isProcessing: Boolean = false,
)

/** Callbacks a review surface uses to drive the surrounding scan flow. */
data class ScanReviewCallbacks(
    /** Return to the camera to capture additional photos. */
    val onScanMore: () -> Unit,
    /** Leave the scan flow (after saving, or on discard). */
    val onExit: () -> Unit,
    /** Save completed and the draft may be closed. */
    val onSaved: () -> Unit,
    /** Explicit destructive discard. */
    val onDiscard: () -> Unit,
    /** Transfer staged-photo ownership to another review form before leaving. */
    val onTransfer: () -> Unit,
)

/**
 * Hosts the guided bag-scan flow on a single nav route: camera capture and the
 * review/action surface, switched by [BagScanCaptureViewModel] phase so "scan
 * more photos" keeps the session intact. Extraction requests emitted by the
 * capture VM are forwarded to [BrewViewModel.processNewBagPhotos], so extraction
 * starts after the first photo and refines as more arrive. Review opens as an
 * editable draft immediately; recognition results merge into that draft while
 * the user can continue editing, save, add photos, or leave it in the background.
 */
@Composable
fun GuidedScanFlow(
    captureViewModel: BagScanCaptureViewModel,
    brewViewModel: BrewViewModel,
    onExit: () -> Unit,
    reviewContext: BagReviewContext = BagReviewContext.addNew(),
    onReviewReady: ((sessionId: String) -> Unit)? = null,
    reviewContent: @Composable (data: ScanReviewData, callbacks: ScanReviewCallbacks) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val state by captureViewModel.uiState.collectAsStateWithLifecycle()
    val bagPhotoResult by brewViewModel.bagPhotoResult.collectAsStateWithLifecycle()
    val bagPhotoRetryResult by brewViewModel.bagPhotoRetryResult.collectAsStateWithLifecycle()
    val pendingScanReview by brewViewModel.pendingScanReview.collectAsStateWithLifecycle()
    val bagPhotoProgress by brewViewModel.bagPhotoProgress.collectAsStateWithLifecycle()
    val bagAnalysisPreview by brewViewModel.bagAnalysisPreview.collectAsStateWithLifecycle()
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

    LaunchedEffect(state.phase, scanSessionId, state.photos, reviewContext, onReviewReady) {
        if (state.phase != BagScanPhase.REVIEWING) return@LaunchedEffect
        brewViewModel.ensureBagDraft(scanSessionId, state.photosCsv(), reviewContext)
        brewViewModel.markBagDraftReviewing(scanSessionId)
        onReviewReady?.invoke(scanSessionId)
    }

    LaunchedEffect(bagAnalysisPreview, scanSessionId) {
        val preview = bagAnalysisPreview?.result ?: return@LaunchedEffect
        reviewData = preview.toScanReviewData(scanSessionId, isProcessing = true)
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
                isProcessing = false,
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
            isProcessing = false,
        )
    }

    fun cleanupCaches() {
        captureViewModel.uiState.value.photos.forEach {
            ScanPhotoStorage.deleteStagedCapture(context, it.uri)
        }
    }

    fun discardAndExit() {
        brewViewModel.markBagDraftDiscarded(scanSessionId)
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

    fun saveAndExit() {
        brewViewModel.markBagDraftSaved(scanSessionId)
        finishAndExit()
    }

    fun preserveAndExit() {
        brewViewModel.markBagDraftBackgrounded(scanSessionId)
        brewViewModel.continueBagAnalysisInBackground(scanSessionId)
        captureViewModel.reset()
        onExit()
    }

    fun transferAndExit() {
        if (shouldDeleteStagedPhotosOnExit(ownershipTransferred = true)) cleanupCaches()
        captureViewModel.reset()
        onExit()
    }

    val scanMore = {
        captureViewModel.backToCapture()
    }
    if (state.phase == BagScanPhase.REVIEWING && onReviewReady == null) {
        reviewContent(
            reviewData.copy(isProcessing = reviewData.isProcessing || bagPhotoProgress != null),
            ScanReviewCallbacks(
                onScanMore = scanMore,
                onExit = ::preserveAndExit,
                onSaved = ::saveAndExit,
                onDiscard = ::discardAndExit,
                onTransfer = ::transferAndExit,
            ),
        )
    } else {
        when (state.phase) {
            BagScanPhase.CAPTURING -> GuidedCaptureScreen(
                captureViewModel = captureViewModel,
                onBack = ::discardAndExit,
            )

            BagScanPhase.REVIEWING -> Unit
        }
    }
}

internal fun shouldShowCompletedScanReview(
    hasCompletedResult: Boolean,
    isReviewing: Boolean,
): Boolean = hasCompletedResult && isReviewing

internal fun initialScanReviewData(sessionId: String): ScanReviewData =
    ScanReviewData(sessionId = sessionId)

private fun BagPhotoProcessingResult.toScanReviewData(
    sessionId: String,
    isProcessing: Boolean,
): ScanReviewData = ScanReviewData(
    sessionId = sessionId,
    ocrPrefill = ocrPrefill,
    capturedPhotoUris = capturedPhotoUris,
    detectedBarcode = detectedBarcode,
    detectedQrUrl = detectedQrUrl,
    offLookupName = offLookupName,
    offLookupRoaster = offLookupRoaster,
    fieldEvidence = fieldEvidence,
    reviewHints = reviewHints,
    llmStatus = llmStatus,
    thumbnailFocus = thumbnailFocus,
    isProcessing = isProcessing,
)

internal fun shouldDeleteStagedPhotosOnExit(ownershipTransferred: Boolean): Boolean =
    !ownershipTransferred

/** Exact-session review destination shared by capture, inventory, and notifications. */
@Composable
fun BagDraftReviewRoute(
    draftViewModel: BagScanDraftViewModel,
    brewViewModel: BrewViewModel,
    existingBags: List<CoffeeBagEntity>,
    onExit: () -> Unit,
    onScanMore: (String) -> Unit,
    onMissing: () -> Unit,
    onNewBagTransfer: (ScanDraftTransfer) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val draft by draftViewModel.draft.collectAsStateWithLifecycle()
    val current = draft
    LaunchedEffect(current?.sessionId) {
        if (current == null || !current.isActive) {
            onMissing()
        } else {
            draftViewModel.markReviewing()
        }
    }
    if (current == null || !current.isActive) return

    val result = remember(current.resultJson) {
        current.resultJson?.let { encoded ->
            runCatching { decodeBagExtractionResult(encoded) }.getOrNull()
        }
    }
    val data = remember(current, result) {
        (result?.toScanReviewData(
            sessionId = current.sessionId,
            isProcessing = current.recognitionRunState == com.adsamcik.starlitcoffee.util.RecognitionRunState.RUNNING ||
                current.recognitionRunState == com.adsamcik.starlitcoffee.util.RecognitionRunState.PARTIAL,
        ) ?: initialScanReviewData(current.sessionId).copy(
            capturedPhotoUris = current.photoUris.joinToString(",").takeIf(String::isNotBlank),
        )).copy(generationId = current.generationId)
    }

    fun leaveDraft() {
        draftViewModel.markBackgrounded()
        brewViewModel.markBagDraftBackgrounded(current.sessionId)
        brewViewModel.continueBagAnalysisInBackground(current.sessionId)
        onExit()
    }
    fun closeSavedDraft() {
        draftViewModel.markSaved()
        brewViewModel.markBagDraftSaved(current.sessionId)
        brewViewModel.completeBagPhotoReview(current.sessionId)
        brewViewModel.cancelBagPhotoProcessing(current.sessionId)
        scope.launch(Dispatchers.IO) {
            ScanPhotoStorage.deleteStagedCaptures(context, current.photoUris.joinToString(","))
        }
        onExit()
    }
    fun discardDraft() {
        draftViewModel.markDiscarded()
        brewViewModel.markBagDraftDiscarded(current.sessionId)
        brewViewModel.completeBagPhotoReview(current.sessionId)
        brewViewModel.cancelBagPhotoProcessing(current.sessionId)
        scope.launch(Dispatchers.IO) {
            ScanPhotoStorage.deleteStagedCaptures(context, current.photoUris.joinToString(","))
        }
        onExit()
    }
    val callbacks = ScanReviewCallbacks(
        onScanMore = {
            draftViewModel.markCapturing()
            onScanMore(current.sessionId)
        },
        onExit = ::leaveDraft,
        onSaved = ::closeSavedDraft,
        onDiscard = ::discardDraft,
        onTransfer = ::leaveDraft,
    )

    if (current.reviewContext.mode == BagReviewMode.RESCAN) {
        val target = existingBags.firstOrNull { it.id == current.reviewContext.targetBagId }
        if (target == null) {
            LaunchedEffect(current.reviewContext.targetBagId) { onMissing() }
            return
        }
        ScanRescanReview(
            brewViewModel = brewViewModel,
            bag = target,
            data = data,
            callbacks = callbacks,
            onNewBag = onNewBagTransfer,
        )
    } else {
        ScanAddBagReview(
            brewViewModel = brewViewModel,
            data = data,
            callbacks = callbacks,
            existingBags = existingBags,
            durableDraft = current,
            onUserFieldChange = draftViewModel::onUserEdit,
            onFieldFocusChange = draftViewModel::onFieldFocusChanged,
            onAcceptDraftSuggestion = draftViewModel::acceptSuggestion,
        )
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
    durableDraft: BagScanDraft? = null,
    onUserFieldChange: (BagDraftField, String?) -> Unit = { _, _ -> },
    onFieldFocusChange: (BagDraftField, Boolean) -> Unit = { _, _ -> },
    onAcceptDraftSuggestion: (BagDraftField) -> Unit = {},
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
    val recognitionPreference by brewViewModel.recognitionPreference.collectAsStateWithLifecycle()
    val mindlayerInstalled = rememberMindlayerInstalled()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedLabel = stringResource(R.string.msg_bag_saved)
    val couldNotReadLabel = stringResource(R.string.msg_could_not_read_label)
    val couldNotSaveBag = stringResource(R.string.msg_could_not_save_bag)
    val consentMessages = ConsentOutcome.entries.associateWith { outcome ->
        stringResource(outcome.messageRes())
    }
    var isRetryingLlm by remember { mutableStateOf(false) }
    var setupReturnPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data.llmStatus, data.fieldEvidence, data.generationId) {
        isRetryingLlm = false
    }
    val aiConsentFlow = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> scope.launch {
                brewViewModel.enableLabelRecognition()
                (context.applicationContext as? StarlitCoffeeApp)?.reconnectMindlayer()
                isRetryingLlm = data.sessionId?.let(brewViewModel::retryBagPhotoLlm) == true
            }
            else -> Toast.makeText(context, consentMessages.getValue(outcome), Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(lifecycleOwner, setupReturnPending, data.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && setupReturnPending) {
                setupReturnPending = false
                scope.launch {
                    (context.applicationContext as? StarlitCoffeeApp)?.reconnectMindlayer()
                    isRetryingLlm = data.sessionId?.let(brewViewModel::retryBagPhotoLlm) == true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var isSavingBag by remember { mutableStateOf(false) }
    val recognitionPresentation = remember(
        data.llmStatus,
        data.isProcessing,
        sanitizedFieldEvidence,
        durableDraft,
        recognitionPreference,
        mindlayerInstalled,
        isRetryingLlm,
    ) {
        if (durableDraft != null) {
            val capability = when {
                data.llmStatus == LlmEnrichmentStatus.SETUP_REQUIRED ->
                    RecognitionCapability.ASSET_SETUP_REQUIRED
                data.llmStatus == LlmEnrichmentStatus.UNAVAILABLE &&
                    !MindlayerAvailability.isSupported() ->
                    RecognitionCapability.UNSUPPORTED
                data.llmStatus == LlmEnrichmentStatus.UNAVAILABLE &&
                    !mindlayerInstalled ->
                    RecognitionCapability.INSTALLATION_REQUIRED
                data.llmStatus == LlmEnrichmentStatus.UNAVAILABLE ->
                    RecognitionCapability.AUTHORIZATION_REQUIRED
                else -> durableDraft.recognitionCapability
            }
            RecognitionUiStateMapper.map(
                capability = capability,
                runState = durableDraft.recognitionRunState,
                preference = recognitionPreference,
                hasValues = durableDraft.fields.values.any { !it.value.isNullOrBlank() },
                unresolvedCount = durableDraft.fields.values.count {
                    it.reviewState == BagDraftReviewState.NEEDS_REVIEW
                },
            )
        } else {
            RecognitionUiStateMapper.fromPipeline(
                pipelineStatus = data.llmStatus,
                isProcessing = data.isProcessing || isRetryingLlm,
                hasValues = sanitizedFieldEvidence.isNotEmpty(),
                unresolvedCount = sanitizedFieldEvidence.values.count {
                    it.confidence != com.adsamcik.starlitcoffee.util.BagFieldConfidence.HIGH
                },
                preference = recognitionPreference,
                mindlayerSupported = MindlayerAvailability.isSupported(),
                mindlayerInstalled = mindlayerInstalled,
            )
        }
    }

    AddBagSheet(
        initialBarcode = data.detectedBarcode,
        ocrPrefill = sanitizedPrefill,
        initialName = data.offLookupName,
        initialRoaster = data.offLookupRoaster,
        traceabilityUrl = data.detectedQrUrl,
        capturedPhotoUris = data.capturedPhotoUris,
        fieldEvidence = sanitizedFieldEvidence,
        reviewHints = data.reviewHints,
        recognition = recognitionPresentation,
        isProcessing = data.isProcessing || isRetryingLlm,
        isSaving = isSavingBag,
        existingBags = existingBags,
        onScanMorePhotos = callbacks.onScanMore,
        onExploreQrUrl = { url, callback -> brewViewModel.exploreApprovedQrLink(url, callback) },
        onRetryLlmEnrichment = {
            isRetryingLlm = data.sessionId?.let(brewViewModel::retryBagPhotoLlm) == true
        },
        onEnableAi = aiConsentFlow.request,
        onInstallLabelRecognition = {
            if (!MindlayerInstallLink.open(context)) {
                Toast.makeText(context, R.string.msg_could_not_open_app_store, Toast.LENGTH_LONG).show()
            }
        },
        onSetupAi = {
            setupReturnPending = true
            brewViewModel.openMindlayerModelSetup()
        },
        onDisableLabelRecognition = brewViewModel::disableLabelRecognition,
        onDismiss = callbacks.onExit,
        initialFormOverride = durableDraft?.toBagFormSnapshot(),
        onUserFieldChange = { fieldName, value ->
            BagDraftField.fromWireName(fieldName)?.let { onUserFieldChange(it, value) }
        },
        onFieldFocusChange = { fieldName, focused ->
            BagDraftField.fromWireName(fieldName)?.let { onFieldFocusChange(it, focused) }
        },
        pendingSuggestions = durableDraft?.fields.orEmpty().mapNotNull { (fieldName, value) ->
            value.pendingSuggestion?.value?.let { suggestion -> fieldName to suggestion }
        }.toMap(),
        onAcceptSuggestion = { fieldName ->
            BagDraftField.fromWireName(fieldName)?.let(onAcceptDraftSuggestion)
        },
        preserveDraftOnDismiss = durableDraft != null,
        onDiscardDraft = callbacks.onDiscard.takeIf { durableDraft != null },
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
                            callbacks.onSaved()
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

internal fun BagScanDraft.toBagFormSnapshot(): BagFormSnapshot {
    fun value(field: BagDraftField): String = this.field(field).value.orEmpty()
    fun dateValue(field: BagDraftField): Long? = value(field).toLongOrNull()
        ?: value(field).takeIf(String::isNotBlank)?.let(com.adsamcik.starlitcoffee.util.DateParser::parse)
    return BagFormSnapshot(
        name = value(BagDraftField.NAME),
        roaster = value(BagDraftField.ROASTER),
        originCountry = value(BagDraftField.ORIGIN),
        originRegion = value(BagDraftField.REGION),
        farm = value(BagDraftField.FARM),
        altitude = value(BagDraftField.ALTITUDE),
        roastLevel = value(BagDraftField.ROAST_LEVEL),
        variety = value(BagDraftField.VARIETY),
        processType = value(BagDraftField.PROCESS_TYPE),
        tastingNotes = value(BagDraftField.TASTING_NOTES),
        barcode = value(BagDraftField.BARCODE),
        weight = value(BagDraftField.WEIGHT),
        notes = value(BagDraftField.NOTES),
        isDecaf = value(BagDraftField.IS_DECAF).toBooleanStrictOrNull() ?: false,
        decafProcess = field(BagDraftField.DECAF_PROCESS).value,
        roastDateMillis = dateValue(BagDraftField.ROAST_DATE),
        expiryDateMillis = dateValue(BagDraftField.EXPIRY_DATE),
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
                callbacks.onDiscard()
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
                        is ScannedBagSaveResult.Saved -> callbacks.onSaved()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
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
