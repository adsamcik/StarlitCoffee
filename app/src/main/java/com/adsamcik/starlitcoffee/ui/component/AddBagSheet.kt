package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.testTag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.adsamcik.starlitcoffee.scan.observability.ScanCorrectionLog
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.work.BagDraftReviewLocation
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety
import com.adsamcik.starlitcoffee.data.model.DecafProcess
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoReviewUris
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabularyLoader
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestion
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestionEngine
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyEntry
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.RecognitionPresentation
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import java.net.URL

private const val QR_METADATA_FIELD_COUNT = 13

private fun CoffeeVocabularyEntry.toInputSuggestion(): CoffeeInputSuggestion =
    CoffeeInputSuggestion(value = value, aliases = aliases)

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
    recognition: RecognitionPresentation = RecognitionPresentation(),
    isProcessing: Boolean = false,
    isSaving: Boolean = false,
    existingBags: List<CoffeeBagEntity> = emptyList(),
    bagToEdit: CoffeeBagEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        roaster: String?,
        origin: String?,
        region: String?,
        farm: String?,
        altitude: String?,
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
    onEnableAi: (() -> Unit)? = null,
    onInstallLabelRecognition: (() -> Unit)? = null,
    onSetupAi: (() -> Unit)? = null,
    setupLaunchState: RecognitionSetupState = RecognitionSetupState.IDLE,
    onDisableLabelRecognition: (() -> Unit)? = null,
    onScanMorePhotos: (() -> Unit)? = null,
    initialFormOverride: BagFormSnapshot? = null,
    onUserFieldChange: ((fieldName: String, value: String?) -> Unit)? = null,
    onFieldFocusChange: ((fieldName: String, focused: Boolean) -> Unit)? = null,
    pendingSuggestions: Map<String, String> = emptyMap(),
    onAcceptSuggestion: ((fieldName: String, expectedValue: String) -> Boolean)? = null,
    reviewedFields: Set<String> = emptySet(),
    unknownFields: Set<String> = emptySet(),
    saveErrorResId: Int? = null,
    preserveDraftOnDismiss: Boolean = false,
    onDiscardDraft: (() -> Unit)? = null,
    initialReviewLocation: BagDraftReviewLocation = BagDraftReviewLocation(),
    onReviewLocationChange: ((BagDraftReviewLocation) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val correctionContext = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val vocabulary = remember(correctionContext) {
        CoffeeFilterVocabularyLoader.getInstance(correctionContext)
    }
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
    val recentFarms = remember(existingBags) {
        existingBags.mapNotNull { it.farm }.distinct().take(10)
    }
    val recentAltitudes = remember(existingBags) {
        existingBags.mapNotNull { it.altitude }.distinct().take(10)
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
    val recentTastingNotes = remember(localizedExistingMetadata) {
        localizedExistingMetadata.values.mapNotNull { it.tastingNotes }
            .flatMap { it.split(",").map { part -> part.trim() } }
            .filter { it.isNotBlank() }.distinct().take(12)
    }
    val nameSuggestions = remember(recentNames) {
        CoffeeInputSuggestionEngine.merge(recentNames, emptyList())
    }
    val roasterSuggestions = remember(recentRoasters) {
        CoffeeInputSuggestionEngine.merge(recentRoasters, emptyList())
    }
    val farmSuggestions = remember(recentFarms) {
        CoffeeInputSuggestionEngine.merge(recentFarms, emptyList())
    }
    val altitudeSuggestions = remember(recentAltitudes) {
        CoffeeInputSuggestionEngine.merge(recentAltitudes, emptyList())
    }
    val originSuggestions = remember(recentOrigins, vocabulary, locale) {
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentOrigins,
            libraryValues = CoffeeOrigin.Known.entries.map { origin ->
                val displayName = CoffeeMetadataNormalizer.displayOrigin(
                    origin.name,
                    origin.displayName,
                    locale,
                ) ?: origin.displayName
                CoffeeInputSuggestion(
                    value = displayName,
                    aliases = origin.searchAliases + origin.displayName,
                )
            } + vocabulary.origins.map { it.toInputSuggestion() },
        )
    }
    val roastLevelSuggestions = remember(recentRoastLevels, vocabulary, locale) {
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentRoastLevels,
            libraryValues = CoffeeRoastLevel.Known.entries.map { level ->
                val displayName = CoffeeMetadataNormalizer.displayRoastLevels(
                    level.name,
                    level.displayName,
                    locale,
                ) ?: level.displayName
                CoffeeInputSuggestion(
                    value = displayName,
                    aliases = level.searchAliases + level.displayName,
                )
            } + vocabulary.roastLevels.map { it.toInputSuggestion() },
        )
    }
    val varietySuggestions = remember(recentVarieties, vocabulary, locale) {
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentVarieties,
            libraryValues = CoffeeVariety.Known.entries.map { varietyEntry ->
                val displayName = CoffeeMetadataNormalizer.displayVarieties(
                    varietyEntry.name,
                    varietyEntry.displayName,
                    locale,
                ) ?: varietyEntry.displayName
                CoffeeInputSuggestion(
                    value = displayName,
                    aliases = varietyEntry.searchAliases + varietyEntry.displayName,
                )
            } + vocabulary.varieties.map { it.toInputSuggestion() },
        )
    }
    val processSuggestions = remember(recentProcesses, vocabulary, locale) {
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentProcesses,
            libraryValues = CoffeeProcessType.Known.entries.map { process ->
                val displayName = CoffeeMetadataNormalizer.displayProcessType(
                    process.name,
                    process.displayName,
                    locale,
                ) ?: process.displayName
                CoffeeInputSuggestion(
                    value = displayName,
                    aliases = process.searchAliases + process.displayName,
                )
            } + vocabulary.processTypes.map { it.toInputSuggestion() },
        )
    }
    val tastingNoteSuggestions = remember(recentTastingNotes, vocabulary) {
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentTastingNotes,
            libraryValues = vocabulary.tastingNotes.map { it.toInputSuggestion() },
        )
    }

    val isEditMode = bagToEdit != null
    val correctionScope = rememberCoroutineScope()
    val initialForm = remember(
        ocrPrefill,
        initialName,
        initialRoaster,
        initialBarcode,
        bagToEdit,
        editBagMetadata,
        initialFormOverride,
    ) {
        initialFormOverride ?: BagFormSnapshot(
            name = bagToEdit?.name ?: ocrPrefill?.name ?: initialName ?: "",
            roaster = bagToEdit?.roaster ?: ocrPrefill?.roaster ?: initialRoaster ?: "",
            originCountry = editBagMetadata?.origin ?: ocrPrefill?.origin ?: "",
            originRegion = editBagMetadata?.region ?: ocrPrefill?.region ?: "",
            farm = bagToEdit?.farm ?: ocrPrefill?.farm ?: "",
            altitude = bagToEdit?.altitude ?: ocrPrefill?.altitude ?: "",
            roastLevel = editBagMetadata?.roastLevel ?: ocrPrefill?.roastLevel ?: "",
            variety = editBagMetadata?.variety ?: ocrPrefill?.variety ?: "",
            processType = editBagMetadata?.processType ?: ocrPrefill?.processType ?: "",
            tastingNotes = editBagMetadata?.tastingNotes ?: ocrPrefill?.tastingNotes ?: "",
            barcode = bagToEdit?.barcode ?: initialBarcode.orEmpty(),
            weight = bagToEdit?.weightG?.let { "%.0f".format(it) } ?: ocrPrefill?.weight ?: "",
            notes = bagToEdit?.notes ?: "",
            isDecaf = bagToEdit?.isDecaf ?: ocrPrefill?.isDecaf ?: false,
            decafProcess = bagToEdit?.decafProcess,
            roastDateMillis = bagToEdit?.roastDate ?: ocrPrefill?.roastDate?.let { DateParser.parse(it) },
            expiryDateMillis = bagToEdit?.expiryDate ?: ocrPrefill?.expiryDate?.let { DateParser.parse(it) },
        )
    }
    var name by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.name)
    }
    var roaster by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.roaster)
    }
    var originCountry by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.originCountry)
    }
    var originRegion by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.originRegion)
    }
    var farm by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.farm)
    }
    var altitude by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.altitude)
    }
    var roastLevel by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.roastLevel)
    }
    var variety by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.variety)
    }
    var processType by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.processType)
    }
    var tastingNotes by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.tastingNotes)
    }
    var barcode by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.barcode)
    }
    var weight by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.weight)
    }
    var notes by rememberSaveable(bagToEdit) { mutableStateOf(initialForm.notes) }
    var isDecaf by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.isDecaf)
    }
    var decafProcess by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.decafProcess)
    }
    var roastDateMillis by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.roastDateMillis)
    }
    var expiryDateMillis by rememberSaveable(bagToEdit) {
        mutableStateOf(initialForm.expiryDateMillis)
    }
    val regionSuggestions = remember(
        originCountry,
        recentRegions,
        localizedExistingMetadata,
        vocabulary,
        locale,
    ) {
        val canonicalOrigin = CoffeeInputSuggestionEngine.resolveCanonicalOrigin(
            input = originCountry,
            vocabularyOrigins = vocabulary.origins,
            locale = locale,
        )
        val recentForOrigin = if (canonicalOrigin == null) {
            recentRegions
        } else {
            localizedExistingMetadata
                .filter { (bag, _) ->
                    CoffeeInputSuggestionEngine.resolveCanonicalOrigin(
                        input = bag.origin.orEmpty(),
                        vocabularyOrigins = vocabulary.origins,
                        locale = locale,
                    )?.equals(canonicalOrigin, ignoreCase = true) == true
                }
                .mapNotNull { (_, metadata) -> metadata.region }
                .distinct()
        }
        val knownRegions = if (canonicalOrigin == null) {
            CoffeeRegion.Known.entries.toList()
        } else {
            CoffeeRegion.Known.entries.filter { region ->
                region.countries.any { it.equals(canonicalOrigin, ignoreCase = true) }
            }
        }
        val vocabularyRegions = if (canonicalOrigin == null) {
            vocabulary.regions
        } else {
            vocabulary.regions.filter {
                it.country == null || it.country.equals(canonicalOrigin, ignoreCase = true)
            }
        }
        CoffeeInputSuggestionEngine.merge(
            recentValues = recentForOrigin,
            libraryValues = knownRegions.map { region ->
                val displayName = CoffeeMetadataNormalizer.displayRegion(
                    region.name,
                    region.displayName,
                    locale,
                ) ?: region.displayName
                CoffeeInputSuggestion(
                    value = displayName,
                    aliases = region.searchAliases + region.displayName,
                )
            } + vocabularyRegions.map { it.toInputSuggestion() },
        )
    }
    var showMoreDetails by rememberSaveable(bagToEdit) { mutableStateOf(initialReviewLocation.showMoreDetails) }
    var expandedFields by rememberSaveable(bagToEdit) { mutableStateOf(initialReviewLocation.expandedFields) }
    var editedFields by rememberSaveable(bagToEdit) { mutableStateOf(emptyList<String>()) }
    var decafWasCleared by rememberSaveable(bagToEdit) { mutableStateOf("isDecaf" in unknownFields) }
    var saveAttempted by rememberSaveable(bagToEdit) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSourceNotes by rememberSaveable { mutableStateOf(false) }
    var isExploringQr by remember { mutableStateOf(false) }
    var reviewActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { reviewActive = false } }
    var qrExploredMetadata by rememberSaveable(stateSaver = QrCoffeeMetadataSaver) { mutableStateOf<QrCoffeeMetadata?>(null) }
    var pendingDiscardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDraftDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialReviewLocation.scrollIndex, initialReviewLocation.scrollOffset)
    val latestLocationChange by rememberUpdatedState(onReviewLocationChange)
    LaunchedEffect(listState) {
        if (onReviewLocationChange != null) {
            snapshotFlow {
                if (listState.isScrollInProgress) null else BagDraftReviewLocation(
                    listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                    expandedFields, showMoreDetails,
                )
            }.filterNotNull().distinctUntilChanged().collect { latestLocationChange?.invoke(it) }
        }
    }
    val nameFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    val weightValidation = remember(weight) { validateBagWeightInput(weight) }
    val currentForm = BagFormSnapshot(
        name = name,
        roaster = roaster,
        originCountry = originCountry,
        originRegion = originRegion,
        farm = farm,
        altitude = altitude,
        roastLevel = roastLevel,
        variety = variety,
        processType = processType,
        tastingNotes = tastingNotes,
        barcode = barcode,
        weight = weight,
        notes = notes,
        isDecaf = isDecaf,
        decafProcess = decafProcess,
        roastDateMillis = roastDateMillis,
        expiryDateMillis = expiryDateMillis,
    )
    val latestForm by rememberUpdatedState(currentForm)
    val latestSaving by rememberUpdatedState(isSaving)
    var previousEnrichmentForm by remember(bagToEdit) { mutableStateOf(initialForm) }
    LaunchedEffect(initialForm, isEditMode, isSaving) {
        if (isEditMode || isSaving) return@LaunchedEffect
        val merged = mergeBagFormEnrichment(
            current = currentForm,
            previousEnrichment = previousEnrichmentForm,
            incomingEnrichment = initialForm,
            protectedFields = (editedFields + reviewedFields).toSet(),
        )
        name = merged.name
        roaster = merged.roaster
        originCountry = merged.originCountry
        originRegion = merged.originRegion
        farm = merged.farm
        altitude = merged.altitude
        roastLevel = merged.roastLevel
        variety = merged.variety
        processType = merged.processType
        tastingNotes = merged.tastingNotes
        barcode = merged.barcode
        weight = merged.weight
        isDecaf = merged.isDecaf
        roastDateMillis = merged.roastDateMillis
        expiryDateMillis = merged.expiryDateMillis
        previousEnrichmentForm = initialForm
    }
    val confirmDismiss = shouldConfirmBagDismiss(isEditMode, initialForm, currentForm,
        !capturedPhotoUris.isNullOrBlank(), !traceabilityUrl.isNullOrBlank() || qrExploredMetadata != null)
    fun requestPotentiallyDestructiveAction(action: () -> Unit) {
        if (confirmDismiss) pendingDiscardAction = action else action()
    }
    fun requestDismiss() {
        if (preserveDraftOnDismiss) {
            onReviewLocationChange?.invoke(BagDraftReviewLocation(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, expandedFields, showMoreDetails,
            ))
            reviewActive = false
            onDismiss()
        } else requestPotentiallyDestructiveAction(onDismiss)
    }
    fun reportFocus(fieldName: String, focused: Boolean) { onFieldFocusChange?.invoke(fieldName, focused) }
    fun changeField(fieldName: String, value: String?) {
        when (fieldName) {
            "name" -> name = value.orEmpty()
            "roaster" -> roaster = value.orEmpty()
            "origin" -> originCountry = value.orEmpty()
            "region" -> originRegion = value.orEmpty()
            "farm" -> farm = value.orEmpty()
            "altitude" -> altitude = value.orEmpty()
            "roastLevel" -> roastLevel = value.orEmpty()
            "variety" -> variety = value.orEmpty()
            "processType" -> processType = value.orEmpty()
            "tastingNotes" -> tastingNotes = value.orEmpty()
            "barcode" -> barcode = value.orEmpty()
            "weight" -> weight = value.orEmpty()
            "notes" -> notes = value.orEmpty()
            "isDecaf" -> {
                isDecaf = value == "true"
                decafWasCleared = value.isNullOrBlank()
            }
            "decafProcess" -> decafProcess = value
            "roastDate" -> roastDateMillis = value?.toLongOrNull() ?: value?.let(DateParser::parse)
            "expiryDate" -> expiryDateMillis = value?.toLongOrNull() ?: value?.let(DateParser::parse)
        }
        if (fieldName !in editedFields) editedFields = editedFields + fieldName
        onUserFieldChange?.invoke(fieldName, value)
    }
    fun needsCheck(key: String, value: String): Boolean = labelReviewNeedsCheck(
        value, fieldEvidence[key], pendingSuggestions[key],
        if (key in editedFields || key in reviewedFields) false else null,
    )
    val decafIsKnown = when {
        "isDecaf" in editedFields -> !decafWasCleared
        "isDecaf" in reviewedFields -> "isDecaf" !in unknownFields
        else -> fieldEvidence["isDecaf"]?.let { it.confidence == BagFieldConfidence.HIGH && it.value == "false" } == true
    }
    fun accept(key: String): Boolean {
        val proposed = pendingSuggestions[key] ?: return false
        if (onAcceptSuggestion?.invoke(key, proposed) != true) return false
        changeField(key, proposed)
        return true
    }
    fun expand(key: String, expanded: Boolean) {
        focusManager.clearFocus()
        expandedFields = if (expanded) expandedFields + key else expandedFields - key
    }
    LaunchedEffect(isSaving) { if (isSaving) { focusManager.clearFocus(force = true); pendingDiscardAction = null } }
    val fields = listOf(
        LabelFormField("name", R.string.label_name_required, name, nameSuggestions),
        LabelFormField("roaster", R.string.label_roaster, roaster, roasterSuggestions),
        LabelFormField("origin", R.string.label_origin, originCountry, originSuggestions),
        LabelFormField("region", R.string.label_region, originRegion, regionSuggestions),
    )
    val optionalFields = listOf(
        LabelFormField("roastLevel", R.string.label_roast_level, roastLevel, roastLevelSuggestions, true),
        LabelFormField("variety", R.string.label_variety, variety, varietySuggestions, true),
        LabelFormField("farm", R.string.label_farm, farm, farmSuggestions),
        LabelFormField("altitude", R.string.label_altitude, altitude, altitudeSuggestions),
        LabelFormField("processType", R.string.label_process, processType, processSuggestions),
        LabelFormField("tastingNotes", R.string.label_tasting_notes, tastingNotes, tastingNoteSuggestions, true),
        LabelFormField("barcode", R.string.label_barcode_ean, barcode),
        LabelFormField("notes", R.string.label_notes, notes),
    )
    val optionalChecks = optionalFields.count { needsCheck(it.key, it.value) } +
        listOf("expiryDate", "isDecaf").count { key ->
            needsCheck(key, if (key == "expiryDate") expiryDateMillis?.let(DateParser::format).orEmpty()
                else if (isDecaf) "true" else "")
        }
    val visibleKeys = listOf("photos") + fields.map { it.key } + listOf("weight", "roastDate", "more") +
        (if (showMoreDetails) optionalFields.map { it.key } + listOf("expiryDate", "isDecaf", "sourceNotes") else emptyList())

    Dialog(onDismissRequest = { if (!isSaving) requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false,
            dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        val darkSystemBarIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        LaunchedEffect(dialogView, darkSystemBarIcons) {
            // Apply after the host and dialog window have committed a theme change.
            withFrameNanos { }
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = darkSystemBarIcons
                    isAppearanceLightNavigationBars = darkSystemBarIcons
                }
            }
        }
        BackHandler { if (!isSaving) requestDismiss() }
        if (pendingDiscardAction != null) {
            DestructiveActionDialog(R.string.dialog_discard_changes_title, R.string.action_discard,
                messageRes = R.string.msg_discard_changes_body,
                onConfirm = { val action = pendingDiscardAction; pendingDiscardAction = null; action?.invoke() },
                onDismiss = { pendingDiscardAction = null })
        }
        if (showDiscardDraftDialog && onDiscardDraft != null) {
            DestructiveActionDialog(R.string.dialog_discard_scan_title, R.string.action_discard,
                messageRes = R.string.msg_discard_scan_body,
                onConfirm = { showDiscardDraftDialog = false; onDiscardDraft() },
                onDismiss = { showDiscardDraftDialog = false })
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                TopAppBar(title = {
                    Text(stringResource(if (isEditMode) R.string.screen_edit_coffee_title else R.string.screen_add_coffee_title),
                        modifier = Modifier.semantics { heading() })
                }, navigationIcon = {
                    IconButton(onClick = ::requestDismiss, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }, actions = {
                    if (onDiscardDraft != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }, enabled = !isSaving) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.nav_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_discard)) },
                                    onClick = { showMenu = false; showDiscardDraftDialog = true })
                            }
                        }
                    }
                })
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).testTag("label-review-list"), state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item(key = "photos") {
                        CapturedPhotoReviewStrip(capturedPhotoUris, !isSaving, onAddPhoto = onScanMorePhotos?.let { addPhoto ->
                            {
                                if (preserveDraftOnDismiss) addPhoto() else requestPotentiallyDestructiveAction(addPhoto)
                            }
                        })
                        RecognitionStatusCard(
                            recognition.copy(status = when {
                                isProcessing && recognition.status == null ->
                                    com.adsamcik.starlitcoffee.util.RecognitionStatusText.CHECKING_LABEL
                                recognition.status == com.adsamcik.starlitcoffee.util.RecognitionStatusText.DETAILS_NEED_REVIEW -> null
                                else -> recognition.status
                            }, unresolvedCount = 0),
                            actions = RecognitionActions(onRetryLlmEnrichment, onEnableAi, onInstallLabelRecognition,
                                onSetupAi, onDisableLabelRecognition, onScanMorePhotos, setupLaunchState), enabled = !isSaving)
                    }
                    items(fields, key = { it.key }) { field ->
                        Box(Modifier.padding(top = if (field.key == "origin") 16.dp else 0.dp)) {
                            LabelReviewField(field.key, stringResource(field.labelRes), field.value,
                                expanded = field.key in expandedFields, onExpandedChange = { expand(field.key, it) }, enabled = !isSaving,
                                evidence = fieldEvidence[field.key], capturedPhotoUris = capturedPhotoUris,
                                suggestion = pendingSuggestions[field.key], onAcceptSuggestion = { accept(field.key) },
                                onKeepMine = { changeField(field.key, field.value) },
                                onClear = { changeField(field.key, "") }.takeIf { field.value.isNotBlank() || pendingSuggestions[field.key] != null },
                                needsReview = needsCheck(field.key, field.value), alwaysEditable = field.key in setOf("name", "roaster"),
                                shape = labelReviewGroupShape(first = field.key in setOf("name", "origin"), last = field.key == "roaster"),
                                error = stringResource(R.string.msg_review_name_required).takeIf { field.key == "name" && saveAttempted && name.isBlank() }) {
                                SuggestingTextField(field.value, { changeField(field.key, it) }, stringResource(field.labelRes), field.suggestions,
                                    enabled = !isSaving, modifier = if (field.key == "name") Modifier.focusRequester(nameFocus) else Modifier,
                                    isError = field.key == "name" && saveAttempted && name.isBlank(),
                                    filled = true, showLabel = field.key in setOf("name", "roaster"),
                                    textStyle = if (field.key == "name") MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                    onFocusChanged = { reportFocus(field.key, it) })
                            }
                        }
                    }
                    item(key = "weight") {
                        LabelReviewField("weight", stringResource(R.string.label_weight_grams), weight,
                            expanded = "weight" in expandedFields, onExpandedChange = { expand("weight", it) }, enabled = !isSaving,
                            evidence = fieldEvidence["weight"], capturedPhotoUris = capturedPhotoUris,
                            suggestion = pendingSuggestions["weight"], onAcceptSuggestion = { accept("weight") },
                            onKeepMine = { changeField("weight", weight) }, onClear = { changeField("weight", "") },
                            needsReview = needsCheck("weight", weight),
                            shape = labelReviewGroupShape(),
                            error = stringResource(R.string.msg_invalid_weight).takeUnless { weightValidation.isValid }) {
                            val weightLabel = stringResource(R.string.label_weight_grams)
                            TextField(weight, { changeField("weight", it) }, singleLine = true, enabled = !isSaving,
                                isError = !weightValidation.isValid,
                                trailingIcon = if (!weightValidation.isValid) ({ Icon(Icons.Outlined.WarningAmber, null) }) else null,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text("g") },
                                modifier = Modifier.fillMaxWidth().focusRequester(weightFocus)
                                    .semantics { contentDescription = weightLabel }.onFocusChanged { reportFocus("weight", it.isFocused) })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("250", "500", "1000").forEach { preset ->
                                    FilterChip(weight == preset, { changeField("weight", preset) }, enabled = !isSaving,
                                        label = { Text("$preset g") })
                                }
                            }
                        }
                    }
                    item(key = "roastDate") {
                        ReviewDateField("roastDate", R.string.label_roast_date, roastDateMillis, "roastDate" in expandedFields,
                            { expand("roastDate", it) }, !isSaving, fieldEvidence["roastDate"], capturedPhotoUris,
                            pendingSuggestions["roastDate"], { accept("roastDate") },
                            { changeField("roastDate", roastDateMillis?.toString()) }, { changeField("roastDate", it?.toString()) },
                            needsCheck("roastDate", roastDateMillis?.let(DateParser::format).orEmpty()), labelReviewGroupShape(last = true))
                    }
                    item(key = "more") {
                        TextButton(onClick = { focusManager.clearFocus(); showMoreDetails = !showMoreDetails }, enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp)) {
                            if (optionalChecks > 0) Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp))
                            Text(if (optionalChecks > 0) stringResource(R.string.format_review_more_details, optionalChecks)
                                else stringResource(if (showMoreDetails) R.string.action_hide_optional_details else R.string.action_show_optional_details))
                        }
                    }
                    if (showMoreDetails) {
                        items(optionalFields, key = { it.key }) { field ->
                            LabelReviewField(field.key, stringResource(field.labelRes), field.value,
                                expanded = field.key in expandedFields, onExpandedChange = { expand(field.key, it) }, enabled = !isSaving,
                                evidence = fieldEvidence[field.key], capturedPhotoUris = capturedPhotoUris,
                                suggestion = pendingSuggestions[field.key], onAcceptSuggestion = { accept(field.key) },
                                onKeepMine = { changeField(field.key, field.value) }, onClear = { changeField(field.key, "") },
                                shape = labelReviewGroupShape(first = field.key == optionalFields.first().key),
                                needsReview = needsCheck(field.key, field.value)) {
                                SuggestingTextField(field.value, { changeField(field.key, it) }, stringResource(field.labelRes),
                                    field.suggestions, multiValue = field.multiValue, singleLine = field.key != "notes", enabled = !isSaving,
                                    filled = true, showLabel = false,
                                    onFocusChanged = { reportFocus(field.key, it) })
                                if (field.key == "barcode" && onScanBarcode != null) {
                                    TextButton(onClick = { requestPotentiallyDestructiveAction(onScanBarcode) }, enabled = !isSaving) {
                                        Text(stringResource(R.string.action_scan_barcode))
                                    }
                                }
                            }
                        }
                        item(key = "expiryDate") {
                            ReviewDateField("expiryDate", R.string.label_best_before, expiryDateMillis, "expiryDate" in expandedFields,
                                { expand("expiryDate", it) }, !isSaving, fieldEvidence["expiryDate"], capturedPhotoUris,
                                pendingSuggestions["expiryDate"], { accept("expiryDate") },
                                { changeField("expiryDate", expiryDateMillis?.toString()) }, { changeField("expiryDate", it?.toString()) },
                                needsCheck("expiryDate", expiryDateMillis?.let(DateParser::format).orEmpty()))
                        }
                        item(key = "isDecaf") {
                            LabelReviewField("isDecaf", stringResource(R.string.label_decaf_coffee), when {
                                isDecaf -> stringResource(R.string.label_decaf)
                                decafIsKnown -> stringResource(R.string.label_review_not_decaf)
                                else -> ""
                            },
                                expanded = "isDecaf" in expandedFields, onExpandedChange = { expand("isDecaf", it) }, enabled = !isSaving,
                                evidence = fieldEvidence["isDecaf"], capturedPhotoUris = capturedPhotoUris,
                                suggestion = pendingSuggestions["isDecaf"], onAcceptSuggestion = { accept("isDecaf") },
                                onKeepMine = { changeField("isDecaf", isDecaf.toString().takeIf { isDecaf || decafIsKnown }) },
                                shape = labelReviewGroupShape(last = true),
                                needsReview = needsCheck("isDecaf", if (isDecaf) "true" else "")) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(null, "false", "true").forEach { choice ->
                                        val selected = when (choice) {
                                            "true" -> isDecaf
                                            "false" -> !isDecaf && decafIsKnown
                                            else -> !isDecaf && !decafIsKnown
                                        }
                                        FilterChip(selected, { changeField("isDecaf", choice) }, enabled = !isSaving,
                                            modifier = Modifier.testTag("label-review-decaf-${choice ?: "unknown"}"),
                                            leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                                            label = { Text(stringResource(when (choice) {
                                                "true" -> R.string.label_decaf
                                                "false" -> R.string.label_review_not_decaf
                                                else -> R.string.label_review_not_set
                                            })) })
                                    }
                                }
                                if (isDecaf) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DecafProcess.entries.forEach { process ->
                                        FilterChip(decafProcess == process.name || decafProcess == null && process == DecafProcess.UNKNOWN,
                                            { changeField("decafProcess", process.name.takeUnless { process == DecafProcess.UNKNOWN }) },
                                            enabled = !isSaving, label = { Text(process.shortLabel) })
                                    }
                                }
                            }
                        }
                        item(key = "sourceNotes") {
                            if (reviewHints.isNotEmpty() || traceabilityUrl != null) {
                                TextButton(onClick = { showSourceNotes = !showSourceNotes }, enabled = !isSaving) {
                                    Text(stringResource(R.string.label_review_source_notes))
                                }
                            }
                            if (showSourceNotes) {
                                reviewHints.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
                                traceabilityUrl?.let { url ->
                                    if (qrExploredMetadata == null) QrApprovalCard(url, isExploringQr, onExplore = {
                                        val explore = onExploreQrUrl ?: return@QrApprovalCard
                                        isExploringQr = true
                                        explore(url) qrResult@{ metadata ->
                                            if (!reviewActive) return@qrResult
                                            isExploringQr = false
                                            qrExploredMetadata = metadata
                                            // Optional lookup cannot replace a displayed or explicitly cleared field.
                                            if (metadata != null && !latestSaving) {
                                                val values = mapOf("name" to metadata.name, "roaster" to metadata.roaster,
                                                    "origin" to metadata.origin, "region" to metadata.region,
                                                    "processType" to metadata.processType, "tastingNotes" to metadata.tastingNotes)
                                                values.forEach { (key, value) ->
                                                    val untouched = key !in editedFields && key !in reviewedFields
                                                    if (value != null && untouched && latestForm.reviewValue(key).isNullOrBlank()) {
                                                        changeField(key, value)
                                                    }
                                                }
                                            }
                                        }
                                    }, onSkip = { showSourceNotes = false })
                                    else QrLinkCard(url, onOpen = { uriHandler.openUri(url) },
                                        exploredLabel = stringResource(R.string.msg_review_qr_details, URL(url).host))
                                }
                            }
                        }
                    }
                }
                saveErrorResId?.let { error ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                            .padding(12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(stringResource(error), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                    Button(
                    onClick = {
                        saveAttempted = true
                        val invalidField = when {
                            name.isBlank() -> "name"
                            !weightValidation.isValid -> "weight"
                            else -> null
                        }
                        if (invalidField != null) {
                            if (invalidField !in expandedFields) expandedFields = expandedFields + invalidField
                            correctionScope.launch {
                                listState.animateScrollToItem(visibleKeys.indexOf(invalidField).coerceAtLeast(0))
                                if (invalidField == "name") nameFocus.requestFocus() else weightFocus.requestFocus()
                            }
                            return@Button
                        }
                        if (name.isNotBlank()) {
                            if (ocrPrefill != null) {
                                // Phase 3 — capture a real-world model-vs-user diff for
                                // this scanned bag. Opt-in + on-device; the recorder is
                                // a no-op unless the user enabled correction logging.
                                val prefill = ocrPrefill
                                val modelValues = mapOf(
                                    "name" to prefill.name,
                                    "roaster" to prefill.roaster,
                                    "origin" to prefill.origin,
                                    "region" to prefill.region,
                                    "farm" to prefill.farm,
                                    "altitude" to prefill.altitude,
                                    "variety" to prefill.variety,
                                    "processType" to prefill.processType,
                                    "roastLevel" to prefill.roastLevel,
                                    "tastingNotes" to prefill.tastingNotes,
                                )
                                val finalValues = mapOf(
                                    "name" to name,
                                    "roaster" to roaster,
                                    "origin" to originCountry,
                                    "region" to originRegion,
                                    "farm" to farm,
                                    "altitude" to altitude,
                                    "variety" to variety,
                                    "processType" to processType,
                                    "roastLevel" to roastLevel,
                                    "tastingNotes" to tastingNotes,
                                )
                                val corrections = ScanCorrectionLog.buildCorrections(
                                    modelValues = modelValues,
                                    finalValues = finalValues,
                                    confidence = prefill.fieldConfidence,
                                )
                                val appContext = correctionContext.applicationContext
                                correctionScope.launch { ScanCorrectionLog.record(appContext, corrections) }
                            }
                            if (bagToEdit != null && onEdit != null) {
                                val editedBag = applyValidatedBagWeight(
                                    bag = bagToEdit.copy(
                                        name = name,
                                        roaster = roaster.takeIf { it.isNotBlank() },
                                        origin = originCountry.takeIf { it.isNotBlank() },
                                        region = originRegion.takeIf { it.isNotBlank() },
                                        farm = farm.takeIf { it.isNotBlank() },
                                        altitude = altitude.takeIf { it.isNotBlank() },
                                        roastLevel = roastLevel.takeIf { it.isNotBlank() },
                                        barcode = barcode.takeIf { it.isNotBlank() },
                                        notes = notes.takeIf { it.isNotBlank() },
                                        variety = variety.takeIf { it.isNotBlank() },
                                        processType = processType.takeIf { it.isNotBlank() },
                                        tastingNotes = tastingNotes.takeIf { it.isNotBlank() },
                                        isDecaf = isDecaf,
                                        decafProcess = decafProcess?.takeIf { isDecaf },
                                        roastDate = roastDateMillis,
                                        expiryDate = expiryDateMillis,
                                    ),
                                    validation = weightValidation,
                                ) ?: return@Button
                                onEdit(
                                    editedBag,
                                )
                            } else {
                                onSave(
                                    name,
                                    roaster.takeIf { it.isNotBlank() },
                                    originCountry.takeIf { it.isNotBlank() },
                                    originRegion.takeIf { it.isNotBlank() },
                                    farm.takeIf { it.isNotBlank() },
                                    altitude.takeIf { it.isNotBlank() },
                                    roastLevel.takeIf { it.isNotBlank() },
                                    barcode.takeIf { it.isNotBlank() },
                                    weightValidation.valueGrams,
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
                    shape = MaterialTheme.shapes.large,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("label-review-save")
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 12.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(when {
                        isSaving -> R.string.label_review_saving
                        saveErrorResId != null -> R.string.action_review_retry_save
                        isEditMode -> R.string.action_save
                        else -> R.string.action_save_coffee
                    }), style = MaterialTheme.typography.labelLarge)
                }

            }
        }
    }
}

private data class LabelFormField(
    val key: String,
    val labelRes: Int,
    val value: String,
    val suggestions: List<CoffeeInputSuggestion> = emptyList(),
    val multiValue: Boolean = false,
)

private fun labelReviewGroupShape(first: Boolean = false, last: Boolean = false): Shape = RoundedCornerShape(
    topStart = if (first) 28.dp else 4.dp,
    topEnd = if (first) 28.dp else 4.dp,
    bottomStart = if (last) 28.dp else 4.dp,
    bottomEnd = if (last) 28.dp else 4.dp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDateField(
    key: String, labelRes: Int, millis: Long?, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean, evidence: BagFieldEvidence?, photoUris: String?, suggestion: String?, onAccept: () -> Boolean,
    onKeep: () -> Unit, onChange: (Long?) -> Unit, needsReview: Boolean, shape: Shape = labelReviewGroupShape(),
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    LabelReviewField(key, stringResource(labelRes), millis?.let(DateParser::format).orEmpty(), expanded, onExpandedChange,
        enabled, evidence, photoUris, suggestion, onAccept, onKeep, { onChange(null) }, needsReview = needsReview, shape = shape) {
        TextButton(onClick = { picking = true }, enabled = enabled, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DateRange, null)
                Text(millis?.let(DateParser::format) ?: stringResource(R.string.action_review_choose_date))
            }
        }
    }
    if (picking) {
        val date = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { onChange(date.selectedDateMillis); picking = false }) {
                Text(stringResource(R.string.action_ok))
            } }, dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } }) {
            DatePicker(date)
        }
    }
}

@Composable
private fun CapturedPhotoReviewStrip(capturedPhotoUris: String?, enabled: Boolean, onAddPhoto: (() -> Unit)?) {
    val photoUris = remember(capturedPhotoUris) {
        BagPhotoReviewUris.parse(capturedPhotoUris)
    }
    if (photoUris.isEmpty()) {
        onAddPhoto?.let { addPhoto ->
            TextButton(enabled = enabled, onClick = addPhoto) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.padding(end = 8.dp).size(20.dp))
                Text(stringResource(R.string.action_review_add_photo))
            }
        }
        return
    }

    var fullScreenPhotoUri by remember(capturedPhotoUris) { mutableStateOf<String?>(null) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(items = photoUris, key = { _, uri -> uri }) { index, uri ->
                BagThumbnail(
                    uri = uri,
                    modifier = Modifier
                        .size(88.dp)
                        .clickable { fullScreenPhotoUri = uri },
                    downsampleTarget = 88.dp,
                    shape = MaterialTheme.shapes.medium,
                    contentDescription = when (index) {
                        0 -> stringResource(R.string.cd_front_label_photo)
                        1 -> stringResource(R.string.cd_back_label_photo)
                        else -> stringResource(R.string.format_cd_additional_label_photo, index - 1)
                    },
                )
            }
        }
        onAddPhoto?.let { addPhoto ->
            OutlinedIconButton(onClick = addPhoto, enabled = enabled, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(88.dp)) {
                Icon(Icons.Outlined.AddAPhoto, stringResource(R.string.action_review_add_photo))
            }
        }
    }
    fullScreenPhotoUri?.let { uri ->
        FullScreenImageViewer(uri = uri) {
            fullScreenPhotoUri = null
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
                text = stringResource(R.string.msg_review_qr_approval),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isExploring) {
                LoadingIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onExplore, colors = primaryActionButtonColors()) {
                        Text(stringResource(R.string.action_explore_extract))
                    }
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }
        }
    }
}
