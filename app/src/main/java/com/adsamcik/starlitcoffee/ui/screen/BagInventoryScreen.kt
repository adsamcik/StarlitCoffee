package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
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
){
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val allBrewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val knownFieldValues by brewViewModel.knownFieldValues.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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
                message = "No coffee bags yet",
                subtitle = "Track your beans — add roast details and tasting notes",
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 16.dp,
                    bottom = 88.dp,
                ),
            ) {
                item {
                    Text(
                        text = "Coffee Bags",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(bags, key = { it.id }) { bag ->
                    BagCard(
                        bag = bag,
                        dateFormat = dateFormat,
                        onTap = { selectedBag = bag },
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
            title = { Text("Retake recommended") },
            text = {
                Text(
                    reviewHints.joinToString("\n") { hint -> "• ${hint.message}" }
                        .ifBlank { "We could not confidently read this bag yet." },
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
                    Text("Retake photos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetakeDialog = false }) {
                    Text("Continue manually")
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
            onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
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

