package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.db.dao.UserBarcodeStemDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.DecafProcess
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.HomeContextCard
import com.adsamcik.starlitcoffee.data.model.InventoryAlert
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.data.network.QrLinkExploreResult
import com.adsamcik.starlitcoffee.data.network.QrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.llm.LlmCacheKey
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RatioPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.domain.pickWeightedBloomSpritesheetId
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
import com.adsamcik.starlitcoffee.util.InventoryAlertEngine
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class GrindResult {
    data class Generic(val descriptor: GrindDescriptor) : GrindResult()
    data class Specific(
        val recommendation: GrindRecommendation,
        val grinder: Grinder,
    ) : GrindResult()
}

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
    // Remembers the user's filter selection per method, so round-tripping
    // (e.g. PULSAR -> V60 -> PULSAR) restores the prior filter rather than
    // dropping it. FilterType is only meaningful for Pulsar; entries for
    // other methods are stored but never restored as filterType.
    val lastFilterByMethod: Map<BrewMethod, FilterType?> = emptyMap(),
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
    val effectiveBloomDurationSeconds: Int = BrewMethod.PULSAR.bloomDurationSeconds,
    val retainedWaterG: Float = 0f,
    val predictedCupVolumeG: Float = 0f,
    // Timer state
    val timerRunning: Boolean = false,
    val elapsedSeconds: Int = 0,
    val bloomMarkedAtSeconds: Int? = null,
    val bloomCountdownSeconds: Int? = null,
    val bloomFinished: Boolean = false,
    // ID of the bloom spritesheet picked for this brew session. Selected once
    // when bloom is marked (or eagerly via selectBloomSpritesheetIfNeeded) so
    // the same flower is shown during bloom and in the post-bloom flash on
    // BrewTimerScreen — fixes the "final flower differs from the blooming
    // flower" regression. Cleared by stopTimer / resetBrew / startNewBrewSession.
    val bloomSpritesheetId: String? = null,
    val minuteAlertEnabled: Boolean = true,
    val showFeedbackSnackbar: Boolean = false,
    // Decaf
    // Derived from manualDecafOverride (user intent) or selected bag. Always kept in sync by recalculate().
    val isDecafBrew: Boolean = false,
    // User override for decaf. null = inherit from selected bag (or false if no bag).
    // Non-null = user explicitly set decaf state; sticks across bag changes until cleared.
    val manualDecafOverride: Boolean? = null,
    // True when a bag is selected AND user override disagrees with bag.isDecaf.
    val decafMismatchWithBag: Boolean = false,
    // Feedback state
    val tasteFeedback: TasteFeedback? = null,
    val rating: Int = 0,
    val feedbackNotes: String = "",
)

class BrewViewModel(
    private val application: Application? = null,
    private val recipeRepository: RecipeRepository? = null,
    private val brewLogRepository: BrewLogRepository? = null,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
    private val ratioPresetRepository: RatioPresetRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val grinderData: GrinderDataProvider = DefaultGrinders,
    private val qrLinkMetadataExplorer: QrLinkMetadataExplorer = SafeQrLinkMetadataExplorer(),
    private val llmProvider: LlmInferenceProvider = StubLlmInferenceProvider(),
    private val userBarcodeStemDao: UserBarcodeStemDao? = null,
) : ViewModel() {

    private val llmCache = LlmResultCache()

    private val _uiState = MutableStateFlow(BrewUiState())
    private fun str(@StringRes resId: Int, vararg args: Any): String {
        return application?.getString(resId, *args) ?: ""
    }
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
    private val _homeContextCard = MutableStateFlow<HomeContextCard?>(null)
    val homeContextCard: StateFlow<HomeContextCard?> = _homeContextCard.asStateFlow()
    private val _bagPhotoResult = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val bagPhotoResult: StateFlow<BagPhotoProcessingResult?> = _bagPhotoResult.asStateFlow()
    private val _inventoryAlerts = MutableStateFlow(emptyList<InventoryAlert>())
    val inventoryAlerts: StateFlow<List<InventoryAlert>> = _inventoryAlerts.asStateFlow()

    private var timerJob: Job? = null
    private var bloomCountdownJob: Job? = null
    private var timerStartMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L
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
                refreshHomeContextCard()
                refreshInventoryAlerts()
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
                refreshHomeContextCard()
                refreshInventoryAlerts()
            }
        }
        collectRatioPresets(_uiState.value.method)
        recalculate()
        applyUserDefaults()
        refreshLastUnrated()
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
        _uiState.update { state ->
            // Snapshot the outgoing method's filter selection so we can restore
            // it later if the user switches back. Always store, even nulls, so
            // an explicit "no filter" choice is preserved.
            val updatedMap = state.lastFilterByMethod + (state.method to state.filterType)
            // FilterType is a Pulsar-specific concept (paper / 19K / 40K mesh).
            // Clear it for any other method; restore the last-known filter
            // when returning to PULSAR.
            val newFilter = if (method == BrewMethod.PULSAR) updatedMap[method] else null
            state.copy(
                method = method,
                filterType = newFilter,
                lastFilterByMethod = updatedMap,
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
        _uiState.update { state ->
            state.copy(
                filterType = type,
                lastFilterByMethod = state.lastFilterByMethod + (state.method to type),
            )
        }
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
        if (timerJob?.isActive == true) return
        _uiState.update { it.copy(timerRunning = true) }
        timerStartMs = System.nanoTime() / 1_000_000L
        launchTimerLoop()
        resumeBloomCountdownIfNeeded()
    }

    private fun resumeBloomCountdownIfNeeded() {
        val state = _uiState.value
        if (state.bloomMarkedAtSeconds == null) return
        if (state.bloomFinished) return
        val remaining = state.bloomCountdownSeconds ?: return
        if (remaining <= 0) return

        bloomCountdownJob?.cancel()
        bloomCountdownJob = viewModelScope.launch {
            for (tick in remaining - 1 downTo 0) {
                delay(1000L)
                _uiState.update { it.copy(bloomCountdownSeconds = tick) }
            }
            _uiState.update { it.copy(bloomFinished = true, bloomCountdownSeconds = 0) }
        }
    }

    private fun launchTimerLoop() {
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerRunning) {
                delay(250L)
                val nowMs = System.nanoTime() / 1_000_000L
                val totalElapsedMs = pausedAccumulatedMs + (nowMs - timerStartMs)
                val totalElapsedSeconds = (totalElapsedMs / 1000).toInt()
                _uiState.update { it.copy(elapsedSeconds = totalElapsedSeconds) }
                TimerStateHolder.instance.update(
                    phaseName = "",
                    elapsedSeconds = totalElapsedSeconds,
                    instruction = "",
                    isRunning = true,
                )
            }
        }
    }

    /**
     * Ensures the timer coroutine is running. Call on app resume to recover
     * from Doze or battery optimization pausing the coroutine.
     * Does NOT reset the clock — wall-clock anchoring handles the gap.
     */
    fun ensureTimerRunning() {
        if (!_uiState.value.timerRunning) return
        if (timerJob?.isActive == true) return
        launchTimerLoop()
    }

    fun pauseTimer() {
        val nowMs = System.nanoTime() / 1_000_000L
        pausedAccumulatedMs += (nowMs - timerStartMs)
        _uiState.update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerJob = null
        bloomCountdownJob?.cancel()
        TimerStateHolder.instance.update(
            phaseName = "",
            elapsedSeconds = _uiState.value.elapsedSeconds,
            instruction = "",
            isRunning = false,
        )
    }

    fun stopTimer() {
        pausedAccumulatedMs = 0L
        bloomCountdownJob?.cancel()
        bloomCountdownJob = null
        _uiState.update {
            it.copy(
                timerRunning = false,
                elapsedSeconds = 0,
                bloomMarkedAtSeconds = null,
                bloomCountdownSeconds = null,
                bloomFinished = false,
                bloomSpritesheetId = null,
            )
        }
        timerJob?.cancel()
        timerJob = null
        TimerStateHolder.instance.reset()
    }

    fun advancePhase() {
        // Advances the active phase index during guided brewing.
        // Phase list and order are preserved — only the pointer moves.
    }

    /**
     * Picks a bloom spritesheet for the current brew session if one hasn't
     * been picked yet. Idempotent — repeated calls are no-ops once an ID is
     * set, so screens can safely call this on every recomposition while
     * weights are loading. Cleared by [stopTimer] / [resetBrew] / [startNewBrewSession].
     *
     * The selection is biased toward less-frequently-shown flowers so every
     * enabled flower gets fair rotation over many brews — see
     * [pickWeightedBloomSpritesheetId] for the weighting math.
     */
    fun selectBloomSpritesheetIfNeeded(weights: Map<String, Int>) {
        if (_uiState.value.bloomSpritesheetId != null) return
        viewModelScope.launch {
            // Re-check after the suspending preferences read in case another
            // launch raced ahead of us and already claimed the slot.
            if (_uiState.value.bloomSpritesheetId != null) return@launch
            val displayCounts = userPreferencesRepository
                ?.userPreferences
                ?.first()
                ?.bloomSpritesheetDisplayCounts
                ?: emptyMap()
            if (_uiState.value.bloomSpritesheetId != null) return@launch

            val pickedId = pickWeightedBloomSpritesheetId(weights, displayCounts) ?: return@launch
            val resolved = _uiState.updateAndGet { current ->
                if (current.bloomSpritesheetId == null) current.copy(bloomSpritesheetId = pickedId)
                else current
            }
            // Only record the display if our pick is the one that actually
            // landed in state — if a parallel call won the race we mustn't
            // double-count its flower or attribute it to the wrong id.
            if (resolved.bloomSpritesheetId == pickedId) {
                userPreferencesRepository?.incrementBloomSpritesheetDisplayCount(pickedId)
            }
        }
    }

    fun markBloom() {
        val state = _uiState.value
        if (state.bloomMarkedAtSeconds != null) return
        if (!state.timerRunning) return

        val duration = state.effectiveBloomDurationSeconds
        _uiState.update {
            it.copy(
                bloomMarkedAtSeconds = it.elapsedSeconds,
                bloomCountdownSeconds = duration,
                bloomFinished = false,
            )
        }

        bloomCountdownJob?.cancel()
        bloomCountdownJob = viewModelScope.launch {
            for (remaining in duration - 1 downTo 0) {
                delay(1000L)
                _uiState.update { it.copy(bloomCountdownSeconds = remaining) }
            }
            _uiState.update { it.copy(bloomFinished = true, bloomCountdownSeconds = 0) }
        }
    }

    fun toggleMinuteAlert() {
        _uiState.update { it.copy(minuteAlertEnabled = !it.minuteAlertEnabled) }
    }

    companion object {
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
            "isDecaf",
            "weight",
        )
    }

    fun requestFeedbackSnackbar() {
        _uiState.update { it.copy(showFeedbackSnackbar = true) }
    }

    fun clearFeedbackSnackbar() {
        _uiState.update { it.copy(showFeedbackSnackbar = false) }
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
                    isDecaf = state.isDecafBrew,
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
                // Recipe is an explicit user intent for decaf → set override.
                manualDecafOverride = entity.isDecaf,
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
                    isDecaf = state.isDecafBrew,
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
        // Decaf derivation now happens in recalculate() from manualDecafOverride + bag.isDecaf.
        // We no longer silently clobber user intent here.

        // Auto-switch brew method to the one most recently used with this bag, if any.
        // Derives from in-memory brew logs (already collected into _brewLogs); no DB round-trip.
        if (bagId != null) {
            val lastMethod = _brewLogs.value
                .asSequence()
                .filter { it.coffeeBagId == bagId }
                .mapNotNull { log ->
                    runCatching { BrewMethod.valueOf(log.method) }.getOrNull()
                }
                .firstOrNull()
            if (lastMethod != null && lastMethod != _uiState.value.method) {
                // setMethod() also refreshes presets/ratios and calls recalculate().
                setMethod(lastMethod)
                return
            }
        }
        recalculate()
    }

    /**
     * Select a bag for brewing, auto-transitioning SEALED → OPEN with today's opened date.
     */
    fun selectBagForBrewing(bagId: Long) {
        val repository = coffeeBagRepository ?: return
        val bag = _coffeeBags.value.find { it.id == bagId }
        if (bag != null && bag.status == "SEALED") {
            viewModelScope.launch {
                repository.updateBag(
                    bag.copy(status = "OPEN", openedDate = System.currentTimeMillis()),
                )
            }
        }
        selectBag(bagId)
    }

    fun refreshLastUnrated() {
        viewModelScope.launch {
            _lastUnratedBrew.value = brewLogRepository?.getLastUnratedLog()
            refreshHomeContextCard()
        }
    }

    fun refreshHomeContextCard() {
        _homeContextCard.value = HomeContextCard.resolve(
            bags = _coffeeBags.value,
            brewLogs = _brewLogs.value,
            selectedBagId = _selectedBagId.value,
        )
    }

    fun refreshInventoryAlerts() {
        _inventoryAlerts.value = InventoryAlertEngine.buildAlerts(
            bags = _coffeeBags.value,
            brewLogs = _brewLogs.value,
        )
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
        // Writes into manualDecafOverride (user intent); recalculate() derives isDecafBrew.
        _uiState.update { it.copy(manualDecafOverride = isDecaf) }
        recalculate()
    }

    /** Clear the manual decaf override so decaf once again follows the selected bag. */
    fun clearDecafOverride() {
        _uiState.update { it.copy(manualDecafOverride = null) }
        recalculate()
    }

    /**
     * Resolve any bag/brew decaf mismatch by adopting the bag's decaf status.
     * Equivalent to clearDecafOverride() semantically but named for the user-facing action.
     */
    fun syncDecafToBag() {
        clearDecafOverride()
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
        decafProcess: String? = null,
        photoUri: String? = null,
        photoUris: String? = null,
        traceabilityUrl: String? = null,
        status: String = "SEALED",
        onBagAdded: ((Long) -> Unit)? = null,
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
                    decafProcess = decafProcess?.takeIf { isDecaf },
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
            val newId = repository.insertBag(entity)

            // Learn barcode→roaster mapping for future scans
            val stemDao = userBarcodeStemDao
            if (stemDao != null && normalizedBarcode != null && roaster != null) {
                BarcodeInsights.learnStem(normalizedBarcode, roaster, stemDao)
            }

            loadKnownFieldValues()
            onBagAdded?.invoke(newId)
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
                    val rawDetectedBarcode = detectedBarcode
                    detectedBarcode = BarcodeInsights.normalizeBarcode(detectedBarcode)
                        ?: detectedBarcode?.trim()?.takeIf { it.isNotBlank() }

                    // Partial barcode recovery: if normalize rejected a 5–7 digit fragment,
                    // try to extract roaster hints from it (but do NOT persist as bag barcode)
                    if (BarcodeInsights.normalizeBarcode(rawDetectedBarcode) == null &&
                        rawDetectedBarcode != null
                    ) {
                        val partialResult = BarcodeInsights.recoverPartialBarcode(
                            rawDetectedBarcode,
                            userBarcodeStemDao,
                        )
                        if (partialResult.isPartial && partialResult.candidates.isNotEmpty()) {
                            allCandidates += partialResult.candidates
                            detectedBarcode = null
                        }
                    }

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

                    // Also add user-taught stem candidates
                    val stemDao = userBarcodeStemDao
                    if (stemDao != null && detectedBarcode != null) {
                        allCandidates += BarcodeInsights.findUserStemMatch(detectedBarcode, stemDao)
                    }

                    // Boost OCR known values with barcode-resolved roaster for future scans
                    if (detectedBarcode != null) {
                        val boostedKnownValues = BarcodeInsights.buildBarcodeOcrBoost(
                            barcode = detectedBarcode,
                            currentKnownValues = knownFieldValues,
                            userStemDao = userBarcodeStemDao,
                        )
                        if (boostedKnownValues !== knownFieldValues) {
                            _knownFieldValues.value = boostedKnownValues
                        }
                    }

                    var offLookupName: String? = null
                    var offLookupRoaster: String? = null
                    detectedBarcode?.let { barcode ->
                        try {
                            val lookup = OpenFoodFactsClient.lookupBarcode(barcode)
                            if (lookup != null) {
                                offLookupName = lookup.name
                                offLookupRoaster = lookup.brand
                                val lookupSupportingText = listOfNotNull(
                                    lookup.name?.takeIf { it.isNotBlank() },
                                    lookup.brand?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").takeIf { it.isNotBlank() }
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
                                allCandidates.addDecafCandidate(
                                    isDecaf = lookupSupportingText
                                        ?.let(CoffeeMetadataNormalizer::containsDecafMarker)
                                        ?.takeIf { it },
                                    sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                                    confidenceHint = BagFieldConfidence.HIGH,
                                    rawValue = lookupSupportingText,
                                    supportingText = lookupSupportingText,
                                )

                                // OFF data — confidence gated because these fields may refer to country of sale, not origin
                                if (!lookup.origins.isNullOrBlank()) {
                                    allCandidates += BagFieldCandidate(
                                        fieldName = "origin",
                                        value = lookup.origins,
                                        sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                                        confidenceHint = BagFieldConfidence.MEDIUM,
                                        supportingText = "OFF origins field",
                                    )
                                }
                                lookup.countriesTags
                                    ?.firstNotNullOfOrNull(::coffeeProducingCountryFromTag)
                                    ?.let { originName ->
                                        allCandidates += BagFieldCandidate(
                                            fieldName = "origin",
                                            value = originName,
                                            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
                                            confidenceHint = BagFieldConfidence.LOW,
                                            supportingText = "OFF countries_tags (may be country of sale)",
                                        )
                                    }
                                // lookup.labels — informational only (organic, fair trade, etc.); not mapped to bag fields yet
                            }
                        } catch (e: Exception) {
                            Log.w(BAG_PHOTO_TAG, "Failed to fetch product info from OpenFoodFacts", e)
                        }
                    }

                    val qrEnrichment= buildQrLinkEnrichment(rawDetectedQrUrl)
                    allCandidates += qrEnrichment.candidates

                    // LLM enrichment — primary-source reasoning over all core fields.
                    // Receives prior candidates (OCR+lookup) for cross-reference,
                    // raw OCR text for character-level verification, and the user's
                    // known-values vocabulary for grounding.
                    val llmCandidates = tryLlmEnrichment(
                        photoUriList = photoUriList,
                        processedPhotos = processedPhotos,
                        allCandidates = allCandidates,
                        combinedOcrText = combinedOcrText,
                        knownFieldValues = _knownFieldValues.value,
                    )
                    allCandidates += llmCandidates

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
            val decafSupportingBlock = pass.blocks.firstOrNull { block ->
                CoffeeMetadataNormalizer.containsDecafMarker(block.text)
            }
            val decafSupportingText = decafSupportingBlock?.text
                ?: pass.fullText.lineSequence().map(String::trim).firstOrNull { line ->
                    CoffeeMetadataNormalizer.containsDecafMarker(line)
                }
            addDecafCandidate(
                isDecaf = pass.result.isDecaf,
                sourceType = BagFieldSourceType.OCR,
                confidenceHint = pass.result.fieldConfidence["isDecaf"] ?: confidenceHint,
                rawValue = decafSupportingText,
                supportingText = decafSupportingText,
                side = photo.side,
                previewUri = photo.uri,
                previewRect = decafSupportingBlock?.normalizedBounds(),
            )
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
                        message = "Ignored an unsafe QR link. Only public HTTPS pages are supported.",
                    ),
                ),
            )
        }
        if (safeUrl == null) return QrLinkEnrichment()

        // Never auto-explore — require user approval
        return QrLinkEnrichment(
            safeUrl = safeUrl,
            reviewHints = listOf(
                BagPhotoReviewHint(
                    severity = BagReviewSeverity.INFO,
                    message = "QR link found. Approve exploration to extract coffee details from the website.",
                ),
            ),
        )
    }

    fun exploreApprovedQrLink(url: String, onResult: (QrCoffeeMetadata?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    qrLinkMetadataExplorer.explore(url)
                }
                when (result) {
                    is QrLinkExploreResult.Success -> onResult(result.metadata)
                    is QrLinkExploreResult.Skipped -> onResult(null)
                }
            } catch (e: Exception) {
                Log.w("BrewViewModel", "QR link exploration failed", e)
                onResult(null)
            }
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
        addDecafCandidate(
            isDecaf = metadata.isDecaf,
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            confidenceHint = BagFieldConfidence.MEDIUM,
            rawValue = metadata.supportingSnippet ?: metadata.pageDescription ?: metadata.pageTitle,
            supportingText = buildQrSupportingText("isDecaf", metadata),
        )
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

    private fun MutableList<BagFieldCandidate>.addDecafCandidate(
        isDecaf: Boolean?,
        sourceType: BagFieldSourceType,
        confidenceHint: BagFieldConfidence,
        rawValue: String? = null,
        supportingText: String? = null,
        side: BagCaptureSide? = null,
        previewUri: String? = null,
        previewRect: BagPhotoRect? = null,
    ) {
        if (isDecaf != true) return
        val cleanRawValue = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: "decaf"
        add(
            BagFieldCandidate(
                fieldName = "isDecaf",
                value = "Decaf",
                rawValue = cleanRawValue,
                canonicalKey = "true",
                sourceType = sourceType,
                side = side,
                confidenceHint = confidenceHint,
                supportingText = supportingText,
                previewUri = previewUri,
                previewRect = previewRect,
            ),
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

    /**
     * Extracts a coffee-producing country name from an OFF `countries_tags` entry.
     * Tags use the format `en:ethiopia`, `en:costa-rica`, etc.
     * Returns the canonical display name if the tag matches a known [CoffeeOrigin.Known],
     * or null if the country is not a coffee producer.
     */
    private fun coffeeProducingCountryFromTag(tag: String): String? {
        val countryPart = tag.substringAfter(":", tag)
            .replace("-", " ")
            .trim()
            .lowercase()
        return CoffeeOrigin.Known.entries.firstOrNull { origin ->
            origin.displayName.lowercase() == countryPart
        }?.displayName
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
            "origin", "region", "processType", "tastingNotes", "isDecaf" ->
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
            normalizedBarcode != null && normalizedBarcode != rawBarcode -> {
                repository.findByBarcode(normalizedBarcode)
                    ?: repository.findByBarcode(rawBarcode)
            }
            normalizedBarcode != null -> repository.findByBarcode(normalizedBarcode)
            else -> repository.findByBarcode(rawBarcode)
        }
    }

    // --- LLM enrichment for photo pipeline ---

    private suspend fun tryLlmEnrichment(
        photoUriList: List<String>,
        processedPhotos: List<ProcessedBagPhoto>,
        allCandidates: List<BagFieldCandidate>,
        combinedOcrText: String,
        knownFieldValues: KnownFieldValues,
    ): List<BagFieldCandidate> {
        if (!llmProvider.isAvailable()) return emptyList()

        // Primary-source mode: ask the LLM about every field, not just the
        // ones OCR couldn't resolve. The LLM should be able to verify and,
        // where appropriate, correct OCR/lookup values by re-reading the label.
        val fieldsNeeded = BAG_PHOTO_FIELD_NAMES.toSet()

        // Assemble all HIGH/MEDIUM-confidence candidates already gathered, not
        // just OCR. The LLM prompt treats each source (USER / LOOKUP / OCR / LLM)
        // with different trust rules, so barcode/QR lookup results MUST reach
        // the LLM too — otherwise it will hallucinate over known product data.
        val existingFields = buildExistingFieldsContext(allCandidates)

        // Read the best-quality photo bytes
        val bestPhotoUri = processedPhotos.maxByOrNull { it.quality.blurScore }?.uri
            ?: photoUriList.firstOrNull()
        val photoBytes = bestPhotoUri?.let { uri ->
            try {
                java.io.File(Uri.parse(uri).path ?: return@let null).readBytes()
            } catch (e: Exception) {
                Log.w(BAG_PHOTO_TAG, "Failed to read photo bytes for LLM enrichment", e)
                null
            }
        } ?: return emptyList()

        return tryLlmEnrichment(
            photoBytes = photoBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = combinedOcrText.takeIf { it.isNotBlank() },
            knownFieldValues = knownFieldValues,
        )
    }

    /**
     * Collapse the candidate list to a single best value per field, mapped to
     * the [FieldSource] taxonomy the LLM prompt understands.
     *
     * Priority order: USER (if any exist in candidates — rare in photo path),
     * LOOKUP (barcode/QR), LLM (from prior runs, if any), OCR. Only
     * HIGH/MEDIUM-confidence candidates are forwarded to avoid feeding the LLM
     * low-confidence OCR noise.
     */
    private fun buildExistingFieldsContext(
        candidates: List<BagFieldCandidate>,
    ): Map<String, com.adsamcik.starlitcoffee.scan.model.FieldContext> {
        val strong = candidates.filter {
            it.confidenceHint == BagFieldConfidence.HIGH ||
                it.confidenceHint == BagFieldConfidence.MEDIUM
        }
        fun sourceOf(c: BagFieldCandidate): com.adsamcik.starlitcoffee.scan.model.FieldSource =
            when (c.sourceType) {
                BagFieldSourceType.LLM ->
                    com.adsamcik.starlitcoffee.scan.model.FieldSource.LLM
                BagFieldSourceType.BARCODE_LOOKUP,
                BagFieldSourceType.LOCAL_BARCODE_MATCH,
                BagFieldSourceType.QR_LINK_LOOKUP,
                BagFieldSourceType.OBSERVED_BARCODE_STEM ->
                    com.adsamcik.starlitcoffee.scan.model.FieldSource.LOOKUP
                BagFieldSourceType.OCR,
                BagFieldSourceType.CONSENSUS ->
                    com.adsamcik.starlitcoffee.scan.model.FieldSource.OCR
            }
        // Per-source priority for picking the representative value per field.
        val sourceRank = mapOf(
            com.adsamcik.starlitcoffee.scan.model.FieldSource.USER to 0,
            com.adsamcik.starlitcoffee.scan.model.FieldSource.LOOKUP to 1,
            com.adsamcik.starlitcoffee.scan.model.FieldSource.LLM to 2,
            com.adsamcik.starlitcoffee.scan.model.FieldSource.OCR to 3,
        )
        val confidenceRank = mapOf(
            BagFieldConfidence.HIGH to 0,
            BagFieldConfidence.MEDIUM to 1,
            BagFieldConfidence.LOW to 2,
            BagFieldConfidence.NEEDS_REVIEW to 3,
        )
        return strong
            .groupBy { it.fieldName }
            .mapNotNull { (fieldName, group) ->
                val winner = group.minWithOrNull(
                    compareBy(
                        { sourceRank[sourceOf(it)] ?: Int.MAX_VALUE },
                        { confidenceRank[it.confidenceHint] ?: Int.MAX_VALUE },
                    ),
                ) ?: return@mapNotNull null
                val value = winner.value.trim()
                if (value.isEmpty()) null
                else fieldName to com.adsamcik.starlitcoffee.scan.model.FieldContext(
                    value = value,
                    source = sourceOf(winner),
                    confidence = winner.confidenceHint.name,
                )
            }
            .toMap()
    }

    private suspend fun tryLlmEnrichment(
        photoBytes: ByteArray,
        existingFields: Map<String, com.adsamcik.starlitcoffee.scan.model.FieldContext>,
        fieldsNeeded: Set<String>,
        rawOcrText: String?,
        knownFieldValues: KnownFieldValues?,
    ): List<BagFieldCandidate> {
        if (!llmProvider.isAvailable()) return emptyList()

        val imageHash = LlmCacheKey.compute(
            imageBytes = photoBytes,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = rawOcrText,
            existingFields = existingFields,
        )
        llmCache.get(imageHash)?.let { cached ->
            return cached.fieldCandidates
        }

        val request = LlmExtractionRequest(
            imageBytes = photoBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = rawOcrText,
            knownFieldValues = knownFieldValues,
        )
        return when (val result = llmProvider.extractBagFields(request)) {
            is LlmExtractionResult.Success -> {
                llmCache.put(imageHash, result)
                result.fieldCandidates
            }
            is LlmExtractionResult.Unavailable -> emptyList()
            is LlmExtractionResult.Failed -> {
                Log.w(BAG_PHOTO_TAG, "LLM enrichment failed: ${result.error}")
                emptyList()
            }
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
        stopTimer()
        _uiState.value = BrewUiState()
        collectRatioPresets(BrewMethod.PULSAR)
        recalculate()
        applyUserDefaults()
    }

    /**
     * Resets per-brew-session state when starting a fresh brew flow (e.g. tapping
     * "Brew" on the calculator screen). Preserves recipe configuration the user
     * just dialled in (method, amount, ratio, filter, grinder, selected bag,
     * decaf override) so they don't have to redo it. Clears timer progress,
     * bloom progress, taste feedback, and resets the minute-alert toggle to its
     * default-on state for the new session.
     */
    fun startNewBrewSession() {
        stopTimer()
        _uiState.update {
            it.copy(
                tasteFeedback = null,
                rating = 0,
                feedbackNotes = "",
                showFeedbackSnackbar = false,
                minuteAlertEnabled = true,
            )
        }
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

            // --- Decaf derivation (single source of truth) ---
            // Manual override wins; else follow selected bag; else false.
            val selectedBag = _selectedBagId.value?.let { id ->
                _coffeeBags.value.find { it.id == id }
            }
            val effectiveIsDecaf = state.manualDecafOverride
                ?: selectedBag?.isDecaf
                ?: false
            // Mismatch only exists when user has explicitly overridden AND a bag is selected
            // AND they disagree. No bag → no mismatch.
            val decafMismatchWithBag = state.manualDecafOverride != null &&
                selectedBag != null &&
                selectedBag.isDecaf != state.manualDecafOverride

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
                if (effectiveIsDecaf) (it - 30).coerceAtLeast(120) else it
            }
            val timeTargetHighS = method.timeTargetHigh.let {
                if (effectiveIsDecaf) (it - 30).coerceAtLeast(150) else it
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

            // Bloom duration adjusted by roast freshness (when bag is selected)
            val effectiveBloomDurationSeconds = run {
                val baseDuration = method.bloomDurationSeconds
                val roastDateMillis = _selectedBagId.value?.let { bagId ->
                    _coffeeBags.value.find { it.id == bagId }?.roastDate
                }
                if (roastDateMillis == null || !method.hasBloom) {
                    baseDuration
                } else {
                    val daysOff = ((System.currentTimeMillis() - roastDateMillis) / 86_400_000L)
                        .toInt().coerceAtLeast(0)
                    when {
                        daysOff <= 7 -> (baseDuration + 10).coerceAtMost(60)  // Very fresh
                        daysOff <= 21 -> baseDuration                          // Normal
                        else -> (baseDuration - 10).coerceAtLeast(30)          // Older
                    }
                }
            }

            val retainedWaterG = coffeeG * method.absorptionRatio
            val predictedCupVolumeG = (waterG - retainedWaterG).coerceAtLeast(0f)

            val grindResult = resolveGrindResult(
                grinderId = state.selectedGrinderId,
                method = method,
                filterType = state.filterType,
                calibrationStyle = state.calibrationStyle,
                isDecaf = effectiveIsDecaf,
                roastLevel = selectedBag?.roastLevel,
                decafProcess = selectedBag?.decafProcess,
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
                effectiveBloomDurationSeconds = effectiveBloomDurationSeconds,
                isDecafBrew = effectiveIsDecaf,
                decafMismatchWithBag = decafMismatchWithBag,
                retainedWaterG = retainedWaterG,
                predictedCupVolumeG = predictedCupVolumeG,
            )
        }
    }

    private fun resolveGrindResult(
        grinderId: String?,
        method: BrewMethod,
        filterType: FilterType?,
        calibrationStyle: CalibrationStyle?,
        isDecaf: Boolean = false,
        roastLevel: String? = null,
        decafProcess: String? = null,
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

        // Decaf offset: start COARSER, not finer. Decaf beans shatter into more
        // fines at the same grinder gap, which reduces bed permeability and slows
        // flow — so going finer often makes things worse, especially for
        // percolation and espresso. Magnitude depends on brew family (immersion is
        // less fines-sensitive) plus a roast brittleness modifier.
        // Refs: Coffee ad Astra (kettle-flow / V60 brewing), Scientific Reports
        // 2024 on espresso fines & permeability, Sweet Maria's on decaf
        // structural changes, Al-Shemmeri grinding study.
        if (isDecaf) {
            val decafSteps = decafCoarserStepsFor(method, roastLevel, decafProcess)
            val processNote = decafProcessNote(decafProcess)
            if (decafSteps > 0) {
                val offset = recommendation.adjustmentStepSize * decafSteps
                val stepLabel = if (decafSteps == 1) "1 step coarser" else "$decafSteps steps coarser"
                recommendation = recommendation.copy(
                    suggestedStart = (recommendation.suggestedStart + offset)
                        .coerceAtMost(recommendation.rangeEnd),
                    adjustmentNote = recommendation.adjustmentNote +
                        " · Decaf: $stepLabel (more fines → coarsen for permeability)" +
                        processNote,
                )
            } else {
                recommendation = recommendation.copy(
                    adjustmentNote = recommendation.adjustmentNote +
                        " · Decaf: same start (immersion or gentle process — fines impact small); dial by taste" +
                        processNote,
                )
            }
        }

        if (calibrationStyle == null) {
            return GrindResult.Specific(recommendation, grinder)
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
            grinder,
        )
    }

    /**
     * How many steps **coarser** to start when brewing decaf, vs the caffeinated
     * baseline. Returns 0 (same start) for fines-insensitive immersion methods or
     * gentle decaf processes.
     *
     * Evidence-based default (2026 research pass):
     *   - Decaf grinds to a smaller median particle size + ~4 % more fines at the
     *     same gap (Al-Shemmeri grinding study).
     *   - Fines reduce bed permeability and slow flow more than they boost
     *     extraction (Sci. Rep. 2024 on espresso, Coffee ad Astra on V60).
     *   - Therefore "auto-finer" is wrong-direction. Default is **same to 1 step
     *     coarser**.
     *
     * Rule:
     *   - Percolation (Pulsar, V60, Moka) + Espresso: +1 step coarser baseline.
     *   - Immersion (French press, AeroPress, cold brew): no change — fines
     *     barely affect flow when there's no pressurised bed.
     *   - Roast modifier: dark / espresso / medium-dark roasts add +1 (more
     *     brittle, more fines).
     *   - Decaf-process modifier: gentle processes (Swiss Water / Mountain Water,
     *     supercritical CO₂) reduce the offset by 1 (clamped at 0). Solvent and
     *     unknown processes get no relief. ACS C&EN 2024 + Swiss Water brew guide
     *     support softer biasing for water/CO₂ processes.
     */
    private fun decafCoarserStepsFor(
        method: BrewMethod,
        roastLevel: String?,
        decafProcess: String? = null,
    ): Int {
        val baseSteps = when (method) {
            BrewMethod.PULSAR,
            BrewMethod.V60,
            BrewMethod.MOKA_POT,
            BrewMethod.ESPRESSO -> 1
            BrewMethod.FRENCH_PRESS,
            BrewMethod.AEROPRESS,
            BrewMethod.COLD_BREW -> 0
        }
        val known = roastLevel?.let {
            runCatching { CoffeeRoastLevel.Known.valueOf(it) }.getOrNull()
        }
        val roastModifier = when (known) {
            CoffeeRoastLevel.Known.MEDIUM_DARK,
            CoffeeRoastLevel.Known.DARK,
            CoffeeRoastLevel.Known.ESPRESSO -> 1
            else -> 0
        }
        val processRelief = when (DecafProcess.fromStorageKey(decafProcess)) {
            DecafProcess.SWISS_WATER,
            DecafProcess.MOUNTAIN_WATER,
            DecafProcess.CO2_SUPERCRITICAL -> 1
            else -> 0
        }
        return (baseSteps + roastModifier - processRelief).coerceAtLeast(0)
    }

    /** Short suffix appended to the grind adjustment note when a decaf process is known. */
    private fun decafProcessNote(decafProcess: String?): String {
        val process = DecafProcess.fromStorageKey(decafProcess) ?: return ""
        return when (process) {
            DecafProcess.SWISS_WATER,
            DecafProcess.MOUNTAIN_WATER -> " · ${process.shortLabel}: gentle, less coarsening needed"
            DecafProcess.CO2_SUPERCRITICAL -> " · CO₂: selective extraction, structure preserved"
            DecafProcess.EA_SUGARCANE,
            DecafProcess.EA_DIRECT,
            DecafProcess.MC_DIRECT -> " · ${process.shortLabel}: solvent process, expect more fines"
            DecafProcess.UNKNOWN -> ""
        }
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
