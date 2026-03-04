package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.StrengthPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.service.TimerStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class GrindResult {
    data class Generic(val descriptor: GrindDescriptor) : GrindResult()
    data class Specific(val recommendation: GrindRecommendation) : GrindResult()
}

data class BrewPhase(
    val name: String,
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
    val strengthPreset: StrengthPreset = StrengthPreset.BALANCED,
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
    // Feedback state
    val tasteFeedback: TasteFeedback? = null,
    val rating: Int = 0,
    val feedbackNotes: String = "",
)

class BrewViewModel(
    private val recipeRepository: RecipeRepository? = null,
    private val brewLogRepository: BrewLogRepository? = null,
    private val coffeeBagRepository: CoffeeBagRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrewUiState())
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()
    private val _savedRecipes = MutableStateFlow(emptyList<SavedRecipeEntity>())
    val savedRecipes: StateFlow<List<SavedRecipeEntity>> = _savedRecipes.asStateFlow()
    private val _brewLogs = MutableStateFlow(emptyList<BrewLogEntity>())
    val brewLogs: StateFlow<List<BrewLogEntity>> = _brewLogs.asStateFlow()
    private val _coffeeBags = MutableStateFlow(emptyList<CoffeeBagEntity>())
    val coffeeBags: StateFlow<List<CoffeeBagEntity>> = _coffeeBags.asStateFlow()

    private var timerJob: Job? = null
    private var timerStartMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L

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
        recalculate()
    }

    fun setMethod(method: BrewMethod) {
        _uiState.update { it.copy(method = method, filterType = null) }
        recalculate()
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update { it.copy(inputMode = mode) }
        recalculate()
    }

    fun setAmount(amount: String) {
        if (amount.isNotEmpty() && amount.toFloatOrNull() == null) return
        _uiState.update { it.copy(amount = amount) }
        recalculate()
    }

    fun setStrengthPreset(preset: StrengthPreset) {
        _uiState.update { it.copy(strengthPreset = preset) }
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
        if (timerJob?.isActive == true) return
        _uiState.update { it.copy(timerRunning = true) }
        timerStartMs = System.nanoTime() / 1_000_000L
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerRunning) {
                delay(250L)
                val nowMs = System.nanoTime() / 1_000_000L
                val totalElapsedMs = pausedAccumulatedMs + (nowMs - timerStartMs)
                val totalElapsedSeconds = (totalElapsedMs / 1000).toInt()
                _uiState.update { state ->
                    val newPhaseIndex = computePhaseIndex(state.timerPhases, totalElapsedSeconds)
                    state.copy(
                        elapsedSeconds = totalElapsedSeconds,
                        currentPhaseIndex = newPhaseIndex,
                    )
                }
                val state = _uiState.value
                val phase = state.timerPhases.getOrNull(state.currentPhaseIndex)
                TimerStateHolder.update(
                    phaseName = phase?.name ?: "",
                    elapsedSeconds = totalElapsedSeconds,
                    instruction = phase?.instruction ?: "",
                    isRunning = true,
                )
            }
        }
    }

    fun pauseTimer() {
        val nowMs = System.nanoTime() / 1_000_000L
        pausedAccumulatedMs += (nowMs - timerStartMs)
        _uiState.update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerJob = null
        val state = _uiState.value
        val phase = state.timerPhases.getOrNull(state.currentPhaseIndex)
        TimerStateHolder.update(
            phaseName = phase?.name ?: "",
            elapsedSeconds = state.elapsedSeconds,
            instruction = phase?.instruction ?: "",
            isRunning = false,
        )
    }

    fun stopTimer() {
        pausedAccumulatedMs = 0L
        _uiState.update {
            it.copy(
                timerRunning = false,
                elapsedSeconds = 0,
                currentPhaseIndex = 0,
            )
        }
        timerJob?.cancel()
        timerJob = null
        TimerStateHolder.reset()
    }

    fun advancePhase() {
        _uiState.update { state ->
            val nextIndex = (state.currentPhaseIndex + 1)
                .coerceAtMost(state.timerPhases.lastIndex.coerceAtLeast(0))
            state.copy(currentPhaseIndex = nextIndex)
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
        _uiState.update {
            it.copy(
                method = method,
                inputMode = InputMode.COFFEE_TO_WATER,
                amount = entity.doseG.toString(),
                customRatio = entity.ratio.toString(),
                filterType = filterType,
                selectedGrinderId = entity.grinderId,
                bloomMultiplier = "",
                pulseCount = "",
                tempC = "",
                calibrationStyle = null,
                strengthPreset = StrengthPreset.BALANCED,
            )
        }
        recalculate()
    }

    fun logBrew() {
        val repository = brewLogRepository ?: return
        val state = _uiState.value
        viewModelScope.launch {
            repository.insertLog(
                BrewLogEntity(
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
                    rating = state.rating.takeIf { it > 0 },
                    freeformNotes = state.feedbackNotes.takeIf { it.isNotBlank() },
                    brewTimeSeconds = state.elapsedSeconds.takeIf { it > 0 },
                ),
            )
        }
    }

    fun deleteBrewLog(entity: BrewLogEntity) {
        val repository = brewLogRepository ?: return
        viewModelScope.launch {
            repository.deleteLog(entity)
        }
    }

    fun addCoffeeBag(
        name: String,
        roaster: String? = null,
        origin: String? = null,
        roastLevel: String? = null,
        processType: String? = null,
        roastDate: Long? = null,
        openedDate: Long? = null,
        barcode: String? = null,
        weightG: Float? = null,
        priceAmount: Float? = null,
        priceCurrency: String? = "USD",
        notes: String? = null,
        photoUri: String? = null,
        status: String = "SEALED",
    ) {
        val repository = coffeeBagRepository ?: return
        viewModelScope.launch {
            repository.insertBag(
                CoffeeBagEntity(
                    name = name,
                    roaster = roaster,
                    origin = origin,
                    roastLevel = roastLevel,
                    processType = processType,
                    roastDate = roastDate,
                    openedDate = openedDate,
                    barcode = barcode,
                    weightG = weightG,
                    priceAmount = priceAmount,
                    priceCurrency = priceCurrency,
                    notes = notes,
                    photoUri = photoUri,
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

    fun resetBrew() {
        timerJob?.cancel()
        timerJob = null
        pausedAccumulatedMs = 0L
        _uiState.value = BrewUiState()
        recalculate()
        TimerStateHolder.reset()
    }

    private fun recalculate() {
        _uiState.update { state ->
            val amount = state.amount.toFloatOrNull() ?: 0f
            val method = state.method

            val effectiveRatio = if (state.customRatio.isNotEmpty()) {
                state.customRatio.toFloatOrNull()
                    ?: (method.defaultRatio + state.strengthPreset.ratioOffset)
            } else {
                method.defaultRatio + state.strengthPreset.ratioOffset
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
                InputMode.CUP_SIZE_TO_BOTH -> {
                    val cupMl = amount
                    val absorptionFactor = 2.0f
                    val divisor = effectiveRatio - absorptionFactor
                    if (divisor > 0f) {
                        coffeeG = cupMl / divisor
                        waterG = coffeeG * effectiveRatio
                    } else {
                        // For espresso/moka where ratio ≤ absorption, treat as water input
                        waterG = cupMl
                        coffeeG = if (effectiveRatio != 0f) waterG / effectiveRatio else 0f
                    }
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

            val timeTargetLowS = method.timeTargetLow
            val timeTargetHighS = method.timeTargetHigh

            val refillCount = if (method.capacityMaxG != null && waterG > method.capacityMaxG) {
                kotlin.math.ceil(waterG.toDouble() / method.capacityMaxG).toInt() - 1
            } else {
                0
            }

            val ratioWarning = when {
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
            )

            val timerPhases = buildTimerPhases(
                method = method,
                bloomG = bloomG,
                pulseSizeG = pulseSizeG,
                effectivePulseCount = effectivePulseCount,
                waterG = waterG,
                timeTargetLowS = timeTargetLowS,
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
    }

    private fun resolveGrindResult(
        grinderId: String?,
        method: BrewMethod,
        filterType: FilterType?,
        calibrationStyle: CalibrationStyle?,
    ): GrindResult {
        if (grinderId == null) {
            return GrindResult.Generic(method.defaultGrindDescriptor)
        }

        val grinder = DefaultGrinders.grinders.find { it.id == grinderId }
            ?: return GrindResult.Generic(method.defaultGrindDescriptor)

        val recommendation = DefaultGrinders.recommendations.find { rec ->
            rec.grinderId == grinder.id &&
                rec.methodId == method.name &&
                rec.filterType == filterType
        } ?: return GrindResult.Generic(method.defaultGrindDescriptor)

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

    private fun buildTimerPhases(
        method: BrewMethod,
        bloomG: Float,
        pulseSizeG: Float,
        effectivePulseCount: Int,
        waterG: Float,
        timeTargetLowS: Int,
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
                    waterG = bloomG,
                    cumulativeWaterG = cumulative,
                    durationSeconds = if (isPulsar) 50 else 45,
                    instruction = if (isPulsar) {
                        "Valve OPEN → pour ${"%.0f".format(bloomG)}g → wait ~10s → CLOSE valve → gentle swirl"
                    } else {
                        "Pour ${"%.0f".format(bloomG)}g, let CO₂ escape"
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
                            "Pour ${"%.0f".format(pulseSizeG)}g (total ${"%.0f".format(cumulative)}g)"
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
                        waterG = pourWater,
                        cumulativeWaterG = cumulative,
                        durationSeconds = pourDuration,
                        instruction = "Pour ${"%.0f".format(pourWater)}g total",
                    ),
                )
            }
        }

        if (phases.isNotEmpty()) {
            phases.add(
                BrewPhase(
                    name = "Drawdown",
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
}

