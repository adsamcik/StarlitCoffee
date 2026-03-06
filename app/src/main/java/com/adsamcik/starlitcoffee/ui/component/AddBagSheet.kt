package com.adsamcik.starlitcoffee.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety
import com.adsamcik.starlitcoffee.util.DateParser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBagSheet(
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
