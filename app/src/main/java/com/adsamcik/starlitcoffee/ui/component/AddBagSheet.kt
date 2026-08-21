package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.scan.observability.ScanCorrectionLog
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
import com.adsamcik.starlitcoffee.util.BagPhotoReviewUris
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.CoffeeFilterVocabularyLoader
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestion
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestionEngine
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.CoffeeVocabularyEntry
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.RecognitionOffer
import com.adsamcik.starlitcoffee.util.RecognitionPresentation
import com.adsamcik.starlitcoffee.util.RecognitionRecoveryAction
import com.adsamcik.starlitcoffee.util.RecognitionStatusText
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onDisableLabelRecognition: (() -> Unit)? = null,
    onScanMorePhotos: (() -> Unit)? = null,
    initialFormOverride: BagFormSnapshot? = null,
    onUserFieldChange: ((fieldName: String, value: String?) -> Unit)? = null,
    onFieldFocusChange: ((fieldName: String, focused: Boolean) -> Unit)? = null,
    pendingSuggestions: Map<String, String> = emptyMap(),
    onAcceptSuggestion: ((fieldName: String) -> Unit)? = null,
    preserveDraftOnDismiss: Boolean = false,
    onDiscardDraft: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val correctionContext = LocalContext.current
    val suggestionSnackbarHostState = remember { SnackbarHostState() }
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
    var showMoreDetails by rememberSaveable(bagToEdit) { mutableStateOf(isEditMode || !isProcessing) }
    var selectedEvidenceField by rememberSaveable(fieldEvidence) { mutableStateOf(fieldEvidence.keys.firstOrNull()) }
    var snapApproveMode by rememberSaveable(bagToEdit) {
        mutableStateOf(false)
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
    var pendingDiscardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDraftDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pendingSuggestion = pendingSuggestions.entries.firstOrNull()
    val pendingSuggestionFieldLabel = pendingSuggestion?.key?.let { fieldName ->
        localizedBagFieldLabel(fieldName)
    }
    val pendingSuggestionMessage = pendingSuggestion?.let { suggestion ->
        pendingSuggestionFieldLabel?.let { fieldLabel ->
            stringResource(
                R.string.format_new_label_suggestion,
                fieldLabel,
                suggestion.value,
            )
        }
    }
    val applySuggestionLabel = stringResource(R.string.action_apply_suggestion)
    LaunchedEffect(pendingSuggestion, pendingSuggestionMessage) {
        val suggestion = pendingSuggestion ?: return@LaunchedEffect
        val message = pendingSuggestionMessage ?: return@LaunchedEffect
        val result = suggestionSnackbarHostState.showSnackbar(
            message = message,
            actionLabel = applySuggestionLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onAcceptSuggestion?.invoke(suggestion.key)
        }
    }
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
    var previousEnrichmentForm by remember(bagToEdit) { mutableStateOf(initialForm) }
    LaunchedEffect(initialForm, isEditMode, isSaving) {
        if (isEditMode || isSaving) return@LaunchedEffect
        val merged = mergeBagFormEnrichment(
            current = currentForm,
            previousEnrichment = previousEnrichmentForm,
            incomingEnrichment = initialForm,
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
    val confirmDismiss = shouldConfirmBagDismiss(
        isEditMode = isEditMode,
        initial = initialForm,
        current = currentForm,
        hasCapturedPhotos = !capturedPhotoUris.isNullOrBlank(),
        hasTraceabilityData = !traceabilityUrl.isNullOrBlank() || qrExploredMetadata != null,
    )
    fun requestPotentiallyDestructiveAction(action: () -> Unit) {
        if (confirmDismiss) pendingDiscardAction = action else action()
    }
    fun requestDismiss() {
        if (preserveDraftOnDismiss) onDismiss() else requestPotentiallyDestructiveAction(onDismiss)
    }
    fun reportUserEdit(fieldName: String, value: String?) {
        onUserFieldChange?.invoke(fieldName, value)
    }
    fun reportFocus(fieldName: String, focused: Boolean) {
        onFieldFocusChange?.invoke(fieldName, focused)
        if (focused) selectedEvidenceField = fieldName
    }

    LaunchedEffect(isSaving) {
        if (isSaving) {
            focusManager.clearFocus(force = true)
            pendingDiscardAction = null
        }
    }

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
            farm = farm,
            altitude = altitude,
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
    val saveButtonLabel = stringResource(
        if (isEditMode) R.string.action_save else R.string.action_save_coffee,
    )
    val saveButtonColors = if (snapApproveMode && reviewDetectedFields.isEmpty() && snapDetectedFields.isNotEmpty()) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else {
        primaryActionButtonColors()
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

    Dialog(
        onDismissRequest = {
            if (!isSaving) requestDismiss()
        },
        // Full-screen create/edit task (Material full-screen dialog). Unlike the
        // previous bottom sheet it cannot be swipe- or tap-outside-dismissed; an
        // intentional back press still closes it via the BackHandler below.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        if (pendingDiscardAction != null) {
            DestructiveActionDialog(
                titleRes = R.string.dialog_discard_changes_title,
                messageRes = R.string.msg_discard_changes_body,
                confirmLabelRes = R.string.action_discard,
                onConfirm = {
                    val action = pendingDiscardAction
                    pendingDiscardAction = null
                    action?.invoke()
                },
                onDismiss = { pendingDiscardAction = null },
            )
        }
        if (showDiscardDraftDialog && onDiscardDraft != null) {
            DestructiveActionDialog(
                titleRes = R.string.dialog_discard_scan_title,
                messageRes = R.string.msg_discard_scan_body,
                confirmLabelRes = R.string.action_discard,
                onConfirm = {
                    showDiscardDraftDialog = false
                    onDiscardDraft()
                },
                onDismiss = { showDiscardDraftDialog = false },
            )
        }
        BackHandler {
            if (!isSaving) requestDismiss()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .focusProperties { canFocus = !isSaving }
                    .then(
                        if (isSaving) {
                            Modifier.clearAndSetSemantics { disabled() }
                        } else {
                            Modifier
                        },
                    ),
            ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when {
                                isEditMode -> R.string.screen_edit_coffee_title
                                preserveDraftOnDismiss || capturedPhotoUris != null || ocrPrefill != null ->
                                    R.string.screen_review_coffee_title
                                else -> R.string.screen_add_coffee_title
                            },
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestDismiss, enabled = !isSaving) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                actions = {
                    if (onDiscardDraft != null) {
                        IconButton(
                            onClick = { showDiscardDraftDialog = true },
                            enabled = !isSaving,
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.action_discard),
                            )
                        }
                    }
                    if (onScanMorePhotos != null) {
                        IconButton(
                            onClick = {
                                if (preserveDraftOnDismiss) {
                                    onScanMorePhotos()
                                } else {
                                    requestPotentiallyDestructiveAction(onScanMorePhotos)
                                }
                            },
                            enabled = !isSaving,
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = stringResource(R.string.guided_scan_scan_more),
                            )
                        }
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                state = listState,
            ) {
                item {
                    CapturedPhotoReviewStrip(capturedPhotoUris)
                    RecognitionStatusRow(
                        presentation = recognition,
                        onRetry = onRetryLlmEnrichment,
                        onEnable = onEnableAi,
                        onInstall = onInstallLabelRecognition,
                        onSetup = onSetupAi,
                        onDisable = onDisableLabelRecognition,
                        onRetake = onScanMorePhotos,
                    )
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
                                                        if (name.isBlank() && metadata.name != null) {
                                                            name = metadata.name
                                                            reportUserEdit("name", metadata.name)
                                                        }
                                                        if (roaster.isBlank() && metadata.roaster != null) {
                                                            roaster = metadata.roaster
                                                            reportUserEdit("roaster", metadata.roaster)
                                                        }
                                                        if (originCountry.isBlank() && metadata.origin != null) {
                                                            originCountry = metadata.origin
                                                            reportUserEdit("origin", metadata.origin)
                                                        }
                                                        if (originRegion.isBlank() && metadata.region != null) {
                                                            originRegion = metadata.region
                                                            reportUserEdit("region", metadata.region)
                                                        }
                                                        if (processType.isBlank() && metadata.processType != null) {
                                                            processType = metadata.processType
                                                            reportUserEdit("processType", metadata.processType)
                                                        }
                                                        if (tastingNotes.isBlank() && metadata.tastingNotes != null) {
                                                            tastingNotes = metadata.tastingNotes
                                                            reportUserEdit("tastingNotes", metadata.tastingNotes)
                                                        }
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

                if (snapApproveMode) {
                    item {
                        SnapApproveSection(
                            name = name,
                            onNameChange = {
                                name = it
                                reportUserEdit("name", it)
                            },
                            nameSuggestions = nameSuggestions,
                            roaster = roaster,
                            onRoasterChange = {
                                roaster = it
                                reportUserEdit("roaster", it)
                            },
                            roasterSuggestions = roasterSuggestions,
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
                                onValueChange = {
                                    barcode = it
                                    reportUserEdit("barcode", it)
                                },
                                label = { Text(stringResource(R.string.label_barcode_ean)) },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = if (onScanBarcode != null) {
                                    {
                                        IconButton(
                                            onClick = { requestPotentiallyDestructiveAction(onScanBarcode) },
                                            enabled = !isSaving,
                                        ) {
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
                                onValueChange = {
                                    barcode = it
                                    reportUserEdit("barcode", it)
                                },
                                label = { Text(stringResource(R.string.label_barcode_ean)) },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { requestPotentiallyDestructiveAction(onScanBarcode) },
                                        enabled = !isSaving,
                                    ) {
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
                            onValueChange = {
                                name = it
                                reportUserEdit("name", it)
                            },
                            label = stringResource(R.string.label_name_required),
                            suggestions = nameSuggestions,
                            enabled = !isSaving,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                reportFocus("name", focused)
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
                            onValueChange = {
                                roaster = it
                                reportUserEdit("roaster", it)
                            },
                            label = stringResource(R.string.label_roaster),
                            suggestions = roasterSuggestions,
                            enabled = !isSaving,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                reportFocus("roaster", focused)
                            },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["roaster"],
                            onClick = { selectedEvidenceField = "roaster" },
                        )
                    }
                    item {
                        SuggestingTextField(
                            value = originCountry,
                            onValueChange = {
                                originCountry = it
                                reportUserEdit("origin", it)
                            },
                            label = stringResource(R.string.label_origin),
                            suggestions = originSuggestions,
                            enabled = !isSaving,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                reportFocus("origin", focused)
                            },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["origin"],
                            onClick = { selectedEvidenceField = "origin" },
                        )
                    }
                    item {
                        SuggestingTextField(
                            value = originRegion,
                            onValueChange = {
                                originRegion = it
                                reportUserEdit("region", it)
                            },
                            label = stringResource(R.string.label_region),
                            suggestions = regionSuggestions,
                            enabled = !isSaving,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onFocusChanged = { focused ->
                                reportFocus("region", focused)
                            },
                        )
                        FieldEvidenceAssist(
                            evidence = fieldEvidence["region"],
                            onClick = { selectedEvidenceField = "region" },
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = {
                                weight = it
                                reportUserEdit("weight", it)
                            },
                            label = { Text(stringResource(R.string.label_weight_grams)) },
                            shape = MaterialTheme.shapes.small,
                            singleLine = true,
                            enabled = !isSaving,
                            isError = !weightValidation.isValid,
                            supportingText = if (!weightValidation.isValid) {
                                {
                                    Text(stringResource(R.string.msg_invalid_weight))
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text("g") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .onFocusChanged {
                                    reportFocus("weight", it.isFocused)
                                },
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            listOf("250", "500", "1000").forEach { preset ->
                                FilterChip(
                                    selected = weight == preset,
                                    enabled = !isSaving,
                                    onClick = {
                                        selectedEvidenceField = "weight"
                                        weight = preset
                                        reportUserEdit("weight", preset)
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
                                    IconButton(onClick = {
                                        roastDateMillis = null
                                        reportUserEdit("roastDate", null)
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            stringResource(R.string.action_clear),
                                        )
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
                            Text(
                                stringResource(
                                    if (roastDateMillis != null) {
                                        R.string.action_change_roast_date
                                    } else {
                                        R.string.action_set_roast_date
                                    },
                                ),
                            )
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
                                        reportUserEdit("roastDate", roastDateMillis?.toString())
                                        showRoastDatePicker = false
                                    }) { Text(stringResource(R.string.action_ok)) }
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
                            Text(
                                stringResource(
                                    if (showMoreDetails) {
                                        R.string.action_hide_optional_details
                                    } else {
                                        R.string.action_show_optional_details
                                    },
                                ),
                            )
                        }
                    }

                    if (showMoreDetails) {
                        item {
                            SuggestingTextField(
                                value = roastLevel,
                                onValueChange = {
                                    roastLevel = it
                                    reportUserEdit("roastLevel", it)
                                },
                                label = stringResource(R.string.label_roast_level),
                                suggestions = roastLevelSuggestions,
                                enabled = !isSaving,
                                multiValue = true,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("roastLevel", focused)
                                },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["roastLevel"],
                                onClick = { selectedEvidenceField = "roastLevel" },
                            )
                        }
                        item {
                            SuggestingTextField(
                                value = variety,
                                onValueChange = {
                                    variety = it
                                    reportUserEdit("variety", it)
                                },
                                label = stringResource(R.string.label_variety),
                                suggestions = varietySuggestions,
                                multiValue = true,
                                enabled = !isSaving,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("variety", focused)
                                },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["variety"],
                                onClick = { selectedEvidenceField = "variety" },
                            )
                        }
                        item {
                            SuggestingTextField(
                                value = farm,
                                onValueChange = {
                                    farm = it
                                    reportUserEdit("farm", it)
                                },
                                label = stringResource(R.string.label_farm),
                                suggestions = farmSuggestions,
                                enabled = !isSaving,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("farm", focused)
                                },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["farm"],
                                onClick = { selectedEvidenceField = "farm" },
                            )
                        }
                        item {
                            SuggestingTextField(
                                value = altitude,
                                onValueChange = {
                                    altitude = it
                                    reportUserEdit("altitude", it)
                                },
                                label = stringResource(R.string.label_altitude),
                                suggestions = altitudeSuggestions,
                                enabled = !isSaving,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("altitude", focused)
                                },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["altitude"],
                                onClick = { selectedEvidenceField = "altitude" },
                            )
                        }
                        item {
                            SuggestingTextField(
                                value = processType,
                                onValueChange = {
                                    processType = it
                                    reportUserEdit("processType", it)
                                },
                                label = stringResource(R.string.label_process),
                                suggestions = processSuggestions,
                                enabled = !isSaving,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("processType", focused)
                                },
                            )
                            FieldEvidenceAssist(
                                evidence = fieldEvidence["processType"],
                                onClick = { selectedEvidenceField = "processType" },
                            )
                        }
                        item {
                            SuggestingTextField(
                                value = tastingNotes,
                                onValueChange = {
                                    tastingNotes = it
                                    reportUserEdit("tastingNotes", it)
                                },
                                label = stringResource(R.string.label_tasting_notes),
                                suggestions = tastingNoteSuggestions,
                                multiValue = true,
                                enabled = !isSaving,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onFocusChanged = { focused ->
                                    reportFocus("tastingNotes", focused)
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
                                        IconButton(onClick = {
                                            expiryDateMillis = null
                                            reportUserEdit("expiryDate", null)
                                        }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                stringResource(R.string.action_clear),
                                            )
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
                                Text(
                                    stringResource(
                                        if (expiryDateMillis != null) {
                                            R.string.action_change_best_before
                                        } else {
                                            R.string.action_set_best_before
                                        },
                                    ),
                                )
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
                                            reportUserEdit("expiryDate", expiryDateMillis?.toString())
                                            showExpiryDatePicker = false
                                        }) { Text(stringResource(R.string.action_ok)) }
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
                                            onValueChange = {
                                                isDecaf = it
                                                reportUserEdit("isDecaf", it.toString())
                                            },
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
                                                        reportUserEdit("decafProcess", decafProcess)
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
                                onValueChange = {
                                    notes = it
                                    reportUserEdit("notes", it)
                                },
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
                colors = saveButtonColors,
                shape = MaterialTheme.shapes.large,
                enabled = name.isNotBlank() &&
                    weightValidation.isValid &&
                    !isExploringQr &&
                    !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(saveButtonLabel, style = MaterialTheme.typography.labelLarge)
            }
            }
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                        .clearAndSetSemantics { disabled() },
                )
            }
            SnackbarHost(
                hostState = suggestionSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 84.dp),
            )
        }
    }
}

@Composable
private fun localizedBagFieldLabel(fieldName: String): String = stringResource(
    when (fieldName) {
        "name" -> R.string.label_name_required
        "roaster" -> R.string.label_roaster
        "origin" -> R.string.label_origin
        "region" -> R.string.label_region
        "farm" -> R.string.label_farm
        "altitude" -> R.string.label_altitude
        "roastLevel" -> R.string.label_roast_level
        "barcode" -> R.string.label_barcode_ean
        "weight" -> R.string.label_weight_grams
        "notes" -> R.string.label_notes
        "variety" -> R.string.label_variety
        "processType" -> R.string.label_process
        "tastingNotes" -> R.string.label_tasting_notes
        "isDecaf" -> R.string.label_decaf_coffee
        "roastDate" -> R.string.label_roast_date
        "expiryDate" -> R.string.label_best_before
        else -> R.string.label_coffee
    },
)

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
    val farm: String,
    val altitude: String,
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
    snapApproveFieldItem("farm", form.farm, fieldEvidence, fieldConfidence),
    snapApproveFieldItem("altitude", form.altitude, fieldEvidence, fieldConfidence),
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
    "farm",
    "altitude",
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
                    "farm",
                    "altitude",
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
private fun CapturedPhotoReviewStrip(capturedPhotoUris: String?) {
    val photoUris = remember(capturedPhotoUris) {
        BagPhotoReviewUris.parse(capturedPhotoUris)
    }
    if (photoUris.isEmpty()) return

    var fullScreenPhotoUri by remember(capturedPhotoUris) { mutableStateOf<String?>(null) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        itemsIndexed(items = photoUris, key = { _, uri -> uri }) { index, uri ->
            BagThumbnail(
                uri = uri,
                modifier = Modifier
                    .size(168.dp)
                    .clickable { fullScreenPhotoUri = uri },
                downsampleTarget = 168.dp,
                shape = MaterialTheme.shapes.medium,
                contentDescription = when (index) {
                    0 -> stringResource(R.string.cd_front_label_photo)
                    1 -> stringResource(R.string.cd_back_label_photo)
                    else -> stringResource(R.string.format_cd_additional_label_photo, index - 1)
                },
            )
        }
    }
    fullScreenPhotoUri?.let { uri ->
        FullScreenImageViewer(uri = uri) {
            fullScreenPhotoUri = null
        }
    }
}

@Composable
private fun SnapApproveSection(
    name: String,
    onNameChange: (String) -> Unit,
    nameSuggestions: List<CoffeeInputSuggestion>,
    roaster: String,
    onRoasterChange: (String) -> Unit,
    roasterSuggestions: List<CoffeeInputSuggestion>,
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
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SuggestingTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.label_name_required),
                    suggestions = nameSuggestions,
                )
                SuggestingTextField(
                    value = roaster,
                    onValueChange = onRoasterChange,
                    label = stringResource(R.string.label_roaster),
                    suggestions = roasterSuggestions,
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
        label = { Text(evidence.localizedSummaryLabel()) },
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun RecognitionStatusRow(
    presentation: RecognitionPresentation,
    onRetry: (() -> Unit)?,
    onEnable: (() -> Unit)?,
    onInstall: (() -> Unit)?,
    onSetup: (() -> Unit)?,
    onDisable: (() -> Unit)?,
    onRetake: (() -> Unit)?,
) {
    val message = when (presentation.status) {
        RecognitionStatusText.CHECKING_LABEL -> stringResource(R.string.msg_checking_label)
        RecognitionStatusText.CHECKING_MORE_DETAILS -> stringResource(R.string.msg_checking_more_details)
        RecognitionStatusText.DETAILS_NEED_REVIEW -> pluralStringResource(
            R.plurals.format_label_details_need_review,
            presentation.unresolvedCount,
            presentation.unresolvedCount,
        )
        RecognitionStatusText.COULD_NOT_READ_MORE -> stringResource(R.string.msg_could_not_read_more_details)
        null -> null
    }
    if (message == null && presentation.offer == null && presentation.recoveryAction == null) return
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .semantics {
                if (presentation.announceUpdate) liveRegion = LiveRegionMode.Polite
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (presentation.offer != null) {
                Text(
                    text = stringResource(R.string.msg_label_recognition_offer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                presentation.offer == RecognitionOffer.FINISH_SETUP && onSetup != null -> {
                    TextButton(onClick = onSetup) {
                        Text(stringResource(R.string.action_finish_label_recognition_setup))
                    }
                }
                presentation.offer == RecognitionOffer.INSTALL && onInstall != null -> {
                    TextButton(onClick = onInstall) {
                        Text(stringResource(R.string.action_set_up_label_recognition))
                    }
                }
                presentation.offer == RecognitionOffer.ENABLE && onEnable != null -> {
                    TextButton(onClick = onEnable) {
                        Text(stringResource(R.string.action_use_label_recognition))
                    }
                }
                presentation.recoveryAction == RecognitionRecoveryAction.RETRY && onRetry != null -> {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_try_label_again))
                    }
                }
                presentation.recoveryAction == RecognitionRecoveryAction.RETAKE && onRetake != null -> {
                    TextButton(onClick = onRetake) {
                        Text(stringResource(R.string.action_retake_label_photo))
                    }
                }
            }
            if (presentation.offer != null && onDisable != null) {
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.action_always_enter_manually))
                }
            }
        }
    }
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
                text = stringResource(R.string.label_label_evidence),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = evidence.localizedSummaryLabel(),
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

@Composable
private fun BagFieldEvidence.localizedSummaryLabel(): String {
    val sourceLabel = stringResource(
        when (sourceType) {
            BagFieldSourceType.OCR -> R.string.evidence_from_label
            BagFieldSourceType.CONSENSUS -> R.string.evidence_confirmed_on_label
            BagFieldSourceType.QR_LINK_LOOKUP -> R.string.evidence_from_qr_website
            BagFieldSourceType.OBSERVED_BARCODE_STEM -> R.string.evidence_from_label_code
            BagFieldSourceType.LOCAL_BARCODE_MATCH -> R.string.evidence_from_saved_bag
            BagFieldSourceType.BARCODE_LOOKUP -> R.string.evidence_from_barcode_lookup
            BagFieldSourceType.LLM -> R.string.evidence_suggested_from_label_context
        },
    )
    val sideLabel = side?.let { side ->
        stringResource(
            if (side == BagCaptureSide.FRONT) {
                R.string.evidence_front_photo
            } else {
                R.string.evidence_back_photo
            },
        )
    }
    val reviewLabel = if (confidence == BagFieldConfidence.HIGH) {
        null
    } else {
        stringResource(R.string.label_needs_review)
    }
    return listOfNotNull(sourceLabel, sideLabel, reviewLabel).joinToString(" · ")
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
