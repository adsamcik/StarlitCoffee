package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.ui.component.AddBagSheet
import com.adsamcik.starlitcoffee.ui.component.BagCard
import com.adsamcik.starlitcoffee.ui.component.BagDetailSheet
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private const val TAG = "BagInventoryScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagInventoryScreen(
    brewViewModel: BrewViewModel,
    onNavigateToCamera: () -> Unit,
    capturedPhotosResult: String? = null,
    scanFieldsResult: String? = null,
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
    val context = LocalContext.current

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
    var lastProcessedScanFields by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scanFieldsResult) {
        val fields = scanFieldsResult ?: return@LaunchedEffect
        if (fields == lastProcessedScanFields) return@LaunchedEffect
        lastProcessedScanFields = fields

        // Parse pipe-delimited "key=value" pairs into BagFieldEvidence
        val parsed = fields.split("|").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

        fieldEvidence = parsed.map { (key, value) ->
            key to BagFieldEvidence(
                fieldName = key,
                value = value,
                sourceType = com.adsamcik.starlitcoffee.util.BagFieldSourceType.OCR,
                confidence = com.adsamcik.starlitcoffee.util.BagFieldConfidence.MEDIUM,
            )
        }.toMap()
        reviewHints = emptyList()
        isProcessingScan = false
        showAddSheet = true
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("add_bag_fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Bag")
            }
        },
    ) { innerPadding ->
        if (bags.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Filled.ShoppingBag,
                message = "No beans yet",
                subtitle = "Add your coffee to track freshness and get brew suggestions",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                        Text(
                            text = "Your Beans",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
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
                items(
                    items = rankedBags.map { it.bag },
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

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Add bag bottom sheet
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
                        roastDate = roastDateMillis,
                        expiryDate = expiryDateMillis,
                        photoUri = permanentUris?.split(",")?.firstOrNull(),
                        photoUris = permanentUris,
                        traceabilityUrl = qrUrl,
                    )
                }
            },
        )
    }

    if (showRetakeDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeDialog = false },
            title = { Text("Could not read label") },
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
                    Text("Retake photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetakeDialog = false }) {
                    Text("Add details manually")
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
            onWeightAdjust = { bagId, weight ->
                brewViewModel.adjustBagWeight(bagId, weight)
            },
        )
    }

    // Edit bag sheet
    editBag?.let { bag ->
        AddBagSheet(
            bagToEdit = bag,
            existingBags = bags,
            onDismiss = { editBag = null },
            onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
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

