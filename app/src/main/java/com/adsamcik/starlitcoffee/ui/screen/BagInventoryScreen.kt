package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
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
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagCard
import com.adsamcik.starlitcoffee.ui.component.BagDetailSheet
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.ScanFieldSupport
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.HashMap
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private const val TAG = "BagInventoryScreen"

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
){
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val allBrewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val flavorTags by brewViewModel.flavorTags.collectAsStateWithLifecycle()
    val knownFieldValues by brewViewModel.knownFieldValues.collectAsStateWithLifecycle()
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

    var decafFilter by remember { mutableStateOf(com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL) }
    val decafCounts = remember(bags) {
        mapOf(
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL to bags.size,
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR to bags.count { !it.isDecaf },
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF to bags.count { it.isDecaf },
        )
    }
    val filteredRankedBags = remember(rankedBags, decafFilter) {
        rankedBags.filter { decafFilter.matches(it.bag.isDecaf) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var editBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var ocrPrefill by remember { mutableStateOf<com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult?>(null) }
    var capturedPhotoUris by remember { mutableStateOf<String?>(null) }
    var detectedBarcode by remember { mutableStateOf<String?>(null) }
    var detectedQrUrl by remember { mutableStateOf<String?>(null) }
    var offLookupName by remember { mutableStateOf<String?>(null) }
    var offLookupRoaster by remember { mutableStateOf<String?>(null) }
    var fieldEvidence by remember { mutableStateOf<Map<String, BagFieldEvidence>>(emptyMap()) }
    var reviewHints by remember { mutableStateOf<List<BagPhotoReviewHint>>(emptyList()) }
    var isProcessingScan by remember { mutableStateOf(false) }
    var showRetakeDialog by remember { mutableStateOf(false) }
    var lastProcessedPhotos by remember { mutableStateOf<String?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Photo picker launcher for "From photo" option
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            fabExpanded = false
            val photosCsv = uri.toString()
            showAddSheet = true
            isProcessingScan = true
            capturedPhotoUris = photosCsv
            ocrPrefill = null
            detectedBarcode = null
            detectedQrUrl = null
            offLookupName = null
            offLookupRoaster = null
            fieldEvidence = emptyMap()
            reviewHints = emptyList()
            brewViewModel.processNewBagPhotos(photosCsv, knownFieldValues)
        }
    }

    // Handle captured photos result (from CameraCaptureScreen)
    val capturedPhotos = capturedPhotosResult

    // Observe bag photo processing results from ViewModel
    val bagPhotoResult by brewViewModel.bagPhotoResult.collectAsStateWithLifecycle()
    LaunchedEffect(bagPhotoResult) {
        val result = bagPhotoResult ?: return@LaunchedEffect
        ocrPrefill = result.ocrPrefill
        capturedPhotoUris = result.capturedPhotoUris
        detectedBarcode = result.detectedBarcode
        detectedQrUrl = result.detectedQrUrl
        offLookupName = result.offLookupName
        offLookupRoaster = result.offLookupRoaster
        fieldEvidence = result.fieldEvidence
        reviewHints = result.reviewHints
        isProcessingScan = false
        showRetakeDialog = result.shouldSuggestRetake
        brewViewModel.clearBagPhotoResult()
        // Sheet is already open from LaunchedEffect(capturedPhotos) — don't force-reopen
        // if the user dismissed it during processing
    }

    LaunchedEffect(capturedPhotos) {
        val photos = capturedPhotos ?: return@LaunchedEffect
        if (photos == lastProcessedPhotos) return@LaunchedEffect
        lastProcessedPhotos = photos

        // User skipped camera → open empty form
        if (photos == "skipped") {
            isProcessingScan = false
            fieldEvidence = emptyMap()
            reviewHints = emptyList()
            showAddSheet = true
            return@LaunchedEffect
        }

        showAddSheet = true
        isProcessingScan = true
        capturedPhotoUris = photos
        ocrPrefill = null
        detectedBarcode = null
        detectedQrUrl = null
        offLookupName = null
        offLookupRoaster = null
        fieldEvidence = emptyMap()
        reviewHints = emptyList()

        brewViewModel.processNewBagPhotos(photos, knownFieldValues)
    }

    // Handle resolved scan fields from LiveScan "Review First"
    var lastProcessedScanFields by remember { mutableStateOf<HashMap<String, String>?>(null) }
    LaunchedEffect(scanFieldsResult) {
        val fields = scanFieldsResult ?: return@LaunchedEffect
        if (fields == lastProcessedScanFields) return@LaunchedEffect
        lastProcessedScanFields = fields

        ocrPrefill = ScanFieldSupport.buildPrefill(fields)
        fieldEvidence = ScanFieldSupport.buildFieldEvidence(fields)
        capturedPhotoUris = null
        detectedBarcode = null
        detectedQrUrl = null
        offLookupName = null
        offLookupRoaster = null
        reviewHints = emptyList()
        isProcessingScan = false
        showRetakeDialog = false
        showAddSheet = true
    }

    // Handle barcode scanned from dedicated BarcodeScannerScreen
    LaunchedEffect(scannedBarcodeResult) {
        val barcode = scannedBarcodeResult ?: return@LaunchedEffect
        detectedBarcode = barcode
        ocrPrefill = null
        fieldEvidence = emptyMap()
        reviewHints = emptyList()
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
                                    // Reset any preflight scan state so the sheet opens blank
                                    detectedBarcode = null
                                    detectedQrUrl = null
                                    ocrPrefill = null
                                    capturedPhotoUris = null
                                    offLookupName = null
                                    offLookupRoaster = null
                                    fieldEvidence = emptyMap()
                                    reviewHints = emptyList()
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

            if (bags.isEmpty()) {
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
                if (decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF]!! > 0 &&
                    decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR]!! > 0) {
                    item {
                        com.adsamcik.starlitcoffee.ui.component.DecafFilterChipRow(
                            selected = decafFilter,
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
                        onTap = { selectedBag = bag },
                        isRecommended = bag.id == topRecommendedId,
                        brewsRemaining = brewsRemaining,
                    )
                }
            }
        }
        }
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Add bag bottom sheet
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
            isProcessing = isProcessingScan,
            existingBags = bags,
            onScanBarcode = {
                showAddSheet = false
                onNavigateToBarcode()
            },
            onExploreQrUrl = { url, callback ->
                brewViewModel.exploreApprovedQrLink(url, callback)
            },
            onDismiss = {
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
            },
            onSave = {
                name,
                roaster,
                origin,
                region,
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
                val rawPhotoUris = capturedPhotoUris
                val qrUrl = detectedQrUrl
                showAddSheet = false
                isProcessingScan = false
                showRetakeDialog = false
                ocrPrefill = null
                capturedPhotoUris = null
                detectedBarcode = null
                detectedQrUrl = null
                fieldEvidence = emptyMap()
                reviewHints = emptyList()

                // Copy photos to permanent storage on background thread
                coroutineScope.launch {
                    val permanentUris = rawPhotoUris?.let { uris ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            copyPhotosToPermanentStorage(context, uris)
                        }
                    }
                    brewViewModel.addCoffeeBag(
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
                        photoUri = permanentUris?.split(",")?.firstOrNull(),
                        photoUris = permanentUris,
                        traceabilityUrl = qrUrl,
                        onBagAdded = { newBagId ->
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Bag saved — brew with it now?",
                                    actionLabel = brewNowLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onNavigateToBrewWithBag(newBagId)
                                }
                            }
                        },
                    )
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
                        showRetakeDialog = false
                        showAddSheet = false
                        isProcessingScan = false
                        fieldEvidence = emptyMap()
                        reviewHints = emptyList()
                        ocrPrefill = null
                        capturedPhotoUris = null
                        detectedBarcode = null
                        detectedQrUrl = null
                        offLookupName = null
                        offLookupRoaster = null
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
            onDismiss = { selectedBag = null },
            onStatusChange = { status ->
                brewViewModel.updateBagStatus(bag.id, status.name)
                selectedBag = bag.copy(status = status.name)
            },
            onDelete = {
                brewViewModel.deleteCoffeeBag(bag)
                selectedBag = null
            },
            onEdit = {
                selectedBag = null
                editBag = bag
            },
            onRescan = {
                selectedBag = null
                onNavigateToRescan(bag.id)
            },
            onWeightAdjust = { bagId, weight ->
                brewViewModel.adjustBagWeight(bagId, weight)
            },
            onSelectForBrewing = {
                selectedBag = null
                onNavigateToBrewWithBag(bag.id)
            },
        )
    }

    // Edit bag sheet
    editBag?.let { bag ->
        AddBagSheet(
            bagToEdit = bag,
            existingBags = bags,
            onDismiss = { editBag = null },
            onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            onEdit = { updatedBag ->
                brewViewModel.updateCoffeeBag(updatedBag)
                editBag = null
            },
        )
    }
}

/**
 * Copies photos from cache to permanent storage (filesDir/bag_photos/).
 * Returns comma-separated permanent URI strings.
 */
private fun copyPhotosToPermanentStorage(
    context: android.content.Context,
    cacheUris: String,
): String {
    val bagPhotosDir = java.io.File(context.filesDir, "bag_photos").apply { mkdirs() }
    return cacheUris.split(",").mapNotNull { uriStr ->
        try {
            val sourceFile = java.io.File(android.net.Uri.parse(uriStr).path ?: return@mapNotNull null)
            if (!sourceFile.exists()) return@mapNotNull null
            val destFile = java.io.File(bagPhotosDir, "bag_${System.currentTimeMillis()}_${sourceFile.name}")
            sourceFile.copyTo(destFile, overwrite = true)
            destFile.toUri().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy photo to permanent storage", e)
            null
        }
    }.joinToString(",")
}
