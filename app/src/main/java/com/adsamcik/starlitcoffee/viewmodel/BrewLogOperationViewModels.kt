package com.adsamcik.starlitcoffee.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrewLogDeleteUiState(
    val deletingLogId: Long? = null,
    val deletedLogId: Long? = null,
    val failedLogId: Long? = null,
)

fun interface BrewLogDeleter {
    suspend fun delete(entity: BrewLogEntity): Boolean
}

class BrewLogListViewModel(
    private val deleter: BrewLogDeleter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewLogDeleteUiState())
    val uiState: StateFlow<BrewLogDeleteUiState> = _uiState.asStateFlow()

    fun delete(entity: BrewLogEntity) {
        if (_uiState.value.deletingLogId != null) return
        _uiState.update {
            it.copy(
                deletingLogId = entity.id,
                deletedLogId = null,
                failedLogId = null,
            )
        }
        viewModelScope.launch {
            try {
                check(deleter.delete(entity)) { "Brew log repository is unavailable" }
                _uiState.update {
                    it.copy(
                        deletingLogId = null,
                        deletedLogId = entity.id,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Failed to delete brew log", error)
                _uiState.update {
                    it.copy(
                        deletingLogId = null,
                        failedLogId = entity.id,
                    )
                }
            }
        }
    }

    fun consumeResult() {
        _uiState.update { it.copy(deletedLogId = null, failedLogId = null) }
    }

    companion object {
        private const val TAG = "BrewLogListViewModel"
    }
}

data class BrewLogFeedbackSubmission(
    val logId: Long,
    val rating: Float?,
    val notes: String,
    val tasteFeedback: String?,
    val descriptors: Set<String>,
)

sealed interface BrewLogFeedbackSaveTarget {
    data object Stay : BrewLogFeedbackSaveTarget
    data object Back : BrewLogFeedbackSaveTarget
    data class SelectLog(val logId: Long) : BrewLogFeedbackSaveTarget
}

data class BrewLogFeedbackCompletion(
    val submission: BrewLogFeedbackSubmission,
    val target: BrewLogFeedbackSaveTarget,
)

data class BrewLogFeedbackUiState(
    val isSaving: Boolean = false,
    val pendingTarget: BrewLogFeedbackSaveTarget = BrewLogFeedbackSaveTarget.Stay,
    val completion: BrewLogFeedbackCompletion? = null,
    val failure: BrewLogFeedbackSaveTarget? = null,
)

fun interface BrewLogFeedbackSaver {
    suspend fun save(submission: BrewLogFeedbackSubmission): Boolean
}

class BrewLogFeedbackViewModel(
    private val saver: BrewLogFeedbackSaver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewLogFeedbackUiState())
    val uiState: StateFlow<BrewLogFeedbackUiState> = _uiState.asStateFlow()

    fun save(
        submission: BrewLogFeedbackSubmission,
        target: BrewLogFeedbackSaveTarget,
    ) {
        if (_uiState.value.isSaving) {
            if (target != BrewLogFeedbackSaveTarget.Stay) {
                _uiState.update { it.copy(pendingTarget = target) }
            }
            return
        }
        _uiState.update {
            it.copy(
                isSaving = true,
                pendingTarget = target,
                completion = null,
                failure = null,
            )
        }
        viewModelScope.launch {
            try {
                check(saver.save(submission)) { "Brew log repository is unavailable" }
                val completedTarget = _uiState.value.pendingTarget
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingTarget = BrewLogFeedbackSaveTarget.Stay,
                        completion = BrewLogFeedbackCompletion(submission, completedTarget),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Failed to save brew feedback", error)
                val failedTarget = _uiState.value.pendingTarget
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingTarget = BrewLogFeedbackSaveTarget.Stay,
                        failure = failedTarget,
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

    companion object {
        private const val TAG = "BrewLogFeedbackVM"
    }
}

internal fun requiresFollowUpFeedbackSave(
    persisted: BrewLogFeedbackSubmission,
    current: BrewLogFeedbackSubmission,
): Boolean = persisted != current

class BrewLogListViewModelFactory(
    private val brewViewModel: BrewViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewLogListViewModel::class.java)) {
            return BrewLogListViewModel(brewViewModel::deleteBrewLogAndWait) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class BrewLogFeedbackViewModelFactory(
    private val brewViewModel: BrewViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewLogFeedbackViewModel::class.java)) {
            return BrewLogFeedbackViewModel { submission ->
                brewViewModel.updateBrewLogFeedbackAndWait(
                    logId = submission.logId,
                    rating = submission.rating,
                    notes = submission.notes,
                    tasteFeedback = submission.tasteFeedback,
                    descriptors = submission.descriptors.toList(),
                )
            } as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
