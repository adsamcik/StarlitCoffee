package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.PhaseMode
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RatioPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.audio.BrewAudioManager
import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.domain.TimerController
import com.adsamcik.starlitcoffee.service.TimerStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

data class BagPhotoProcessingResult(
    val ocrPrefill: OcrFieldExtractor.OcrExtractionResult? = null,
    val capturedPhotoUris: String? = null,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
    val offLookupName: String? = null,
    val offLookupRoaster: String? = null,
)

class BrewViewModel(
    private val recipeRepository: RecipeRepository? = null,
    private val brewLogRepository: BrewLogRepository? = null,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
    private val ratioPresetRepository: RatioPresetRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val grinderData: GrinderDataProvider = DefaultGrinders,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrewUiState())
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()
    private val _savedRecipes = MutableStateFlow(emptyList<SavedRecipeEntity>())
    val savedRecipes: StateFlow<List<SavedRecipeEntity>> = _savedRecipes.asStateFlow()
    private val _brewLogs = MutableStateFlow(emptyList<BrewLogEntity>())
    val brewLogs: StateFlow<List<BrewLogEntity>> = _brewLogs.asStateFlow()
    private val _coffeeBags = MutableStateFlow(emptyList<CoffeeBagEntity>())
    val coffeeBags: StateFlow<List<CoffeeBagEntity>> = _coffeeBags.asStateFlow()
    private val _selectedBagId = MutableStateFlow<Long?>(null)
    val selectedBagId: StateFlow<Long?> = _selectedBagId.asStateFlow()
    private var lastLoggedBrewId: Long? = null
    private val _bagPhotoResult = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val bagPhotoResult: StateFlow<BagPhotoProcessingResult?> = _bagPhotoResult.asStateFlow()

    private var _audioManager: BrewAudioManager? = null
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
            coffeeBagRepository?.getAllBags()?.collect { bags ->
                _coffeeBags.value = bags
            }
        }
        collectRatioPresets(_uiState.value.method)
        recalculate()
        applyUserDefaults()
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
        _uiState.update { it.copy(inputMode = mode) }
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
            _audioManager!!.startMonitoring()
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

    // --- Audio Monitoring ---

    /**
     * Initializes the audio manager with the given output directory for recordings.
     * Call once from the UI layer when RECORD_AUDIO permission is granted.
     */
    fun initAudioManager(outputDirectory: java.io.File) {
        if (_audioManager != null) return
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

    fun selectBag(bagId: Long?) {
        _selectedBagId.value = bagId
        // Auto-set decaf from bag
        val bag = _coffeeBags.value.find { it.id == bagId }
        _uiState.update { it.copy(isDecafBrew = bag?.isDecaf ?: false) }
        recalculate()
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
        photoUri: String? = null,
        photoUris: String? = null,
        traceabilityUrl: String? = null,
        status: String = "SEALED",
    ) {
        val repository = coffeeBagRepository ?: return
        viewModelScope.launch {
            repository.insertBag(
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
                    barcode = barcode,
                    weightG = weightG,
                    initialWeightG = weightG,
                    priceAmount = priceAmount,
                    priceCurrency = priceCurrency,
                    notes = notes,
                    photoUri = photoUri,
                    photoUris = photoUris,
                    traceabilityUrl = traceabilityUrl,
                    status = status,
                ),
            )
        }
    }

    fun updateCoffeeBag(entity: CoffeeBagEntity) {
        val repository = coffeeBagRepository ?: return
        viewModelScope.launch {
            repository.updateBag(entity)
        }
    }

    fun deleteCoffeeBag(entity: CoffeeBagEntity) {
        val repository = coffeeBagRepository ?: return
        viewModelScope.launch {
            repository.deleteBag(entity)
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
        val repository = coffeeBagRepository ?: run {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val bag = repository.findByBarcode(barcode)
            onResult(bag)
        }
    }


    fun processNewBagPhotos(photosCsv: String, knownRoasters: List<String>, knownNames: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val photoUriList = photosCsv.split(",")
                val ocrResults = mutableListOf<OcrFieldExtractor.OcrExtractionResult>()
                var detectedBarcode: String? = null
                var detectedQrUrl: String? = null

                for (uriStr in photoUriList) {
                    try {
                        val file = java.io.File(android.net.Uri.parse(uriStr).path ?: continue)
                        val rawBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: continue

                        val bitmap = ImagePreprocessor.applyExifRotation(rawBitmap, file.absolutePath)

                        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
                        )

                        val originalText = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            recognizer.process(
                                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0),
                            )
                                .addOnSuccessListener { text -> cont.resume(text, null) }
                                .addOnFailureListener { cont.resume(null, null) }
                        }

                        val aligned = if (originalText != null && originalText.textBlocks.isNotEmpty()) {
                            val alignment = ImagePreprocessor.computeAlignment(originalText.textBlocks)
                            ImagePreprocessor.applyAlignment(bitmap, alignment)
                        } else {
                            bitmap
                        }

                        val enhanced = ImagePreprocessor.preprocessForOcr(aligned)
                        val enhancedText = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            recognizer.process(
                                com.google.mlkit.vision.common.InputImage.fromBitmap(enhanced, 0),
                            )
                                .addOnSuccessListener { text -> cont.resume(text, null) }
                                .addOnFailureListener { cont.resume(null, null) }
                        }

                        val origResult = originalText?.let { text ->
                            val blocks = text.textBlocks.map { block ->
                                OcrFieldExtractor.OcrTextBlock(
                                    text = block.text,
                                    heightPx = block.boundingBox?.height() ?: 0,
                                    topPx = block.boundingBox?.top ?: 0,
                                )
                            }
                            OcrFieldExtractor.extractFieldsFromBlocks(blocks, knownRoasters, knownNames)
                        }
                        val enhResult = enhancedText?.let { text ->
                            val blocks = text.textBlocks.map { block ->
                                OcrFieldExtractor.OcrTextBlock(
                                    text = block.text,
                                    heightPx = block.boundingBox?.height() ?: 0,
                                    topPx = block.boundingBox?.top ?: 0,
                                )
                            }
                            OcrFieldExtractor.extractFieldsFromBlocks(blocks, knownRoasters, knownNames)
                        }
                        if (origResult != null || enhResult != null) {
                            ocrResults.add(
                                OcrFieldExtractor.OcrExtractionResult(
                                    name = enhResult?.name ?: origResult?.name,
                                    roaster = enhResult?.roaster ?: origResult?.roaster,
                                    origin = enhResult?.origin ?: origResult?.origin,
                                    region = enhResult?.region ?: origResult?.region,
                                    variety = enhResult?.variety ?: origResult?.variety,
                                    processType = enhResult?.processType ?: origResult?.processType,
                                    altitude = enhResult?.altitude ?: origResult?.altitude,
                                    tastingNotes = enhResult?.tastingNotes ?: origResult?.tastingNotes,
                                    roastLevel = enhResult?.roastLevel ?: origResult?.roastLevel,
                                    roastDate = enhResult?.roastDate ?: origResult?.roastDate,
                                    weight = enhResult?.weight ?: origResult?.weight,
                                ),
                            )
                        }
                        val allOcrText = listOfNotNull(originalText?.text, enhancedText?.text)
                            .joinToString("\n")
                        if (detectedBarcode == null && allOcrText.isNotEmpty()) {
                            detectedBarcode = OcrFieldExtractor.extractBarcodeFromText(allOcrText)
                        }

                        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                        val barcodes = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            scanner.process(
                                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0),
                            )
                                .addOnSuccessListener { codes -> cont.resume(codes, null) }
                                .addOnFailureListener { cont.resume(null, null) }
                        }
                        barcodes?.forEach { code ->
                            val raw = code.rawValue ?: return@forEach
                            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                                if (detectedQrUrl == null) detectedQrUrl = raw
                            } else {
                                if (detectedBarcode == null) detectedBarcode = raw
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(BAG_PHOTO_TAG, "Failed to process bag photo for OCR and barcode extraction", e)
                    }
                }

                val ocrPrefill = if (ocrResults.isNotEmpty()) {
                    OcrFieldExtractor.OcrExtractionResult(
                        name = ocrResults.firstNotNullOfOrNull { it.name },
                        roaster = ocrResults.firstNotNullOfOrNull { it.roaster },
                        origin = ocrResults.firstNotNullOfOrNull { it.origin },
                        region = ocrResults.firstNotNullOfOrNull { it.region },
                        variety = ocrResults.firstNotNullOfOrNull { it.variety },
                        processType = ocrResults.firstNotNullOfOrNull { it.processType },
                        altitude = ocrResults.firstNotNullOfOrNull { it.altitude },
                        tastingNotes = ocrResults.firstNotNullOfOrNull { it.tastingNotes },
                        roastLevel = ocrResults.firstNotNullOfOrNull { it.roastLevel },
                        roastDate = ocrResults.firstNotNullOfOrNull { it.roastDate },
                        weight = ocrResults.firstNotNullOfOrNull { it.weight },
                    )
                } else {
                    null
                }

                var offLookupName: String? = null
                var offLookupRoaster: String? = null
                detectedBarcode?.let { barcode ->
                    try {
                        val result = OpenFoodFactsClient.lookupBarcode(barcode)
                        if (result != null) {
                            offLookupName = result.name
                            offLookupRoaster = result.brand
                        }
                    } catch (e: Exception) {
                        Log.w(BAG_PHOTO_TAG, "Failed to fetch product info from OpenFoodFacts", e)
                    }
                }

                _bagPhotoResult.value = BagPhotoProcessingResult(
                    ocrPrefill = ocrPrefill,
                    capturedPhotoUris = photosCsv,
                    detectedBarcode = detectedBarcode,
                    detectedQrUrl = detectedQrUrl,
                    offLookupName = offLookupName,
                    offLookupRoaster = offLookupRoaster,
                )
            }
        }
    }

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

