package com.adsamcik.starlitcoffee.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.audio.BrewAudioManager
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.PhaseMode
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.data.network.QrLinkExploreResult
import com.adsamcik.starlitcoffee.data.network.QrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RatioPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.domain.TimerController
import com.adsamcik.starlitcoffee.service.TimerStateHolder
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoAnalysis
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.BarcodeInsights
import com.adsamcik.starlitcoffee.util.CoffeeCountryDictionaries
import com.adsamcik.starlitcoffee.util.CoffeeMetadataMatchStrategy
import com.adsamcik.starlitcoffee.util.CoffeeCountryDictionary
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.NormalizedCoffeeField
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class GrindResult {
    data class Generic(val descriptor: GrindDescriptor) : GrindResult()
    data class Specific(val recommendation: GrindRecommendation) : GrindResult()
}

data class BrewPhase(
    val name: String,
    val phaseType: PhaseType,
    val mode: PhaseMode,
    val waterG: Float,
    val cumulativeWaterG: Float,
    val durationSeconds: Int,
    val instruction: String = "",
    val valveState: String = "",
)

data class BrewUiState(
    val method: BrewMethod = BrewMethod.PULSAR,
    val inputMode: InputMode = InputMode.COFFEE_TO_WATER,
    val amount: String = "20",
    val ratioPresets: List<RatioPreset> = BrewMethod.PULSAR.defaultRatioPresets,
    val selectedPresetIndex: Int = BrewMethod.PULSAR.defaultRatioPresets.indexOfFirst { it.isDefault }.coerceAtLeast(0),
    val customRatio: String = "",
    val tempC: String = "",
    val bloomMultiplier: String = "",
    val pulseCount: String = "",
    val filterType: FilterType? = null,
    val selectedGrinderId: String? = null,
    val calibrationStyle: CalibrationStyle? = null,
    val showAdvanced: Boolean = false,
    // Computed results
    val coffeeG: Float = 0f,
    val waterG: Float = 0f,
    val effectiveRatio: Float = 0f,
    val bloomG: Float = 0f,
    val remainingWaterG: Float = 0f,
    val pulseSizeG: Float = 0f,
    val effectivePulseCount: Int = 0,
    val timeTargetLowS: Int = 0,
    val timeTargetHighS: Int = 0,
    val grindResult: GrindResult = GrindResult.Generic(GrindDescriptor.MEDIUM),
    val refillCount: Int = 0,
    val ratioWarning: String? = null,
    val bloomWarning: String? = null,
    // Timer state
    val timerPhases: List<BrewPhase> = emptyList(),
    val currentPhaseIndex: Int = 0,
    val timerRunning: Boolean = false,
    val elapsedSeconds: Int = 0,
    val phaseSecondsRemaining: Int = 0,
    val phaseOvertime: Boolean = false,
    val showNextPreview: Boolean = false,
    val showFeedbackSnackbar: Boolean = false,
    val lastDriftSeconds: Int = 0,
    // Decaf
    val isDecafBrew: Boolean = false,
    // Audio auto-advance
    val audioAutoAdvanceEnabled: Boolean = true,
    // Feedback state
    val tasteFeedback: TasteFeedback? = null,
    val rating: Int = 0,
    val feedbackNotes: String = "",
)

class BrewViewModel(
    private val recipeRepository: RecipeRepository? = null,
    private val brewLogRepository: BrewLogRepository? = null,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
    private val ratioPresetRepository: RatioPresetRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val grinderData: GrinderDataProvider = DefaultGrinders,
    private val qrLinkMetadataExplorer: QrLinkMetadataExplorer = SafeQrLinkMetadataExplorer(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrewUiState())
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()
    private val _savedRecipes = MutableStateFlow(emptyList<SavedRecipeEntity>())
    val savedRecipes: StateFlow<List<SavedRecipeEntity>> = _savedRecipes.asStateFlow()
    private val _brewLogs = MutableStateFlow(emptyList<BrewLogEntity>())
    val brewLogs: StateFlow<List<BrewLogEntity>> = _brewLogs.asStateFlow()
    private val _flavorTags = MutableStateFlow(emptyList<FlavorTagEntity>())
    val flavorTags: StateFlow<List<FlavorTagEntity>> = _flavorTags.asStateFlow()
    private val _coffeeBags = MutableStateFlow(emptyList<CoffeeBagEntity>())
    val coffeeBags: StateFlow<List<CoffeeBagEntity>> = _coffeeBags.asStateFlow()
    private val _knownFieldValues = MutableStateFlow(KnownFieldValues.EMPTY)
    val knownFieldValues: StateFlow<KnownFieldValues> = _knownFieldValues.asStateFlow()
    private val _selectedBagId = MutableStateFlow<Long?>(null)
    val selectedBagId: StateFlow<Long?> = _selectedBagId.asStateFlow()
    var lastLoggedBrewId: Long? = null
        private set
    private val _lastUnratedBrew = MutableStateFlow<BrewLogEntity?>(null)
    val lastUnratedBrew: StateFlow<BrewLogEntity?> = _lastUnratedBrew.asStateFlow()
    private val _bagPhotoResult = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val bagPhotoResult: StateFlow<BagPhotoProcessingResult?> = _bagPhotoResult.asStateFlow()

    private var _audioManager: BrewAudioManager? = null
    private var _audioOutputDirectory: java.io.File? = null
    val audioState: StateFlow<AudioAnalysisState>
        get() = _audioManager?.analysisState ?: MutableStateFlow(AudioAnalysisState())
    private var brewEventJob: Job? = null

    @VisibleForTesting
    internal val timerController = TimerController(viewModelScope, TimerStateHolder.instance)
    private var ratioPresetJob: Job? = null

    init {
        viewModelScope.launch {
            recipeRepository?.getAllRecipes()?.collect { recipes ->
                _savedRecipes.value = recipes
            }
        }
        viewModelScope.launch {
            brewLogRepository?.getAllLogs()?.collect { logs ->
                _brewLogs.value = logs
            }
        }
        viewModelScope.launch {
            brewLogRepository?.getAllFlavorTags()?.collect { tags ->
                _flavorTags.value = tags
            }
        }
        viewModelScope.launch {
            coffeeBagRepository?.getAllBags()?.collect { bags ->
                _coffeeBags.value = bags
                loadKnownFieldValues()
            }
        }
        collectRatioPresets(_uiState.value.method)
        recalculate()
        applyUserDefaults()
        refreshLastUnrated()
        // Sync TimerController state → BrewUiState
        viewModelScope.launch {
            timerController.state.collect { ts ->
                _uiState.update { ui ->
                    ui.copy(
                        timerRunning = ts.timerRunning,
                        elapsedSeconds = ts.elapsedSeconds,
                        currentPhaseIndex = ts.currentPhaseIndex,
                        phaseSecondsRemaining = ts.phaseSecondsRemaining,
                        phaseOvertime = ts.phaseOvertime,
                        showNextPreview = ts.showNextPreview,
                        lastDriftSeconds = ts.lastDriftSeconds,
                    )
                }
            }
        }
    }

    private fun applyUserDefaults() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository?.userPreferences?.first() ?: return@launch
            if (!prefs.onboardingCompleted) return@launch
            _uiState.update {
                it.copy(
                    method = prefs.defaultMethod,
                    filterType = prefs.defaultFilterType,
                    selectedGrinderId = prefs.selectedGrinderId,
                )
            }
            collectRatioPresets(prefs.defaultMethod)
            recalculate()
        }
    }

    fun setMethod(method: BrewMethod) {
        val defaultPresets = method.defaultRatioPresets
        val defaultIndex = defaultPresets.indexOfFirst { it.isDefault }.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                method = method,
                // Preserve filterType when switching back to a filter-capable method
                filterType = if (method.capacityMaxG != null) it.filterType else null,
                customRatio = "",
                bloomMultiplier = "",
                pulseCount = "",
                calibrationStyle = null,
                ratioPresets = defaultPresets,
                selectedPresetIndex = defaultIndex,
            )
        }
        collectRatioPresets(method)
        recalculate()
    }

    fun setInputMode(mode: InputMode) {
        val state = _uiState.value
        val oldMode = state.inputMode
        val currentAmount = state.amount.toFloatOrNull() ?: 0f

        // Convert amount intelligently when switching modes
        val convertedAmount = when {
            oldMode == mode -> currentAmount
            // From coffee grams → water/cup/brew ml: multiply by ratio
            oldMode == InputMode.COFFEE_TO_WATER && mode != InputMode.COFFEE_TO_WATER -> {
                val ratio = state.ratioPresets.getOrNull(state.selectedPresetIndex)?.ratio
                    ?: state.method.defaultRatio
                (currentAmount * ratio).let { kotlin.math.round(it) }
            }
            // From water/cup/brew ml → coffee grams: divide by ratio
            oldMode != InputMode.COFFEE_TO_WATER && mode == InputMode.COFFEE_TO_WATER -> {
                val ratio = state.ratioPresets.getOrNull(state.selectedPresetIndex)?.ratio
                    ?: state.method.defaultRatio
                if (ratio > 0f) (currentAmount / ratio).let { kotlin.math.round(it) } else currentAmount
            }
            // Between non-coffee modes: keep the ml value
            else -> currentAmount
        }

        _uiState.update {
            it.copy(
                inputMode = mode,
                amount = convertedAmount.toInt().toString(),
            )
        }
        recalculate()
    }

    fun setAmount(amount: String) {
        if (amount.isNotEmpty() && amount.toFloatOrNull() == null) return
        // Cap extreme values at 100g for coffee / 2000g for water/brew size
        val maxAmount = when (_uiState.value.inputMode) {
            InputMode.COFFEE_TO_WATER -> 100f
            else -> 2000f
        }
        val capped = amount.toFloatOrNull()?.let {
            if (it > maxAmount) maxAmount.toInt().toString() else amount
        } ?: amount
        _uiState.update { it.copy(amount = capped) }
        recalculate()
    }

    fun selectRatioPreset(index: Int) {
        _uiState.update { it.copy(selectedPresetIndex = index.coerceIn(0, it.ratioPresets.lastIndex)) }
        recalculate()
    }

    fun setCustomRatio(ratio: String) {
        if (ratio.isNotEmpty() && ratio.toFloatOrNull() == null) return
        _uiState.update { it.copy(customRatio = ratio) }
        recalculate()
    }

    fun setTempC(temp: String) {
        if (temp.isNotEmpty() && temp.toIntOrNull() == null) return
        _uiState.update { it.copy(tempC = temp) }
    }

    fun setBloomMultiplier(mult: String) {
        if (mult.isNotEmpty() && mult.toFloatOrNull() == null) return
        _uiState.update { it.copy(bloomMultiplier = mult) }
        recalculate()
    }

    fun setPulseCount(count: String) {
        if (count.isNotEmpty() && count.toIntOrNull() == null) return
        _uiState.update { it.copy(pulseCount = count) }
        recalculate()
    }

    fun setFilterType(type: FilterType?) {
        _uiState.update { it.copy(filterType = type) }
        recalculate()
    }

    fun setGrinder(grinderId: String?) {
        _uiState.update { it.copy(selectedGrinderId = grinderId) }
        recalculate()
    }

    fun setCalibrationStyle(style: CalibrationStyle?) {
        _uiState.update { it.copy(calibrationStyle = style) }
        recalculate()
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    fun startTimer() {
        timerController.setPhases(_uiState.value.timerPhases)
        timerController.start()

        // Auto-start audio monitoring when timer begins
        if (_audioManager != null && !_audioManager!!.isMonitoring) {
            // Start shadow comparison log for A/B testing
            _audioOutputDirectory?.let { dir -> _audioManager?.startShadowLog(dir) }

            _audioManager!!.startMonitoring()

            // Set brew context for metadata export
            val state = _uiState.value
            _audioManager?.setBrewContext(
                method = state.method.name,
                filterType = state.filterType?.name ?: "",
                doseG = state.coffeeG,
                waterG = state.waterG,
                ratio = state.effectiveRatio,
                grinderId = state.selectedGrinderId,
                grinderSetting = when (val result = state.grindResult) {
                    is GrindResult.Generic -> result.descriptor.displayName
                    is GrindResult.Specific ->
                        "${"%.1f".format(result.recommendation.rangeStart)}-${"%.1f".format(result.recommendation.rangeEnd)}"
                },
            )
        }

        // Notify audio manager of initial phase (phase 0)
        val initialPhase = _uiState.value.timerPhases.firstOrNull()
        if (initialPhase != null) {
            _audioManager?.onPhaseChanged(0, initialPhase.name)
        }
    }

    /**
     * Ensures the timer coroutine is running. Call on app resume to recover
     * from Doze or battery optimization pausing the coroutine.
     * Does NOT reset the clock — wall-clock anchoring handles the gap.
     */
    fun ensureTimerRunning() {
        timerController.ensureRunning()
    }

    fun pauseTimer() {
        timerController.pause()
        _audioManager?.stopMonitoring()
    }

    fun stopTimer() {
        timerController.stop()
        _audioManager?.stopMonitoring()
    }

    /**
     * Updates session metadata with placement and environment from lab setup dialog.
     */
    fun updateSessionSetup(placement: String, environment: String, notes: String) {
        val state = _uiState.value
        _audioManager?.setBrewContext(
            method = state.method.name,
            filterType = state.filterType?.name ?: "",
            doseG = state.coffeeG,
            waterG = state.waterG,
            ratio = state.effectiveRatio,
            grinderId = state.selectedGrinderId,
            grinderSetting = when (val result = state.grindResult) {
                is GrindResult.Generic -> result.descriptor.displayName
                is GrindResult.Specific ->
                    "${"%.1f".format(result.recommendation.rangeStart)}-${"%.1f".format(result.recommendation.rangeEnd)}"
            },
            placement = placement,
            environment = environment,
            notes = notes,
        )
    }

    // --- Audio Monitoring ---

    /**
     * Initializes the audio manager with the given output directory for recordings.
     * Call once from the UI layer when RECORD_AUDIO permission is granted.
     */
    fun initAudioManager(outputDirectory: java.io.File) {
        if (_audioManager != null) return
        _audioOutputDirectory = outputDirectory
        _audioManager = BrewAudioManager(
            outputDirectory = outputDirectory,
            autoRecord = true, // Always capture WAV + JSONL for debugging
        )
        startBrewEventCollection()
    }

    private fun startBrewEventCollection() {
        brewEventJob?.cancel()
        val manager = _audioManager ?: return
        brewEventJob = viewModelScope.launch {
            manager.brewEvents.collect { event ->
                handleBrewAudioEvent(event)
            }
        }
    }

    /**
     * Handles brew audio events for auto-advance.
     * Advances EVENT_GATED phases when matching audio events are detected:
     * - DRAIN_AND_REFILL: PourStarted → user resumed pouring
     * - DRAWDOWN: DrawdownComplete → silence after dripping
     *
     * BLOOM is excluded — user pours bloom water then waits, so PourStarted
     * during bloom is expected behavior, not a phase transition signal.
     *
     * Guard: ignores events in the first 3 seconds of a phase to prevent
     * false triggers from detector startup/calibration transients.
     */
    private fun handleBrewAudioEvent(event: BrewAudioEvent) {
        val state = _uiState.value
        if (!state.audioAutoAdvanceEnabled || !state.timerRunning) return
        if (state.currentPhaseIndex >= state.timerPhases.lastIndex) return

        val phase = state.timerPhases.getOrNull(state.currentPhaseIndex) ?: return

        // Ignore audio events in the first seconds of a phase — detector needs
        // time to calibrate its noise floor for the new acoustic environment
        val phaseElapsedSeconds = phase.durationSeconds - state.phaseSecondsRemaining
        if (phaseElapsedSeconds < AUDIO_ADVANCE_MIN_PHASE_SECONDS) return

        val shouldAdvance = when (phase.phaseType) {
            // Bloom should NOT auto-advance — user pours bloom water then waits.
            // Detecting PourStarted during bloom is expected, not a phase transition.
            PhaseType.BLOOM -> false
            // Drawdown complete: silence sustained after dripping
            PhaseType.DRAWDOWN -> {
                phase.mode == PhaseMode.EVENT_GATED && event is BrewAudioEvent.DrawdownComplete
            }
            // Drain & refill: user started pouring again
            PhaseType.DRAIN_AND_REFILL -> {
                phase.mode == PhaseMode.EVENT_GATED && event is BrewAudioEvent.PourStarted
            }
            else -> false
        }

        if (shouldAdvance) {
            advancePhase()
        }
    }

    fun setAudioAutoAdvance(enabled: Boolean) {
        _uiState.update { it.copy(audioAutoAdvanceEnabled = enabled) }
    }

    companion object {
        /** Minimum seconds into a phase before audio can trigger auto-advance.
         *  Prevents false triggers during detector calibration. */
        private const val AUDIO_ADVANCE_MIN_PHASE_SECONDS = 3
        private const val BAG_PHOTO_TAG = "BagPhotoProcessing"
        private val CANONICAL_METADATA_FIELDS = setOf(
            "origin",
            "region",
            "variety",
            "processType",
            "tastingNotes",
            "roastLevel",
        )
        private val BAG_PHOTO_FIELD_NAMES = listOf(
            "name",
            "roaster",
            "origin",
            "region",
            "farm",
            "variety",
            "processType",
            "altitude",
            "tastingNotes",
            "roastLevel",
            "roastDate",
            "expiryDate",
            "weight",
        )
    }

    fun startAudioMonitoring() {
        _audioManager?.startMonitoring()
    }

    fun stopAudioMonitoring() {
        _audioManager?.stopMonitoring()
    }

    fun toggleAudioMonitoring() {
        val manager = _audioManager ?: return
        if (manager.isMonitoring) manager.stopMonitoring() else manager.startMonitoring()
    }

    fun startAudioRecording() {
        _audioManager?.startRecording()
    }

    fun stopAudioRecording() {
        _audioManager?.stopRecording()
    }

    fun toggleAudioRecording() {
        val manager = _audioManager ?: return
        if (manager.isRecording) manager.stopRecording() else manager.startRecording()
    }

    /**
     * Records a user-marked brew event for ground truth labeling.
     * Called from debug overlay event marker buttons.
     * Labels are approximate (±2-3s) — user taps roughly when events happen.
     */
    fun markBrewEvent(label: String) {
        _audioManager?.markUserLabel(label)
    }

    /**
     * Records a problem/feedback marker during a brew experiment.
     */
    fun markBrewProblem(description: String) {
        _audioManager?.markUserProblem(description)
    }

    /**
     * Exports the current/latest brew session as a zip file and opens the share sheet.
     * Bundles all WAV, JSONL, metadata, labels, and shadow logs.
     */
    fun exportBrewSession(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioDir = _audioOutputDirectory ?: return@launch

            val result = com.adsamcik.starlitcoffee.audio.BrewDataBundler.bundleLatest(audioDir)
            if (!result.success || result.fileCount == 0) {
                Log.w("BrewExport", "Export failed: ${result.errors}")
                return@launch
            }

            val zipUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                result.zipFile,
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, zipUri)
                putExtra(
                    android.content.Intent.EXTRA_SUBJECT,
                    "Brew Audio Data — ${result.zipFile.nameWithoutExtension}",
                )
                putExtra(
                    android.content.Intent.EXTRA_TEXT,
                    "Brew data bundle: ${result.fileCount} files, ${result.totalSizeBytes / 1024} KB",
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                val chooser = android.content.Intent.createChooser(shareIntent, "Share Brew Data")
                context.startActivity(chooser)
            }
        }
    }

    fun requestFeedbackSnackbar() {
        _uiState.update { it.copy(showFeedbackSnackbar = true) }
    }

    fun clearFeedbackSnackbar() {
        _uiState.update { it.copy(showFeedbackSnackbar = false) }
    }

    fun advancePhase() {
        timerController.advancePhase(::rebalancePhases)
        _uiState.update { it.copy(timerPhases = timerController.phases) }

        // Notify audio manager of phase change
        val state = _uiState.value
        val newPhase = state.timerPhases.getOrNull(state.currentPhaseIndex)
        if (newPhase != null) {
            _audioManager?.onPhaseChanged(state.currentPhaseIndex, newPhase.name)
        }

        // Record user phase transition for shadow comparison (A/B testing)
        val phase = _uiState.value.timerPhases.getOrNull(_uiState.value.currentPhaseIndex)
        _audioManager?.recordUserTap(
            _uiState.value.currentPhaseIndex,
            phase?.name ?: "unknown"
        )
    }

    fun setTasteFeedback(feedback: TasteFeedback) {
        _uiState.update { it.copy(tasteFeedback = feedback) }
    }

    fun setRating(rating: Int) {
        _uiState.update { it.copy(rating = rating.coerceIn(0, 5)) }
    }

    fun setFeedbackNotes(notes: String) {
        _uiState.update { it.copy(feedbackNotes = notes) }
    }

    fun saveRecipe(name: String?) {
        val repository = recipeRepository ?: return
        val state = _uiState.value
        viewModelScope.launch {
            repository.insertRecipe(
                SavedRecipeEntity(
                    coffeeName = name,
                    method = state.method.name,
                    ratio = state.effectiveRatio,
                    doseG = state.coffeeG,
                    waterG = state.waterG,
                    grinderId = state.selectedGrinderId,
                    grindSetting = when (val result = state.grindResult) {
                        is GrindResult.Generic -> result.descriptor.displayName
                        is GrindResult.Specific -> {
                            "${"%.1f".format(result.recommendation.rangeStart)}-${"%.1f".format(result.recommendation.rangeEnd)}"
                        }
                    },
                    filterType = state.filterType?.name,
                    notes = state.feedbackNotes.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    fun deleteRecipe(entity: SavedRecipeEntity) {
        val repository = recipeRepository ?: return
        viewModelScope.launch {
            repository.deleteRecipe(entity)
        }
    }

    fun loadRecipe(entity: SavedRecipeEntity) {
        val method = BrewMethod.entries.find { it.name == entity.method } ?: BrewMethod.PULSAR
        val filterType = entity.filterType?.let { raw -> FilterType.entries.find { it.name == raw } }
        val presets = _uiState.value.let { state ->
            if (state.method == method) state.ratioPresets else method.defaultRatioPresets
        }
        val matchedIndex = presets.indexOfFirst { it.ratio == entity.ratio }
        _uiState.update {
            it.copy(
                method = method,
                inputMode = InputMode.COFFEE_TO_WATER,
                amount = entity.doseG.toString(),
                ratioPresets = presets,
                selectedPresetIndex = if (matchedIndex >= 0) matchedIndex else presets.indexOfFirst { p -> p.isDefault }.coerceAtLeast(0),
                customRatio = if (matchedIndex >= 0) "" else entity.ratio.toString(),
                filterType = filterType,
                selectedGrinderId = entity.grinderId,
                bloomMultiplier = "",
                pulseCount = "",
                tempC = "",
                calibrationStyle = null,
            )
        }
        collectRatioPresets(method)
        recalculate()
    }

    fun logBrew() {
        val repository = brewLogRepository ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val logId = repository.insertLog(
                BrewLogEntity(
                    coffeeBagId = _selectedBagId.value,
                    method = state.method.name,
                    doseG = state.coffeeG,
                    waterG = state.waterG,
                    ratio = state.effectiveRatio,
                    grindSetting = when (val result = state.grindResult) {
                        is GrindResult.Generic -> result.descriptor.displayName
                        is GrindResult.Specific -> {
                            "${"%.1f".format(result.recommendation.rangeStart)}-${"%.1f".format(result.recommendation.rangeEnd)}"
                        }
                    },
                    filterType = state.filterType?.name,
                    tasteFeedback = state.tasteFeedback?.name,
                    rating = state.rating.takeIf { it > 0 }?.toFloat(),
                    freeformNotes = state.feedbackNotes.takeIf { it.isNotBlank() },
                    brewTimeSeconds = state.elapsedSeconds.takeIf { it > 0 },
                ),
            )
            lastLoggedBrewId = logId
            // Auto-decrement bag weight + auto-status transitions
            _selectedBagId.value?.let { bagId ->
                val bag = _coffeeBags.value.find { it.id == bagId }
                if (bag != null) {
                    var updated = bag
                    // SEALED → OPEN on first brew
                    if (bag.status == "SEALED") {
                        updated = updated.copy(
                            status = "OPEN",
                            openedDate = System.currentTimeMillis(),
                        )
                    }
                    // Save dialed-in grind setting to bag
                    val grindStr = when (val result = state.grindResult) {
                        is GrindResult.Specific ->
                            "%.1f".format(result.recommendation.suggestedStart)
                        is GrindResult.Generic -> null
                    }
                    if (grindStr != null && updated.grindSetting != grindStr) {
                        updated = updated.copy(grindSetting = grindStr)
                    }
                    // Decrement weight
                    if (updated.weightG != null) {
                        val newWeight = (updated.weightG - state.coffeeG).coerceAtLeast(0f)
                        updated = updated.copy(weightG = newWeight)
                        // OPEN → FINISHED when depleted
                        if (newWeight <= 0f && updated.status == "OPEN") {
                            updated = updated.copy(status = "FINISHED")
                        }
                    }
                    if (updated != bag) {
                        coffeeBagRepository?.updateBag(updated)
                    }
                    // Auto-rotate: when bag finishes, open next sealed bag of same coffee
                    if (updated.status == "FINISHED" && bag.status != "FINISHED") {
                        val nextBag = coffeeBagRepository?.findNextSealed(
                            updated.name,
                            updated.roaster,
                        )
                        if (nextBag != null) {
                            val opened = nextBag.copy(
                                status = "OPEN",
                                openedDate = System.currentTimeMillis(),
                                grindSetting = updated.grindSetting,
                            )
                            coffeeBagRepository?.updateBag(opened)
                            _selectedBagId.value = opened.id
                        }
                    }
                }
            }
            refreshLastUnrated()
        }
    }

    fun saveBrewWithRating(
        rating: Float,
        descriptors: List<String>,
        notes: String,
    ) {
        val repository = brewLogRepository ?: return
        val logId = lastLoggedBrewId ?: return
        viewModelScope.launch {
            repository.updateRating(logId, rating, notes.takeIf { it.isNotBlank() })
            if (descriptors.isNotEmpty()) {
                val tags = descriptors.map { descriptor ->
                    FlavorTagEntity(brewLogId = logId, descriptor = descriptor)
                }
                repository.insertFlavorTags(tags)
            }
            lastLoggedBrewId = null
        }
    }

    fun saveRatingForLog(
        logId: Long,
        rating: Float,
        descriptors: List<String>,
        notes: String,
    ) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            repository.updateRating(logId, rating, notes.takeIf { it.isNotBlank() })
            if (descriptors.isNotEmpty()) {
                val tags = descriptors.map { descriptor ->
                    FlavorTagEntity(brewLogId = logId, descriptor = descriptor)
                }
                repository.insertFlavorTags(tags)
            }
            refreshLastUnrated()
        }
    }

    fun selectBag(bagId: Long?) {
        _selectedBagId.value = bagId
        // Auto-set decaf from bag
        val bag = _coffeeBags.value.find { it.id == bagId }
        _uiState.update { it.copy(isDecafBrew = bag?.isDecaf ?: false) }
        recalculate()
    }

    fun refreshLastUnrated() {
        viewModelScope.launch {
            _lastUnratedBrew.value = brewLogRepository?.getLastUnratedLog()
        }
    }

    fun quickRateBrewLog(
        logId: Long,
        rating: Float,
        tasteFeedback: TasteFeedback?,
    ) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            repository.updateFeedback(
                logId = logId,
                rating = rating,
                notes = null,
                tasteFeedback = tasteFeedback?.name,
                flavorTags = emptyList(),
            )
            refreshLastUnrated()
        }
    }

    fun setDecafBrew(isDecaf: Boolean) {
        _uiState.update { it.copy(isDecafBrew = isDecaf) }
        recalculate()
    }

    fun deleteBrewLog(entity: BrewLogEntity) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            repository.deleteLog(entity)
        }
    }

    fun getFlavorTagsForLog(logId: Long): Flow<List<FlavorTagEntity>> {
        return brewLogRepository?.getFlavorTagsForBrewLog(logId)
            ?: flowOf(emptyList())
    }

    fun getFlavorTagsForBag(bagId: Long): Flow<List<FlavorTagEntity>> {
        return brewLogRepository?.getFlavorTagsForBag(bagId)
            ?: flowOf(emptyList())
    }

    suspend fun getBrewLogById(logId: Long): BrewLogEntity? {
        return brewLogRepository?.getLogById(logId)
    }

    fun updateBrewLogFeedback(
        logId: Long,
        rating: Float?,
        notes: String?,
        tasteFeedback: String?,
        descriptors: List<String>,
    ) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            val tags = descriptors.map { descriptor ->
                FlavorTagEntity(brewLogId = logId, descriptor = descriptor)
            }
            repository.updateFeedback(
                logId = logId,
                rating = rating,
                notes = notes?.takeIf { it.isNotBlank() },
                tasteFeedback = tasteFeedback,
                flavorTags = tags,
            )
        }
    }

    fun addCoffeeBag(
        name: String,
        roaster: String? = null,
        origin: String? = null,
        region: String? = null,
        roastLevel: String? = null,
        processType: String? = null,
        variety: String? = null,
        tastingNotes: String? = null,
        roastDate: Long? = null,
        expiryDate: Long? = null,
        openedDate: Long? = null,
        barcode: String? = null,
        weightG: Float? = null,
        priceAmount: Float? = null,
        priceCurrency: String? = "USD",
        notes: String? = null,
        isDecaf: Boolean = false,
        photoUri: String? = null,
        photoUris: String? = null,
        traceabilityUrl: String? = null,
        status: String = "SEALED",
    ) {
        val repository = coffeeBagRepository ?: return
        val normalizedBarcode = BarcodeInsights.normalizeBarcode(barcode)
            ?: barcode?.trim()?.takeIf { it.isNotBlank() }
        val locale = Locale.getDefault()
        viewModelScope.launch {
            val entity = CoffeeMetadataNormalizer.applyToBagEntity(
                CoffeeBagEntity(
                    name = name,
                    roaster = roaster,
                    origin = origin,
                    region = region,
                    roastLevel = roastLevel,
                    processType = processType,
                    variety = variety,
                    tastingNotes = tastingNotes,
                    roastDate = roastDate,
                    expiryDate = expiryDate,
                    openedDate = openedDate,
                    barcode = normalizedBarcode,
                    weightG = weightG,
                    initialWeightG = weightG,
                    priceAmount = priceAmount,
                    priceCurrency = priceCurrency,
                    notes = notes,
                    isDecaf = isDecaf,
                    photoUri = photoUri,
                    photoUris = photoUris,
                    traceabilityUrl = traceabilityUrl,
                    status = status,
                ),
                origin = origin,
                region = region,
                roastLevel = roastLevel,
                processType = processType,
                variety = variety,
                tastingNotes = tastingNotes,
                locale = locale,
            )
            repository.insertBag(entity)
            loadKnownFieldValues()
        }
    }

    fun updateCoffeeBag(entity: CoffeeBagEntity) {
        val repository = coffeeBagRepository ?: return
        val normalizedBarcode = BarcodeInsights.normalizeBarcode(entity.barcode)
            ?: entity.barcode?.trim()?.takeIf { it.isNotBlank() }
        val locale = Locale.getDefault()
        viewModelScope.launch {
            repository.updateBag(
                CoffeeMetadataNormalizer.applyToBagEntity(
                    bag = entity.copy(barcode = normalizedBarcode),
                    origin = entity.origin,
                    region = entity.region,
                    roastLevel = entity.roastLevel,
                    processType = entity.processType,
                    variety = entity.variety,
                    tastingNotes = entity.tastingNotes,
                    locale = locale,
                ),
            )
            loadKnownFieldValues()
        }
    }

    fun deleteCoffeeBag(entity: CoffeeBagEntity) {
        val repository = coffeeBagRepository ?: return
        viewModelScope.launch {
            repository.deleteBag(entity)
            loadKnownFieldValues()
        }
    }

    fun updateBagStatus(bagId: Long, status: String) {
        val repository = coffeeBagRepository ?: return
        val bag = _coffeeBags.value.find { it.id == bagId } ?: return
        viewModelScope.launch {
            repository.updateBag(bag.copy(status = status))
        }
    }

    fun adjustBagWeight(bagId: Long, newWeightG: Float) {
        val repository = coffeeBagRepository ?: return
        val bag = _coffeeBags.value.find { it.id == bagId } ?: return
        viewModelScope.launch {
            var updated = bag.copy(weightG = newWeightG.coerceAtLeast(0f))
            if (updated.initialWeightG == null && newWeightG > 0f) {
                updated = updated.copy(initialWeightG = newWeightG)
            }
            if (newWeightG <= 0f && updated.status == "OPEN") {
                updated = updated.copy(status = "FINISHED")
            }
            repository.updateBag(updated)
        }
    }

    fun findBagByBarcode(barcode: String, onResult: (CoffeeBagEntity?) -> Unit) {
        if (coffeeBagRepository == null) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            onResult(findLocalBagByBarcode(barcode))
        }
    }
    fun processNewBagPhotos(
        photosCsv: String,
        knownFieldValues: KnownFieldValues = _knownFieldValues.value,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val photoUriList = photosCsv.split(",").map(String::trim).filter(String::isNotBlank)
                if (photoUriList.isEmpty()) {
                    _bagPhotoResult.value = BagPhotoProcessingResult(capturedPhotoUris = photosCsv)
                    return@withContext
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val barcodeScanner = BarcodeScanning.getClient()
                try {
                    val processedPhotos= photoUriList.mapIndexedNotNull { index, uriStr ->
                        processBagPhoto(
                            uriStr = uriStr,
                            side = if (index == 0) BagCaptureSide.FRONT else BagCaptureSide.BACK,
                            knownFieldValues = knownFieldValues,
                            recognizer = recognizer,
                            barcodeScanner = barcodeScanner,
                        )
                    }

                    val photoAnalyses = processedPhotos.map { photo ->
                        BagPhotoAnalysis(
                            uri = photo.uri,
                            side = photo.side,
                            quality = photo.quality,
                            extractedText = photo.fullText,
                        )
                    }

                    var detectedBarcode = processedPhotos.firstNotNullOfOrNull { it.detectedBarcode }
                    val rawDetectedQrUrl = processedPhotos.firstNotNullOfOrNull { it.detectedQrUrl }
                    val allCandidates = processedPhotos
                        .flatMap { photo -> buildFieldCandidates(photo) }
                        .toMutableList()
                    val combinedOcrText = processedPhotos.joinToString("\n\n") { it.fullText }.trim()

                    if (detectedBarcode == null && combinedOcrText.isNotBlank()) {
                        detectedBarcode = OcrFieldExtractor.extractBarcodeFromText(combinedOcrText)
                    }
                    detectedBarcode = BarcodeInsights.normalizeBarcode(detectedBarcode)
                        ?: detectedBarcode?.trim()?.takeIf { it.isNotBlank() }
                    val matchedBagByBarcode = findLocalBagByBarcode(detectedBarcode)

                    // Infer country dictionary from barcode for locale-aware OCR and display
                    val barcodeCountry = CoffeeCountryDictionaries.localeFromBarcode(detectedBarcode)
                        ?.let { locale -> CoffeeCountryDictionaries.ALL.firstOrNull { it.locale == locale } }
                    val inferredLocale = barcodeCountry?.locale

                    matchedBagByBarcode?.let { matchedBag ->
                        allCandidates += BarcodeInsights.buildLocalMatchCandidates(
                            matchedBag,
                            locale = inferredLocale ?: Locale.getDefault(),
                        )
                    }
                    val observedStemMatch = BarcodeInsights.findObservedStemMatch(detectedBarcode)
                    allCandidates += BarcodeInsights.buildObservedStemCandidates(observedStemMatch)

                    var offLookupName: String? = null
                    var offLookupRoaster: String? = null
                    detectedBarcode?.let { barcode ->
                        try {
                            val lookup = OpenFoodFactsClient.lookupBarcode(barcode)
                            if (lookup != null) {
                                offLookupName = lookup.name
                                offLookupRoaster = lookup.brand
                                if (!lookup.name.isNullOrBlank()) {
                                    allCandidates += BagFieldCandidate(
                                        fieldName = "name",
                                        value = lookup.name,
                                        sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                                        confidenceHint = BagFieldConfidence.HIGH,
                                    )
                                }
                                if (!lookup.brand.isNullOrBlank()) {
                                    allCandidates += BagFieldCandidate(
                                        fieldName = "roaster",
                                        value = lookup.brand,
                                        sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                                        confidenceHint = BagFieldConfidence.HIGH,
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(BAG_PHOTO_TAG, "Failed to fetch product info from OpenFoodFacts", e)
                        }
                    }

                    val qrEnrichment= buildQrLinkEnrichment(rawDetectedQrUrl)
                    allCandidates += qrEnrichment.candidates

                    val fieldEvidence = buildMap<String, BagFieldEvidence> {
                        for (fieldName in BAG_PHOTO_FIELD_NAMES) {
                            val resolved = BagPhotoScanSupport.resolveField(
                                fieldName = fieldName,
                                candidates = allCandidates.filter { it.fieldName == fieldName },
                            )
                            if (resolved != null) {
                                put(fieldName, resolved)
                            }
                        }
                    }

                    val reviewHints = BagPhotoScanSupport.buildReviewHints(
                        photoAnalyses = photoAnalyses,
                        resolvedFields = fieldEvidence,
                        additionalHints = BarcodeInsights.buildBarcodeReviewHints(
                            barcode = detectedBarcode,
                            matchedBag = matchedBagByBarcode,
                            observedStemMatch = observedStemMatch,
                        ) + qrEnrichment.reviewHints,
                    )
                    val ocrPrefill = fieldEvidence
                        .takeIf { it.isNotEmpty() }
                        ?.let(BagPhotoScanSupport::buildPrefill)

                    _bagPhotoResult.value = BagPhotoProcessingResult(
                        ocrPrefill = ocrPrefill,
                        capturedPhotoUris = photosCsv,
                        detectedBarcode = detectedBarcode,
                        detectedQrUrl = qrEnrichment.safeUrl,
                        offLookupName = offLookupName,
                        offLookupRoaster = offLookupRoaster,
                        fieldEvidence = fieldEvidence,
                        photoAnalyses = photoAnalyses,
                        reviewHints = reviewHints,
                    )
                } finally {
                    recognizer.close()
                    barcodeScanner.close()
                }
            }
        }
    }

    fun processNewBagPhotos(
        photosCsv: String,
        knownRoasters: List<String>,
        knownNames: List<String>,
    ) {
        processNewBagPhotos(
            photosCsv = photosCsv,
            knownFieldValues = _knownFieldValues.value.copy(
                roasters = knownRoasters,
                names = knownNames,
            ),
        )
    }

    private suspend fun processBagPhoto(
        uriStr: String,
        side: BagCaptureSide,
        knownFieldValues: KnownFieldValues,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    ): ProcessedBagPhoto? {
        return try {
            val file = java.io.File(Uri.parse(uriStr).path ?: return null)
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val bitmap = ImagePreprocessor.applyExifRotation(rawBitmap, file.absolutePath)

            val originalText = recognizeText(recognizer, bitmap)
            val alignedBitmap = if (originalText != null && originalText.textBlocks.isNotEmpty()) {
                val alignment = ImagePreprocessor.computeAlignment(originalText.textBlocks)
                ImagePreprocessor.applyAlignment(bitmap, alignment)
            } else {
                bitmap
            }
            val alignedText = recognizeText(recognizer, alignedBitmap)
            val enhancedBitmap = ImagePreprocessor.preprocessForOcr(alignedBitmap)
            val enhancedText = recognizeText(recognizer, enhancedBitmap)

            val passes = listOfNotNull(
                buildScanPass("original", bitmap, originalText, knownFieldValues),
                buildScanPass("aligned", alignedBitmap, alignedText, knownFieldValues),
                buildScanPass("enhanced", enhancedBitmap, enhancedText, knownFieldValues),
            )

            val mergedText = passes.joinToString("\n") { it.fullText }.trim()
            val textBlockCount = passes.maxOfOrNull { it.blocks.size } ?: 0
            val quality = BagCaptureQualityAnalyzer.analyzeBitmap(
                bitmap = bitmap,
                textBlockCount = textBlockCount,
                textDetected = textBlockCount > 0,
            )

            var detectedBarcode = if (mergedText.isNotBlank()) {
                OcrFieldExtractor.extractBarcodeFromText(mergedText)
            } else {
                null
            }
            var detectedQrUrl: String? = null

            scanBarcodes(barcodeScanner, bitmap)?.forEach { code ->
                val rawValue = code.rawValue ?: return@forEach
                if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
                    if (detectedQrUrl == null) detectedQrUrl = rawValue
                } else if (detectedBarcode == null) {
                    detectedBarcode = rawValue
                }
            }

            ProcessedBagPhoto(
                uri = uriStr,
                side = side,
                quality = quality,
                passes = passes,
                fullText = mergedText,
                detectedBarcode = detectedBarcode,
                detectedQrUrl = detectedQrUrl,
            )
        } catch (e: Exception) {
            Log.w(BAG_PHOTO_TAG, "Failed to process bag photo for OCR and barcode extraction", e)
            null
        }
    }

    private fun buildScanPass(
        label: String,
        bitmap: Bitmap,
        text: Text?,
        knownFieldValues: KnownFieldValues,
        countryHint: CoffeeCountryDictionary? = null,
    ): ScanPass? {
        val ocrText = text ?: return null
        val blocks = ocrText.textBlocks.map { block ->
            OcrFieldExtractor.OcrTextBlock(
                text = block.text,
                heightPx = block.boundingBox?.height() ?: 0,
                topPx = block.boundingBox?.top ?: 0,
                leftPx = block.boundingBox?.left ?: 0,
                widthPx = block.boundingBox?.width() ?: 0,
                imageWidthPx = bitmap.width,
                imageHeightPx = bitmap.height,
            )
        }
        return ScanPass(
            label = label,
            result = OcrFieldExtractor.extractFieldsFromBlocks(blocks, knownFieldValues, countryHint),
            blocks = blocks,
            fullText = ocrText.text,
        )
    }

    private fun loadKnownFieldValues() {
        val repository = coffeeBagRepository ?: run {
            _knownFieldValues.value = KnownFieldValues.EMPTY
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val bags = repository.getAllBags().first()
            val locale = Locale.getDefault()
            val resolvedMetadata = bags.map { bag -> CoffeeMetadataNormalizer.resolveBagMetadata(bag, locale) }
            _knownFieldValues.value = KnownFieldValues(
                names = bags.map { it.name }.sanitizeKnownValues(),
                roasters = bags.mapNotNull { it.roaster }.sanitizeKnownValues(),
                origins = resolvedMetadata.mapNotNull { it.origin }.sanitizeKnownValues(),
                regions = resolvedMetadata.mapNotNull { it.region }.sanitizeKnownValues(),
                varieties = resolvedMetadata
                    .mapNotNull { it.variety }
                    .flatMap(::splitMetadataValues)
                    .sanitizeKnownValues(),
                processTypes = resolvedMetadata.mapNotNull { it.processType }.sanitizeKnownValues(),
                roastLevels = resolvedMetadata
                    .mapNotNull { it.roastLevel }
                    .flatMap(::splitMetadataValues)
                    .sanitizeKnownValues(),
                farms = bags.mapNotNull { it.farm }.sanitizeKnownValues(),
            )
        }
    }

    private fun buildFieldCandidates(
        photo: ProcessedBagPhoto,
        inferredLocale: Locale? = null,
    ): List<BagFieldCandidate> = buildList {
        val locale = inferredLocale ?: Locale.getDefault()
        photo.passes.forEach { pass ->
            val confidenceHint = confidenceHintForPass(photo.quality, pass.blocks.size)
            addFieldCandidate("name", pass.result.name, photo, pass, confidenceHint, locale)
            addFieldCandidate("roaster", pass.result.roaster, photo, pass, confidenceHint, locale)
            addFieldCandidate("origin", pass.result.origin, photo, pass, confidenceHint, locale)
            addFieldCandidate("region", pass.result.region, photo, pass, confidenceHint, locale)
            addFieldCandidate("farm", pass.result.farm, photo, pass, confidenceHint, locale)
            addFieldCandidate("variety", pass.result.variety, photo, pass, confidenceHint, locale)
            addFieldCandidate("processType", pass.result.processType, photo, pass, confidenceHint, locale)
            addFieldCandidate("altitude", pass.result.altitude, photo, pass, confidenceHint, locale)
            addFieldCandidate("tastingNotes", pass.result.tastingNotes, photo, pass, confidenceHint, locale)
            addFieldCandidate("roastLevel", pass.result.roastLevel, photo, pass, confidenceHint, locale)
            addFieldCandidate("roastDate", pass.result.roastDate, photo, pass, confidenceHint, locale)
            addFieldCandidate("expiryDate", pass.result.expiryDate, photo, pass, confidenceHint, locale)
            addFieldCandidate("weight", pass.result.weight, photo, pass, confidenceHint, locale)
        }
    }

    private fun MutableList<BagFieldCandidate>.addFieldCandidate(
        fieldName: String,
        value: String?,
        photo: ProcessedBagPhoto,
        pass: ScanPass,
        confidenceHint: BagFieldConfidence,
        locale: Locale,
    ) {
        val cleanValue = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val supportingBlock = findSupportingBlock(pass.blocks, cleanValue)
        val normalized = normalizeMetadataField(fieldName, cleanValue, locale)
        add(
            BagFieldCandidate(
                fieldName = fieldName,
                value = normalized?.value ?: cleanValue,
                rawValue = cleanValue,
                canonicalKey = normalized?.canonicalKey,
                sourceType = BagFieldSourceType.OCR,
                side = photo.side,
                confidenceHint = confidenceHint,
                matchStrategy = normalized?.matchStrategy,
                supportingText = supportingBlock?.text ?: pass.fullText.lineSequence().firstOrNull { line ->
                    line.contains(cleanValue, ignoreCase = true)
                },
                previewUri = photo.uri,
                previewRect = supportingBlock?.normalizedBounds(),
            ),
        )
        addInferredMetadataCandidates(
            normalized = normalized,
            rawValue = cleanValue,
            sourceType = BagFieldSourceType.OCR,
            confidenceHint = confidenceHint,
            side = photo.side,
            supportingText = supportingBlock?.text ?: pass.fullText.lineSequence().firstOrNull { line ->
                line.contains(cleanValue, ignoreCase = true)
            },
            previewUri = photo.uri,
            previewRect = supportingBlock?.normalizedBounds(),
            locale = locale,
        )
    }

    private suspend fun buildQrLinkEnrichment(rawUrl: String?): QrLinkEnrichment {
        val safeUrl = SafeQrLinkMetadataExplorer.sanitizePublicWebUrl(rawUrl)
        if (rawUrl != null && safeUrl == null) {
            return QrLinkEnrichment(
                reviewHints = listOf(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "Ignored an unsafe QR website link. Only public http(s) pages are supported.",
                    ),
                ),
            )
        }
        if (safeUrl == null) return QrLinkEnrichment()

        val qrLookupEnabled = userPreferencesRepository
            ?.userPreferences
            ?.first()
            ?.qrLinkExplorerEnabled == true

        if (!qrLookupEnabled) {
            return QrLinkEnrichment(
                safeUrl = safeUrl,
                reviewHints = listOf(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "QR website saved, but automatic QR exploration is off in Settings.",
                    ),
                ),
            )
        }

        return when (val result = qrLinkMetadataExplorer.explore(safeUrl)) {
            is QrLinkExploreResult.Success -> {
                val candidates = buildQrLinkCandidates(result.metadata)
                QrLinkEnrichment(
                    safeUrl = safeUrl,
                    candidates = candidates,
                    reviewHints = listOf(
                        BagPhotoReviewHint(
                            severity = BagReviewSeverity.INFO,
                            message = if (candidates.isEmpty()) {
                                "QR website reached, but it did not expose usable coffee details."
                            } else {
                                "Fetched extra coffee hints from ${result.metadata.host}. Review them before saving."
                            },
                        ),
                    ),
                )
            }
            is QrLinkExploreResult.Skipped -> QrLinkEnrichment(
                safeUrl = safeUrl.takeIf { result.keepUrl },
                reviewHints = listOf(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = result.reason,
                    ),
                ),
            )
        }
    }

    private fun buildQrLinkCandidates(metadata: QrCoffeeMetadata): List<BagFieldCandidate> = buildList {
        val locale = Locale.getDefault()
        addQrLinkCandidate("name", metadata.name, BagFieldConfidence.HIGH, metadata, locale)
        addQrLinkCandidate("roaster", metadata.roaster, BagFieldConfidence.HIGH, metadata, locale)
        addQrLinkCandidate("origin", metadata.origin, BagFieldConfidence.MEDIUM, metadata, locale)
        addQrLinkCandidate("region", metadata.region, BagFieldConfidence.MEDIUM, metadata, locale)
        addQrLinkCandidate("processType", metadata.processType, BagFieldConfidence.MEDIUM, metadata, locale)
        addQrLinkCandidate("tastingNotes", metadata.tastingNotes, BagFieldConfidence.MEDIUM, metadata, locale)
    }

    private fun MutableList<BagFieldCandidate>.addQrLinkCandidate(
        fieldName: String,
        value: String?,
        confidenceHint: BagFieldConfidence,
        metadata: QrCoffeeMetadata,
        locale: Locale,
    ) {
        val cleanValue = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val normalized = normalizeMetadataField(fieldName, cleanValue, locale)
        add(
            BagFieldCandidate(
                fieldName = fieldName,
                value = normalized?.value ?: cleanValue,
                rawValue = cleanValue,
                canonicalKey = normalized?.canonicalKey,
                sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
                confidenceHint = confidenceHint,
                matchStrategy = normalized?.matchStrategy,
                supportingText = buildQrSupportingText(fieldName, metadata),
            ),
        )
        addInferredMetadataCandidates(
            normalized = normalized,
            rawValue = cleanValue,
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            confidenceHint = confidenceHint,
            supportingText = buildQrSupportingText(fieldName, metadata),
            locale = locale,
        )
    }

    private fun normalizeMetadataField(
        fieldName: String,
        rawValue: String,
        locale: Locale,
    ): NormalizedCoffeeField? =
        rawValue.takeIf { fieldName in CANONICAL_METADATA_FIELDS }
            ?.let { CoffeeMetadataNormalizer.normalizeField(fieldName, it, locale) }

    private fun MutableList<BagFieldCandidate>.addInferredMetadataCandidates(
        normalized: NormalizedCoffeeField?,
        rawValue: String,
        sourceType: BagFieldSourceType,
        confidenceHint: BagFieldConfidence,
        side: BagCaptureSide? = null,
        supportingText: String? = null,
        previewUri: String? = null,
        previewRect: BagPhotoRect? = null,
        locale: Locale,
    ) {
        normalized?.relatedCanonicalKeys
            ?.filterKeys { it in BAG_PHOTO_FIELD_NAMES }
            ?.forEach { (fieldName, canonicalKey) ->
                val localizedValue = CoffeeMetadataNormalizer.displayField(
                    fieldName = fieldName,
                    canonicalKey = canonicalKey,
                    fallbackRaw = rawValue,
                    locale = locale,
                ) ?: return@forEach
                add(
                    BagFieldCandidate(
                        fieldName = fieldName,
                        value = localizedValue,
                        rawValue = rawValue,
                        canonicalKey = canonicalKey,
                        sourceType = sourceType,
                        side = side,
                        confidenceHint = inferredConfidence(confidenceHint),
                        matchStrategy = CoffeeMetadataMatchStrategy.RELATION_INFERENCE,
                        supportingText = supportingText,
                        previewUri = previewUri,
                        previewRect = previewRect,
                    ),
                )
            }
    }

    private fun inferredConfidence(confidence: BagFieldConfidence): BagFieldConfidence = when (confidence) {
        BagFieldConfidence.HIGH -> BagFieldConfidence.MEDIUM
        BagFieldConfidence.MEDIUM -> BagFieldConfidence.LOW
        BagFieldConfidence.LOW -> BagFieldConfidence.LOW
        BagFieldConfidence.NEEDS_REVIEW -> BagFieldConfidence.NEEDS_REVIEW
    }

    private fun splitMetadataValues(values: String): List<String> = values
        .split(",")
        .map(String::trim)
        .filter(String::isNotBlank)

    private fun buildQrSupportingText(
        fieldName: String,
        metadata: QrCoffeeMetadata,
    ): String {
        val hostLabel = metadata.host.removePrefix("www.")
        val detail = when (fieldName) {
            "name" -> metadata.pageTitle ?: metadata.pageDescription
            "roaster" -> metadata.pageTitle ?: metadata.pageDescription
            "origin", "region", "processType", "tastingNotes" ->
                metadata.supportingSnippet ?: metadata.pageDescription ?: metadata.pageTitle
            else -> metadata.pageTitle ?: metadata.pageDescription
        }?.take(160)

        return if (detail.isNullOrBlank()) {
            "Parsed from $hostLabel"
        } else {
            "Parsed from $hostLabel: $detail"
        }
    }

    private suspend fun findLocalBagByBarcode(barcode: String?): CoffeeBagEntity? {
        val repository = coffeeBagRepository ?: return null
        val rawBarcode = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedBarcode = BarcodeInsights.normalizeBarcode(rawBarcode)

        return when {
            normalizedBarcode != null && normalizedBarcode != rawBarcode ->
                repository.findByBarcode(normalizedBarcode) ?: repository.findByBarcode(rawBarcode)
            normalizedBarcode != null -> repository.findByBarcode(normalizedBarcode)
            else -> repository.findByBarcode(rawBarcode)
        }
    }

    private fun confidenceHintForPass(
        quality: BagCaptureQuality,
        blockCount: Int,
    ): BagFieldConfidence = when {
        quality.readyForCapture && blockCount >= 2 -> BagFieldConfidence.MEDIUM
        quality.textDetected -> BagFieldConfidence.LOW
        else -> BagFieldConfidence.NEEDS_REVIEW
    }

    private fun findSupportingBlock(
        blocks: List<OcrFieldExtractor.OcrTextBlock>,
        value: String,
    ): OcrFieldExtractor.OcrTextBlock? {
        val normalizedValue = normalizeMatchText(value)
        if (normalizedValue.isBlank()) return null

        blocks.firstOrNull { block ->
            normalizeMatchText(block.text).contains(normalizedValue)
        }?.let { return it }

        val targetTokens = normalizedValue.split(" ").filter { it.length >= 3 }
        if (targetTokens.isEmpty()) return null

        return blocks.maxByOrNull { block ->
            val normalizedBlock = normalizeMatchText(block.text)
            targetTokens.count { token -> normalizedBlock.contains(token) }
        }?.takeIf { block ->
            val normalizedBlock = normalizeMatchText(block.text)
            targetTokens.any { token -> normalizedBlock.contains(token) }
        }
    }

    private fun normalizeMatchText(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private suspend fun recognizeText(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): Text? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> cont.resume(result, null) }
            .addOnFailureListener { cont.resume(null, null) }
    }

    private suspend fun scanBarcodes(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        bitmap: Bitmap,
    ): List<Barcode>? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes -> cont.resume(codes, null) }
            .addOnFailureListener { cont.resume(null, null) }
    }

    private data class ProcessedBagPhoto(
        val uri: String,
        val side: BagCaptureSide,
        val quality: BagCaptureQuality,
        val passes: List<ScanPass>,
        val fullText: String,
        val detectedBarcode: String?,
        val detectedQrUrl: String?,
    )

    private data class QrLinkEnrichment(
        val safeUrl: String? = null,
        val candidates: List<BagFieldCandidate> = emptyList(),
        val reviewHints: List<BagPhotoReviewHint> = emptyList(),
    )

    private data class ScanPass(
        val label: String,
        val result: OcrFieldExtractor.OcrExtractionResult,
        val blocks: List<OcrFieldExtractor.OcrTextBlock>,
        val fullText: String,
    )

    fun clearBagPhotoResult() {
        _bagPhotoResult.value = null
    }

    fun resetBrew() {
        timerController.resetForBrew()
        _uiState.value = BrewUiState()
        collectRatioPresets(BrewMethod.PULSAR)
        recalculate()
        applyUserDefaults()
    }

    private fun collectRatioPresets(method: BrewMethod) {
        ratioPresetJob?.cancel()
        val repository = ratioPresetRepository ?: return
        ratioPresetJob = viewModelScope.launch {
            repository.getPresetsForMethod(method).collect { presets ->
                val currentState = _uiState.value
                if (currentState.method == method) {
                    val defaultIndex = presets.indexOfFirst { it.isDefault }.coerceAtLeast(0)
                    val selectedIndex = if (currentState.selectedPresetIndex < presets.size) {
                        currentState.selectedPresetIndex
                    } else {
                        defaultIndex
                    }
                    _uiState.update { it.copy(ratioPresets = presets, selectedPresetIndex = selectedIndex) }
                    recalculate()
                }
            }
        }
    }

    private fun recalculate() {
        _uiState.update { state ->
            val amount = state.amount.toFloatOrNull() ?: 0f
            val method = state.method

            val selectedPreset = state.ratioPresets.getOrNull(state.selectedPresetIndex)
            val presetRatio = selectedPreset?.ratio ?: method.defaultRatio

            val effectiveRatio = if (state.customRatio.isNotEmpty()) {
                state.customRatio.toFloatOrNull() ?: presetRatio
            } else {
                presetRatio
            }

            val coffeeG: Float
            val waterG: Float
            when (state.inputMode) {
                InputMode.COFFEE_TO_WATER -> {
                    coffeeG = amount
                    waterG = if (effectiveRatio != 0f) coffeeG * effectiveRatio else 0f
                }
                InputMode.WATER_TO_COFFEE -> {
                    waterG = amount
                    coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                }
                InputMode.BREW_SIZE_TO_BOTH -> {
                    val brewMl = amount
                    val absorptionFactor = 2.0f
                    val divisor = effectiveRatio - absorptionFactor
                    if (divisor > 0f) {
                        coffeeG = brewMl / divisor
                        waterG = coffeeG * effectiveRatio
                    } else {
                        // For espresso/moka where ratio ≤ absorption, treat as water input
                        waterG = brewMl
                        coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                    }
                }
                InputMode.CUP_SIZE_TO_BOTH -> {
                    waterG = amount
                    coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                }
            }

            val effectiveBloomMultiplier = if (state.bloomMultiplier.isNotEmpty()) {
                state.bloomMultiplier.toFloatOrNull() ?: method.bloomMultiplier
            } else {
                method.bloomMultiplier
            }
            val bloomG = if (method.hasBloom) coffeeG * effectiveBloomMultiplier else 0f

            val remainingWaterG = waterG - bloomG

            val effectivePulseCount = if (state.pulseCount.isNotEmpty()) {
                state.pulseCount.toIntOrNull() ?: method.defaultPulses
            } else {
                method.defaultPulses
            }

            val pulseSizeG = if (method.hasPulses && effectivePulseCount > 0) {
                remainingWaterG / effectivePulseCount
            } else {
                0f
            }

            val timeTargetLowS = method.timeTargetLow.let {
                if (state.isDecafBrew) (it - 30).coerceAtLeast(120) else it
            }
            val timeTargetHighS = method.timeTargetHigh.let {
                if (state.isDecafBrew) (it - 30).coerceAtLeast(150) else it
            }

            val refillCount = if (method.capacityMaxG != null && waterG > method.capacityMaxG) {
                kotlin.math.ceil(waterG.toDouble() / method.capacityMaxG).toInt() - 1
            } else {
                0
            }

            val ratioWarning = when {
                effectiveRatio <= 0f -> "Ratio must be greater than zero"
                coffeeG <= 0f || waterG <= 0f -> null
                method == BrewMethod.ESPRESSO -> null
                effectiveRatio < 10f -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually strong"
                effectiveRatio > 20f -> "Ratio 1:${"%.1f".format(effectiveRatio)} is unusually weak"
                else -> null
            }

            val bloomWarning = if (method.hasBloom && bloomG > waterG && waterG > 0f) {
                "Bloom (${"%.0f".format(bloomG)}g) exceeds total water (${"%.0f".format(waterG)}g)"
            } else {
                null
            }

            val grindResult = resolveGrindResult(
                grinderId = state.selectedGrinderId,
                method = method,
                filterType = state.filterType,
                calibrationStyle = state.calibrationStyle,
                isDecaf = state.isDecafBrew,
            )

            val timerPhases = buildTimerPhases(
                method = method,
                bloomG = bloomG,
                pulseSizeG = pulseSizeG,
                effectivePulseCount = effectivePulseCount,
                waterG = waterG,
                timeTargetLowS = timeTargetLowS,
                isDecaf = state.isDecafBrew,
            )

            state.copy(
                coffeeG = coffeeG,
                waterG = waterG,
                effectiveRatio = effectiveRatio,
                bloomG = bloomG,
                remainingWaterG = remainingWaterG,
                pulseSizeG = pulseSizeG,
                effectivePulseCount = effectivePulseCount,
                timeTargetLowS = timeTargetLowS,
                timeTargetHighS = timeTargetHighS,
                grindResult = grindResult,
                refillCount = refillCount,
                ratioWarning = ratioWarning,
                bloomWarning = bloomWarning,
                timerPhases = timerPhases,
            )
        }
        timerController.setPhases(_uiState.value.timerPhases)
    }

    private fun resolveGrindResult(
        grinderId: String?,
        method: BrewMethod,
        filterType: FilterType?,
        calibrationStyle: CalibrationStyle?,
        isDecaf: Boolean = false,
    ): GrindResult {
        if (grinderId == null) {
            return GrindResult.Generic(method.defaultGrindDescriptor)
        }

        val grinder = grinderData.grinders.find { it.id == grinderId }
            ?: return GrindResult.Generic(method.defaultGrindDescriptor)

        // Try exact filterType match first, then fall back to filter-agnostic recommendation
        var recommendation = grinderData.recommendations.find { rec ->
            rec.grinderId == grinder.id &&
                rec.methodId == method.name &&
                rec.filterType == filterType
        } ?: grinderData.recommendations.find { rec ->
            rec.grinderId == grinder.id &&
                rec.methodId == method.name &&
                rec.filterType == null
        } ?: return GrindResult.Generic(method.defaultGrindDescriptor)

        // Decaf offset: grind finer by ~2 steps (decaf beans are more brittle, extract faster)
        if (isDecaf) {
            val decafSteps = 2
            val offset = recommendation.adjustmentStepSize * decafSteps
            recommendation = recommendation.copy(
                suggestedStart = (recommendation.suggestedStart - offset)
                    .coerceAtLeast(recommendation.rangeStart),
                adjustmentNote = recommendation.adjustmentNote + " · Decaf: ${decafSteps} steps finer",
            )
        }

        if (calibrationStyle == null) {
            return GrindResult.Specific(recommendation)
        }

        val multiplier = calibrationStyle.rangeWidthMultiplier
        val midpoint = (recommendation.rangeStart + recommendation.rangeEnd) / 2f
        val halfWidth = (recommendation.rangeEnd - recommendation.rangeStart) / 2f
        val adjustedStart = midpoint - halfWidth * multiplier
        val adjustedEnd = midpoint + halfWidth * multiplier

        return GrindResult.Specific(
            recommendation.copy(
                rangeStart = adjustedStart,
                rangeEnd = adjustedEnd,
            ),
        )
    }

    /**
     * Redistributes time drift across remaining TIMED phases.
     * Positive [drift] = user finished early (add time), negative = late (subtract).
     * Guardrail: no phase drops below 50% of its original duration.
     */
    private fun rebalancePhases(
        phases: List<BrewPhase>,
        fromIndex: Int,
        drift: Int,
    ): List<BrewPhase> {
        val timedIndices = (fromIndex..phases.lastIndex)
            .filter { phases[it].mode == PhaseMode.TIMED }
        if (timedIndices.isEmpty()) return phases

        val totalTimedDuration = timedIndices.sumOf { phases[it].durationSeconds }
        if (totalTimedDuration == 0) return phases

        val mutable = phases.toMutableList()
        var remainingDrift = drift
        for (idx in timedIndices) {
            val phase = mutable[idx]
            val proportion = phase.durationSeconds.toFloat() / totalTimedDuration
            val adjustment = (drift * proportion).toInt()
            val minDuration = (phase.durationSeconds / 2).coerceAtLeast(1)
            val newDuration = (phase.durationSeconds + adjustment).coerceAtLeast(minDuration)
            val actualAdjustment = newDuration - phase.durationSeconds
            remainingDrift -= actualAdjustment
            mutable[idx] = phase.copy(durationSeconds = newDuration)
        }
        return mutable
    }

    private fun buildTimerPhases(
        method: BrewMethod,
        bloomG: Float,
        pulseSizeG: Float,
        effectivePulseCount: Int,
        waterG: Float,
        timeTargetLowS: Int,
        isDecaf: Boolean = false,
    ): List<BrewPhase> {
        val phases = mutableListOf<BrewPhase>()
        var cumulative = 0f
        val isPulsar = method == BrewMethod.PULSAR
        val capacity = method.capacityMaxG?.toFloat()

        // Tracks water poured since last drain to know when to refill
        var waterSinceDrain = 0f

        if (method.hasBloom && bloomG > 0f) {
            cumulative += bloomG
            waterSinceDrain += bloomG
            phases.add(
                BrewPhase(
                    name = "Bloom",
                    phaseType = PhaseType.BLOOM,
                    mode = if (isPulsar) PhaseMode.EVENT_GATED else PhaseMode.TIMED,
                    waterG = bloomG,
                    cumulativeWaterG = cumulative,
                    durationSeconds = when {
                        isDecaf && isPulsar -> 35
                        isDecaf -> 30
                        isPulsar -> 50
                        else -> 45
                    },
                    instruction = if (isPulsar) {
                        val decafHint = if (isDecaf) " · Decaf: shorter steep" else ""
                        "Valve OPEN → pour to ${"%.0f".format(cumulative)}g → wait ~10s → CLOSE valve → gentle swirl$decafHint"
                    } else {
                        "Pour to ${"%.0f".format(cumulative)}g, let CO₂ escape"
                    },
                    valveState = if (isPulsar) "open → close" else "",
                ),
            )
        }

        if (method.hasPulses && effectivePulseCount > 0 && pulseSizeG > 0f) {
            val pourDuration = if (effectivePulseCount > 0) {
                val bloomTime = if (method.hasBloom) { if (isPulsar) 50 else 45 } else 0
                val totalPourTime = timeTargetLowS - bloomTime - 30
                (totalPourTime.coerceAtLeast(effectivePulseCount) / effectivePulseCount)
                    .coerceAtLeast(1)
            } else {
                30
            }

            var drainCount = 0
            for (i in 1..effectivePulseCount) {
                // Insert drain phase when next pulse would exceed capacity
                if (capacity != null && waterSinceDrain + pulseSizeG > capacity) {
                    drainCount++
                    phases.add(
                        BrewPhase(
                            name = "Drain & Refill" +
                                if (drainCount > 1) " $drainCount" else "",
                            phaseType = PhaseType.DRAIN_AND_REFILL,
                            mode = PhaseMode.EVENT_GATED,
                            waterG = 0f,
                            cumulativeWaterG = cumulative,
                            durationSeconds = 30,
                            instruction = if (isPulsar) {
                                "Let it drain until slurry drops · then continue pouring"
                            } else {
                                "Let it drain, then continue"
                            },
                            valveState = if (isPulsar) "open" else "",
                        ),
                    )
                    waterSinceDrain = 0f
                }

                cumulative += pulseSizeG
                waterSinceDrain += pulseSizeG
                val isFirst = i == 1
                val isLast = i == effectivePulseCount
                phases.add(
                    BrewPhase(
                        name = "Pour $i/$effectivePulseCount",
                        phaseType = PhaseType.POUR,
                        mode = PhaseMode.TIMED,
                        waterG = pulseSizeG,
                        cumulativeWaterG = cumulative,
                        durationSeconds = pourDuration,
                        instruction = if (isPulsar) {
                            buildString {
                                if (isFirst) append("OPEN valve → ")
                                append("Pour to ${"%.0f".format(cumulative)}g")
                                append(" · keep slurry ~1cm above bed")
                                if (isLast) append(" → gentle swirl")
                            }
                        } else {
                            "Pour to ${"%.0f".format(cumulative)}g (+${"%.0f".format(pulseSizeG)}g)"
                        },
                        valveState = if (isPulsar) "open" else "",
                    ),
                )
            }
        } else if (!method.hasPulses && waterG > 0f) {
            val pourWater = waterG - cumulative
            if (pourWater > 0f) {
                cumulative += pourWater
                val pourDuration = (timeTargetLowS - 30).coerceAtLeast(1)
                phases.add(
                    BrewPhase(
                        name = "Pour",
                        phaseType = PhaseType.POUR,
                        mode = PhaseMode.TIMED,
                        waterG = pourWater,
                        cumulativeWaterG = cumulative,
                        durationSeconds = pourDuration,
                        instruction = "Pour to ${"%.0f".format(cumulative)}g total",
                    ),
                )
            }
        }

        if (phases.isNotEmpty()) {
            phases.add(
                BrewPhase(
                    name = "Drawdown",
                    phaseType = PhaseType.DRAWDOWN,
                    mode = PhaseMode.EVENT_GATED,
                    waterG = 0f,
                    cumulativeWaterG = cumulative,
                    durationSeconds = 30,
                    instruction = if (isPulsar) {
                        "Valve open · let it drain completely"
                    } else {
                        "Let it drain"
                    },
                    valveState = if (isPulsar) "open" else "",
                ),
            )
        }

        return phases
    }

    private fun computePhaseIndex(phases: List<BrewPhase>, elapsedSeconds: Int): Int {
        if (phases.isEmpty()) return 0
        var cumulativeTime = 0
        for (i in phases.indices) {
            cumulativeTime += phases[i].durationSeconds
            if (elapsedSeconds <= cumulativeTime) return i
        }
        return phases.lastIndex
    }

    @VisibleForTesting
    internal fun setUiStateForTesting(state: BrewUiState) {
        _uiState.value = state
    }
}

private fun List<String>.sanitizeKnownValues(): List<String> = this
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sorted()

