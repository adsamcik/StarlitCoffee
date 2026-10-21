package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.adsamcik.starlitcoffee.data.work.BagDraftField
import com.adsamcik.starlitcoffee.data.work.BagDraftFocusRegistry
import com.adsamcik.starlitcoffee.data.work.BagDraftPhase
import com.adsamcik.starlitcoffee.data.work.BagDraftReviewLocation
import com.adsamcik.starlitcoffee.data.work.BagDraftStore
import com.adsamcik.starlitcoffee.data.work.BagReviewMode
import com.adsamcik.starlitcoffee.data.work.BagScanDraft
import com.adsamcik.starlitcoffee.data.work.updateReviewLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Route-scoped owner for one durable, editable coffee draft. */
class BagScanDraftViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    explicitSessionId: String? = null,
) : ViewModel() {
    val sessionId: String = explicitSessionId
        ?: checkNotNull(savedStateHandle[SESSION_ID_KEY]) { "Bag draft route is missing its session ID" }

    private val _draft = MutableStateFlow(BagDraftStore.read(application, sessionId))
    val draft: StateFlow<BagScanDraft?> = _draft.asStateFlow()

    init {
        savedStateHandle[SESSION_ID_KEY] = sessionId
        viewModelScope.launch {
            BagDraftStore.observeActive(application).collect { drafts ->
                _draft.value = drafts.firstOrNull { it.sessionId == sessionId }
                    ?: BagDraftStore.read(application, sessionId)
            }
        }
    }

    fun refresh() {
        _draft.value = BagDraftStore.read(application, sessionId)
    }

    fun onUserEdit(field: BagDraftField, value: String?) {
        _draft.value = BagDraftStore.applyUserEdit(application, sessionId, field, value)
    }

    fun acceptSuggestion(field: BagDraftField, expectedValue: String): Boolean {
        val pending = _draft.value?.field(field)?.pendingSuggestion?.value
        val updated = BagDraftStore.acceptSuggestion(application, sessionId, field,
            expectedSuggestionValue = expectedValue)
        _draft.value = updated
        return pending == expectedValue && updated?.field(field)?.let {
            it.value == expectedValue && it.pendingSuggestion == null
        } == true
    }

    fun onFieldFocusChanged(field: BagDraftField, focused: Boolean) {
        BagDraftFocusRegistry.update(sessionId, field, focused)
    }

    fun updateReviewLocation(location: BagDraftReviewLocation) {
        _draft.value = BagDraftStore.updateReviewLocation(application, sessionId, location)
    }

    fun markReviewing() {
        _draft.value = BagDraftStore.markPhase(application, sessionId, BagDraftPhase.REVIEWING)
    }

    fun markCapturing() {
        _draft.value = BagDraftStore.markPhase(application, sessionId, BagDraftPhase.CAPTURING)
    }

    fun markBackgrounded() {
        _draft.value = BagDraftStore.markPhase(application, sessionId, BagDraftPhase.BACKGROUND)
    }

    fun markSaved() {
        _draft.value = BagDraftStore.markPhase(application, sessionId, BagDraftPhase.SAVED)
    }

    fun markDiscarded() {
        _draft.value = BagDraftStore.markPhase(application, sessionId, BagDraftPhase.DISCARDED)
    }

    fun convertRescanToNewBag(expectedGenerationId: String?): Boolean {
        val converted = BagDraftStore.convertRescanToNewBag(application, sessionId, expectedGenerationId)
        _draft.value = converted
        return converted?.isActive == true &&
            converted.reviewContext.mode == BagReviewMode.ADD_NEW
    }

    override fun onCleared() {
        BagDraftFocusRegistry.clear(sessionId)
    }

    companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}

class BagScanDraftViewModelFactory(
    private val application: Application,
    private val sessionId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(BagScanDraftViewModel::class.java)) {
            return BagScanDraftViewModel(
                application = application,
                savedStateHandle = extras.createSavedStateHandle(),
                explicitSessionId = sessionId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
