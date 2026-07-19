package com.adsamcik.starlitcoffee.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesStore
import com.adsamcik.starlitcoffee.data.repository.normalizeMethodSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingSubmission(
    val enabledMethods: Set<BrewMethod>,
    val defaultMethod: BrewMethod,
    val filterType: FilterType?,
    val grinderId: String?,
)

data class OnboardingUiState(
    val isSubmitting: Boolean = false,
    val failure: Boolean = false,
    val completedSubmission: OnboardingSubmission? = null,
)

class OnboardingViewModel(
    private val preferences: UserPreferencesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun complete(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
        filterType: FilterType?,
        grinderId: String?,
    ) {
        if (_uiState.value.isSubmitting) return
        val methodSelection = normalizeMethodSelection(enabledMethods, defaultMethod)
        val submission = OnboardingSubmission(
            enabledMethods = methodSelection.enabledMethods,
            defaultMethod = methodSelection.defaultMethod,
            filterType = filterType.takeIf {
                methodSelection.enabledMethods.contains(BrewMethod.PULSAR)
            },
            grinderId = grinderId,
        )
        _uiState.update {
            it.copy(
                isSubmitting = true,
                failure = false,
                completedSubmission = null,
            )
        }
        viewModelScope.launch {
            try {
                preferences.completeOnboarding(
                    enabledMethods = submission.enabledMethods,
                    defaultMethod = submission.defaultMethod,
                    defaultFilterType = submission.filterType,
                    selectedGrinderId = submission.grinderId,
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        completedSubmission = submission,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Failed to complete onboarding", error)
                _uiState.update { it.copy(isSubmitting = false, failure = true) }
            }
        }
    }

    fun consumeCompletion() {
        _uiState.update { it.copy(completedSubmission = null) }
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
    }
}

class OnboardingViewModelFactory(
    private val preferences: UserPreferencesStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
