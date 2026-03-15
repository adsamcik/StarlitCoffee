package com.adsamcik.starlitcoffee.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.scan.ConsensusEngine
import com.adsamcik.starlitcoffee.scan.FrameEvidenceAccumulator
import com.adsamcik.starlitcoffee.scan.SideDetector
import com.adsamcik.starlitcoffee.scan.model.AccumulatedEvidence
import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Nav-scoped ViewModel for the live scan session. Created fresh for each
 * camera session via NavBackStackEntry scoping — automatically destroyed
 * when the user navigates away.
 *
 * Orchestrates:
 * - [FrameEvidenceAccumulator] for consensus building
 * - [SideDetector] for automatic front/back detection
 * - IMU (accelerometer) stillness gating
 * - Adaptive throttle management
 * - Golden frame detection and heavy-pass triggering
 * - Live enrichment (barcode/QR/API) integration
 */
class LiveScanViewModel(
    private val config: AccumulatorConfig = AccumulatorConfig.DEFAULT,
) : ViewModel() {

    // --- Core accumulator ---

    private var accumulator: FrameEvidenceAccumulator? = null
    private var sideDetector: SideDetector? = null
    private var consensusEngine: ConsensusEngine? = null
    private var accumulatorEvidenceJob: Job? = null

    private val _evidence = MutableStateFlow(AccumulatedEvidence.EMPTY)
    val evidence: StateFlow<AccumulatedEvidence> = _evidence.asStateFlow()

    // --- UI State ---

    private val _liveScanUiState = MutableStateFlow(LiveScanUiState())
    val liveScanUiState: StateFlow<LiveScanUiState> = _liveScanUiState.asStateFlow()

    // --- Frame counter ---

    private var frameIndex: Int = 0
    private var isStarted: Boolean = false

    // --- Enrichment dedup tracking (for UI feedback) ---

    private val processedBarcodes = mutableSetOf<String>()
    private val processedQrUrls = mutableSetOf<String>()

    /**
     * Initialize and start the live scan session.
     * Call from the composable once camera permissions are granted.
     *
     * @param knownFieldValues Historical field values from existing bags (for Bayesian priors)
     * @param sensorManager Android SensorManager for IMU gating (nullable for testing)
     */
    fun start(
        knownFieldValues: KnownFieldValues,
        sensorManager: SensorManager? = null,
    ) {
        if (isStarted) {
            android.util.Log.w("LiveScan", "start() called but already started — ignoring")
            return
        }
        isStarted = true
        resetSessionState()

        android.util.Log.d("LiveScan", "start() — creating accumulator + consensus engine")

        consensusEngine = ConsensusEngine(config)
        accumulator = FrameEvidenceAccumulator(
            config = config,
            knownValues = knownFieldValues,
            consensusEngine = consensusEngine!!,
        )
        sideDetector = SideDetector(config) {
            accumulator?.notifyPotentialSideFlip()
            _liveScanUiState.value = _liveScanUiState.value.copy(
                sideFlipDetected = true,
            )
        }

        accumulator?.start()
        android.util.Log.d("LiveScan", "accumulator started — launching evidence collector")
        accumulatorEvidenceJob = viewModelScope.launch {
            accumulator?.evidence?.collectLatest { evidence ->
                _evidence.value = evidence
                if (evidence.totalFramesProcessed > 0 && evidence.totalFramesProcessed % 5 == 0) {
                    android.util.Log.d("LiveScan", "Evidence update: " +
                        "processed=${evidence.totalFramesProcessed}, " +
                        "rejected=${evidence.totalFramesRejected}, " +
                        "fields=${evidence.fields.size}, " +
                        "progress=${evidence.scanProgress}")
                }
            }
        }

        _liveScanUiState.value = _liveScanUiState.value.copy(
            isScanning = true,
            scanStartTimeMs = System.currentTimeMillis(),
        )

        android.util.Log.d("LiveScan", "start() complete — ready to receive frames")
    }

    /**
     * Stop the scan session and clean up resources.
     * Called automatically on ViewModel clear, or explicitly on navigation.
     */
    fun stop(sensorManager: SensorManager? = null) {
        if (!isStarted) return
        isStarted = false

        accumulatorEvidenceJob?.cancel()
        accumulatorEvidenceJob = null
        accumulator?.stop()
        accumulator = null
        sideDetector?.reset()
        sideDetector = null
        consensusEngine = null
        _evidence.value = AccumulatedEvidence.EMPTY

        _liveScanUiState.value = LiveScanUiState()
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    // --- Frame processing (called from camera analyzer) ---

    /**
     * Called by the camera analyzer when a fresh OCR result is available.
     * This is the ONLY path that feeds data into the accumulator.
     * Called ~every 700ms when text is visible.
     */
    fun onOcrResult(
        ocrResult: OcrExtractionResult,
        quality: BagCaptureQuality,
        lumaGrid: ByteArray? = null,
    ) {
        if (!isStarted) return

        // Side detection
        lumaGrid?.let { sideDetector?.onFrame(it) }

        val frame = FrameResult(
            ocrResult = ocrResult,
            quality = quality,
            frameIndex = frameIndex++,
            timestampMs = System.currentTimeMillis(),
        )

        val engine = consensusEngine ?: return
        val acc = accumulator ?: return

        android.util.Log.d("LiveScan", "onOcrResult: frame=$frameIndex, fields=${
            listOfNotNull(
                ocrResult.name?.let { "name" },
                ocrResult.roaster?.let { "roaster" },
                ocrResult.origin?.let { "origin" },
                ocrResult.region?.let { "region" },
                ocrResult.variety?.let { "variety" },
            ).joinToString(",")
        }, blur=${quality.blurScore}")

        if (engine.isGoldenFrame(frame)) {
            acc.submitGoldenFrame(frame)
            _liveScanUiState.value = _liveScanUiState.value.copy(
                goldenFrameCount = _liveScanUiState.value.goldenFrameCount + 1,
                lastRejectionReason = null,
            )
        } else {
            acc.submitFrame(frame)
            _liveScanUiState.value = _liveScanUiState.value.copy(
                lastRejectionReason = null,
            )
        }
    }

    /**
     * Called by the camera analyzer on every raw frame (no OCR).
     * Used only for side detection via luma grid — no data goes to accumulator.
     */
    fun onRawFrame(quality: BagCaptureQuality, lumaGrid: ByteArray?) {
        if (!isStarted) return
        lumaGrid?.let { sideDetector?.onFrame(it) }
    }

    /**
     * Force-capture the current frame, bypassing IMU gating, throttle, and quality gate.
     * Used by the manual capture fallback button. Submits as a golden frame.
     */
    fun forceCapture(ocrResult: OcrExtractionResult, quality: BagCaptureQuality) {
        if (!isStarted) return
        val frame = FrameResult(
            ocrResult = ocrResult,
            quality = quality,
            frameIndex = frameIndex++,
            timestampMs = System.currentTimeMillis(),
            isGoldenFrame = true,
        )
        accumulator?.submitGoldenFrame(frame)
        _liveScanUiState.value = _liveScanUiState.value.copy(
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount + 1,
            lastRejectionReason = null,
        )
    }

    // --- Enrichment ---

    /**
     * Submit barcode enrichment results from live barcode detection.
     * Deduplicates by barcode value.
     */
    fun onBarcodeDetected(
        barcode: String,
        lookupFields: Map<String, String> = emptyMap(),
        sourceType: BagFieldSourceType = BagFieldSourceType.BARCODE_LOOKUP,
    ) {
        if (barcode in processedBarcodes) return
        processedBarcodes.add(barcode)

        accumulator?.submitEnrichment(
            fieldValues = lookupFields,
            sourceType = sourceType,
            barcode = barcode,
        )
    }

    /**
     * Submit QR code metadata from live QR detection.
     * Deduplicates by URL.
     */
    fun onQrCodeDetected(
        url: String,
        extractedFields: Map<String, String> = emptyMap(),
    ) {
        if (url in processedQrUrls) return
        processedQrUrls.add(url)

        accumulator?.submitEnrichment(
            fieldValues = extractedFields,
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            qrUrl = url,
        )
    }

    /**
     * Submit local barcode match (existing bag in database).
     */
    fun onLocalBarcodeMatch(
        barcode: String,
        matchedFields: Map<String, String>,
    ) {
        accumulator?.submitEnrichment(
            fieldValues = matchedFields,
            sourceType = BagFieldSourceType.LOCAL_BARCODE_MATCH,
            barcode = barcode,
        )
    }

    // --- User actions ---

    /**
     * User taps a conflict chip to resolve a field disagreement.
     */
    fun resolveConflict(fieldName: String, chosenValue: String) {
        accumulator?.userResolveField(fieldName, chosenValue)
    }

    /**
     * User taps edit icon to reset a locked field back to scanning.
     */
    fun resetField(fieldName: String) {
        accumulator?.userResetField(fieldName)
    }

    // --- Draft bag ---

    /**
     * Check if enough core fields are provisional/locked to show the draft bag preview.
     */
    fun isDraftReady(): Boolean {
        val fields = accumulator?.evidence?.value?.fields ?: return false
        val coreResolved = config.coreFields.count { fieldName ->
            val field = fields[fieldName]
            field != null && (field.isResolved || field.status == FieldStatus.PROVISIONAL)
        }
        return coreResolved >= config.draftTriggerCoreFields
    }

    /**
     * Build a map of resolved field values suitable for creating a coffee bag entity.
     */
    fun buildResolvedFields(): Map<String, String> {
        val fields = accumulator?.evidence?.value?.fields ?: return emptyMap()
        return fields.values
            .filter { it.isResolved || it.status == FieldStatus.PROVISIONAL }
            .mapNotNull { field ->
                val value = field.resolvedEvidence?.value ?: field.resolvedValue
                if (value != null) field.fieldName to value else null
            }
            .toMap()
    }

    // --- Session state ---

    private fun resetSessionState() {
        frameIndex = 0
        processedBarcodes.clear()
        processedQrUrls.clear()
        _evidence.value = AccumulatedEvidence.EMPTY
        _liveScanUiState.value = LiveScanUiState()
    }

    // --- Debug info ---

    fun debugInfo(): DebugInfo {
        return DebugInfo(
            frameIndex = frameIndex,
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount,
        )
    }

    data class DebugInfo(
        val frameIndex: Int,
        val goldenFrameCount: Int,
    )
}

/**
 * UI state for the live scan screen (not the accumulated evidence —
 * that comes from [FrameEvidenceAccumulator.evidence]).
 */
data class LiveScanUiState(
    val isScanning: Boolean = false,
    val scanStartTimeMs: Long = 0L,
    val sideFlipDetected: Boolean = false,
    val goldenFrameCount: Int = 0,
    val lastRejectionReason: String? = null,
)
