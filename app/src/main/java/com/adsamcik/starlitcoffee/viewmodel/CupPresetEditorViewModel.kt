package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate

private object CupPresetEditorViewModelTraceboxTemplates {
    val FAILED_TO_SAVE_CUP_PRESET = LogTemplate.of("Failed to save cup preset")
    val FAILED_TO_DELETE_CUP_PRESET = LogTemplate.of("Failed to delete cup preset")
}

enum class CupPresetEditorOperation { IDLE, SAVING, DELETING }

enum class CupPresetEditorCompletion { SAVED, DELETED }

enum class CupPresetEditorFailure { SAVE, DELETE }

data class CupPresetEditorUiState(
    val operation: CupPresetEditorOperation = CupPresetEditorOperation.IDLE,
    val completion: CupPresetEditorCompletion? = null,
    val failure: CupPresetEditorFailure? = null,
)

class CupPresetEditorViewModel(
    private val repository: CupPresetRepository,
) : ViewModel() {
    val presets = repository.presets

    private val _uiState = MutableStateFlow(CupPresetEditorUiState())
    val uiState: StateFlow<CupPresetEditorUiState> = _uiState.asStateFlow()

    fun savePreset(preset: CupPreset, isNew: Boolean) {
        if (_uiState.value.operation != CupPresetEditorOperation.IDLE) return
        _uiState.update {
            it.copy(
                operation = CupPresetEditorOperation.SAVING,
                completion = null,
                failure = null,
            )
        }
        viewModelScope.launch {
            try {
                if (isNew) repository.addPreset(preset) else repository.updatePreset(preset)
                _uiState.update {
                    it.copy(
                        operation = CupPresetEditorOperation.IDLE,
                        completion = CupPresetEditorCompletion.SAVED,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Tracebox.log.error(error, CupPresetEditorViewModelTraceboxTemplates.FAILED_TO_SAVE_CUP_PRESET)
                _uiState.update {
                    it.copy(
                        operation = CupPresetEditorOperation.IDLE,
                        failure = CupPresetEditorFailure.SAVE,
                    )
                }
            }
        }
    }

    fun deletePreset(preset: CupPreset) {
        if (_uiState.value.operation != CupPresetEditorOperation.IDLE) return
        _uiState.update {
            it.copy(
                operation = CupPresetEditorOperation.DELETING,
                completion = null,
                failure = null,
            )
        }
        viewModelScope.launch {
            try {
                repository.deletePreset(preset)
                _uiState.update {
                    it.copy(
                        operation = CupPresetEditorOperation.IDLE,
                        completion = CupPresetEditorCompletion.DELETED,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Tracebox.log.error(error, CupPresetEditorViewModelTraceboxTemplates.FAILED_TO_DELETE_CUP_PRESET)
                _uiState.update {
                    it.copy(
                        operation = CupPresetEditorOperation.IDLE,
                        failure = CupPresetEditorFailure.DELETE,
                    )
                }
            }
        }
    }

    fun consumeCompletion() {
        _uiState.update { it.copy(completion = null) }
    }

    fun consumeFailure() {
        _uiState.update { it.copy(failure = null) }
    }

}

class CupPresetEditorViewModelFactory(
    private val repository: CupPresetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CupPresetEditorViewModel::class.java)) {
            return CupPresetEditorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
