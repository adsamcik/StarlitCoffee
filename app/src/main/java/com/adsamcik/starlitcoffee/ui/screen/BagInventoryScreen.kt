package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety
import com.adsamcik.starlitcoffee.navigation.CameraCapture
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.FieldChipPicker
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.ui.component.SuggestingTextField
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private const val TAG = "BagInventoryScreen"
private const val LOW_COFFEE_THRESHOLD_G = 30f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagInventoryScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val allBrewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var editBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var ocrPrefill by remember { mutableStateOf<com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult?>(null) }
    var capturedPhotoUris by remember { mutableStateOf<String?>(null) }
    var detectedBarcode by remember { mutableStateOf<String?>(null) }
    var detectedQrUrl by remember { mutableStateOf<String?>(null) }
    var offLookupName by remember { mutableStateOf<String?>(null) }
    var offLookupRoaster by remember { mutableStateOf<String?>(null) }
    var lastProcessedPhotos by remember { mutableStateOf<String?>(null) }

    // Known roasters/names from saved bags for OCR scoring
    val knownRoasters = remember(bags) {
        bags.mapNotNull { it.roaster }.distinct()
    }
    val knownNames = remember(bags) {
        bags.map { it.name }.distinct()
    }

    // Handle captured photos result (from CameraCaptureScreen)
    val capturedPhotos by (currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("captured_photos", null)
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

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
        brewViewModel.clearBagPhotoResult()
        showAddSheet = true
    }

    LaunchedEffect(capturedPhotos) {
        val photos = capturedPhotos ?: return@LaunchedEffect
        if (photos == lastProcessedPhotos) return@LaunchedEffect
        lastProcessedPhotos = photos

        // User skipped camera → open empty form
        if (photos == "skipped") {
            showAddSheet = true
            return@LaunchedEffect
        }

        brewViewModel.processNewBagPhotos(photos, knownRoasters, knownNames)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate to camera guide
                    navController.navigate(CameraCapture)
                },
                shape = MaterialTheme.shapes.large,
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

    val context = androidx.compose.ui.platform.LocalContext.current
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
            existingBags = bags,
            onDismiss = {
                showAddSheet = false
                ocrPrefill = null
                capturedPhotoUris = null
                offLookupName = null
                offLookupRoaster = null
                detectedBarcode = null
                detectedQrUrl = null
            },
            onSave = { name, roaster, origin, region, roastLevel, barcode, weightG, notes, variety, processType, tastingNotes, roastDateMillis, expiryDateMillis ->
                val rawPhotoUris = capturedPhotoUris
                val qrUrl = detectedQrUrl
                showAddSheet = false
                ocrPrefill = null
                capturedPhotoUris = null
                detectedBarcode = null
                detectedQrUrl = null

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

    // Bag detail bottom sheet
    selectedBag?.let { bag ->
        val bagBrewLogs = allBrewLogs.filter { it.coffeeBagId == bag.id }
        BagDetailSheet(
            bag = bag,
            brewLogs = bagBrewLogs,
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

@Composable
private fun BagCard(
    bag: CoffeeBagEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
) {
    val statusColor = when (bag.status) {
        "SEALED" -> MaterialTheme.colorScheme.outline
        "OPEN" -> MaterialTheme.colorScheme.primary
        "FROZEN" -> MaterialTheme.colorScheme.tertiary
        "FINISHED" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }

    ElevatedCard(
        onClick = onTap,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Photo thumbnail
            bag.photoUri?.let { uri ->
                val bitmap = remember(uri) {
                    try {
                        val file = java.io.File(android.net.Uri.parse(uri).path ?: return@remember null)
                        val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            ?: return@remember null
                        ImagePreprocessor.applyExifRotation(raw, file.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load bag thumbnail", e)
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Bag photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bag.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (bag.roaster != null) {
                    Text(
                        text = bag.roaster,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bag.weightG?.let { w ->
                    val initial = bag.initialWeightG ?: w
                    val progress = if (initial > 0f) (w / initial).coerceIn(0f, 1f) else 0f
                    val isLow = w in 0.01f..LOW_COFFEE_THRESHOLD_G
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                isLow -> MaterialTheme.colorScheme.error
                                progress < 0.3f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${"%.0f".format(w)}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLow) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isLow) {
                        Text(
                            text = "⚠ Low coffee",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (bag.roastDate != null) {
                    Text(
                        text = "Roasted: ${dateFormat.format(Date(bag.roastDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val freshness = DateParser.assessFreshness(bag.roastDate)
                    Text(
                        "${freshness.emoji} ${freshness.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (freshness) {
                            DateParser.Freshness.PEAK -> MaterialTheme.colorScheme.primary
                            DateParser.Freshness.STALE, DateParser.Freshness.OLD -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (bag.isDecaf) {
                    Text(
                        text = "☘ Decaf",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        bag.status.lowercase()
                            .replaceFirstChar { it.uppercase() },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = statusColor,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBagSheet(
    initialBarcode: String? = null,
    ocrPrefill: com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult? = null,
    initialName: String? = null,
    initialRoaster: String? = null,
    traceabilityUrl: String? = null,
    capturedPhotoUris: String? = null,
    existingBags: List<CoffeeBagEntity> = emptyList(),
    bagToEdit: CoffeeBagEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        roaster: String?,
        origin: String?,
        region: String?,
        roastLevel: String?,
        barcode: String?,
        weightG: Float?,
        notes: String?,
        variety: String?,
        processType: String?,
        tastingNotes: String?,
        roastDate: Long?,
        expiryDate: Long?,
    ) -> Unit,
    onEdit: ((CoffeeBagEntity) -> Unit)? = null,
) {
    // History suggestions from existing bags (already sorted newest-first by DAO)
    val recentNames = remember(existingBags) {
        existingBags.map { it.name }.distinct().take(10)
    }
    val recentRoasters = remember(existingBags) {
        existingBags.mapNotNull { it.roaster }.distinct().take(10)
    }
    val recentOrigins = remember(existingBags) {
        existingBags.mapNotNull { it.origin }.distinct().take(10)
    }
    val recentRegions = remember(existingBags) {
        existingBags.mapNotNull { it.region }.distinct().take(10)
    }
    val recentVarieties = remember(existingBags) {
        existingBags.mapNotNull { it.variety }
            .flatMap { it.split(",").map { part -> part.trim() } }
            .filter { it.isNotBlank() }.distinct().take(10)
    }
    val recentProcesses = remember(existingBags) {
        existingBags.mapNotNull { it.processType }.distinct().take(10)
    }
    val recentRoastLevels = remember(existingBags) {
        existingBags.mapNotNull { it.roastLevel }
            .flatMap { it.split(",").map { part -> part.trim() } }
            .filter { it.isNotBlank() }.distinct().take(10)
    }

    val isEditMode = bagToEdit != null
    var name by remember(ocrPrefill, initialName, bagToEdit) {
        mutableStateOf(bagToEdit?.name ?: initialName ?: ocrPrefill?.name ?: "")
    }
    var roaster by remember(ocrPrefill, initialRoaster, bagToEdit) {
        mutableStateOf(bagToEdit?.roaster ?: ocrPrefill?.roaster ?: initialRoaster ?: "")
    }
    var originCountry by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.origin ?: ocrPrefill?.origin ?: "")
    }
    var originRegion by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.region ?: ocrPrefill?.region ?: "")
    }
    var roastLevel by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.roastLevel ?: ocrPrefill?.roastLevel ?: "")
    }
    var variety by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.variety ?: ocrPrefill?.variety ?: "")
    }
    var processType by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.processType ?: ocrPrefill?.processType ?: "")
    }
    var tastingNotes by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.tastingNotes ?: ocrPrefill?.tastingNotes ?: "")
    }
    var barcode by remember(initialBarcode, bagToEdit) {
        mutableStateOf(bagToEdit?.barcode ?: initialBarcode.orEmpty())
    }
    var weight by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.weightG?.let { "%.0f".format(it) } ?: ocrPrefill?.weight ?: "")
    }
    var notes by remember(bagToEdit) { mutableStateOf(bagToEdit?.notes ?: "") }
    var isDecaf by remember(bagToEdit) { mutableStateOf(bagToEdit?.isDecaf ?: false) }
    var roastDateMillis by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.roastDate ?: ocrPrefill?.roastDate?.let { DateParser.parse(it) })
    }
    var expiryDateMillis by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.expiryDate ?: ocrPrefill?.expiryDate?.let { DateParser.parse(it) })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isEditMode) "Edit Coffee Bag" else "Add Coffee Bag",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp, top = 16.dp),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (initialBarcode != null) {
                    item {
                        OutlinedTextField(
                            value = initialBarcode,
                            onValueChange = {},
                            label = { Text("Barcode") },
                            shape = MaterialTheme.shapes.small,
                            readOnly = true,
                            enabled = false,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                }
                item {
                    SuggestingTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name *",
                        suggestions = recentNames,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    SuggestingTextField(
                        value = roaster,
                        onValueChange = { roaster = it },
                        label = "Roaster",
                        suggestions = recentRoasters,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    FieldChipPicker(
                        label = "Origin",
                        knownValues = CoffeeOrigin.known,
                        selectedValue = originCountry,
                        onValueChange = { originCountry = it },
                        displayName = { it.displayName },
                        recentValues = recentOrigins,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    // Filter regions by selected origin country, sorted alphabetically
                    val filteredRegions = if (originCountry.isNotBlank()) {
                        CoffeeRegion.forCountry(originCountry).ifEmpty { CoffeeRegion.known }
                    } else {
                        CoffeeRegion.known
                    }
                    FieldChipPicker(
                        label = if (originCountry.isNotBlank()) "Region ($originCountry)" else "Region",
                        knownValues = filteredRegions,
                        selectedValue = originRegion,
                        onValueChange = { originRegion = it },
                        displayName = { it.displayName },
                        recentValues = recentRegions,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    FieldChipPicker(
                        label = "Roast level",
                        knownValues = CoffeeRoastLevel.known,
                        selectedValue = roastLevel,
                        onValueChange = { roastLevel = it },
                        displayName = { it.displayName },
                        recentValues = recentRoastLevels,
                        multiSelect = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    FieldChipPicker(
                        label = "Variety",
                        knownValues = CoffeeVariety.known,
                        selectedValue = variety,
                        onValueChange = { variety = it },
                        displayName = { it.displayName },
                        recentValues = recentVarieties,
                        multiSelect = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    FieldChipPicker(
                        label = "Process",
                        knownValues = CoffeeProcessType.known,
                        selectedValue = processType,
                        onValueChange = { processType = it },
                        displayName = { it.displayName },
                        recentValues = recentProcesses,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = tastingNotes,
                        onValueChange = { tastingNotes = it },
                        label = { Text("Tasting notes") },
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (g)") },
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("g") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        listOf("250", "500", "1000").forEach { preset ->
                            FilterChip(
                                selected = weight == preset,
                                onClick = { weight = preset },
                                label = { Text("${preset}g") },
                            )
                        }
                    }
                }
                // Roast date picker
                item {
                    var showRoastDatePicker by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = roastDateMillis?.let { DateParser.format(it) } ?: "",
                        onValueChange = {},
                        label = { Text("Roast date") },
                        shape = RoundedCornerShape(16.dp),
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        trailingIcon = {
                            if (roastDateMillis != null) {
                                IconButton(onClick = { roastDateMillis = null }) {
                                    Icon(Icons.Filled.Close, "Clear")
                                }
                            }
                        },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    OutlinedButton(
                        onClick = { showRoastDatePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(if (roastDateMillis != null) "Change roast date" else "Set roast date")
                    }

                    if (showRoastDatePicker) {
                        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = roastDateMillis)
                        DatePickerDialog(
                            onDismissRequest = { showRoastDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    roastDateMillis = datePickerState.selectedDateMillis
                                    showRoastDatePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRoastDatePicker = false }) { Text("Cancel") }
                            },
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }
                // Expiry date picker
                item {
                    var showExpiryDatePicker by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = expiryDateMillis?.let { DateParser.format(it) } ?: "",
                        onValueChange = {},
                        label = { Text("Best before") },
                        shape = RoundedCornerShape(16.dp),
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        trailingIcon = {
                            if (expiryDateMillis != null) {
                                IconButton(onClick = { expiryDateMillis = null }) {
                                    Icon(Icons.Filled.Close, "Clear")
                                }
                            }
                        },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    OutlinedButton(
                        onClick = { showExpiryDatePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(if (expiryDateMillis != null) "Change best before" else "Set best before")
                    }

                    if (showExpiryDatePicker) {
                        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expiryDateMillis)
                        DatePickerDialog(
                            onDismissRequest = { showExpiryDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    expiryDateMillis = datePickerState.selectedDateMillis
                                    showExpiryDatePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExpiryDatePicker = false }) { Text("Cancel") }
                            },
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }
                item {
                    FilterChip(
                        selected = isDecaf,
                        onClick = { isDecaf = !isDecaf },
                        label = { Text("☘ Decaf") },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        shape = MaterialTheme.shapes.small,
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isEditMode && bagToEdit != null && onEdit != null) {
                            onEdit(
                                bagToEdit.copy(
                                    name = name,
                                    roaster = roaster.takeIf { it.isNotBlank() },
                                    origin = originCountry.takeIf { it.isNotBlank() },
                                    region = originRegion.takeIf { it.isNotBlank() },
                                    roastLevel = roastLevel.takeIf { it.isNotBlank() },
                                    barcode = barcode.takeIf { it.isNotBlank() },
                                    weightG = weight.toFloatOrNull() ?: bagToEdit.weightG,
                                    notes = notes.takeIf { it.isNotBlank() },
                                    variety = variety.takeIf { it.isNotBlank() },
                                    processType = processType.takeIf { it.isNotBlank() },
                                    tastingNotes = tastingNotes.takeIf { it.isNotBlank() },
                                    isDecaf = isDecaf,
                                    roastDate = roastDateMillis,
                                    expiryDate = expiryDateMillis,
                                ),
                            )
                        } else {
                            onSave(
                                name,
                                roaster.takeIf { it.isNotBlank() },
                                originCountry.takeIf { it.isNotBlank() },
                                originRegion.takeIf { it.isNotBlank() },
                                roastLevel.takeIf { it.isNotBlank() },
                                barcode.takeIf { it.isNotBlank() },
                                weight.toFloatOrNull(),
                                notes.takeIf { it.isNotBlank() },
                                variety.takeIf { it.isNotBlank() },
                                processType.takeIf { it.isNotBlank() },
                                tastingNotes.takeIf { it.isNotBlank() },
                                roastDateMillis,
                                expiryDateMillis,
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(if (isEditMode) "Save Changes" else "Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BagDetailSheet(
    bag: CoffeeBagEntity,
    brewLogs: List<BrewLogEntity>,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onStatusChange: (CoffeeBagStatus) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onWeightAdjust: (Long, Float) -> Unit = { _, _ -> },
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bag.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp, top = 16.dp),
                    )
                    if (bag.roaster != null) {
                        Text(
                            text = "by ${bag.roaster}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit bag",
                    )
                }
            }

            // Photo gallery
            bag.photoUris?.let { urisStr ->
                val uriList = urisStr.split(",").filter { it.isNotBlank() }
                if (uriList.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        uriList.forEach { uriStr ->
                            val bitmap = remember(uriStr) {
                                try {
                                    val file = java.io.File(android.net.Uri.parse(uriStr).path ?: return@remember null)
                                    val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                        ?: return@remember null
                                    ImagePreprocessor.applyExifRotation(raw, file.absolutePath)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to load bag detail photo", e)
                                    null
                                }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Bag photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // Bag details
                item {
                    if (bag.origin != null) DetailRow("Origin", bag.origin)
                    if (bag.variety != null) DetailRow("Variety", bag.variety)
                    if (bag.roastLevel != null) DetailRow("Roast", bag.roastLevel)
                    if (bag.roastDate != null) DetailRow("Roast date", dateFormat.format(Date(bag.roastDate)))
                    // Weight section with progress bar, estimated doses, and adjust button
                    if (bag.weightG != null) {
                        val remaining = bag.weightG
                        val initial = bag.initialWeightG ?: remaining
                        val progress = if (initial > 0f) (remaining / initial).coerceIn(0f, 1f) else 0f
                        val isLow = remaining in 0.01f..LOW_COFFEE_THRESHOLD_G

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Remaining Coffee",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                isLow -> MaterialTheme.colorScheme.error
                                progress < 0.3f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${"%.0f".format(remaining)}g / ${"%.0f".format(initial)}g",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLow) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${"%.0f".format(progress * 100)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Estimated doses remaining
                        val avgDose = brewLogs.takeIf { it.isNotEmpty() }
                            ?.map { it.doseG }?.average()?.toFloat()
                            ?: 20f
                        if (remaining > 0f) {
                            val estimatedDoses = (remaining / avgDose).toInt()
                            Text(
                                text = "~$estimatedDoses dose${if (estimatedDoses != 1) "s" else ""} remaining" +
                                    " (at ${"%.0f".format(avgDose)}g/dose)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (isLow) {
                            Text(
                                text = "⚠ Running low — consider reordering",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        // Manual adjust button
                        var showWeightDialog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showWeightDialog = true },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Adjust weight")
                        }
                        if (showWeightDialog) {
                            WeightAdjustDialog(
                                currentWeight = remaining,
                                onDismiss = { showWeightDialog = false },
                                onConfirm = { newWeight ->
                                    showWeightDialog = false
                                    onWeightAdjust(bag.id, newWeight)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        // No weight tracked yet — offer to add it
                        var showWeightDialog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showWeightDialog = true },
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Text("Add weight tracking")
                        }
                        if (showWeightDialog) {
                            WeightAdjustDialog(
                                currentWeight = 0f,
                                onDismiss = { showWeightDialog = false },
                                onConfirm = { newWeight ->
                                    showWeightDialog = false
                                    onWeightAdjust(bag.id, newWeight)
                                },
                            )
                        }
                    }
                    if (bag.tastingNotes != null) DetailRow("Tasting notes", bag.tastingNotes)
                    if (bag.notes != null) DetailRow("Notes", bag.notes)
                    if (bag.traceabilityUrl != null) {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        TextButton(
                            onClick = { uriHandler.openUri(bag.traceabilityUrl) },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("🔗 Traceability info")
                        }
                    }
                }

                // Brew history section
                if (brewLogs.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = "Brew History",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    // Stats summary
                    item {
                        val avgRating = brewLogs.mapNotNull { it.rating }.let { ratings ->
                            if (ratings.isNotEmpty()) ratings.average().toFloat() else null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${brewLogs.size} brew${if (brewLogs.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (avgRating != null) {
                                Text(
                                    text = "⭐ ${"%.1f".format(avgRating)} avg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // Individual brew entries
                    items(brewLogs.sortedByDescending { it.createdAt }) { log ->
                        ElevatedCard(
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.method} · ${"%.0f".format(log.doseG)}g → ${"%.0f".format(log.waterG)}g",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                log.rating?.let { rating ->
                                    Text(
                                        text = "⭐ $rating",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status changer
            Box(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = { statusMenuExpanded = true },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Status: ${bag.status.lowercase().replaceFirstChar { it.uppercase() }}")
                }
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                ) {
                    CoffeeBagStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                onStatusChange(status)
                                statusMenuExpanded = false
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Delete Bag",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeightAdjustDialog(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(currentWeight.toString()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Remaining weight (g)") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toFloatOrNull()?.let(onConfirm) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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

