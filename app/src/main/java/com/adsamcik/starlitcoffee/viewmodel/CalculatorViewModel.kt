package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredRecipePayload
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.CalculatorSetupStore
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.domain.BeverageOutputCalibration
import com.adsamcik.starlitcoffee.domain.BeverageOutputEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import java.util.UUID
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate

enum class WaterAmountMode {
    WATER_INPUT,
    BEVERAGE_OUTPUT,
}

data class CalcUiState(
    val tokens: List<CalcToken> = emptyList(),
    val previewDoseG: Float = 0f,
    val previewWaterMl: Float = 0f,
    val previewBeverageG: Float? = null,
    val previewApparentLossG: Float? = null,
    val ratio: Float = 17f,
    val inputDirection: InputDirection = InputDirection.DOSE,
    val waterAmountMode: WaterAmountMode = WaterAmountMode.WATER_INPUT,
    val brewMethod: BrewMethod = BrewMethod.PULSAR,
    val beverageOutputCalibration: BeverageOutputCalibration.Profile? = null,
    val availablePresets: List<CupPreset> = emptyList(),
    val hasValidExpression: Boolean = false,
    val filterType: FilterType? = null,
    val grinderId: String? = null,
    val preferencesLoaded: Boolean = false,
    val brewingSets: List<BrewingSet> = emptyList(),
    val activeBrewingSetId: String? = null,
    val setSaveInProgress: Boolean = false,
    val setSaveFailed: Boolean = false,
    val savedSetId: String? = null,
) {
    val quantityTarget: CalculatorQuantityTarget
        get() = when {
            inputDirection == InputDirection.DOSE -> CalculatorQuantityTarget.COFFEE
            waterAmountMode == WaterAmountMode.BEVERAGE_OUTPUT -> CalculatorQuantityTarget.IN_CUP
            else -> CalculatorQuantityTarget.WATER_IN
        }
}

private sealed interface SetupWrite {
    data class Initialize(val recipes: List<SavedRecipeEntity>?) : SetupWrite
    data class Remember(val method: BrewMethod, val setup: CalculatorSetup, val id: String?, val revision: Int) : SetupWrite
    data class Select(val id: String) : SetupWrite
    data class Save(val set: BrewingSet) : SetupWrite
}

// Calculator input and the selected set share one state transaction and one ordered writer.
@Suppress("TooManyFunctions")
class CalculatorViewModel(
    private val presetRepository: CupPresetRepository,
    private val userPreferencesRepository: CalculatorSetupStore? = null,
    private val recipeRepository: RecipeRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()
    private val setups = mutableMapOf<BrewMethod, CalculatorSetup>()
    private val editedMethods = mutableSetOf<BrewMethod>()
    private val setupWrites = Channel<SetupWrite>(Channel.UNLIMITED)
    private var initialPreferences = UserPreferences()
    private var contextSelected = false
    private var lastPersistedSetId: String? = null
    private var pendingSelectionId: String? = null
    private val pendingSetups = mutableMapOf<String, SetupWrite.Remember>()
    private var failedWrite: SetupWrite? = null

    init {
        viewModelScope.launch {
            presetRepository.seedDefaultsIfEmpty()
        }
        viewModelScope.launch {
            presetRepository.presets.collect { presets ->
                _uiState.update { it.copy(availablePresets = presets) }
            }
        }
        viewModelScope.launch {
            persistSetupWrite(SetupWrite.Initialize(recipeRepository?.getAllRecipes()?.first()))
            initialPreferences = userPreferencesRepository?.userPreferences?.first() ?: UserPreferences()
            initialPreferences.calculatorSetups.forEach { (method, setup) ->
                if (method !in editedMethods) setup.validatedFor(method)?.let { setups[method] = it }
            }
            val method = if (contextSelected || editedMethods.isNotEmpty()) {
                _uiState.value.brewMethod
            } else {
                initialPreferences.defaultMethod
            }
            if (method !in editedMethods) {
                val target = if (initialPreferences.defaultInputDirection == "WATER") {
                    CalculatorQuantityTarget.WATER_IN
                } else {
                    CalculatorQuantityTarget.COFFEE
                }
                val setup = setups[method] ?: defaultSetup(method, target)
                _uiState.update { restoreSetup(it.copy(brewMethod = method), setup) }
            }
            initializeSetSelection(method)
            _uiState.update { it.copy(preferencesLoaded = true) }
            if (userPreferencesRepository != null) {
                viewModelScope.launch { userPreferencesRepository.userPreferences.collect(::applySetPreferences) }
            }
            // Serialize writes in user-action order, including edits made while preferences load.
            for (write in setupWrites) persistSetupWrite(write)
        }
    }

    fun appendDigit(digit: Char) {
        require(digit in '0'..'9') { "Expected digit, got: $digit" }
        _uiState.update { state ->
            val tokens = state.tokens.toMutableList()
            val last = tokens.lastOrNull()

            if (last is CalcToken.Number) {
                val newValue = last.value + digit
                tokens[tokens.lastIndex] = CalcToken.Number(newValue)
            } else {
                tokens.add(CalcToken.Number(digit.toString()))
            }

            recalculate(state.copy(tokens = tokens))
        }
        rememberSetup()
    }

    fun appendDecimal() {
        _uiState.update { state ->
            val tokens = state.tokens.toMutableList()
            val last = tokens.lastOrNull()

            if (last is CalcToken.Number && !last.value.contains('.')) {
                tokens[tokens.lastIndex] = CalcToken.Number(last.value + ".")
            } else if (last !is CalcToken.Number) {
                tokens.add(CalcToken.Number("0."))
            }

            recalculate(state.copy(tokens = tokens))
        }
        rememberSetup()
    }

    fun appendOperator(op: CalcOp) {
        _uiState.update { state ->
            val tokens = state.tokens.toMutableList()
            if (tokens.isEmpty()) return@update state

            val last = tokens.lastOrNull()
            if (last is CalcToken.Operator) {
                tokens[tokens.lastIndex] = CalcToken.Operator(op)
            } else {
                tokens.add(CalcToken.Operator(op))
            }

            recalculate(state.copy(tokens = tokens))
        }
        rememberSetup()
    }

    fun appendPreset(preset: CupPreset) {
        _uiState.update { state ->
            val tokens = state.tokens.toMutableList()
            val last = tokens.lastOrNull()

            if (last is CalcToken.Number || last is CalcToken.PresetRef) {
                tokens.add(CalcToken.Operator(CalcOp.ADD))
            }

            tokens.add(CalcToken.PresetRef(preset))

            recalculate(state.copy(tokens = tokens))
        }
        rememberSetup()
    }

    fun backspace() {
        _uiState.update { state ->
            val tokens = state.tokens.toMutableList()
            if (tokens.isEmpty()) return@update state

            val last = tokens.last()
            if (last is CalcToken.Number && last.value.length > 1) {
                tokens[tokens.lastIndex] = CalcToken.Number(last.value.dropLast(1))
            } else {
                tokens.removeAt(tokens.lastIndex)
            }

            recalculate(state.copy(tokens = tokens))
        }
        rememberSetup()
    }

    fun clear() {
        _uiState.update {
            it.copy(
                tokens = emptyList(),
                previewDoseG = 0f,
                previewWaterMl = 0f,
                previewBeverageG = null,
                previewApparentLossG = null,
                hasValidExpression = false,
            )
        }
        rememberSetup()
    }

    /**
     * Selects the quantity controlled by the existing calculator expression.
     *
     * This intentionally maps onto the existing direction/output-mode model
     * instead of duplicating brew math in the UI selector.
     */
    fun selectQuantity(target: CalculatorQuantityTarget) {
        if (!target.isAvailableFor(_uiState.value.brewMethod)) return
        _uiState.update { state ->
            when (target) {
                CalculatorQuantityTarget.COFFEE -> recalculate(
                    state.copy(
                        inputDirection = InputDirection.DOSE,
                        waterAmountMode = WaterAmountMode.WATER_INPUT,
                    ),
                )
                CalculatorQuantityTarget.WATER_IN -> recalculate(
                    state.copy(
                        inputDirection = InputDirection.WATER,
                        waterAmountMode = WaterAmountMode.WATER_INPUT,
                    ),
                )
                CalculatorQuantityTarget.IN_CUP -> {
                    recalculate(
                        state.copy(
                            inputDirection = InputDirection.WATER,
                            waterAmountMode = WaterAmountMode.BEVERAGE_OUTPUT,
                        ),
                    )
                }
            }
        }
        rememberSetup()
    }

    fun toggleDirection() {
        selectQuantity(
            if (_uiState.value.inputDirection == InputDirection.DOSE) {
                if (CalculatorQuantityTarget.WATER_IN.isAvailableFor(_uiState.value.brewMethod)) {
                    CalculatorQuantityTarget.WATER_IN
                } else {
                    CalculatorQuantityTarget.IN_CUP
                }
            } else {
                CalculatorQuantityTarget.COFFEE
            },
        )
    }

    fun toggleBeverageOutputMode() {
        val state = _uiState.value
        selectQuantity(
            if (state.waterAmountMode == WaterAmountMode.WATER_INPUT) {
                CalculatorQuantityTarget.IN_CUP
            } else {
                CalculatorQuantityTarget.WATER_IN
            },
        )
    }

    fun setBrewMethod(method: BrewMethod) {
        setBrewContext(method, _uiState.value.beverageOutputCalibration)
    }

    fun setBrewContext(
        method: BrewMethod,
        calibration: BeverageOutputCalibration.Profile?,
    ) {
        contextSelected = true
        if (_uiState.value.brewMethod != method && _uiState.value.brewingSets.isNotEmpty()) {
            val target = _uiState.value.brewingSets.firstOrNull { it.method == method }
            if (target != null) {
                selectBrewingSet(target.id)
            } else {
                val generated = BrewingSet(UUID.randomUUID().toString(), method = method,
                    setup = defaultSetup(method, _uiState.value.quantityTarget))
                _uiState.update { restoreSet(it.copy(brewingSets = it.brewingSets + generated), generated) }
                setupWrites.trySend(SetupWrite.Save(generated))
            }
        }
        val outgoing = _uiState.value
        if (outgoing.brewMethod != method) setups[outgoing.brewMethod] = currentSetup()
        _uiState.update { state ->
            val context = state.copy(
                brewMethod = method,
                beverageOutputCalibration = calibration?.takeIf { it.method == method },
            )
            if (state.brewMethod == method) {
                recalculate(context)
            } else {
                restoreSetup(context, setups[method] ?: defaultSetup(method, state.quantityTarget))
            }
        }
    }

    fun setRatio(ratio: Float) {
        if (!ratio.isFinite() || ratio <= 0f) return
        _uiState.update { state ->
            recalculate(state.copy(ratio = ratio))
        }
        rememberSetup()
    }

    fun setEquipment(filterType: FilterType?, grinderId: String?) {
        val filter = filterType.takeIf { _uiState.value.brewMethod == BrewMethod.PULSAR }
        if (_uiState.value.filterType == filter && _uiState.value.grinderId == grinderId) return
        _uiState.update { it.copy(filterType = filter, grinderId = grinderId) }
        rememberSetup()
    }

    fun currentSetup(): CalculatorSetup = _uiState.value.let { state ->
        CalculatorSetup(state.ratio, state.quantityTarget.name, state.tokens, state.filterType?.name, state.grinderId)
    }

    fun loadRecipe(recipe: SavedRecipeEntity): Boolean {
        val method = BrewMethod.entries.find { it.name == recipe.method } ?: return false
        val saved = (BrewingPersistenceMapper.recipeRecord(recipe).payload as? StoredRecipePayload.Versioned)
            ?.snapshot?.calculatorSetup?.validatedFor(method)
        val setup = saved ?: CalculatorSetup(
            ratio = recipe.ratio,
            tokens = listOf(CalcToken.Number(recipe.doseG.toString())),
            filterType = recipe.filterType.takeIf { method == BrewMethod.PULSAR },
            grinderId = recipe.grinderId,
        ).validatedFor(method) ?: return false
        setBrewMethod(method)
        _uiState.update { restoreSetup(it, setup) }
        rememberSetup()
        return true
    }

    private fun defaultSetup(method: BrewMethod, preferredTarget: CalculatorQuantityTarget): CalculatorSetup {
        val target = when {
            preferredTarget.isAvailableFor(method) -> preferredTarget
            method.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD -> CalculatorQuantityTarget.IN_CUP
            else -> CalculatorQuantityTarget.WATER_IN
        }
        return CalculatorSetup(
            ratio = method.defaultRatio,
            quantity = target.name,
            filterType = initialPreferences.defaultFilterType?.name.takeIf { method == BrewMethod.PULSAR },
            grinderId = initialPreferences.selectedGrinderId,
        )
    }

    private fun restoreSetup(state: CalcUiState, setup: CalculatorSetup): CalcUiState = recalculate(
        state.copy(
            ratio = setup.ratio,
            tokens = setup.tokens,
            inputDirection = if (setup.quantity == CalculatorQuantityTarget.COFFEE.name) {
                InputDirection.DOSE
            } else {
                InputDirection.WATER
            },
            waterAmountMode = if (setup.quantity == CalculatorQuantityTarget.IN_CUP.name) {
                WaterAmountMode.BEVERAGE_OUTPUT
            } else {
                WaterAmountMode.WATER_INPUT
            },
            filterType = setup.filterType?.let { FilterType.valueOf(it) },
            grinderId = setup.grinderId,
        ),
    )

    private fun rememberSetup() {
        val method = _uiState.value.brewMethod
        val setup = currentSetup()
        setups[method] = setup
        editedMethods += method
        val active = _uiState.value.brewingSets.find { it.id == _uiState.value.activeBrewingSetId }
        if (active != null) {
            _uiState.update { state -> state.copy(brewingSets = state.brewingSets.map {
                if (it.id == active.id) it.copy(setup = setup) else it
            }) }
        }
        if (userPreferencesRepository != null) {
            val write = SetupWrite.Remember(method, setup, active?.id, active?.revision ?: 0)
            active?.id?.let { pendingSetups[it] = write }
            setupWrites.trySend(write)
        }
    }

    fun selectBrewingSet(id: String) {
        val set = _uiState.value.brewingSets.find { it.id == id } ?: return
        pendingSelectionId = id
        _uiState.update { restoreSet(it, set) }
        setupWrites.trySend(SetupWrite.Select(id))
    }

    fun saveBrewingSet(set: BrewingSet) {
        val valid = set.validated() ?: return
        _uiState.update { it.copy(setSaveInProgress = true, setSaveFailed = false, savedSetId = null) }
        setupWrites.trySend(SetupWrite.Save(valid))
    }

    fun consumeSetSaveOutcome() {
        _uiState.update { it.copy(savedSetId = null, setSaveFailed = false) }
        if (failedWrite is SetupWrite.Save) failedWrite = null
    }

    fun retrySetupSave() {
        failedWrite?.let { setupWrites.trySend(it) }
    }

    private fun initializeSetSelection(method: BrewMethod) {
        lastPersistedSetId = initialPreferences.activeBrewingSetId
        val set = initialPreferences.brewingSets.find { it.id == lastPersistedSetId && it.method == method }
            ?: initialPreferences.brewingSets.firstOrNull { it.method == method }
        _uiState.update { state ->
            val updated = state.copy(brewingSets = initialPreferences.brewingSets, activeBrewingSetId = set?.id)
            if (set != null && method !in editedMethods) restoreSet(updated, set) else updated
        }
        if (set != null && method in editedMethods) rememberSetup()
        if (set != null && set.id != lastPersistedSetId) {
            pendingSelectionId = set.id
            setupWrites.trySend(SetupWrite.Select(set.id))
        }
    }

    private fun applySetPreferences(preferences: UserPreferences) {
        val state = _uiState.value
        val selected = preferences.brewingSets.find { it.id == preferences.activeBrewingSetId }
        val current = state.brewingSets.find { it.id == state.activeBrewingSetId }
        val changedSelection = preferences.activeBrewingSetId != lastPersistedSetId
        lastPersistedSetId = preferences.activeBrewingSetId
        val canSelect = pendingSelectionId == null || pendingSelectionId == selected?.id
        if (pendingSelectionId == selected?.id) pendingSelectionId = null
        val changedRevision = selected?.id == current?.id && selected?.revision != current?.revision
        val restore = selected != null && canSelect &&
            ((changedSelection && selected.id != state.activeBrewingSetId) || changedRevision)
        pendingSetups.entries.removeAll { (id, write) ->
            preferences.brewingSets.none { it.id == id && it.revision == write.revision }
        }
        _uiState.update {
            val sets = preferences.brewingSets.map { set ->
                val sameRevision = set.revision == current?.revision
                val pending = pendingSetups[set.id]
                when {
                    pending != null -> set.copy(setup = pending.setup)
                    !restore && set.id == current?.id && sameRevision -> set.copy(setup = currentSetup())
                    else -> set
                }
            }
            val updated = it.copy(brewingSets = sets)
            if (restore) restoreSet(updated, sets.first { set -> set.id == selected.id }) else updated
        }
    }

    private fun restoreSet(state: CalcUiState, set: BrewingSet): CalcUiState =
        restoreSetup(state.copy(brewMethod = set.method, activeBrewingSetId = set.id,
            beverageOutputCalibration = state.beverageOutputCalibration?.takeIf { it.method == set.method }), set.setup)

    private suspend fun persistSetupWrite(write: SetupWrite) {
        try {
            when (write) {
                is SetupWrite.Initialize -> userPreferencesRepository?.initializeBrewingSets(write.recipes)
                is SetupWrite.Remember -> if (write.id == null) {
                    userPreferencesRepository?.updateCalculatorSetup(write.method, write.setup)
                } else {
                    userPreferencesRepository?.updateBrewingSetSetup(write.id, write.revision, write.setup)
                }
                is SetupWrite.Select -> userPreferencesRepository?.selectBrewingSet(write.id)
                is SetupWrite.Save -> {
                    userPreferencesRepository?.saveBrewingSet(write.set)
                    _uiState.update { it.copy(setSaveInProgress = false, savedSetId = write.set.id, setSaveFailed = false) }
                }
            }
            if (write is SetupWrite.Remember && pendingSetups[write.id] == write) pendingSetups.remove(write.id)
            val failed = failedWrite
            val replacesFailedInput = write is SetupWrite.Remember && failed is SetupWrite.Remember &&
                write.id == failed.id && write.revision == failed.revision
            val replacesFailedSelection = write is SetupWrite.Select && failed is SetupWrite.Select
            if (failed == write || replacesFailedInput || replacesFailedSelection) {
                failedWrite = null
                _uiState.update { it.copy(setSaveFailed = false) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failedWrite = write
            Tracebox.log.error(error, LogTemplate.of("Brewing set persistence failed"))
            _uiState.update { it.copy(setSaveFailed = true, setSaveInProgress = false) }
        }
    }

    fun getDisplayExpression(): String {
        return _uiState.value.tokens.joinToString(" ") { token ->
            when (token) {
                is CalcToken.Number -> token.value
                is CalcToken.PresetRef -> token.preset.name
                is CalcToken.Operator -> token.op.symbol
            }
        }
    }

    private fun recalculate(state: CalcUiState): CalcUiState {
        val directResult = CalcEvaluator.evaluate(
            tokens = state.tokens,
            ratio = state.ratio,
            direction = state.inputDirection,
        )

        if (state.brewMethod.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD) {
            return state.copy(
                previewDoseG = directResult.totalDoseG,
                // The legacy preparation boundary calls beverage yield waterG for espresso.
                previewWaterMl = directResult.totalWaterMl,
                previewBeverageG = directResult.totalWaterMl.takeIf { directResult.totalDoseG > 0f },
                previewApparentLossG = null,
                hasValidExpression = directResult.totalDoseG > 0f,
            )
        }

        val isPlanningForOutput =
            state.inputDirection == InputDirection.WATER &&
            state.waterAmountMode == WaterAmountMode.BEVERAGE_OUTPUT
        val apparentLossGPerCoffeeG = state.beverageOutputCalibration
            ?.takeIf { it.method == state.brewMethod }
            ?.apparentLossGPerCoffeeG
        val plannedOutput = if (isPlanningForOutput) {
            BeverageOutputEstimator.planForOutput(
                method = state.brewMethod,
                beverageOutputG = directResult.totalWaterMl,
                brewRatio = state.ratio,
                apparentLossGPerCoffeeG = apparentLossGPerCoffeeG,
            )
        } else {
            null
        }
        val previewDoseG = if (isPlanningForOutput) {
            plannedOutput?.coffeeDoseG ?: 0f
        } else {
            directResult.totalDoseG
        }
        val previewWaterMl = if (isPlanningForOutput) {
            plannedOutput?.brewWaterG ?: 0f
        } else {
            directResult.totalWaterMl
        }
        val hasValid = previewDoseG > 0f || previewWaterMl > 0f
        val estimatedOutput = if (hasValid) {
            BeverageOutputEstimator.estimateOutput(
                method = state.brewMethod,
                coffeeDoseG = previewDoseG,
                brewWaterG = previewWaterMl,
                apparentLossGPerCoffeeG = apparentLossGPerCoffeeG,
            )
        } else {
            null
        }

        return state.copy(
            previewDoseG = previewDoseG,
            previewWaterMl = previewWaterMl,
            previewBeverageG = estimatedOutput?.beverageOutputG,
            previewApparentLossG = estimatedOutput?.apparentLossG,
            hasValidExpression = hasValid,
        )
    }
}
