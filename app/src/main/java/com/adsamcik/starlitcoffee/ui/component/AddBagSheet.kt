package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.core.net.toUri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
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
import com.adsamcik.starlitcoffee.data.model.DecafProcess
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.ThumbnailLoader
import com.adsamcik.starlitcoffee.util.WeightParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val QR_METADATA_FIELD_COUNT = 13

// QrCoffeeMetadata is a flat data class of primitives. Save it as a list so the QR-explored
// metadata survives rotation / dark-mode / process death. The data class is not Parcelable
// and the project does not enable kotlin-parcelize, so a custom Saver is required.
private val QrCoffeeMetadataSaver: Saver<QrCoffeeMetadata?, ArrayList<Any?>> = Saver(
    save = { meta ->
        if (meta == null) {
            arrayListOf<Any?>()
        } else {
            arrayListOf<Any?>(
                meta.sourceUrl,
                meta.finalUrl,
                meta.host,
                meta.pageTitle,
                meta.pageDescription,
                meta.name,
                meta.roaster,
                meta.origin,
                meta.region,
                meta.processType,
                meta.tastingNotes,
                meta.isDecaf,
                meta.supportingSnippet,
            )
        }
    },
    restore = { list ->
        if (list.size < QR_METADATA_FIELD_COUNT) {
            null
        } else {
            QrCoffeeMetadata(
                sourceUrl = list[0] as String,
                finalUrl = list[1] as String,
                host = list[2] as String,
                pageTitle = list[3] as String?,
                pageDescription = list[4] as String?,
                name = list[5] as String?,
                roaster = list[6] as String?,
                origin = list[7] as String?,
                region = list[8] as String?,
                processType = list[9] as String?,
                tastingNotes = list[10] as String?,
                isDecaf = list[11] as Boolean?,
                supportingSnippet = list[12] as String?,
            )
        }
    },
)

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
    llmStatus: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
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
        decafProcess: String?,
        roastDate: Long?,
        expiryDate: Long?,
    ) -> Unit,
    onEdit: ((CoffeeBagEntity) -> Unit)? = null,
    onScanBarcode: (() -> Unit)? = null,
    onExploreQrUrl: ((String, (QrCoffeeMetadata?) -> Unit) -> Unit)? = null,
    onRetryLlmEnrichment: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val locale = LocalLocale.current.platformLocale
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
    var name by rememberSaveable(ocrPrefill, initialName, bagToEdit) {
        mutableStateOf(bagToEdit?.name ?: ocrPrefill?.name ?: initialName ?: "")
    }
    var roaster by rememberSaveable(ocrPrefill, initialRoaster, bagToEdit) {
        mutableStateOf(bagToEdit?.roaster ?: ocrPrefill?.roaster ?: initialRoaster ?: "")
    }
    var originCountry by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.origin ?: ocrPrefill?.origin ?: "")
    }
    var originRegion by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.region ?: ocrPrefill?.region ?: "")
    }
    var roastLevel by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.roastLevel ?: ocrPrefill?.roastLevel ?: "")
    }
    var variety by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.variety ?: ocrPrefill?.variety ?: "")
    }
    var processType by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.processType ?: ocrPrefill?.processType ?: "")
    }
    var tastingNotes by rememberSaveable(ocrPrefill, editBagMetadata) {
        mutableStateOf(editBagMetadata?.tastingNotes ?: ocrPrefill?.tastingNotes ?: "")
    }
    var barcode by rememberSaveable(initialBarcode, bagToEdit) {
        mutableStateOf(bagToEdit?.barcode ?: initialBarcode.orEmpty())
    }
    var weight by rememberSaveable(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.weightG?.let { "%.0f".format(it) } ?: ocrPrefill?.weight ?: "")
    }
    var notes by rememberSaveable(bagToEdit) { mutableStateOf(bagToEdit?.notes ?: "") }
    var isDecaf by rememberSaveable(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.isDecaf ?: ocrPrefill?.isDecaf ?: false)
    }
    var decafProcess by rememberSaveable(bagToEdit) {
        mutableStateOf(bagToEdit?.decafProcess)
    }
    var roastDateMillis by rememberSaveable(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.roastDate ?: ocrPrefill?.roastDate?.let { DateParser.parse(it) })
    }
    var expiryDateMillis by rememberSaveable(ocrPrefill, bagToEdit) {
        mutableStateOf(bagToEdit?.expiryDate ?: ocrPrefill?.expiryDate?.let { DateParser.parse(it) })
    }
    var showMoreDetails by rememberSaveable(isProcessing, bagToEdit) { mutableStateOf(isEditMode || !isProcessing) }
    var selectedEvidenceField by rememberSaveable(fieldEvidence) { mutableStateOf(fieldEvidence.keys.firstOrNull()) }
    var snapApproveMode by rememberSaveable(ocrPrefill, isProcessing, bagToEdit) {
        mutableStateOf(ocrPrefill != null && !isProcessing && bagToEdit == null)
    }
    var pendingScrollField by rememberSaveable { mutableStateOf<String?>(null) }
    // Transient: tracks an in-flight async QR exploration. Intentionally not saved across
    // config changes — the explore callback closure would still target the disposed composable's
    // setters, so persisting `true` could leave the loading indicator stuck. Re-trigger on
    // rotation if needed.
    var isExploringQr by remember { mutableStateOf(false) }
    var qrExploredMetadata by rememberSaveable(stateSaver = QrCoffeeMetadataSaver) {
        mutableStateOf<QrCoffeeMetadata?>(null)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(fieldEvidence) {
        if (selectedEvidenceField == null || selectedEvidenceField !in fieldEvidence) {
            selectedEvidenceField = fieldEvidence.keys.firstOrNull()
        }
    }
    val selectedEvidence = selectedEvidenceField?.let(fieldEvidence::get)
    val snapDetectedFields = buildSnapApproveFieldItems(
        form = SnapApproveFormState(
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
        ),
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
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
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
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
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
                        val hasLlmEvidence = fieldEvidence.values.any {
                            it.sourceType == BagFieldSourceType.LLM
                        }
                        LlmEnrichmentStatusCard(
                            status = llmStatus,
                            hasLlmEvidence = hasLlmEvidence,
                            onRetry = onRetryLlmEnrichment,
                        )
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
                        ProcessingTextFieldSkeleton(label = stringResource(R.string.label_name))
                    }
                    item { ProcessingTextFieldSkeleton(label = stringResource(R.string.label_roaster)) }
                    item { ProcessingTextFieldSkeleton(label = stringResource(R.string.label_origin)) }
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
                                label = { Text(stringResource(R.string.label_barcode_ean)) },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = if (onScanBarcode != null) {
                                    {
                                        IconButton(onClick = { onScanBarcode() }) {
                                            Icon(
                                                Icons.Filled.CameraAlt,
                                                contentDescription = stringResource(R.string.action_scan_barcode),
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
                                label = { Text(stringResource(R.string.label_barcode_ean)) },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { onScanBarcode() }) {
                                        Icon(
                                            Icons.Filled.CameraAlt,
                                            contentDescription = stringResource(R.string.action_scan_barcode),
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
                            label = stringResource(R.string.label_name_required),
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
                            label = stringResource(R.string.label_roaster),
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
                            label = stringResource(R.string.label_origin),
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
                            label = { Text(stringResource(R.string.label_weight_grams)) },
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
                        var showRoastDatePicker by rememberSaveable { mutableStateOf(false) }

                        OutlinedTextField(
                            value = roastDateMillis?.let { DateParser.format(it) } ?: "",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.label_roast_date)) },
                            shape = MaterialTheme.shapes.small,
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
                                    TextButton(onClick = { showRoastDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
                                label = stringResource(R.string.label_roast_level),
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
                                label = stringResource(R.string.label_variety),
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
                                label = stringResource(R.string.label_process),
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
                                label = { Text(stringResource(R.string.label_tasting_notes)) },
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
                            var showExpiryDatePicker by rememberSaveable { mutableStateOf(false) }

                            OutlinedTextField(
                                value = expiryDateMillis?.let { DateParser.format(it) } ?: "",
                                onValueChange = {},
                                label = { Text(stringResource(R.string.label_best_before)) },
                                shape = MaterialTheme.shapes.small,
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
                                        TextButton(onClick = { showExpiryDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
                                            text = stringResource(R.string.label_decaf_coffee),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = stringResource(R.string.msg_decaf_guidance),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = isDecaf) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.label_decaf_process_optional),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = stringResource(R.string.msg_decaf_process_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            DecafProcess.entries.forEach { process ->
                                                val key = process.name
                                                val selected = decafProcess == key ||
                                                    (decafProcess == null && process == DecafProcess.UNKNOWN)
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        decafProcess = if (process == DecafProcess.UNKNOWN) null else key
                                                    },
                                                    label = { Text(process.shortLabel) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text(stringResource(R.string.label_notes)) },
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
                        if (bagToEdit != null && onEdit != null) {
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
                                    decafProcess = decafProcess?.takeIf { isDecaf },
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
                                decafProcess?.takeIf { isDecaf },
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

/**
 * Plain bundle of the form field values that drive the snap-approve summary.
 * Grouping here keeps [buildSnapApproveFieldItems] under detekt's parameter
 * threshold while still surfacing the inputs explicitly at the call site.
 */
private data class SnapApproveFormState(
    val origin: String,
    val region: String,
    val roastLevel: String,
    val variety: String,
    val processType: String,
    val tastingNotes: String,
    val roastDateMillis: Long?,
    val expiryDateMillis: Long?,
    val isDecaf: Boolean,
    val weight: String,
)

private fun buildSnapApproveFieldItems(
    form: SnapApproveFormState,
    fieldEvidence: Map<String, BagFieldEvidence>,
    fieldConfidence: Map<String, BagFieldConfidence>,
): List<SnapApproveFieldItem> = listOfNotNull(
    snapApproveFieldItem("origin", form.origin, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("region", form.region, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("processType", form.processType, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("variety", form.variety, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("roastLevel", form.roastLevel, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("weight", form.weight, fieldEvidence, fieldConfidence),
    form.roastDateMillis?.let {
        snapApproveFieldItem("roastDate", DateParser.format(it), fieldEvidence, fieldConfidence)
    },
    form.expiryDateMillis?.let {
        snapApproveFieldItem("expiryDate", DateParser.format(it), fieldEvidence, fieldConfidence)
    },
    if (form.isDecaf) {
        snapApproveFieldItem("isDecaf", "Decaf", fieldEvidence, fieldConfidence)
    } else {
        null
    },
    snapApproveFieldItem("tastingNotes", form.tastingNotes, fieldEvidence, fieldConfidence),
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
    val orderedFields = buildList {
        if (hasBarcode) add("barcode")
        addAll(listOf("name", "roaster", "origin", "region", "weight", "roastDate"))
        add("toggleOptional")
        if (showMoreDetails) {
            addAll(
                listOf(
                    "roastLevel",
                    "variety",
                    "processType",
                    "tastingNotes",
                    "expiryDate",
                    "isDecaf",
                    "notes",
                ),
            )
        }
    }
    val position = orderedFields.indexOf(fieldName)
    return if (position >= 0) position + 1 else null
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
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        capturedPhotoUris
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                BagThumbnail(
                    uri = uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                    downsampleTarget = 168.dp,
                    shape = MaterialTheme.shapes.medium,
                    contentDescription = stringResource(R.string.cd_captured_bag_photo),
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
                        text = stringResource(R.string.label_detected_details),
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
                        text = stringResource(R.string.msg_tap_chip_to_edit),
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
                        text = stringResource(R.string.label_needs_review),
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
                Text(stringResource(R.string.action_edit_all_fields))
            }
            if (hasMissingDetails) {
                TextButton(onClick = onAddMoreDetails) {
                    Text(stringResource(R.string.action_add_more_details))
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
        shape = MaterialTheme.shapes.small,
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
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.msg_analyzing_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LoadingIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.msg_analyzing_label_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LlmEnrichmentStatusCard(
    status: LlmEnrichmentStatus,
    hasLlmEvidence: Boolean,
    onRetry: (() -> Unit)?,
) {
    if (status == LlmEnrichmentStatus.NOT_RUN ||
        status == LlmEnrichmentStatus.SUCCEEDED && !hasLlmEvidence
    ) {
        return
    }
    val message = when (status) {
        LlmEnrichmentStatus.SUCCEEDED -> R.string.msg_llm_enrichment_succeeded
        LlmEnrichmentStatus.FAILED -> R.string.msg_llm_enrichment_failed
        LlmEnrichmentStatus.UNAVAILABLE -> R.string.msg_llm_enrichment_unavailable
        LlmEnrichmentStatus.NOT_RUN -> return
    }
    val canRetry = status == LlmEnrichmentStatus.FAILED ||
        status == LlmEnrichmentStatus.UNAVAILABLE
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
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = if (canRetry) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (canRetry && onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry_llm_enrichment))
                }
            }
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
                text = stringResource(R.string.label_review_fields),
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
                text = stringResource(R.string.label_qr_website),
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
                Text(stringResource(R.string.action_open_website))
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
            Text(stringResource(R.string.label_qr_link_found), style = MaterialTheme.typography.titleMedium)
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
                LoadingIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onExplore) {
                        Text(stringResource(R.string.action_explore_extract))
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
    // Decode + (optionally) crop off the main thread. The card reserves a
    // 120dp-tall slot up front so the surrounding layout doesn't reflow when
    // the bitmap arrives.
    val evidenceTargetPx = with(LocalDensity.current) { 120.dp.roundToPx() }
    val evidenceBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = evidence.previewUri,
        key2 = evidence.previewRect,
        key3 = capturedPhotoUris,
    ) {
        value = withContext(Dispatchers.IO) {
            loadEvidenceBitmap(
                previewUri = evidence.previewUri,
                previewRect = evidence.previewRect,
                capturedPhotoUris = capturedPhotoUris,
                targetSizePx = evidenceTargetPx,
            )
        }
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
            // Reserve 120dp height while the bitmap decodes asynchronously so
            // the card doesn't pop into a different size when it arrives.
            val previewModifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(MaterialTheme.shapes.medium)
            val hasPreviewSource = evidence.previewUri != null ||
                !capturedPhotoUris.isNullOrBlank()
            if (hasPreviewSource) {
                evidenceBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.label_detected_field_evidence),
                        modifier = previewModifier,
                        contentScale = ContentScale.Crop,
                    )
                } ?: Spacer(modifier = previewModifier)
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
    targetSizePx: Int,
): Bitmap? {
    val candidateUri = previewUri ?: capturedPhotoUris?.split(",")?.firstOrNull()?.trim()
    val path = candidateUri?.let { it.toUri().path } ?: return null

    // Estimate the source resolution we need to decode so the cropped result
    // still has at least targetSizePx on its longest side. If we always picked
    // targetSizePx for downsampling, a 25%-area crop would render at quarter
    // resolution. ThumbnailLoader's inSampleSize is a power of two, so a 2x
    // safety factor is plenty for typical bag-label crops.
    val cropScale = previewRect?.let {
        val w = (it.rightFraction - it.leftFraction).coerceAtLeast(0.05f)
        val h = (it.bottomFraction - it.topFraction).coerceAtLeast(0.05f)
        1f / maxOf(w, h)
    } ?: 1f
    val decodeTargetPx = (targetSizePx * cropScale).toInt().coerceAtLeast(1)

    val rotated = ThumbnailLoader.loadThumbnail(path, decodeTargetPx) ?: return null
    if (previewRect == null) return rotated

    val left = (rotated.width * previewRect.leftFraction).toInt().coerceIn(0, rotated.width - 1)
    val top = (rotated.height * previewRect.topFraction).toInt().coerceIn(0, rotated.height - 1)
    val right = (rotated.width * previewRect.rightFraction).toInt().coerceIn(left + 1, rotated.width)
    val bottom = (rotated.height * previewRect.bottomFraction).toInt().coerceIn(top + 1, rotated.height)
    val cropWidth = (right - left).coerceAtLeast(1)
    val cropHeight = (bottom - top).coerceAtLeast(1)
    return Bitmap.createBitmap(rotated, left, top, cropWidth, cropHeight)
}
