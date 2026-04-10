package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.WeightParser
import java.net.URL
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBagSheet(
    initialBarcode: String? = null,
    ocrPrefill: OcrFieldExtractor.OcrExtractionResult? = null,
    initialName: String? = null,
    initialRoaster: String? = null,
    traceabilityUrl: String? = null,
    capturedPhotoUris: String? = null,
    fieldEvidence: Map<String, BagFieldEvidence> = emptyMap(),
    reviewHints: List<BagPhotoReviewHint> = emptyList(),
    isProcessing: Boolean = false,
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
        isDecaf: Boolean,
        roastDate: Long?,
        expiryDate: Long?,
    ) -> Unit,
    onEdit: ((CoffeeBagEntity) -> Unit)? = null,
    onScanBarcode: (() -> Unit)? = null,
    onExploreQrUrl: ((String, (QrCoffeeMetadata?) -> Unit) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val localizedExistingMetadata = remember(existingBags, locale) {
        existingBags.associateWith { bag -> CoffeeMetadataNormalizer.resolveBagMetadata(bag, locale) }
    }
    val editBagMetadata = remember(bagToEdit, locale) {
        bagToEdit?.let { bag -> CoffeeMetadataNormalizer.resolveBagMetadata(bag, locale) }
    }
    // History suggestions from existing bags (already sorted newest-first by DAO)
    val recentNames = remember(existingBags) {
        existingBags.map { it.name }.distinct().take(10)
    }
    val recentRoasters = remember(existingBags) {
        existingBags.mapNotNull { it.roaster }.distinct().take(10)
    }
    val recentOrigins = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.origin }.distinct().take(10)
    }
    val recentRegions = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.region }.distinct().take(10)
    }
    val recentVarieties = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.variety }
            .flatMap { it.split(",").map { part -> part.trim() } }
            .filter { it.isNotBlank() }.distinct().take(10)
    }
    val recentProcesses = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.processType }.distinct().take(10)
    }
    val recentRoastLevels = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.roastLevel }
            .flatMap { it.split(",").map { part -> part.trim() } }
            .filter { it.isNotBlank() }.distinct().take(10)
    }

    val isEditMode = bagToEdit != null
    var name by remember(ocrPrefill, initialName, bagToEdit) {
        mutableStateOf(bagToEdit?.name ?: ocrPrefill?.name ?: initialName ?: "")
    }
    var roaster by remember(ocrPrefill, initialRoaster, bagToEdit) {
        mutableStateOf(bagToEdit?.roaster ?: ocrPrefill?.roaster ?: initialRoaster ?: "")
    }
    var originCountry by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.origin ?: ocrPrefill?.origin ?: "")
    }
    var originRegion by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.region ?: ocrPrefill?.region ?: "")
    }
    var roastLevel by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.roastLevel ?: ocrPrefill?.roastLevel ?: "")
    }
    var variety by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.variety ?: ocrPrefill?.variety ?: "")
    }
    var processType by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.processType ?: ocrPrefill?.processType ?: "")
    }
    var tastingNotes by remember(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.tastingNotes ?: ocrPrefill?.tastingNotes ?: "")
    }
    var barcode by remember(initialBarcode, bagToEdit) {
        mutableStateOf(bagToEdit?.barcode ?: initialBarcode.orEmpty())
    }
    var weight by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.weightG?.let { "%.0f".format(it) } ?: ocrPrefill?.weight ?: "")
    }
    var notes by remember(bagToEdit) { mutableStateOf(bagToEdit?.notes ?: "") }
    var isDecaf by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.isDecaf ?: ocrPrefill?.isDecaf ?: false)
    }
    var roastDateMillis by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.roastDate ?: ocrPrefill?.roastDate?.let { DateParser.parse(it) })
    }
    var expiryDateMillis by remember(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.expiryDate ?: ocrPrefill?.expiryDate?.let { DateParser.parse(it) })
    }
    var showMoreDetails by remember(isProcessing, bagToEdit) { mutableStateOf(isEditMode || !isProcessing) }
    var selectedEvidenceField by remember(fieldEvidence) { mutableStateOf(fieldEvidence.keys.firstOrNull()) }
    var snapApproveMode by remember(ocrPrefill, isProcessing, bagToEdit) {
        mutableStateOf(ocrPrefill != null && !isProcessing && bagToEdit == null)
    }
    var pendingScrollField by remember { mutableStateOf<String?>(null) }
    var isExploringQr by remember { mutableStateOf(false) }
    var qrExploredMetadata by remember { mutableStateOf<QrCoffeeMetadata?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(fieldEvidence) {
        if (selectedEvidenceField == null || selectedEvidenceField !in fieldEvidence) {
            selectedEvidenceField = fieldEvidence.keys.firstOrNull()
        }
    }
    val selectedEvidence = selectedEvidenceField?.let(fieldEvidence::get)
    val snapDetectedFields = buildSnapApproveFieldItems(
        origin = originCountry,
        region = originRegion,
        roastLevel = roastLevel,
        variety = variety,
        processType = processType,
        tastingNotes = tastingNotes,
        roastDateMillis = roastDateMillis,
        expiryDateMillis = expiryDateMillis,
        isDecaf = isDecaf,
        weight = weight,
        fieldEvidence = fieldEvidence,
        fieldConfidence = ocrPrefill?.fieldConfidence.orEmpty(),
    )
    val confidentDetectedFields = snapDetectedFields.filter { it.confidence == BagFieldConfidence.HIGH }
    val reviewDetectedFields = snapDetectedFields.filter { it.confidence != BagFieldConfidence.HIGH }
    val hasMissingDetails = listOf(
        originCountry,
        originRegion,
        roastLevel,
        variety,
        processType,
        tastingNotes,
        weight,
    ).any { it.isBlank() } || roastDateMillis == null || expiryDateMillis == null
    val saveButtonLabel = when {
        isEditMode -> "Save Changes"
        snapApproveMode && reviewDetectedFields.isEmpty() && snapDetectedFields.isNotEmpty() -> "✅ Looks Good — Save"
        snapApproveMode -> "Accept & Save"
        else -> "Save"
    }
    val saveButtonColors = if (snapApproveMode && reviewDetectedFields.isEmpty() && snapDetectedFields.isNotEmpty()) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    LaunchedEffect(snapApproveMode, pendingScrollField, showMoreDetails, initialBarcode) {
        val targetField = pendingScrollField ?: return@LaunchedEffect
        if (snapApproveMode) return@LaunchedEffect
        val targetIndex = fullFormIndexForField(
            fieldName = targetField,
            hasBarcode = initialBarcode != null || onScanBarcode != null,
            showMoreDetails = showMoreDetails,
        ) ?: return@LaunchedEffect
        listState.animateScrollToItem(targetIndex)
        pendingScrollField = null
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
                    text = when {
                        isEditMode -> "Edit Coffee Bag"
                        snapApproveMode -> "Review Bag"
                        else -> "Add Coffee Bag"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = 16.dp, top = 16.dp)
                        .semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
            ) {
                item {
                    if (isProcessing && !isEditMode) {
                        ProcessingStatusCard()
                    } else {
                        if (reviewHints.isNotEmpty()) {
                            ReviewHintsCard(reviewHints = reviewHints)
                        }
                        if (!snapApproveMode) {
                            selectedEvidence?.let { evidence ->
                                FieldEvidencePreviewCard(
                                    evidence = evidence,
                                    capturedPhotoUris = capturedPhotoUris,
                                )
                            }
                            traceabilityUrl?.let { qrUrl ->
                                if (qrExploredMetadata != null) {
                                    QrLinkCard(
                                        url = qrUrl,
                                        onOpen = { uriHandler.openUri(qrUrl) },
                                        exploredLabel = "✓ Coffee details extracted from ${URL(qrUrl).host}",
                                    )
                                } else {
                                    QrApprovalCard(
                                        url = qrUrl,
                                        isExploring = isExploringQr,
                                        onExplore = {
                                            if (onExploreQrUrl != null) {
                                                isExploringQr = true
                                                onExploreQrUrl(qrUrl) { metadata ->
                                                    isExploringQr = false
                                                    qrExploredMetadata = metadata
                                                    if (metadata != null) {
                                                        if (name.isBlank() && metadata.name != null) name = metadata.name
                                                        if (roaster.isBlank() && metadata.roaster != null) roaster = metadata.roaster
                                                        if (originCountry.isBlank() && metadata.origin != null) originCountry = metadata.origin
                                                        if (originRegion.isBlank() && metadata.region != null) originRegion = metadata.region
                                                        if (processType.isBlank() && metadata.processType != null) processType = metadata.processType
                                                        if (tastingNotes.isBlank() && metadata.tastingNotes != null) tastingNotes = metadata.tastingNotes
                                                    }
                                                }
                                            }
                                        },
                                        onSkip = { /* URL is still saved as traceabilityUrl */ },
                                    )
                                }
                            }
                        }
                    }
                }

                if (isProcessing && !isEditMode) {
                    item {
                        ProcessingTextFieldSkeleton(label = "Name")
                    }
                    item { ProcessingTextFieldSkeleton(label = "Roaster") }
                    item { ProcessingTextFieldSkeleton(label = "Origin") }
                    item { ProcessingTextFieldSkeleton(label = "Weight") }
                } else if (snapApproveMode) {
                    item {
                        SnapApproveSection(
                            capturedPhotoUris = capturedPhotoUris,
                            name = name,
                            onNameChange = { name = it },
                            roaster = roaster,
                            onRoasterChange = { roaster = it },
                            confidentFields = confidentDetectedFields,
                            reviewFields = reviewDetectedFields,
                            hasMissingDetails = hasMissingDetails,
                            onFieldClick = { fieldName ->
                                selectedEvidenceField = fieldName
                                if (fieldNeedsOptionalSection(fieldName)) {
                                    showMoreDetails = true
                                }
                                pendingScrollField = fieldName
                                snapApproveMode = false
                            },
                            onEditAllFields = {
                                pendingScrollField = "name"
                                snapApproveMode = false
                            },
                            onAddMoreDetails = {
                                showMoreDetails = true
                                pendingScrollField = "roastLevel"
                                snapApproveMode = false
                            },
                        )
                    }
                } else {
                    if (initialBarcode != null) {
                        item {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode / EAN") },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = if (onScanBarcode != null) {
                                    {
                                        IconButton(onClick = { onScanBarcode() }) {
                                            Icon(
                                                Icons.Filled.CameraAlt,
                                                contentDescription = "Scan barcode",
                                            )
                                        }
                                    }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                        }
                    } else if (onScanBarcode != null) {
                        item {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode / EAN") },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { onScanBarcode() }) {
                                        Icon(
                                            Icons.Filled.CameraAlt,
                                            contentDescription = "Scan barcode",
                                        )
                                    }
                                },
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
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                if (focused) selectedEvidenceField = "name"
                            },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["name"],
                            onClick = { selectedEvidenceField = "name" },
                        )
                    }
                    item {
                        SuggestingTextField(
                            value = roaster,
                            onValueChange = { roaster = it },
                            label = "Roaster",
                            suggestions = recentRoasters,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                if (focused) selectedEvidenceField = "roaster"
                            },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["roaster"],
                            onClick = { selectedEvidenceField = "roaster" },
                        )
                    }
                    item {
                        FieldChipPicker(
                            label = "Origin",
                            knownValues = CoffeeOrigin.known,
                            selectedValue = originCountry,
                            onValueChange = { originCountry = it },
                            displayName = {
                                when (it) {
                                    is CoffeeOrigin.Known ->
                                        CoffeeMetadataNormalizer.displayOrigin(it.name, it.displayName, locale) ?: it.displayName
                                    is CoffeeOrigin.Other -> it.displayName
                                }
                            },
                            recentValues = recentOrigins,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onInteraction = { selectedEvidenceField = "origin" },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["origin"],
                            onClick = { selectedEvidenceField = "origin" },
                        )
                    }
                    item {
                        val filteredRegions = if (originCountry.isNotBlank()) {
                            CoffeeMetadataNormalizer.regionsForOrigin(originCountry, locale).ifEmpty {
                                CoffeeRegion.Known.entries.toList()
                            }
                        } else {
                            CoffeeRegion.Known.entries.toList()
                        }
                        FieldChipPicker(
                            label = if (originCountry.isNotBlank()) "Region ($originCountry)" else "Region",
                            knownValues = filteredRegions,
                            selectedValue = originRegion,
                            onValueChange = { originRegion = it },
                            displayName = {
                                CoffeeMetadataNormalizer.displayRegion(it.name, it.displayName, locale) ?: it.displayName
                            },
                            recentValues = recentRegions,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onInteraction = { selectedEvidenceField = "region" },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["region"],
                            onClick = { selectedEvidenceField = "region" },
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
                                .padding(bottom = 4.dp)
                                .onFocusChanged {
                                    if (it.isFocused) selectedEvidenceField = "weight"
                                },
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            listOf("250", "500", "1000").forEach { preset ->
                                FilterChip(
                                    selected = weight == preset,
                                    onClick = {
                                        selectedEvidenceField = "weight"
                                        weight = preset
                                    },
                                    label = { Text("${preset}g") },
                                )
                            }
                        }
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["weight"],
                            onClick = { selectedEvidenceField = "weight" },
                        )
                    }
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
                            onClick = {
                                selectedEvidenceField = "roastDate"
                                showRoastDatePicker = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        ) {
                            Text(if (roastDateMillis != null) "Change roast date" else "Set roast date")
                        }
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["roastDate"],
                            onClick = { selectedEvidenceField = "roastDate" },
                        )

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
                    item {
                        OutlinedButton(
                            onClick = { showMoreDetails = !showMoreDetails },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            Text(if (showMoreDetails) "Hide optional details" else "Show optional details")
                        }
                    }

                    if (showMoreDetails) {
                        item {
                            FieldChipPicker(
                                label = "Roast level",
                                knownValues = CoffeeRoastLevel.known,
                                selectedValue = roastLevel,
                                onValueChange = { roastLevel = it },
                                displayName = {
                                    when (it) {
                                        is CoffeeRoastLevel.Known ->
                                            CoffeeMetadataNormalizer.displayRoastLevels(it.name, it.displayName, locale)
                                                ?: it.displayName
                                        is CoffeeRoastLevel.Other -> it.displayName
                                    }
                                },
                                recentValues = recentRoastLevels,
                                multiSelect = true,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onInteraction = { selectedEvidenceField = "roastLevel" },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["roastLevel"],
                                onClick = { selectedEvidenceField = "roastLevel" },
                            )
                        }
                        item {
                            FieldChipPicker(
                                label = "Variety",
                                knownValues = CoffeeVariety.known,
                                selectedValue = variety,
                                onValueChange = { variety = it },
                                displayName = {
                                    when (it) {
                                        is CoffeeVariety.Known ->
                                            CoffeeMetadataNormalizer.displayVarieties(it.name, it.displayName, locale)
                                                ?: it.displayName
                                        is CoffeeVariety.Other -> it.displayName
                                    }
                                },
                                recentValues = recentVarieties,
                                multiSelect = true,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onInteraction = { selectedEvidenceField = "variety" },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["variety"],
                                onClick = { selectedEvidenceField = "variety" },
                            )
                        }
                        item {
                            FieldChipPicker(
                                label = "Process",
                                knownValues = CoffeeProcessType.known,
                                selectedValue = processType,
                                onValueChange = { processType = it },
                                displayName = {
                                    when (it) {
                                        is CoffeeProcessType.Known ->
                                            CoffeeMetadataNormalizer.displayProcessType(it.name, it.displayName, locale)
                                                ?: it.displayName
                                        is CoffeeProcessType.Other -> it.displayName
                                    }
                                },
                                recentValues = recentProcesses,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onInteraction = { selectedEvidenceField = "processType" },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["processType"],
                                onClick = { selectedEvidenceField = "processType" },
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
                                    .padding(bottom = 4.dp)
                                    .onFocusChanged {
                                        if (it.isFocused) selectedEvidenceField = "tastingNotes"
                                    },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["tastingNotes"],
                                onClick = { selectedEvidenceField = "tastingNotes" },
                            )
                        }
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
                                onClick = {
                                    selectedEvidenceField = "expiryDate"
                                    showExpiryDatePicker = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                            ) {
                                Text(if (expiryDateMillis != null) "Change best before" else "Set best before")
                            }
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["expiryDate"],
                                onClick = { selectedEvidenceField = "expiryDate" },
                            )

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
                            ElevatedCard(
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = isDecaf,
                                            role = Role.Checkbox,
                                            onValueChange = { isDecaf = it },
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isDecaf,
                                        onCheckedChange = null,
                                    )
                                    Column(
                                        modifier = Modifier.padding(start = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "Decaf coffee",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = "Keeps brew guidance in sync with decaf beans and shorter steep targets.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
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
                                    weightG = WeightParser.parseToGrams(weight) ?: bagToEdit.weightG,
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
                                WeightParser.parseToGrams(weight),
                                notes.takeIf { it.isNotBlank() },
                                variety.takeIf { it.isNotBlank() },
                                processType.takeIf { it.isNotBlank() },
                                tastingNotes.takeIf { it.isNotBlank() },
                                isDecaf,
                                roastDateMillis,
                                expiryDateMillis,
                            )
                        }
                    }
                },
                colors = saveButtonColors,
                shape = MaterialTheme.shapes.large,
                enabled = name.isNotBlank() && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(saveButtonLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private data class SnapApproveFieldItem(
    val fieldName: String,
    val emoji: String,
    val value: String,
    val confidence: BagFieldConfidence,
)

private val SNAP_APPROVE_EMOJI_MAP = mapOf(
    "origin" to "🌍",
    "region" to "🗺️",
    "processType" to "☕",
    "variety" to "🫘",
    "roaster" to "🔥",
    "name" to "🏷️",
    "altitude" to "⛰️",
    "tastingNotes" to "🍓",
    "roastLevel" to "🌗",
    "roastDate" to "📅",
    "expiryDate" to "⏳",
    "isDecaf" to "🌙",
    "weight" to "⚖️",
    "farm" to "🚜",
)

private fun buildSnapApproveFieldItems(
    origin: String,
    region: String,
    roastLevel: String,
    variety: String,
    processType: String,
    tastingNotes: String,
    roastDateMillis: Long?,
    expiryDateMillis: Long?,
    isDecaf: Boolean,
    weight: String,
    fieldEvidence: Map<String, BagFieldEvidence>,
    fieldConfidence: Map<String, BagFieldConfidence>,
): List<SnapApproveFieldItem> = listOfNotNull(
    snapApproveFieldItem("origin", origin, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("region", region, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("processType", processType, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("variety", variety, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("roastLevel", roastLevel, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("weight", weight, fieldEvidence, fieldConfidence),
    roastDateMillis?.let {
        snapApproveFieldItem("roastDate", DateParser.format(it), fieldEvidence, fieldConfidence)
    },
    expiryDateMillis?.let {
        snapApproveFieldItem("expiryDate", DateParser.format(it), fieldEvidence, fieldConfidence)
    },
    if (isDecaf) {
        snapApproveFieldItem("isDecaf", "Decaf", fieldEvidence, fieldConfidence)
    } else {
        null
    },
    snapApproveFieldItem("tastingNotes", tastingNotes, fieldEvidence, fieldConfidence),
)

private fun snapApproveFieldItem(
    fieldName: String,
    value: String,
    fieldEvidence: Map<String, BagFieldEvidence>,
    fieldConfidence: Map<String, BagFieldConfidence>,
): SnapApproveFieldItem? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val emoji = SNAP_APPROVE_EMOJI_MAP[fieldName] ?: return null
    return SnapApproveFieldItem(
        fieldName = fieldName,
        emoji = emoji,
        value = trimmed,
        confidence = resolvedFieldConfidence(
            evidenceConfidence = fieldEvidence[fieldName]?.confidence,
            extractedConfidence = fieldConfidence[fieldName],
        ),
    )
}

private fun resolvedFieldConfidence(
    evidenceConfidence: BagFieldConfidence?,
    extractedConfidence: BagFieldConfidence?,
): BagFieldConfidence {
    val confidences = listOfNotNull(evidenceConfidence, extractedConfidence)
    return when {
        BagFieldConfidence.HIGH in confidences -> BagFieldConfidence.HIGH
        BagFieldConfidence.MEDIUM in confidences -> BagFieldConfidence.MEDIUM
        BagFieldConfidence.LOW in confidences -> BagFieldConfidence.LOW
        else -> BagFieldConfidence.NEEDS_REVIEW
    }
}

private fun fieldNeedsOptionalSection(fieldName: String): Boolean = fieldName in setOf(
    "roastLevel",
    "variety",
    "processType",
    "tastingNotes",
    "expiryDate",
    "isDecaf",
)

private fun fullFormIndexForField(
    fieldName: String,
    hasBarcode: Boolean,
    showMoreDetails: Boolean,
): Int? {
    var index = 1
    if (hasBarcode) {
        if (fieldName == "barcode") return index
        index += 1
    }
    val requiredFields = listOf("name", "roaster", "origin", "region", "weight", "roastDate")
    requiredFields.forEach { key ->
        if (fieldName == key) return index
        index += 1
    }
    if (fieldName == "toggleOptional") return index
    index += 1
    if (!showMoreDetails) return null
    val optionalFields = listOf(
        "roastLevel",
        "variety",
        "processType",
        "tastingNotes",
        "expiryDate",
        "isDecaf",
        "notes",
    )
    optionalFields.forEach { key ->
        if (fieldName == key) return index
        index += 1
    }
    return null
}

@Composable
private fun SnapApproveSection(
    capturedPhotoUris: String?,
    name: String,
    onNameChange: (String) -> Unit,
    roaster: String,
    onRoasterChange: (String) -> Unit,
    confidentFields: List<SnapApproveFieldItem>,
    reviewFields: List<SnapApproveFieldItem>,
    hasMissingDetails: Boolean,
    onFieldClick: (String) -> Unit,
    onEditAllFields: () -> Unit,
    onAddMoreDetails: () -> Unit,
) {
    val thumbnailBitmap = remember(capturedPhotoUris) {
        loadEvidenceBitmap(
            previewUri = capturedPhotoUris?.split(",")?.firstOrNull()?.trim(),
            previewRect = null,
            capturedPhotoUris = null,
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        thumbnailBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured bag photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    placeholder = { Text("Bag name") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = roaster,
                    onValueChange = onRoasterChange,
                    singleLine = true,
                    placeholder = { Text("Roaster") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (confidentFields.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Detected Details",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        confidentFields.forEach { item ->
                            SnapApproveChip(
                                item = item,
                                review = false,
                                onClick = { onFieldClick(item.fieldName) },
                            )
                        }
                    }
                    Text(
                        text = "Tap any chip to edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (reviewFields.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Needs Review",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reviewFields.forEach { item ->
                            SnapApproveChip(
                                item = item,
                                review = true,
                                onClick = { onFieldClick(item.fieldName) },
                            )
                        }
                    }
                    Text(
                        text = "Tap to confirm or change these details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onEditAllFields) {
                Text("Edit all fields")
            }
            if (hasMissingDetails) {
                TextButton(onClick = onAddMoreDetails) {
                    Text("Add more details")
                }
            }
        }
    }
}

@Composable
private fun SnapApproveChip(
    item: SnapApproveFieldItem,
    review: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (review) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (review) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        border = if (review) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = buildString {
                append(item.emoji)
                append(' ')
                append(item.value)
                if (review) append('?')
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FieldEvidenceAssist(
    evidence: BagFieldEvidence?,
    onClick: () -> Unit,
) {
    if (evidence == null) return
    AssistChip(
        onClick = onClick,
        label = { Text(evidence.summaryLabel()) },
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ProcessingStatusCard() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = "Analyzing label photos",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "We are checking blur, merging front/back text, and preparing the most reliable fields.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProcessingTextFieldSkeleton(label: String) {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        enabled = false,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun ReviewHintsCard(reviewHints: List<BagPhotoReviewHint>) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Review these fields",
                style = MaterialTheme.typography.titleMedium,
            )
            reviewHints.forEachIndexed { index, hint ->
                Text(
                    text = hint.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hint.severity == BagReviewSeverity.WARNING) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (index != reviewHints.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun QrLinkCard(
    url: String,
    onOpen: () -> Unit,
    exploredLabel: String? = null,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "QR website",
                style = MaterialTheme.typography.titleMedium,
            )
            if (exploredLabel != null) {
                Text(
                    text = exploredLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpen) {
                Text("Open website")
            }
        }
    }
}

@Composable
private fun QrApprovalCard(
    url: String,
    isExploring: Boolean,
    onExplore: () -> Unit,
    onSkip: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("QR link found", style = MaterialTheme.typography.titleMedium)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "This website may contain coffee details (roaster, origin, tasting notes). " +
                    "Approve to fetch and extract them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isExploring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onExplore) {
                        Text("Explore & extract")
                    }
                    TextButton(onClick = onSkip) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldEvidencePreviewCard(
    evidence: BagFieldEvidence,
    capturedPhotoUris: String?,
) {
    val evidenceBitmap = remember(evidence.previewUri, evidence.previewRect, capturedPhotoUris) {
        loadEvidenceBitmap(
            previewUri = evidence.previewUri,
            previewRect = evidence.previewRect,
            capturedPhotoUris = capturedPhotoUris,
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Detected ${evidence.fieldName.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = evidence.summaryLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            evidenceBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Detected field evidence",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            evidence.supportingText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun loadEvidenceBitmap(
    previewUri: String?,
    previewRect: com.adsamcik.starlitcoffee.util.BagPhotoRect?,
    capturedPhotoUris: String?,
): Bitmap? {
    val candidateUri = previewUri ?: capturedPhotoUris?.split(",")?.firstOrNull()?.trim()
    val path = candidateUri?.let { Uri.parse(it).path } ?: return null
    val rawBitmap = BitmapFactory.decodeFile(path) ?: return null
    val rotated = ImagePreprocessor.applyExifRotation(rawBitmap, path)
    if (previewRect == null) return rotated

    val left = (rotated.width * previewRect.leftFraction).toInt().coerceIn(0, rotated.width - 1)
    val top = (rotated.height * previewRect.topFraction).toInt().coerceIn(0, rotated.height - 1)
    val right = (rotated.width * previewRect.rightFraction).toInt().coerceIn(left + 1, rotated.width)
    val bottom = (rotated.height * previewRect.bottomFraction).toInt().coerceIn(top + 1, rotated.height)
    val cropWidth = (right - left).coerceAtLeast(1)
    val cropHeight = (bottom - top).coerceAtLeast(1)
    return Bitmap.createBitmap(rotated, left, top, cropWidth, cropHeight)
}
