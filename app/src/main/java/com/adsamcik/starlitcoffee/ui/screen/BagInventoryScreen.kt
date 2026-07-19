package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.navigation.ScanDraftTransfer
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.data.work.isAddNewBagReview
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagAnalysisPreviewCard
import com.adsamcik.starlitcoffee.ui.component.BagCard
import com.adsamcik.starlitcoffee.ui.component.BagDetailSheet
import com.adsamcik.starlitcoffee.ui.component.ConsentOutcome
import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.ScannedBagSaveResult
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.messageRes
import com.adsamcik.starlitcoffee.ui.component.normalizedForCounts
import com.adsamcik.starlitcoffee.ui.component.persistScannedBag
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerConsentFlow
import com.adsamcik.starlitcoffee.ui.component.shouldApplyBagResultToDraft
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.util.ScanFieldSupport
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.HashMap
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Longest-side pixel target for the focused square thumbnail baked at save time.
// Comfortably covers the 68.dp list-card slot on high-density screens.
private const val THUMBNAIL_TARGET_PX = 512

private val bagPhotoRectSaver: Saver<BagPhotoRect?, ArrayList<Float>> = Saver(
    save = { focus ->
        focus?.let {
            arrayListOf(
                it.leftFraction,
                it.topFraction,
                it.rightFraction,
                it.bottomFraction,
            )
        }
    },
    restore = { values ->
        BagPhotoRect(
            leftFraction = values[0],
            topFraction = values[1],
            rightFraction = values[2],
            bottomFraction = values[3],
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagInventoryScreen(
    brewViewModel: BrewViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToBarcode: () -> Unit = {},
    onNavigateToBrewWithBag: (Long) -> Unit,
    onNavigateToRescan: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    capturedPhotosResult: String? = null,
    scanFieldsResult: HashMap<String, String>? = null,
    scannedBarcodeResult: String? = null,
    scanDraftTransferResult: ScanDraftTransfer? = null,
    onCapturedPhotosResultConsumed: () -> Unit = {},
    onScanFieldsResultConsumed: () -> Unit = {},
    onScannedBarcodeResultConsumed: () -> Unit = {},
    onScanDraftTransferResultConsumed: () -> Unit = {},
){
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val allBrewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val flavorTags by brewViewModel.flavorTags.collectAsStateWithLifecycle()
    val knownFieldValues by brewViewModel.knownFieldValues.collectAsStateWithLifecycle()
    val bagAnalysisPreview by brewViewModel.bagAnalysisPreview.collectAsStateWithLifecycle()
    val bagPhotoRetryResult by brewViewModel.bagPhotoRetryResult.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val rankedBags = remember(bags, allBrewLogs, flavorTags) {
        CoffeeBagInsights.rankBagsForBrew(
            bags = bags,
            brewLogs = allBrewLogs,
            flavorTags = flavorTags,
            targetDoseG = 20f,
        )
    }
    val topRecommendedId = rankedBags
        .firstOrNull { it.bag.status == "OPEN" }
        ?.bag?.id

    var decafFilter by remember { mutableStateOf(DecafFilter.ALL) }
    val decafCounts = remember(bags) {
        mapOf(
            DecafFilter.ALL to bags.size,
            DecafFilter.REGULAR to bags.count { !it.isDecaf },
            DecafFilter.DECAF to bags.count { it.isDecaf },
        )
    }
    val effectiveDecafFilter = normalizeInventoryDecafFilter(
        selected = decafFilter,
        regularCount = decafCounts[DecafFilter.REGULAR] ?: 0,
        decafCount = decafCounts[DecafFilter.DECAF] ?: 0,
    )
    val filteredRankedBags = remember(rankedBags, effectiveDecafFilter) {
        rankedBags.filter { effectiveDecafFilter.matches(it.bag.isDecaf) }
    }
    LaunchedEffect(effectiveDecafFilter) {
        if (decafFilter != effectiveDecafFilter) decafFilter = effectiveDecafFilter
    }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var selectedBagId by remember { mutableStateOf<Long?>(null) }
    val selectedBag = remember(selectedBagId, bags) {
        selectedInventoryBag(bags, selectedBagId)
    }
    LaunchedEffect(selectedBagId, selectedBag) {
        if (selectedBagId != null && selectedBag == null) selectedBagId = null
    }
    var editBagId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editBag = remember(editBagId, bags) {
        editBagId?.let { id -> bags.find { it.id == id } }
    }
    var ocrPrefill by remember { mutableStateOf<com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult?>(null) }
    var capturedPhotoUris by rememberSaveable { mutableStateOf<String?>(null) }
    var detectedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var detectedQrUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var offLookupName by rememberSaveable { mutableStateOf<String?>(null) }
    var offLookupRoaster by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldEvidence by remember { mutableStateOf<Map<String, BagFieldEvidence>>(emptyMap()) }
    var reviewHints by remember { mutableStateOf<List<BagPhotoReviewHint>>(emptyList()) }
    var llmStatus by remember { mutableStateOf(LlmEnrichmentStatus.NOT_RUN) }
    var thumbnailFocus by rememberSaveable(stateSaver = bagPhotoRectSaver) {
        mutableStateOf<BagPhotoRect?>(null)
    }
    var isProcessingScan by remember { mutableStateOf(false) }
    var isSavingBag by remember { mutableStateOf(false) }
    var isUpdatingBag by remember { mutableStateOf(false) }
    var isDeletingBag by remember { mutableStateOf(false) }
    var showRetakeDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var bagDraftSessionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var bagDraftGenerationId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val couldNotReadLabel = stringResource(R.string.msg_could_not_read_label)
    val couldNotSaveBag = stringResource(R.string.msg_could_not_save_bag)
    val bagSaved = stringResource(R.string.msg_bag_saved)
    val consentMessages = ConsentOutcome.entries.associateWith { outcome ->
        stringResource(outcome.messageRes())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Photo picker launcher for "From photo" option. Uses
    // PickMultipleVisualMedia(maxItems = 2) so the user can pick front + back
    // in one flow — the bag scan pipeline already merges OCR text across
    // multiple photos (see BrewViewModel.processNewBagPhotos +
    // emitBagPhotoResult.combinedOcrText), it just needs the consumer UI to
    // surface that capability. Index 0 of the returned list is treated as
    // the FRONT photo, index 1 as the BACK; the pipeline tags them that
    // way and the LLM prompt knows the OCR text may span both sides. Picker
    // access is temporary, so import the images before scheduling the durable
    // WorkManager pipeline.
    //
    // maxItems is the documented mechanism (Android 13+). Lower API levels
    // see the system picker's own default cap; on those devices the user
    // selects up to the system limit and the pipeline handles any list
    // length correctly.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2),
    ) { uris ->
        if (uris.isNotEmpty()) {
            fabExpanded = false
            coroutineScope.launch {
                val cachedUris = withContext(Dispatchers.IO) {
                    ScanPhotoStorage.copyPhotosToCache(
                        context = context.applicationContext,
                        sourceUris = uris.map { it.toString() },
                    )
                }
                if (cachedUris == null) {
                    Toast.makeText(
                        context,
                        couldNotReadLabel,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }

                val photosCsv = cachedUris.joinToString(",")
                showAddSheet = false
                isProcessingScan = false
                capturedPhotoUris = photosCsv
                ocrPrefill = null
                detectedBarcode = null
                detectedQrUrl = null
                offLookupName = null
                offLookupRoaster = null
                fieldEvidence = emptyMap()
                reviewHints = emptyList()
                llmStatus = LlmEnrichmentStatus.NOT_RUN
                thumbnailFocus = null
                brewViewModel.processNewBagPhotos(
                    photosCsv = photosCsv,
                    knownFieldValues = knownFieldValues,
                    deliverInBackground = true,
                    sessionId = bagDraftSessionId,
                )
            }
        }
    }

    // Handle captured photos result (from CameraCaptureScreen)
    val capturedPhotos = capturedPhotosResult

    // Observe bag photo processing results from ViewModel
    val bagPhotoResult by brewViewModel.bagPhotoResult.collectAsStateWithLifecycle()
    LaunchedEffect(bagPhotoResult, showAddSheet, bagDraftSessionId) {
        val sessionResult = bagPhotoResult ?: return@LaunchedEffect
        if (!isAddNewBagReview(sessionResult.reviewContext)) return@LaunchedEffect
        if (!shouldApplyBagResultToDraft(showAddSheet, bagDraftSessionId, sessionResult.sessionId)) {
            return@LaunchedEffect
        }
        bagDraftSessionId = sessionResult.sessionId
        bagDraftGenerationId = sessionResult.generationId.takeIf(String::isNotBlank)
        val result = sessionResult.result
        ocrPrefill = result.ocrPrefill
        capturedPhotoUris = result.capturedPhotoUris
        detectedBarcode = result.detectedBarcode
        detectedQrUrl = result.detectedQrUrl
        offLookupName = result.offLookupName
        offLookupRoaster = result.offLookupRoaster
        fieldEvidence = result.fieldEvidence
        reviewHints = result.reviewHints
        llmStatus = result.llmStatus
        thumbnailFocus = result.thumbnailFocus
        isProcessingScan = false
        showRetakeDialog = result.shouldSuggestRetake
        // Ensure the form is visible. In the gallery/barcode flow the sheet is
        // already open; this also force-opens it if a (foreground) result arrives
        // while the inventory is showing — e.g. process-death recovery delivers a
        // completed scan to bagPhotoResult on relaunch.
        showAddSheet = true
    }

    LaunchedEffect(bagPhotoRetryResult, bagDraftSessionId) {
        val sessionResult = bagPhotoRetryResult ?: return@LaunchedEffect
        if (!isAddNewBagReview(sessionResult.reviewContext)) return@LaunchedEffect
        if (sessionResult.sessionId != bagDraftSessionId) return@LaunchedEffect
        bagDraftGenerationId = sessionResult.generationId.takeIf(String::isNotBlank)
            ?: bagDraftGenerationId
        val result = sessionResult.result
        ocrPrefill = result.ocrPrefill ?: ocrPrefill
        capturedPhotoUris = result.capturedPhotoUris ?: capturedPhotoUris
        detectedBarcode = result.detectedBarcode ?: detectedBarcode
        detectedQrUrl = result.detectedQrUrl ?: detectedQrUrl
        offLookupName = result.offLookupName ?: offLookupName
        offLookupRoaster = result.offLookupRoaster ?: offLookupRoaster
        fieldEvidence = result.fieldEvidence.ifEmpty { fieldEvidence }
        reviewHints = result.reviewHints.ifEmpty { reviewHints }
        llmStatus = result.llmStatus
        thumbnailFocus = result.thumbnailFocus ?: thumbnailFocus
        isProcessingScan = false
    }

    // Notification deep-link recovery: the "bag analysis complete" notification
    // promotes the background result into this dedicated channel. Open the
    // prefilled review form regardless of navigation timing (a freshly shown
    // BagInventory still sees the replayed value), then consume it.
    val pendingScanReview by brewViewModel.pendingScanReview.collectAsStateWithLifecycle()
    LaunchedEffect(pendingScanReview, showAddSheet, bagDraftSessionId) {
        val sessionResult = pendingScanReview ?: return@LaunchedEffect
        if (!isAddNewBagReview(sessionResult.reviewContext)) return@LaunchedEffect
        if (!shouldApplyBagResultToDraft(showAddSheet, bagDraftSessionId, sessionResult.sessionId)) {
            return@LaunchedEffect
        }
        bagDraftSessionId = sessionResult.sessionId
        bagDraftGenerationId = sessionResult.generationId.takeIf(String::isNotBlank)
        val result = sessionResult.result
        ocrPrefill = result.ocrPrefill
        capturedPhotoUris = result.capturedPhotoUris
        detectedBarcode = result.detectedBarcode
        detectedQrUrl = result.detectedQrUrl
        offLookupName = result.offLookupName
        offLookupRoaster = result.offLookupRoaster
        fieldEvidence = result.fieldEvidence
        reviewHints = result.reviewHints
        llmStatus = result.llmStatus
        thumbnailFocus = result.thumbnailFocus
        isProcessingScan = false
        showRetakeDialog = false
        showAddSheet = true
        brewViewModel.acknowledgePendingScanReviewOpened(sessionResult.sessionId)
    }

    LaunchedEffect(capturedPhotos) {
        val photos = capturedPhotos ?: return@LaunchedEffect
        onCapturedPhotosResultConsumed()

        // User skipped camera → open empty form
        if (photos == "skipped") {
            isProcessingScan = false
            fieldEvidence = emptyMap()
            reviewHints = emptyList()
            llmStatus = LlmEnrichmentStatus.NOT_RUN
            showAddSheet = true
            return@LaunchedEffect
        }

        showAddSheet = false
        isProcessingScan = false
        capturedPhotoUris = photos
        ocrPrefill = null
        detectedBarcode = null
        detectedQrUrl = null
        offLookupName = null
        offLookupRoaster = null
        fieldEvidence = emptyMap()
        reviewHints = emptyList()
        llmStatus = LlmEnrichmentStatus.NOT_RUN
        thumbnailFocus = null

        brewViewModel.processNewBagPhotos(
            photosCsv = photos,
            knownFieldValues = knownFieldValues,
            deliverInBackground = true,
            sessionId = bagDraftSessionId,
        )
    }

    // Handle resolved scan fields from LiveScan "Review First"
    LaunchedEffect(scanFieldsResult) {
        val fields = scanFieldsResult ?: return@LaunchedEffect
        onScanFieldsResultConsumed()

        ocrPrefill = ScanFieldSupport.buildPrefill(fields)
        fieldEvidence = ScanFieldSupport.buildFieldEvidence(fields)
        capturedPhotoUris = null
        detectedBarcode = null
        detectedQrUrl = null
        offLookupName = null
        offLookupRoaster = null
        reviewHints = emptyList()
        llmStatus = LlmEnrichmentStatus.NOT_RUN
        isProcessingScan = false
        showRetakeDialog = false
        showAddSheet = true
    }

    // Handle barcode scanned from dedicated BarcodeScannerScreen
    LaunchedEffect(scannedBarcodeResult) {
        val barcode = scannedBarcodeResult ?: return@LaunchedEffect
        onScannedBarcodeResultConsumed()
        detectedBarcode = barcode
        ocrPrefill = null
        fieldEvidence = emptyMap()
        reviewHints = emptyList()
        llmStatus = LlmEnrichmentStatus.NOT_RUN
        capturedPhotoUris = null
        detectedQrUrl = null

        // Look up barcode in local bag database for prefill
        brewViewModel.findBagByBarcode(barcode) { existingBag ->
            if (existingBag != null) {
                offLookupName = existingBag.name
                offLookupRoaster = existingBag.roaster
            } else {
                offLookupName = null
                offLookupRoaster = null
            }
        }

        showAddSheet = true
    }

    LaunchedEffect(scanDraftTransferResult?.eventId) {
        val transfer = scanDraftTransferResult ?: return@LaunchedEffect
        onScanDraftTransferResultConsumed()
        bagDraftSessionId = transfer.scanSessionId
        bagDraftGenerationId = transfer.generationId
        ocrPrefill = ScanFieldSupport.buildPrefill(transfer.fields)
        fieldEvidence = ScanFieldSupport.buildFieldEvidence(transfer.fields)
        capturedPhotoUris = transfer.capturedPhotoUris
        detectedBarcode = transfer.detectedBarcode
        detectedQrUrl = transfer.detectedQrUrl
        offLookupName = null
        offLookupRoaster = null
        reviewHints = emptyList()
        llmStatus = LlmEnrichmentStatus.NOT_RUN
        thumbnailFocus = transfer.thumbnailFocus?.toBagPhotoRect()
        isProcessingScan = false
        showRetakeDialog = false
        showAddSheet = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_add_bag_gallery),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    bagDraftSessionId = UUID.randomUUID().toString()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("fab_from_photo"),
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.action_from_photo))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_add_bag_camera),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    bagDraftSessionId = UUID.randomUUID().toString()
                                    onNavigateToCamera()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("fab_scan_label"),
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.action_scan_label))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_add_bag_barcode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    bagDraftSessionId = UUID.randomUUID().toString()
                                    onNavigateToBarcode()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("fab_scan_barcode"),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.action_scan_barcode))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_add_bag_manual),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    bagDraftSessionId = UUID.randomUUID().toString()
                                    // Reset any preflight scan state so the sheet opens blank
                                    detectedBarcode = null
                                    detectedQrUrl = null
                                    ocrPrefill = null
                                    capturedPhotoUris = null
                                    offLookupName = null
                                    offLookupRoaster = null
                                    fieldEvidence = emptyMap()
                                    reviewHints = emptyList()
                                    llmStatus = LlmEnrichmentStatus.NOT_RUN
                                    isProcessingScan = false
                                    showAddSheet = true
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("fab_add_manual"),
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.action_add_bag_manual),
                                )
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag("add_bag_fab"),
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (fabExpanded) "Close menu" else "Add Bag",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenTopBar(title = stringResource(R.string.label_your_beans), onBack = onBack)

            if (bags.isEmpty() && bagAnalysisPreview == null) {
                EmptyStateBox(
                    icon = Icons.Filled.ShoppingBag,
                    message = stringResource(R.string.msg_no_beans_yet),
                    subtitle = stringResource(R.string.msg_add_coffee_hint),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 16.dp,
                    bottom = 88.dp,
                ),
            ) {
                bagAnalysisPreview?.let { preview ->
                    item(key = "bag_analysis_preview") {
                        BagAnalysisPreviewCard(
                            result = preview.result,
                            progress = preview.progress,
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val openCount = bags.count { it.status == "OPEN" }
                        val subtitle = when {
                            openCount > 1 -> "$openCount bags open — freshest first"
                            openCount == 1 -> "1 bag open"
                            else -> "Add beans to start tracking freshness"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (decafCounts[DecafFilter.DECAF]!! > 0 &&
                    decafCounts[DecafFilter.REGULAR]!! > 0) {
                    item {
                        com.adsamcik.starlitcoffee.ui.component.DecafFilterChipRow(
                            selected = effectiveDecafFilter,
                            counts = decafCounts,
                            onSelected = { decafFilter = it },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
                items(
                    items = filteredRankedBags.map { it.bag },
                    key = { it.id },
                ) { bag ->
                    val brewsRemaining = if (bag.weightG != null && bag.weightG > 0f) {
                        val avgDose = allBrewLogs
                            .filter { it.coffeeBagId == bag.id }
                            .takeIf { it.isNotEmpty() }
                            ?.map { it.doseG }
                            ?.average()
                            ?.toFloat()
                            ?: 20f
                        (bag.weightG / avgDose).toInt()
                    } else null

                    BagCard(
                        bag = bag,
                        dateFormat = dateFormat,
                        onTap = { selectedBagId = bag.id },
                        modifier = Modifier.animateItem(),
                        isRecommended = bag.id == topRecommendedId,
                        brewsRemaining = brewsRemaining,
                    )
                }
            }
        }
        }
    }

    // First-run AI authorization. When the bag-photo LLM enrichment comes back
    // UNAVAILABLE — typically because Mindlayer is installed but the user has
    // never granted this app consent — the Add-bag screen offers an explicit
    // "Enable AI" action wired here. On grant we rebind Mindlayer and re-run the
    // enrichment so the user doesn't have to retry by hand. requestConsent
    // also resolves the already-approved / not-installed cases gracefully.
    val aiConsentFlow = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> coroutineScope.launch {
                (context.applicationContext as? StarlitCoffeeApp)?.reconnectMindlayer()
                isProcessingScan = brewViewModel.retryBagPhotoLlm(bagDraftSessionId)
            }
            else -> Toast.makeText(context, consentMessages.getValue(outcome), Toast.LENGTH_LONG).show()
        }
    }

    // Add bag sheet
    val brewNowLabel = stringResource(R.string.action_brew_now)
    if (showAddSheet) {
        AddBagSheet(
            initialBarcode = detectedBarcode,
            ocrPrefill = ocrPrefill,
            initialName = offLookupName,
            initialRoaster = offLookupRoaster,
            traceabilityUrl = detectedQrUrl,
            capturedPhotoUris = capturedPhotoUris,
            fieldEvidence = fieldEvidence,
            reviewHints = reviewHints,
            llmStatus = llmStatus,
            isProcessing = isProcessingScan,
            isSaving = isSavingBag,
            existingBags = bags,
            onScanBarcode = {
                val discardedPhotoUris = capturedPhotoUris
                showAddSheet = false
                brewViewModel.completeBagPhotoReview(bagDraftSessionId)
                brewViewModel.cancelBagPhotoProcessing(bagDraftSessionId)
                coroutineScope.launch(Dispatchers.IO) {
                    ScanPhotoStorage.deleteStagedCaptures(
                        context.applicationContext,
                        discardedPhotoUris,
                    )
                }
                onNavigateToBarcode()
            },
            onExploreQrUrl = { url, callback ->
                brewViewModel.exploreApprovedQrLink(url, callback)
            },
            onRetryLlmEnrichment = {
                isProcessingScan = brewViewModel.retryBagPhotoLlm(bagDraftSessionId)
            },
            onEnableAi = aiConsentFlow.request,
            onDismiss = {
                val discardedPhotoUris = capturedPhotoUris
                val discardedSessionId = bagDraftSessionId
                showAddSheet = false
                isProcessingScan = false
                showRetakeDialog = false
                ocrPrefill = null
                capturedPhotoUris = null
                offLookupName = null
                offLookupRoaster = null
                detectedBarcode = null
                detectedQrUrl = null
                fieldEvidence = emptyMap()
                reviewHints = emptyList()
                llmStatus = LlmEnrichmentStatus.NOT_RUN
                bagDraftGenerationId = null
                bagDraftSessionId = UUID.randomUUID().toString()
                brewViewModel.completeBagPhotoReview(discardedSessionId)
                coroutineScope.launch(Dispatchers.IO) {
                    ScanPhotoStorage.deleteStagedCaptures(
                        context.applicationContext,
                        discardedPhotoUris,
                    )
                }
            },
            onSave = save@{
                name,
                roaster,
                origin,
                region,
                farm,
                altitude,
                roastLevel,
                barcode,
                weightG,
                notes,
                variety,
                processType,
                tastingNotes,
                isDecaf,
                decafProcess,
                roastDateMillis,
                expiryDateMillis,
                ->
                val saveSessionId = bagDraftSessionId
                if (isSavingBag || !brewViewModel.beginScannedBagSave(saveSessionId)) return@save
                isSavingBag = true
                val rawPhotoUris = capturedPhotoUris
                val qrUrl = detectedQrUrl
                val scanFocus = thumbnailFocus

                // Copy photos to permanent storage on background thread
                coroutineScope.launch {
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
                            Log.e("BagInventoryScreen", "Failed to save coffee bag", saveResult.error)
                            Toast.makeText(context, couldNotSaveBag, Toast.LENGTH_LONG).show()
                        }
                        is ScannedBagSaveResult.Saved -> {
                            brewViewModel.completeBagPhotoReview(saveSessionId)
                            showAddSheet = false
                            isProcessingScan = false
                            showRetakeDialog = false
                            ocrPrefill = null
                            capturedPhotoUris = null
                            detectedBarcode = null
                            detectedQrUrl = null
                            fieldEvidence = emptyMap()
                            reviewHints = emptyList()
                            llmStatus = LlmEnrichmentStatus.NOT_RUN
                            thumbnailFocus = null
                            bagDraftGenerationId = null
                            bagDraftSessionId = UUID.randomUUID().toString()
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = bagSaved,
                                    actionLabel = brewNowLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onNavigateToBrewWithBag(saveResult.bagId)
                                }
                            }
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

    if (showRetakeDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeDialog = false },
            title = { Text(stringResource(R.string.msg_could_not_read_label)) },
            text = {
                Text(
                    reviewHints.joinToString("\n") { hint -> "• ${hint.message}" }
                        .ifBlank { "No text was detected on this bag. Try a closer shot or add details manually." },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val discardedPhotoUris = capturedPhotoUris
                        showRetakeDialog = false
                        showAddSheet = false
                        isProcessingScan = false
                        fieldEvidence = emptyMap()
                        reviewHints = emptyList()
                        llmStatus = LlmEnrichmentStatus.NOT_RUN
                        ocrPrefill = null
                        capturedPhotoUris = null
                        detectedBarcode = null
                        detectedQrUrl = null
                        offLookupName = null
                        offLookupRoaster = null
                        brewViewModel.completeBagPhotoReview(bagDraftSessionId)
                        brewViewModel.cancelBagPhotoProcessing(bagDraftSessionId)
                        coroutineScope.launch(Dispatchers.IO) {
                            ScanPhotoStorage.deleteStagedCaptures(
                                context.applicationContext,
                                discardedPhotoUris,
                            )
                        }
                        onNavigateToCamera()
                    },
                ) {
                    Text(stringResource(R.string.action_retake_photo))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetakeDialog = false }) {
                    Text(stringResource(R.string.action_add_details_manually))
                }
            },
        )
    }

    // Bag detail bottom sheet
    selectedBag?.let { bag ->
        val bagBrewLogs = allBrewLogs.filter { it.coffeeBagId == bag.id }
        val bagFlavorTags by brewViewModel.getFlavorTagsForBag(bag.id).collectAsStateWithLifecycle(
            initialValue = emptyList(),
        )
        BagDetailSheet(
            bag = bag,
            brewLogs = bagBrewLogs,
            flavorTags = bagFlavorTags,
            dateFormat = dateFormat,
            onDismiss = { selectedBagId = null },
            onStatusChange = { status ->
                brewViewModel.updateBagStatus(bag.id, status.name)
            },
            isDeleting = isDeletingBag,
            onDelete = {
                if (!isDeletingBag) {
                    isDeletingBag = true
                    coroutineScope.launch {
                        try {
                            check(brewViewModel.deleteCoffeeBagAndWait(bag)) {
                                "Coffee bag repository is unavailable"
                            }
                            selectedBagId = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                            Log.e("BagInventoryScreen", "Failed to delete coffee bag", error)
                            Toast.makeText(
                                context,
                                R.string.msg_could_not_delete,
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            isDeletingBag = false
                        }
                    }
                }
            },
            onEdit = {
                selectedBagId = null
                editBagId = bag.id
            },
            onRescan = {
                selectedBagId = null
                onNavigateToRescan(bag.id)
            },
            onWeightAdjust = { bagId, weight ->
                brewViewModel.adjustBagWeight(bagId, weight)
            },
            onSelectForBrewing = {
                selectedBagId = null
                onNavigateToBrewWithBag(bag.id)
            },
        )
    }

    // Edit bag sheet
    editBag?.let { bag ->
        AddBagSheet(
            bagToEdit = bag,
            existingBags = bags,
            isSaving = isUpdatingBag,
            onDismiss = { editBagId = null },
            onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            onEdit = { updatedBag ->
                if (isUpdatingBag) return@AddBagSheet
                isUpdatingBag = true
                coroutineScope.launch {
                    try {
                        check(brewViewModel.updateCoffeeBagAndWait(updatedBag)) {
                            "Coffee bag repository is unavailable"
                        }
                        editBagId = null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                        Log.e("BagInventoryScreen", "Failed to update coffee bag", error)
                        Toast.makeText(context, R.string.msg_could_not_save_changes, Toast.LENGTH_LONG).show()
                    } finally {
                        isUpdatingBag = false
                    }
                }
            },
        )
    }
}

internal fun selectedInventoryBag(
    bags: List<CoffeeBagEntity>,
    selectedBagId: Long?,
): CoffeeBagEntity? = selectedBagId?.let { id -> bags.find { it.id == id } }

internal fun normalizeInventoryDecafFilter(
    selected: DecafFilter,
    regularCount: Int,
    decafCount: Int,
): DecafFilter = selected.normalizedForCounts(regularCount, decafCount)
