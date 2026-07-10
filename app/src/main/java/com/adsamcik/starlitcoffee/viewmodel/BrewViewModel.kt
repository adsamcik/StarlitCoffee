package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.adsamcik.starlitcoffee.data.db.dao.UserBarcodeStemDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.HomeContextCard
import com.adsamcik.starlitcoffee.data.model.InventoryAlert
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.data.network.ProductResult
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.data.network.QrLinkExploreResult
import com.adsamcik.starlitcoffee.data.network.QrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmCallGate
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RatioPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.data.work.BagExtractionScheduler
import com.adsamcik.starlitcoffee.data.work.BagExtractionWorker
import com.adsamcik.starlitcoffee.data.work.decodeBagExtractionResult
import com.adsamcik.starlitcoffee.data.work.encodeToJson
import com.adsamcik.starlitcoffee.domain.pickWeightedBloomSpritesheetId
import com.adsamcik.starlitcoffee.notification.BagAnalysisNotifier
import com.adsamcik.starlitcoffee.notification.NoOpBagAnalysisNotifier
import com.adsamcik.starlitcoffee.notification.RatingReminders
import com.adsamcik.starlitcoffee.scan.BagPhotoExtractor
import com.adsamcik.starlitcoffee.scan.OffLookupSummary
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BarcodeInsights
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.InventoryAlertEngine
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.withTimeout
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

// BrewViewModel is intentionally the single source of truth for app/brew state
// (see .github/instructions/viewmodel.instructions.md: "Keep brew configuration
// and derived brew output in BrewViewModel; composables call setters instead of
// owning brew data locally"). Splitting it into per-feature delegates would
// fragment the StateFlow contract that the calculator, timer, log, bag, and
// scan flows all coordinate through. The constructor surfaces every repository
// + provider explicitly because the project uses manual factory wiring instead
// of a DI framework (per the architecture rule "no DI framework; factories /
// manual wiring are intentional"). Suppress the structural detekt rules at the
// class boundary with that rationale; per-method complexity / LongMethod /
// LongParameterList rules still apply.
@Suppress("LargeClass", "TooManyFunctions")
class BrewViewModel @Suppress("LongParameterList") constructor(
    private val application: Application? = null,
    private val recipeRepository: RecipeRepository? = null,
    private val brewLogRepository: BrewLogRepository? = null,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
    private val ratioPresetRepository: RatioPresetRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val grinderData: GrinderDataProvider = DefaultGrinders,
    private val qrLinkMetadataExplorer: QrLinkMetadataExplorer = SafeQrLinkMetadataExplorer(),
    private val llmProvider: LlmInferenceProvider = StubLlmInferenceProvider(),
    private val ocrService: OcrService? = null,
    private val userBarcodeStemDao: UserBarcodeStemDao? = null,
    private val ratingReminderScheduler: RatingReminders? = null,
    // Groups multi-table brew-log writes (log + inventory) into one atomic
    // transaction. Defaults to a direct runner for unit tests; the factory
    // wires a Room-backed runner in production.
    private val transactionRunner: TransactionRunner = TransactionRunner.Direct,
    // Wall-clock source in milliseconds, injectable for deterministic timer
    // tests. Defaults to a monotonic clock (nanoTime) so elapsed time survives
    // wall-clock changes; tests back it with the coroutine test scheduler.
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    // Open Food Facts barcode lookup, injectable so the candidate-gating logic
    // in addOpenFoodFactsCandidates can be characterized without real network.
    private val openFoodFactsLookup: (barcode: String) -> ProductResult? = {
        OpenFoodFactsClient.lookupBarcode(it)
    },
    // Posts the "bag analysis complete" notification when the user sends the AI
    // bag-label extraction to the background. Defaults to a no-op for unit
    // tests; the factory wires the Android-backed notifier in production.
    private val bagAnalysisNotifier: BagAnalysisNotifier = NoOpBagAnalysisNotifier,
) : ViewModel() {

    private val llmCache = LlmResultCache()
    private val bagPhotoExtractor = BagPhotoExtractor(
        appContext = application,
        coffeeBagRepository = coffeeBagRepository,
        qrLinkMetadataExplorer = qrLinkMetadataExplorer,
        llmProvider = llmProvider,
        ocrService = ocrService,
        userBarcodeStemDao = userBarcodeStemDao,
        openFoodFactsLookup = openFoodFactsLookup,
        llmCache = llmCache,
    )

    private val useWorkManager: Boolean
        get() = application != null

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
    private val _homeContextCard = MutableStateFlow<HomeContextCard?>(null)
    val homeContextCard: StateFlow<HomeContextCard?> = _homeContextCard.asStateFlow()
    private val _bagPhotoResult = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val bagPhotoResult: StateFlow<BagPhotoProcessingResult?> = _bagPhotoResult.asStateFlow()
    private var bagPhotoLlmRetryContext: BagPhotoLlmRetryContext? = null

    // Live per-stage progress of the in-flight bag-photo extraction, surfaced to
    // the analyzing screen as a determinate bar. Null when no pass is running.
    // Fed by the extractor's onProgress callback (inline path) or WorkManager's
    // RUNNING progress data (background path).
    private val _bagPhotoProgress = MutableStateFlow<ScanProgress?>(null)
    val bagPhotoProgress: StateFlow<ScanProgress?> = _bagPhotoProgress.asStateFlow()

    // Holds a bag-photo result that finished while the user had sent the AI
    // extraction to the background. It is NOT pushed into [bagPhotoResult] (which
    // would auto-open the form); instead a notification is posted and the nav
    // host promotes this into the foreground when the user taps it. Process-
    // scoped: a result has no value once the app process dies.
    private val _completedBackgroundResult = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val completedBackgroundResult: StateFlow<BagPhotoProcessingResult?> =
        _completedBackgroundResult.asStateFlow()

    // Dedicated one-shot channel for the "open the analyzed bag form" intent fired
    // by the bag-analysis-complete notification deep link. Kept separate from
    // [bagPhotoResult] so the notification's navigate→promote timing (and a stale
    // BagInventory instance) can't race/consume the result before the freshly
    // shown screen collects it. Consumed by BagInventory via consumePendingScanReview.
    private val _pendingScanReview = MutableStateFlow<BagPhotoProcessingResult?>(null)
    val pendingScanReview: StateFlow<BagPhotoProcessingResult?> = _pendingScanReview.asStateFlow()

    // Set when the user taps "Skip AI" on the analyzing screen. Checked before
    // the LLM phase starts and used to cancel an in-flight enrichment so the
    // pipeline finishes immediately with the OCR/barcode candidates.
    @Volatile
    private var bagPhotoSkipRequested = false

    // Set when the user taps "Continue in background". When the enrichment then
    // completes, the result is delivered via the notification path instead of
    // auto-opening the form.
    @Volatile
    private var bagAnalysisBackgrounded = false

    // The in-flight LLM enrichment, exposed so "Skip AI" can cancel just this
    // phase without tearing down the surrounding OCR pipeline.
    private var bagPhotoLlmDeferred: Deferred<BagPhotoProcessingResult>? = null

    // The in-flight whole-pipeline run for the current photo set. The guided
    // scan flow re-runs extraction over the accumulated photos each time a new
    // one is captured (debounced by the capture flow); cancelling the prior run
    // here keeps only the latest, most-complete pass alive.
    private var bagPhotoProcessJob: Job? = null
    private val _inventoryAlerts = MutableStateFlow(emptyList<InventoryAlert>())
    val inventoryAlerts: StateFlow<List<InventoryAlert>> = _inventoryAlerts.asStateFlow()

    private val timerController = BrewTimerController(
        scope = viewModelScope,
        nowMs = nowMs,
        state = { _uiState.value },
        update = { transform -> _uiState.update(transform) },
    )
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
        if (useWorkManager) observeBagExtractionWork()
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

        // Convert amount intelligently when switching modes. Use the effective
        // ratio (which honours a custom ratio) rather than only the selected
        // preset, otherwise switching coffee<->water with a custom ratio set
        // converts using the wrong ratio.
        val ratio = BrewDerivation.resolveEffectiveRatio(state, state.method)
        val convertedAmount = when {
            oldMode == mode -> currentAmount
            // From coffee grams → water/cup/brew ml: multiply by ratio
            oldMode == InputMode.COFFEE_TO_WATER && mode != InputMode.COFFEE_TO_WATER -> {
                (currentAmount * ratio).let { kotlin.math.round(it) }
            }
            // From water/cup/brew ml → coffee grams: divide by ratio
            oldMode != InputMode.COFFEE_TO_WATER && mode == InputMode.COFFEE_TO_WATER -> {
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

    fun startTimer() = timerController.start()

    /**
     * Ensures the timer coroutine is running. Call on app resume to recover
     * from Doze or battery optimization pausing the coroutine.
     * Does NOT reset the clock — wall-clock anchoring handles the gap.
     */
    fun ensureTimerRunning() = timerController.ensureRunning()

    fun pauseTimer() = timerController.pause()

    fun stopTimer() = timerController.stop()

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

    fun markBloom() = timerController.markBloom()

    fun toggleMinuteAlert() {
        _uiState.update { it.copy(minuteAlertEnabled = !it.minuteAlertEnabled) }
    }

    private companion object {
        // Outer safety-net cap around the text/combine LLM call. Above the
        // provider's inner generation timeout and the Mindlayer service's 5-min
        // single-inference cap (MAX_INFERENCE_MS), so a legitimately long run
        // isn't aborted client-side. Mirrors BagPhotoExtractor.
        private const val BAG_PHOTO_LLM_TIMEOUT_MS = 390_000L
        private const val LLM_MAX_ATTEMPTS = 3
        private const val LLM_RETRY_BACKOFF_MS = 600L
        private const val BAG_SCAN_PREFS = "bag_scan"
        private const val KEY_LAST_CONSUMED_WORK_ID = "lastConsumedWorkId"
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
            val log = buildBrewLogEntity(state)
            // Compute the inventory mutations (current bag + optional rotation)
            // before opening the transaction, then persist the log and all bag
            // updates atomically so a process death can't leave a logged brew
            // without its inventory decrement (or vice versa).
            val plan = planInventoryUpdatesForBrew(state)
            transactionRunner {
                val logId = repository.insertLog(log)
                lastLoggedBrewId = logId
                plan.bagUpdates.forEach { coffeeBagRepository?.updateBag(it) }
            }
            plan.rotatedToBagId?.let { _selectedBagId.value = it }
            refreshLastUnrated()
            scheduleRatingReminderIfEnabled(requireNotNull(lastLoggedBrewId), state.method)
        }
    }

    private fun buildBrewLogEntity(state: BrewUiState): BrewLogEntity = BrewLogEntity(
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
    )

    private data class BrewInventoryPlan(
        val bagUpdates: List<CoffeeBagEntity>,
        val rotatedToBagId: Long?,
    )

    /**
     * Compute the inventory side-effects of logging a brew: SEALED→OPEN on first
     * brew, save the dialed-in grind, decrement weight, OPEN→FINISHED when
     * depleted, and auto-rotate to the next sealed bag of the same coffee. Pure
     * apart from the [findNextSealed] read; returns the bag entities to persist
     * plus the bag we rotated selection to.
     */
    private suspend fun planInventoryUpdatesForBrew(state: BrewUiState): BrewInventoryPlan {
        val bagId = _selectedBagId.value ?: return BrewInventoryPlan(emptyList(), null)
        val bag = _coffeeBags.value.find { it.id == bagId }
            ?: return BrewInventoryPlan(emptyList(), null)

        var updated = bag
        if (bag.status == "SEALED") {
            updated = updated.copy(status = "OPEN", openedDate = System.currentTimeMillis())
        }
        val grindStr = when (val result = state.grindResult) {
            is GrindResult.Specific -> "%.1f".format(result.recommendation.suggestedStart)
            is GrindResult.Generic -> null
        }
        if (grindStr != null && updated.grindSetting != grindStr) {
            updated = updated.copy(grindSetting = grindStr)
        }
        if (updated.weightG != null) {
            val newWeight = (updated.weightG - state.coffeeG).coerceAtLeast(0f)
            updated = updated.copy(weightG = newWeight)
            if (newWeight <= 0f && updated.status == "OPEN") {
                updated = updated.copy(status = "FINISHED")
            }
        }

        val bagUpdates = mutableListOf<CoffeeBagEntity>()
        if (updated != bag) bagUpdates += updated

        var rotatedToBagId: Long? = null
        if (updated.status == "FINISHED" && bag.status != "FINISHED") {
            // Exclude the just-depleted bag: writes are deferred to the transaction,
            // so the lookup still sees this bag's pre-update (possibly SEALED) row and
            // must not rotate selection back onto the bag we are finishing.
            val nextBag = coffeeBagRepository?.findNextSealed(updated.name, updated.roaster)
                ?.takeIf { it.id != bag.id }
            if (nextBag != null) {
                bagUpdates += nextBag.copy(
                    status = "OPEN",
                    openedDate = System.currentTimeMillis(),
                    grindSetting = updated.grindSetting,
                )
                rotatedToBagId = nextBag.id
            }
        }
        return BrewInventoryPlan(bagUpdates, rotatedToBagId)
    }

    private suspend fun scheduleRatingReminderIfEnabled(brewLogId: Long, method: BrewMethod) {
        val scheduler = ratingReminderScheduler ?: return
        // Pref is opt-in; respect the user's choice even if the scheduler is wired.
        val prefs = userPreferencesRepository?.userPreferences?.first() ?: return
        if (!prefs.ratingReminderEnabled) return
        scheduler.scheduleReminder(brewLogId = brewLogId, methodLabel = method.displayName)
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
        rating: BrewRating,
        tasteFeedback: TasteFeedback? = rating.tasteFeedback,
    ) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            repository.updateFeedback(
                logId = logId,
                rating = rating.storedValue,
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

    /**
     * Plain bundle of [addCoffeeBag] input fields. Bundling avoids tripping
     * detekt's [LongParameterList] on the public setter while keeping every
     * field explicit at call sites. All optional fields default to null so
     * tests can construct minimal bags with just a name.
     */
    data class CoffeeBagInput(
        val name: String,
        val roaster: String? = null,
        val origin: String? = null,
        val region: String? = null,
        val roastLevel: String? = null,
        val processType: String? = null,
        val variety: String? = null,
        val tastingNotes: String? = null,
        val roastDate: Long? = null,
        val expiryDate: Long? = null,
        val openedDate: Long? = null,
        val barcode: String? = null,
        val weightG: Float? = null,
        val priceAmount: Float? = null,
        val priceCurrency: String? = "USD",
        val notes: String? = null,
        val isDecaf: Boolean = false,
        val decafProcess: String? = null,
        val photoUri: String? = null,
        val photoUris: String? = null,
        val traceabilityUrl: String? = null,
        val status: String = "SEALED",
    )

    fun addCoffeeBag(input: CoffeeBagInput, onBagAdded: ((Long) -> Unit)? = null) {
        val repository = coffeeBagRepository ?: return
        val normalizedBarcode = BarcodeInsights.normalizeBarcode(input.barcode)
            ?: input.barcode?.trim()?.takeIf { it.isNotBlank() }
        val locale = Locale.getDefault()
        viewModelScope.launch {
            val entity = CoffeeMetadataNormalizer.applyToBagEntity(
                CoffeeBagEntity(
                    name = input.name,
                    roaster = input.roaster,
                    origin = input.origin,
                    region = input.region,
                    roastLevel = input.roastLevel,
                    processType = input.processType,
                    variety = input.variety,
                    tastingNotes = input.tastingNotes,
                    roastDate = input.roastDate,
                    expiryDate = input.expiryDate,
                    openedDate = input.openedDate,
                    barcode = normalizedBarcode,
                    weightG = input.weightG,
                    initialWeightG = input.weightG,
                    priceAmount = input.priceAmount,
                    priceCurrency = input.priceCurrency,
                    notes = input.notes,
                    isDecaf = input.isDecaf,
                    decafProcess = input.decafProcess?.takeIf { input.isDecaf },
                    photoUri = input.photoUri,
                    photoUris = input.photoUris,
                    traceabilityUrl = input.traceabilityUrl,
                    status = input.status,
                ),
                origin = input.origin,
                region = input.region,
                roastLevel = input.roastLevel,
                processType = input.processType,
                variety = input.variety,
                tastingNotes = input.tastingNotes,
                locale = locale,
            )
            val newId = repository.insertBag(entity)

            // Learn barcode→roaster mapping for future scans
            val stemDao = userBarcodeStemDao
            if (stemDao != null && normalizedBarcode != null && input.roaster != null) {
                BarcodeInsights.learnStem(normalizedBarcode, input.roaster, stemDao)
            }

            loadKnownFieldValues()
            bagPhotoLlmRetryContext = null
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
        val repository = coffeeBagRepository ?: run {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val rawBarcode = barcode.trim().takeIf { it.isNotBlank() }
            val normalizedBarcode = BarcodeInsights.normalizeBarcode(rawBarcode)
            val result = when {
                rawBarcode == null -> null
                normalizedBarcode != null && normalizedBarcode != rawBarcode -> {
                    repository.findByBarcode(normalizedBarcode)
                        ?: repository.findByBarcode(rawBarcode)
                }
                normalizedBarcode != null -> repository.findByBarcode(normalizedBarcode)
                else -> repository.findByBarcode(rawBarcode)
            }
            onResult(result)
        }
    }

    fun processNewBagPhotos(
        photosCsv: String,
        knownFieldValues: KnownFieldValues = _knownFieldValues.value,
    ) {
        bagPhotoSkipRequested = false
        bagAnalysisBackgrounded = false
        _bagPhotoProgress.value = null

        val photoUriList = photosCsv.split(",").map(String::trim).filter(String::isNotBlank)
        bagPhotoProcessJob?.cancel()
        bagPhotoLlmDeferred?.cancel()
        bagPhotoLlmDeferred = null
        if (photoUriList.isEmpty()) {
            bagPhotoLlmRetryContext = null
            if (useWorkManager) BagExtractionScheduler.cancel(requireNotNull(application))
            _bagPhotoResult.value = BagPhotoProcessingResult(capturedPhotoUris = photosCsv)
            return
        }

        val retryContext = BagPhotoLlmRetryContext(
            photosCsv = photosCsv,
            photoUriList = photoUriList,
            knownFieldValues = knownFieldValues,
        )
        bagPhotoLlmRetryContext = retryContext
        if (useWorkManager) {
            enqueueBagExtraction(retryContext, runLlm = true)
            return
        }
        runBagPhotoProcessingInViewModel(retryContext, runLlm = !bagPhotoSkipRequested)
    }

    private fun enqueueBagExtraction(context: BagPhotoLlmRetryContext, runLlm: Boolean) {
        BagExtractionScheduler.enqueue(
            context = requireNotNull(application),
            photoUrisCsv = context.photosCsv,
            knownValuesJson = context.knownFieldValues.encodeToJson(),
            runLlm = runLlm,
        )
    }

    private fun runBagPhotoProcessingInViewModel(
        context: BagPhotoLlmRetryContext,
        runLlm: Boolean,
    ) {
        bagPhotoProcessJob?.cancel()
        bagPhotoProcessJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCancellableLlmEnrichment(
                    photoUris = context.photoUriList,
                    knownFieldValues = context.knownFieldValues,
                    runLlm = runLlm,
                    onProgress = { progress -> _bagPhotoProgress.value = progress },
                )
            }
            bagPhotoLlmRetryContext = context
            deliverBagPhotoResult(result.copy(capturedPhotoUris = context.photosCsv))
        }
    }

    /**
     * Delivers a finished bag-photo result. In the normal foreground flow it is
     * pushed into [bagPhotoResult] (which the bag screen observes to open the
     * form). When the user sent the analysis to the background it is stashed in
     * [completedBackgroundResult] and a notification is posted instead, so the
     * form opens only when the user taps it.
     */
    private fun deliverBagPhotoResult(result: BagPhotoProcessingResult) {
        _bagPhotoProgress.value = null
        if (bagAnalysisBackgrounded) {
            bagAnalysisBackgrounded = false
            _completedBackgroundResult.value = result
            bagAnalysisNotifier.notifyComplete(result.fieldEvidence["name"]?.value)
        } else {
            _bagPhotoResult.value = result
        }
    }

    internal suspend fun addOpenFoodFactsCandidates(
        candidates: MutableList<BagFieldCandidate>,
        barcode: String?,
    ): OffLookupSummary = bagPhotoExtractor.addOpenFoodFactsCandidates(candidates, barcode)

    private suspend fun runCancellableLlmEnrichment(
        photoUris: List<String>,
        knownFieldValues: KnownFieldValues,
        runLlm: Boolean,
        onProgress: (ScanProgress) -> Unit = {},
    ): BagPhotoProcessingResult = bagPhotoExtractor.extract(
        photoUris = photoUris,
        knownFieldValues = knownFieldValues,
        runLlm = runLlm,
        onProgress = onProgress,
    )

    @VisibleForTesting
    internal suspend fun runLlmExtractionWithRetry(
        request: LlmExtractionRequest,
    ): LlmExtractionResult {
        var attempt = 1
        while (true) {
            val result = try {
                MindlayerLlmCallGate.withPermit {
                    withTimeout(BAG_PHOTO_LLM_TIMEOUT_MS) {
                        llmProvider.extractBagFields(request)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                LlmExtractionResult.Failed(
                    "Brew photo LLM enrichment timed out after ${BAG_PHOTO_LLM_TIMEOUT_MS / 1000}s",
                    retryable = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                LlmExtractionResult.Failed("Brew photo LLM enrichment threw: ${e.message}", retryable = true)
            }

            if (result !is LlmExtractionResult.Failed || !result.retryable || attempt >= LLM_MAX_ATTEMPTS) {
                return result
            }
            delay(LLM_RETRY_BACKOFF_MS * attempt)
            attempt++
        }
    }

    fun retryBagPhotoLlm() {
        val context = bagPhotoLlmRetryContext ?: return
        bagPhotoSkipRequested = false
        _bagPhotoResult.update { current ->
            current?.copy(llmStatus = LlmEnrichmentStatus.NOT_RUN) ?: BagPhotoProcessingResult(
                capturedPhotoUris = context.photosCsv,
                llmStatus = LlmEnrichmentStatus.NOT_RUN,
            )
        }
        bagPhotoProcessJob?.cancel()
        bagPhotoLlmDeferred?.cancel()
        bagPhotoLlmDeferred = null
        if (useWorkManager) {
            enqueueBagExtraction(context, runLlm = true)
        } else {
            runBagPhotoProcessingInViewModel(context, runLlm = true)
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

    private fun splitMetadataValues(values: String): List<String> = values
        .split(",")
        .map(String::trim)
        .filter(String::isNotBlank)

    private data class BagPhotoLlmRetryContext(
        val photosCsv: String,
        val photoUriList: List<String>,
        val knownFieldValues: KnownFieldValues,
    )

    fun clearBagPhotoResult() {
        bagPhotoLlmRetryContext = null
        _bagPhotoProgress.value = null
        _bagPhotoResult.value = null
    }

    /**
     * Cancels any in-flight bag-photo extraction and clears the result. Used by
     * the guided scan flow when the user discards the session so a stale pass
     * can't land in [bagPhotoResult] after the user has left.
     */
    fun cancelBagPhotoProcessing() {
        bagPhotoProcessJob?.cancel()
        bagPhotoProcessJob = null
        bagPhotoLlmDeferred?.cancel()
        bagPhotoLlmDeferred = null
        if (useWorkManager) BagExtractionScheduler.cancel(requireNotNull(application))
        bagPhotoLlmRetryContext = null
        _bagPhotoProgress.value = null
        _bagPhotoResult.value = null
    }

    /**
     * "Skip AI" on the analyzing screen: cancels the in-flight LLM enrichment so
     * the bag-photo pipeline settles immediately with the OCR/barcode
     * candidates. Safe to call before the LLM phase starts (sets a flag the
     * phase checks) or while it runs (cancels the child coroutine).
     */
    fun skipBagPhotoLlm() {
        bagPhotoSkipRequested = true
        bagPhotoLlmDeferred?.cancel()
        val context = bagPhotoLlmRetryContext ?: return
        bagPhotoProcessJob?.cancel()
        bagPhotoLlmDeferred = null
        if (useWorkManager) {
            enqueueBagExtraction(context, runLlm = false)
        } else {
            runBagPhotoProcessingInViewModel(context, runLlm = false)
        }
    }

    private fun observeBagExtractionWork() {
        val app = application ?: return
        viewModelScope.launch {
            BagExtractionScheduler.workInfoFlow(app).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> updateBagExtractionProgress(workInfo)
                    WorkInfo.State.SUCCEEDED -> handleSucceededBagExtraction(workInfo)
                    WorkInfo.State.FAILED -> handleFailedBagExtraction(workInfo)
                    else -> Unit
                }
            }
        }
    }

    private fun updateBagExtractionProgress(workInfo: WorkInfo) {
        val stageName = workInfo.progress.getString(BagExtractionWorker.KEY_PROGRESS_STAGE) ?: return
        val stage = runCatching { ScanStage.valueOf(stageName) }.getOrNull() ?: return
        _bagPhotoProgress.value = ScanProgress(
            stage = stage,
            stepIndex = workInfo.progress.getInt(BagExtractionWorker.KEY_PROGRESS_INDEX, 0),
            stepCount = workInfo.progress.getInt(BagExtractionWorker.KEY_PROGRESS_COUNT, 0),
        )
    }

    private fun handleSucceededBagExtraction(workInfo: WorkInfo) {
        val workId = workInfo.id.toString()
        if (isBagExtractionWorkConsumed(workId)) return
        val json = workInfo.outputData.getString(BagExtractionWorker.KEY_RESULT_JSON)
        if (json.isNullOrBlank()) return
        try {
            deliverBagPhotoResult(decodeBagExtractionResult(json))
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e("BrewViewModel", "Failed to decode bag extraction result", error)
            deliverBagPhotoResult(BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.UNAVAILABLE))
        }
        markBagExtractionWorkConsumed(workId)
    }

    private fun handleFailedBagExtraction(workInfo: WorkInfo) {
        val workId = workInfo.id.toString()
        if (isBagExtractionWorkConsumed(workId)) return
        deliverBagPhotoResult(BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.UNAVAILABLE))
        markBagExtractionWorkConsumed(workId)
    }

    private fun isBagExtractionWorkConsumed(workId: String): Boolean =
        application
            ?.getSharedPreferences(BAG_SCAN_PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_LAST_CONSUMED_WORK_ID, null) == workId

    private fun markBagExtractionWorkConsumed(workId: String) {
        application
            ?.getSharedPreferences(BAG_SCAN_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_LAST_CONSUMED_WORK_ID, workId)
            ?.apply()
    }

    /**
     * "Continue in background" on the analyzing screen: the in-flight enrichment
     * keeps running; when it completes the result is delivered through a
     * notification ([deliverBagPhotoResult]) instead of auto-opening the form.
     */
    fun continueBagAnalysisInBackground() {
        bagAnalysisBackgrounded = true
    }

    /**
     * Promotes a background-completed result into a dedicated review-open channel
     * so the bag inventory opens the analyzed form. Invoked from the nav host when
     * the user taps the "bag analysis complete" notification. No-op when nothing is
     * pending (e.g. the process was restarted and the in-memory result was lost).
     */
    fun promoteBackgroundResultToForeground() {
        val pending = _completedBackgroundResult.value ?: return
        _completedBackgroundResult.value = null
        _pendingScanReview.value = pending
    }

    /** Clears the review-open intent once BagInventory has shown the form. */
    fun consumePendingScanReview() {
        _pendingScanReview.value = null
    }

    /**
     * Recovers the analyzed-bag result for the notification deep link by reading
     * WorkManager's PERSISTED output, then routes it to the dedicated review-open
     * channel. This is durable across Activity/ViewModel recreation (the
     * notification intent uses FLAG_ACTIVITY_CLEAR_TOP, which can recreate the
     * Activity and clear the in-memory [_completedBackgroundResult]) and across
     * process death. Falls back to the in-memory promote when WorkManager isn't
     * in use (e.g. unit tests with no Application).
     */
    fun openLastBagExtractionResult() {
        val app = application
        if (app == null || !useWorkManager) {
            promoteBackgroundResultToForeground()
            return
        }
        viewModelScope.launch {
            val workInfo = withContext(Dispatchers.IO) { BagExtractionScheduler.currentWorkInfo(app) }
            if (workInfo?.state != WorkInfo.State.SUCCEEDED) {
                promoteBackgroundResultToForeground()
                return@launch
            }
            val json = workInfo.outputData.getString(BagExtractionWorker.KEY_RESULT_JSON)
            if (json.isNullOrBlank()) {
                promoteBackgroundResultToForeground()
                return@launch
            }
            _completedBackgroundResult.value = null
            _pendingScanReview.value = try {
                decodeBagExtractionResult(json)
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e("BrewViewModel", "Failed to decode deep-link bag result", error)
                BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.UNAVAILABLE)
            }
        }
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
        _uiState.update { current ->
            val selectedBag = _selectedBagId.value?.let { id ->
                _coffeeBags.value.find { it.id == id }
            }
            val derived = BrewDerivation.derive(
                state = current,
                selectedBag = selectedBag,
                grinderData = grinderData,
                nowMs = System.currentTimeMillis(),
            )
            current.copy(
                coffeeG = derived.coffeeG,
                waterG = derived.waterG,
                effectiveRatio = derived.effectiveRatio,
                bloomG = derived.bloomG,
                remainingWaterG = derived.remainingWaterG,
                pulseSizeG = derived.pulseSizeG,
                effectivePulseCount = derived.effectivePulseCount,
                timeTargetLowS = derived.timeTargetLowS,
                timeTargetHighS = derived.timeTargetHighS,
                grindResult = derived.grindResult,
                refillCount = derived.refillCount,
                ratioWarning = derived.ratioWarning,
                bloomWarning = derived.bloomWarning,
                effectiveBloomDurationSeconds = derived.effectiveBloomDurationSeconds,
                isDecafBrew = derived.isDecafBrew,
                decafMismatchWithBag = derived.decafMismatchWithBag,
                retainedWaterG = derived.retainedWaterG,
                predictedCupVolumeG = derived.predictedCupVolumeG,
            )
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
