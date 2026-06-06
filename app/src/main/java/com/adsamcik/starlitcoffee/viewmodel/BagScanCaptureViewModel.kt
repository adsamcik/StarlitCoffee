package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phase of the guided bag-scan flow. A single nav route hosts both the camera
 * capture UI and the review/action UI; this flag switches between them so the
 * captured-photo session survives "scan more photos" round-trips.
 */
enum class BagScanPhase { CAPTURING, REVIEWING }

/** A single captured photo, stored as a cache-file URI string. */
data class CapturedBagPhoto(val uri: String)

data class BagScanUiState(
    val phase: BagScanPhase = BagScanPhase.CAPTURING,
    val photos: List<CapturedBagPhoto> = emptyList(),
) {
    val hasPhotos: Boolean get() = photos.isNotEmpty()

    /**
     * The side the user should aim for next: FRONT until the first photo is
     * captured, then BACK. Extra photos beyond the back are still tagged BACK by
     * the extraction pipeline (which keys side off photo index).
     */
    val nextSide: BagCaptureSide
        get() = if (photos.isEmpty()) BagCaptureSide.FRONT else BagCaptureSide.BACK

    fun photosCsv(): String = photos.joinToString(",") { it.uri }
}

/**
 * Nav-scoped session ViewModel for the guided bag-scan capture flow.
 *
 * Owns only the lightweight capture-session state: which photos were captured
 * and whether we are capturing or reviewing. It deliberately does NOT run the
 * OCR/LLM extraction — that stays in the app-scoped [BrewViewModel] (so a result
 * survives navigation and reuses the existing skip/background/retry plumbing).
 * Instead, this VM debounces capture events and emits a CSV of all captured
 * photo URIs through [extractionRequests]; the screen forwards each request to
 * [BrewViewModel.processNewBagPhotos], which cancels any earlier pass and
 * re-extracts over the accumulated photos. Extraction therefore begins right
 * after the first photo and refines as more are added.
 */
class BagScanCaptureViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BagScanUiState())
    val uiState: StateFlow<BagScanUiState> = _uiState.asStateFlow()

    private val _extractionRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val extractionRequests: SharedFlow<String> = _extractionRequests.asSharedFlow()

    private var debounceJob: Job? = null
    private var lastRequestedCsv: String? = null

    /** Append a freshly captured photo and (debounced) re-trigger extraction. */
    fun addPhoto(uri: String) {
        _uiState.update { it.copy(photos = it.photos + CapturedBagPhoto(uri)) }
        scheduleExtraction()
    }

    /** Remove a captured photo (user deleted a thumbnail) and re-extract. */
    fun removePhoto(uri: String) {
        _uiState.update { state -> state.copy(photos = state.photos.filterNot { it.uri == uri }) }
        scheduleExtraction()
    }

    private fun scheduleExtraction() {
        debounceJob?.cancel()
        val csv = _uiState.value.photosCsv()
        if (csv.isBlank()) {
            lastRequestedCsv = null
            return
        }
        debounceJob = viewModelScope.launch {
            delay(EXTRACTION_DEBOUNCE_MS)
            lastRequestedCsv = csv
            _extractionRequests.emit(csv)
        }
    }

    /**
     * User tapped "Finished". Switch to the review phase and make sure a final
     * extraction pass runs over the current photo set (in case the debounce was
     * still pending or new photos arrived since the last request).
     */
    fun finishCapturing() {
        debounceJob?.cancel()
        _uiState.update { it.copy(phase = BagScanPhase.REVIEWING) }
        val csv = _uiState.value.photosCsv()
        if (csv.isNotBlank() && csv != lastRequestedCsv) {
            lastRequestedCsv = csv
            viewModelScope.launch { _extractionRequests.emit(csv) }
        }
    }

    /** User tapped "Scan more photos" from the review screen. */
    fun backToCapture() {
        _uiState.update { it.copy(phase = BagScanPhase.CAPTURING) }
    }

    /** Discard the whole session (clears photos; caller cancels extraction). */
    fun reset() {
        debounceJob?.cancel()
        debounceJob = null
        lastRequestedCsv = null
        _uiState.value = BagScanUiState()
    }

    private companion object {
        const val EXTRACTION_DEBOUNCE_MS = 900L
    }
}
