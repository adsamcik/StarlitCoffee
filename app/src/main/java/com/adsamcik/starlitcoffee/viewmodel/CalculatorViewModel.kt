package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.domain.BeverageOutputEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val availablePresets: List<CupPreset> = emptyList(),
    val hasValidExpression: Boolean = false,
)

class CalculatorViewModel(
    private val presetRepository: CupPresetRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            presetRepository.seedDefaultsIfEmpty()
        }
        viewModelScope.launch {
            presetRepository.presets.collect { presets ->
                _uiState.update { it.copy(availablePresets = presets) }
            }
        }
        if (userPreferencesRepository != null) {
            viewModelScope.launch {
                userPreferencesRepository.userPreferences.collect { prefs ->
                    // Only apply on first load (don't override user's in-session changes)
                    val currentState = _uiState.value
                    if (currentState.tokens.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                ratio = prefs.lastUsedRatio,
                                inputDirection = when (prefs.defaultInputDirection) {
                                    "WATER" -> InputDirection.WATER
                                    else -> InputDirection.DOSE
                                },
                            )
                        }
                    }
                }
            }
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
    }

    fun toggleDirection() {
        _uiState.update { state ->
            val newDirection = when (state.inputDirection) {
                InputDirection.DOSE -> InputDirection.WATER
                InputDirection.WATER -> InputDirection.DOSE
            }
            recalculate(
                state.copy(
                    inputDirection = newDirection,
                    waterAmountMode = WaterAmountMode.WATER_INPUT,
                ),
            )
        }
        viewModelScope.launch {
            userPreferencesRepository?.updateDefaultInputDirection(
                _uiState.value.inputDirection.name,
            )
        }
    }

    fun toggleBeverageOutputMode() {
        _uiState.update { state ->
            if (state.inputDirection != InputDirection.WATER) return@update state
            if (BeverageOutputEstimator.modelFor(state.brewMethod) == null) return@update state

            val mode = when (state.waterAmountMode) {
                WaterAmountMode.WATER_INPUT -> WaterAmountMode.BEVERAGE_OUTPUT
                WaterAmountMode.BEVERAGE_OUTPUT -> WaterAmountMode.WATER_INPUT
            }
            recalculate(state.copy(waterAmountMode = mode))
        }
    }

    fun setBrewMethod(method: BrewMethod) {
        _uiState.update { state ->
            val mode = if (BeverageOutputEstimator.modelFor(method) == null) {
                WaterAmountMode.WATER_INPUT
            } else {
                state.waterAmountMode
            }
            recalculate(state.copy(brewMethod = method, waterAmountMode = mode))
        }
    }

    fun setRatio(ratio: Float) {
        if (ratio <= 0f) return
        _uiState.update { state ->
            recalculate(state.copy(ratio = ratio))
        }
        viewModelScope.launch {
            userPreferencesRepository?.updateLastUsedRatio(ratio)
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

        val isPlanningForOutput =
            state.inputDirection == InputDirection.WATER &&
            state.waterAmountMode == WaterAmountMode.BEVERAGE_OUTPUT
        val plannedOutput = if (isPlanningForOutput) {
            BeverageOutputEstimator.planForOutput(
                method = state.brewMethod,
                beverageOutputG = directResult.totalWaterMl,
                brewRatio = state.ratio,
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
