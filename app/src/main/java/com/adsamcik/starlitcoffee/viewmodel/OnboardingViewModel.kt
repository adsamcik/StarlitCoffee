package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesStore
import com.adsamcik.starlitcoffee.data.repository.normalizeMethodSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate

data class OnboardingSubmission(
    val enabledMethods: Set<BrewMethod>,
    val defaultMethod: BrewMethod,
    val filterType: FilterType?,
    val grinderId: String?,
    val sets: List<BrewingSet> = emptyList(),
    val activeId: String? = null,
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

    fun completeSets(sets: List<BrewingSet>, activeId: String) {
        if (_uiState.value.isSubmitting || sets.isEmpty()) return
        val active = sets.find { it.id == activeId } ?: return
        val submission = OnboardingSubmission(sets.map { it.method }.toSet(), active.method,
            active.setup.filterType?.let(FilterType::valueOf), active.setup.grinderId, sets.toList(), activeId)
        submit(submission) { preferences.completeOnboardingSets(submission.sets, activeId) }
    }

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
        submit(submission) {
            preferences.completeOnboarding(
                enabledMethods = submission.enabledMethods, defaultMethod = submission.defaultMethod,
                defaultFilterType = submission.filterType, selectedGrinderId = submission.grinderId,
            )
        }
    }

    private fun submit(submission: OnboardingSubmission, write: suspend () -> Unit) {
        _uiState.update {
            it.copy(
                isSubmitting = true,
                failure = false,
                completedSubmission = null,
            )
        }
        viewModelScope.launch {
            try {
                write()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        completedSubmission = submission,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Tracebox.log.error(error, LogTemplate.of("Failed to complete onboarding"))
                _uiState.update { it.copy(isSubmitting = false, failure = true) }
            }
        }
    }

    fun consumeCompletion() {
        _uiState.update { it.copy(completedSubmission = null) }
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
