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
import com.adsamcik.starlitcoffee.data.work.BagDraftStore
import com.adsamcik.starlitcoffee.data.work.BagScanDraft
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

    fun acceptSuggestion(field: BagDraftField) {
        _draft.value = BagDraftStore.acceptSuggestion(application, sessionId, field)
    }

    fun onFieldFocusChanged(field: BagDraftField, focused: Boolean) {
        BagDraftFocusRegistry.update(sessionId, field, focused)
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
