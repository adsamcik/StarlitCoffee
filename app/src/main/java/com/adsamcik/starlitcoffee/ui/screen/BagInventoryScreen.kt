package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.navigation.BarcodeScanner
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagInventoryScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val allBrewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val scannedBarcode by (currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("scanned_barcode", null)
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }
    var ocrPrefill by remember { mutableStateOf<com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult?>(null) }
    var capturedPhotoUris by remember { mutableStateOf<String?>(null) }

    // Handle scanned barcode result
    var offLookupName by remember { mutableStateOf<String?>(null) }
    var offLookupRoaster by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scannedBarcode) {
        val barcode = scannedBarcode ?: return@LaunchedEffect
        currentBackStackEntry?.savedStateHandle?.set("scanned_barcode", null)
        brewViewModel.findBagByBarcode(barcode) { bag ->
            if (bag != null) {
                selectedBag = bag
                showAddSheet = false
                pendingBarcode = null
            } else {
                pendingBarcode = barcode
                showAddSheet = true
            }
        }
    }

    // Open Food Facts lookup when we have a pending barcode
    LaunchedEffect(pendingBarcode) {
        val barcode = pendingBarcode ?: return@LaunchedEffect
        try {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient.lookupBarcode(barcode)
            }
            if (result != null) {
                offLookupName = result.name
                offLookupRoaster = result.brand
            }
        } catch (_: Exception) {
            // OFF lookup failed — user fills manually
        }
    }

    // Handle captured photos result
    val capturedPhotos by (currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("captured_photos", null)
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    LaunchedEffect(capturedPhotos) {
        val photos = capturedPhotos ?: return@LaunchedEffect
        currentBackStackEntry?.savedStateHandle?.set("captured_photos", null)
        capturedPhotoUris = photos
        // Run OCR on the first photo
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val firstUri = photos.split(",").firstOrNull() ?: return@withContext
            try {
                val file = java.io.File(android.net.Uri.parse(firstUri).path ?: return@withContext)
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
                )
                val result = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { text -> cont.resume(text, null) }
                        .addOnFailureListener { cont.resume(null, null) }
                }
                if (result != null) {
                    val extraction = com.adsamcik.starlitcoffee.util.OcrFieldExtractor.extractFields(result.text)
                    ocrPrefill = extraction
                }
            } catch (_: Exception) {
                // OCR failed silently — user fills manually
            }
        }
        showAddSheet = true
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pendingBarcode = null
                    showAddSheet = true
                },
                shape = RoundedCornerShape(28.dp),
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

    // Add bag bottom sheet
    if (showAddSheet) {
        AddBagSheet(
            initialBarcode = pendingBarcode,
            ocrPrefill = ocrPrefill,
            initialName = offLookupName,
            initialRoaster = offLookupRoaster,
            onDismiss = {
                showAddSheet = false
                pendingBarcode = null
                ocrPrefill = null
                capturedPhotoUris = null
                offLookupName = null
                offLookupRoaster = null
            },
            onScanBarcode = {
                showAddSheet = false
                navController.navigate(BarcodeScanner)
            },
            onScanLabel = {
                showAddSheet = false
                navController.navigate(com.adsamcik.starlitcoffee.navigation.CameraCapture)
            },
            onSave = { name, roaster, origin, roastLevel, barcode, weightG, notes, variety, processType, tastingNotes ->
                brewViewModel.addCoffeeBag(
                    name = name,
                    roaster = roaster,
                    origin = origin,
                    roastLevel = roastLevel,
                    barcode = barcode,
                    weightG = weightG,
                    notes = notes,
                    variety = variety,
                    processType = processType,
                    tastingNotes = tastingNotes,
                    photoUri = capturedPhotoUris?.split(",")?.firstOrNull(),
                )
                showAddSheet = false
                pendingBarcode = null
                ocrPrefill = null
                capturedPhotoUris = null
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
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    Text(
                        text = "${"%.0f".format(w)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (bag.roastDate != null) {
                    Text(
                        text = "Roasted: ${dateFormat.format(Date(bag.roastDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onDismiss: () -> Unit,
    onScanBarcode: () -> Unit,
    onScanLabel: () -> Unit,
    onSave: (
        name: String,
        roaster: String?,
        origin: String?,
        roastLevel: String?,
        barcode: String?,
        weightG: Float?,
        notes: String?,
        variety: String?,
        processType: String?,
        tastingNotes: String?,
    ) -> Unit,
) {
    var name by remember(ocrPrefill, initialName) { mutableStateOf(initialName ?: "") }
    var roaster by remember(ocrPrefill, initialRoaster) { mutableStateOf(ocrPrefill?.roaster ?: initialRoaster ?: "") }
    var origin by remember(ocrPrefill) { mutableStateOf(ocrPrefill?.origin ?: "") }
    var roastLevel by remember(ocrPrefill) { mutableStateOf(ocrPrefill?.roastLevel ?: "") }
    var variety by remember(ocrPrefill) { mutableStateOf(ocrPrefill?.variety ?: "") }
    var processType by remember(ocrPrefill) { mutableStateOf(ocrPrefill?.processType ?: "") }
    var tastingNotes by remember(ocrPrefill) { mutableStateOf(ocrPrefill?.tastingNotes ?: "") }
    var barcode by remember(initialBarcode) { mutableStateOf(initialBarcode.orEmpty()) }
    var weight by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Add Coffee Bag",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp, top = 16.dp),
            )

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
                            shape = RoundedCornerShape(16.dp),
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = roaster,
                        onValueChange = { roaster = it },
                        label = { Text("Roaster") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = it },
                        label = { Text("Origin") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = roastLevel,
                        onValueChange = { roastLevel = it },
                        label = { Text("Roast level") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = variety,
                        onValueChange = { variety = it },
                        label = { Text("Variety") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = processType,
                        onValueChange = { processType = it },
                        label = { Text("Process") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = tastingNotes,
                        onValueChange = { tastingNotes = it },
                        label = { Text("Tasting notes") },
                        shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onScanLabel()
                            },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("OCR", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onScanBarcode()
                            },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Barcode", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            roaster.takeIf { it.isNotBlank() },
                            origin.takeIf { it.isNotBlank() },
                            roastLevel.takeIf { it.isNotBlank() },
                            barcode.takeIf { it.isNotBlank() },
                            weight.toFloatOrNull(),
                            notes.takeIf { it.isNotBlank() },
                            variety.takeIf { it.isNotBlank() },
                            processType.takeIf { it.isNotBlank() },
                            tastingNotes.takeIf { it.isNotBlank() },
                        )
                    }
                },
                shape = RoundedCornerShape(28.dp),
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
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
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
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
                    if (bag.weightG != null) DetailRow("Weight", "${"%.0f".format(bag.weightG)}g")
                    if (bag.tastingNotes != null) DetailRow("Tasting notes", bag.tastingNotes)
                    if (bag.notes != null) DetailRow("Notes", bag.notes)
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
                            shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(28.dp),
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

