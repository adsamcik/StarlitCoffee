package com.adsamcik.starlitcoffee.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.CupPresetResetter
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesStore
import com.adsamcik.starlitcoffee.scan.observability.ScanLlmDiagnosticsStore
import com.adsamcik.starlitcoffee.scan.observability.ScanSessionRingBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsOperation {
    IDLE,
    SAVING,
    RESETTING_CUP_PRESETS,
    CLEARING_DIAGNOSTICS,
}

enum class SettingsFailure {
    SAVE,
    RESET_CUP_PRESETS,
    CLEAR_DIAGNOSTICS,
}

enum class SettingsCompletion {
    CUP_PRESETS_RESET,
    DIAGNOSTICS_CLEARED,
}

data class SettingsUiState(
    val operation: SettingsOperation = SettingsOperation.IDLE,
    val failure: SettingsFailure? = null,
    val completion: SettingsCompletion? = null,
)

fun interface DiagnosticHistoryClearer {
    suspend fun clear(): Boolean
}

class AndroidDiagnosticHistoryClearer(context: Context) : DiagnosticHistoryClearer {
    private val appContext = context.applicationContext

    override suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        val sessionsCleared = ScanSessionRingBuffer.clear(appContext)
        val llmPassesCleared = ScanLlmDiagnosticsStore.clear(appContext)
        sessionsCleared && llmPassesCleared
    }
}

class SettingsViewModel(
    private val preferences: UserPreferencesStore,
    private val cupPresetResetter: CupPresetResetter? = null,
    private val diagnosticHistoryClearer: DiagnosticHistoryClearer? = null,
) : ViewModel() {
    val userPreferences = preferences.userPreferences

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateMethodSelection(enabledMethods: Set<BrewMethod>, defaultMethod: BrewMethod) {
        persist { preferences.updateMethodSelection(enabledMethods, defaultMethod) }
    }

    fun updateDefaultMethod(enabledMethods: Set<BrewMethod>, method: BrewMethod) {
        persist { preferences.updateMethodSelection(enabledMethods, method) }
    }

    fun updateDefaultFilterType(filterType: FilterType?) {
        persist { preferences.updateDefaultFilterType(filterType) }
    }

    fun updateSelectedGrinder(grinderId: String?) {
        persist { preferences.updateSelectedGrinder(grinderId) }
    }

    fun updateSkipMethodSelection(enabled: Boolean) {
        persist { preferences.updateSkipMethodSelection(enabled) }
    }

    fun updateShowBrewingInstructions(enabled: Boolean) {
        persist { preferences.updateShowBrewingInstructions(enabled) }
    }

    fun updateBloomSpritesheetWeights(weights: Map<String, Int>) {
        persist { preferences.updateBloomSpritesheetWeights(weights) }
    }

    fun updateRatingReminderEnabled(enabled: Boolean) {
        persist { preferences.updateRatingReminderEnabled(enabled) }
    }

    fun updateScanCorrectionLoggingEnabled(enabled: Boolean) {
        persist { preferences.updateScanCorrectionLoggingEnabled(enabled) }
    }

    fun updateDimModeEnabled(enabled: Boolean) {
        persist { preferences.updateDimModeEnabled(enabled) }
    }

    fun updateDimModeTrueBlack(enabled: Boolean) {
        persist { preferences.updateDimModeTrueBlack(enabled) }
    }

    fun updateDimModeReduceBrightness(enabled: Boolean) {
        persist { preferences.updateDimModeReduceBrightness(enabled) }
    }

    fun updateDimModeFullscreen(enabled: Boolean) {
        persist { preferences.updateDimModeFullscreen(enabled) }
    }

    fun updateDimModeForceDarkInLight(enabled: Boolean) {
        persist { preferences.updateDimModeForceDarkInLight(enabled) }
    }

    fun resetCupPresets() {
        val resetter = cupPresetResetter ?: return
        launchOperation(
            operation = SettingsOperation.RESETTING_CUP_PRESETS,
            failure = SettingsFailure.RESET_CUP_PRESETS,
            completion = SettingsCompletion.CUP_PRESETS_RESET,
        ) {
            resetter.resetToDefaults()
        }
    }

    fun clearDiagnostics() {
        val clearer = diagnosticHistoryClearer ?: return
        launchOperation(
            operation = SettingsOperation.CLEARING_DIAGNOSTICS,
            failure = SettingsFailure.CLEAR_DIAGNOSTICS,
            completion = SettingsCompletion.DIAGNOSTICS_CLEARED,
        ) {
            check(clearer.clear()) { "One or more diagnostic stores could not be cleared" }
        }
    }

    fun consumeFailure() {
        _uiState.update { it.copy(failure = null) }
    }

    fun consumeCompletion() {
        _uiState.update { it.copy(completion = null) }
    }

    private fun persist(block: suspend () -> Unit) {
        launchOperation(
            operation = SettingsOperation.SAVING,
            failure = SettingsFailure.SAVE,
            block = block,
        )
    }

    private fun launchOperation(
        operation: SettingsOperation,
        failure: SettingsFailure,
        completion: SettingsCompletion? = null,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.operation != SettingsOperation.IDLE) return
        _uiState.update { it.copy(operation = operation, failure = null, completion = null) }
        viewModelScope.launch {
            try {
                block()
                _uiState.update {
                    it.copy(
                        operation = SettingsOperation.IDLE,
                        completion = completion,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Settings persistence operation failed", error)
                _uiState.update {
                    it.copy(
                        operation = SettingsOperation.IDLE,
                        failure = failure,
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

class SettingsViewModelFactory(
    private val preferences: UserPreferencesStore,
    private val cupPresetResetter: CupPresetResetter? = null,
    private val diagnosticHistoryClearer: DiagnosticHistoryClearer? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                preferences = preferences,
                cupPresetResetter = cupPresetResetter,
                diagnosticHistoryClearer = diagnosticHistoryClearer,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
