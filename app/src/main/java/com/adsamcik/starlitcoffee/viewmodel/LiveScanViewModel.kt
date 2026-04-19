package com.adsamcik.starlitcoffee.viewmodel

import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.scan.ConsensusEngine
import com.adsamcik.starlitcoffee.scan.FrameEvidenceAccumulator
import com.adsamcik.starlitcoffee.scan.SideDetector
import com.adsamcik.starlitcoffee.scan.model.AccumulatedEvidence
import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldContext
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.scan.model.LlmEscalationRequest
import com.adsamcik.starlitcoffee.scan.observability.ScanAnalyticsTracker
import com.adsamcik.starlitcoffee.scan.observability.ScanSessionRingBuffer
import com.adsamcik.starlitcoffee.scan.observability.ScanSessionSummary
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BarcodeInsights
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

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
    private val llmProvider: LlmInferenceProvider = StubLlmInferenceProvider(),
    private val llmCache: LlmResultCache = LlmResultCache(),
) : ViewModel() {

    // --- Core accumulator ---

    private var accumulator: FrameEvidenceAccumulator? = null
    private var sideDetector: SideDetector? = null
    private var consensusEngine: ConsensusEngine? = null
    private var accumulatorEvidenceJob: Job? = null
    private var llmEscalationJob: Job? = null
    private var rejectionJob: Job? = null

    private val _evidence = MutableStateFlow(AccumulatedEvidence.EMPTY)
    val evidence: StateFlow<AccumulatedEvidence> = _evidence.asStateFlow()

    // --- UI State ---

    private val _liveScanUiState = MutableStateFlow(LiveScanUiState())
    val liveScanUiState: StateFlow<LiveScanUiState> = _liveScanUiState.asStateFlow()

    // --- Cross-validation ---

    private val _crossValidationWarning = MutableStateFlow<CrossValidationWarning?>(null)
    val crossValidationWarning: StateFlow<CrossValidationWarning?> = _crossValidationWarning.asStateFlow()

    private var barcodeRoasterName: String? = null
    private var crossValidationDismissed: Boolean = false

    // --- Frame counter ---

    private var frameIndex: Int = 0
    private var isStarted: Boolean = false

    // --- Enrichment dedup tracking (for UI feedback) ---

    private val processedBarcodes = mutableSetOf<String>()
    private val processedQrUrls = mutableSetOf<String>()
    // --- Golden frame JPEG capture ---

    private var bestGoldenFrameScore: Float = 0f

    // --- LLM telemetry tracking ---

    private var lastLlmLatencyMs: Long? = null
    private var lastLlmSuccess: Boolean? = null
    private var lastLlmTokensUsed: Int? = null
    private var llmCallCount: Int = 0
    private var sessionId: String = ""
    private var sessionStartMs: Long = 0L

    // --- Performance tracer ---

    var perfTracer: com.adsamcik.starlitcoffee.scan.observability.ScanPerfTracer? = null
        private set
    private val _perfStats = MutableStateFlow<Map<String, com.adsamcik.starlitcoffee.scan.observability.PerfStats>>(emptyMap())
    val perfStats: StateFlow<Map<String, com.adsamcik.starlitcoffee.scan.observability.PerfStats>> = _perfStats.asStateFlow()
    private var perfStatsJob: kotlinx.coroutines.Job? = null

    // --- Observability context (set via setAppContext) ---

    private var appContext: android.content.Context? = null

    /**
     * Provide application context for observability features (ring buffer, bug reports).
     * Call once from the composable with `context.applicationContext`.
     */
    fun setAppContext(context: android.content.Context) {
        appContext = context.applicationContext
    }

    // Adaptive throttle for OCR frequency
    private val _currentThrottleMs = MutableStateFlow(config.throttleFastMs)
    val currentThrottleMs: StateFlow<Long> = _currentThrottleMs.asStateFlow()

    private var currentKnownValues: KnownFieldValues = KnownFieldValues.EMPTY

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
        currentKnownValues = knownFieldValues
        resetSessionState()

        sessionId = UUID.randomUUID().toString()
        sessionStartMs = System.currentTimeMillis()
        ScanAnalyticsTracker.trackScanStarted()

        android.util.Log.d("LiveScan", "start() — creating accumulator + consensus engine")

        consensusEngine = ConsensusEngine(config)
        perfTracer = com.adsamcik.starlitcoffee.scan.observability.ScanPerfTracer()
        accumulator = FrameEvidenceAccumulator(
            config = config,
            knownValues = knownFieldValues,
            consensusEngine = consensusEngine!!,
            perfTracer = perfTracer,
        )
        sideDetector = SideDetector(config) {
            accumulator?.notifyPotentialSideFlip()
            _liveScanUiState.value = _liveScanUiState.value.copy(
                sideFlipDetected = true,
            )
        }

        accumulator?.start()
        android.util.Log.d("LiveScan", "accumulator started — launching evidence collector")

        // Pre-warm LLM: connect to service early so first LLM call doesn't pay full init
        viewModelScope.launch {
            try {
                llmProvider.let { provider ->
                    if (provider is MindlayerLlmInferenceProvider) {
                        _liveScanUiState.value = _liveScanUiState.value.copy(
                            llmStatus = LlmUiStatus.CONNECTING,
                        )
                        perfTracer?.startTimer("service_connect_ms")
                        provider.ensureConnected()
                        perfTracer?.stopTimer("service_connect_ms")
                        _liveScanUiState.value = _liveScanUiState.value.copy(
                            llmStatus = LlmUiStatus.WAITING,
                        )
                    } else if (provider.isAvailable()) {
                        _liveScanUiState.value = _liveScanUiState.value.copy(
                            llmStatus = LlmUiStatus.WAITING,
                        )
                    }
                }
            } catch (e: Exception) {
                perfTracer?.stopTimer("service_connect_ms")
                Log.w("LiveScanVM", "LLM pre-warm failed: ${e.message}")
                _liveScanUiState.value = _liveScanUiState.value.copy(
                    llmStatus = LlmUiStatus.UNAVAILABLE,
                )
            }
        }

        // Periodically push tracer stats to StateFlow for UI overlay
        perfStatsJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500L)
                perfTracer?.let { _perfStats.value = it.getStats() }
            }
        }

        accumulatorEvidenceJob = viewModelScope.launch {
            accumulator?.evidence?.collectLatest { evidence ->
                _evidence.value = evidence
                checkCrossValidation(evidence)
                if (evidence.totalFramesProcessed > 0 && evidence.totalFramesProcessed % 5 == 0) {
                    android.util.Log.d("LiveScan", "Evidence update: " +
                        "processed=${evidence.totalFramesProcessed}, " +
                        "rejected=${evidence.totalFramesRejected}, " +
                        "fields=${evidence.fields.size}, " +
                        "progress=${evidence.scanProgress}")
                }
            }
        }

        llmEscalationJob = viewModelScope.launch {
            accumulator?.llmEscalation?.collect { escalation ->
                handleLlmEscalation(escalation)
            }
        }

        rejectionJob = viewModelScope.launch {
            accumulator?.lastRejection?.collect { rejection ->
                if (rejection != null) {
                    _liveScanUiState.value = _liveScanUiState.value.copy(
                        lastRejectionReason = rejection.reason,
                    )
                }
            }
        }

        viewModelScope.launch {
            accumulator?.currentThrottleMs?.collect { throttle ->
                _currentThrottleMs.value = throttle
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

        // Emit telemetry before cleanup
        val perfJson = perfTracer?.toJson()
        val perfStatsSnapshot = perfTracer?.getStats()?.mapValues { (_, stats) ->
            com.adsamcik.starlitcoffee.scan.observability.PerfStatsSnapshot.from(stats)
        }
        val telemetry = accumulator?.buildTelemetry()?.copy(
            bestGoldenFrameScore = bestGoldenFrameScore,
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount,
            llmLatencyMs = lastLlmLatencyMs,
            llmSuccess = lastLlmSuccess,
            llmTokensUsed = lastLlmTokensUsed,
            perfStats = perfStatsSnapshot,
        )
        telemetry?.let {
            android.util.Log.i("ScanTelemetry", it.toJson())

            // --- Observability: analytics + ring buffer ---
            val endMs = System.currentTimeMillis()
            val outcome = it.scanOutcome
            if (outcome == "complete" || outcome == "partial") {
                ScanAnalyticsTracker.trackScanCompleted(
                    outcome = outcome,
                    durationMs = it.sessionDurationMs,
                    fieldsResolved = it.fieldsResolved,
                    fieldsTotal = it.fieldsTotal,
                )
            } else {
                ScanAnalyticsTracker.trackScanAbandoned(
                    durationMs = it.sessionDurationMs,
                    fieldsResolved = it.fieldsResolved,
                )
            }

            val ctx = appContext
            if (ctx != null) {
                val appVersion = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
                } catch (_: Exception) { "unknown" }

                val summary = ScanSessionSummary(
                    sessionId = sessionId,
                    startedAt = sessionStartMs,
                    endedAt = endMs,
                    durationMs = it.sessionDurationMs,
                    outcome = outcome,
                    framesProcessed = it.framesProcessed,
                    framesRejected = it.framesRejected,
                    goldenFrameCount = it.goldenFrameCount,
                    llmFired = it.llmEscalated,
                    llmCallCount = llmCallCount,
                    llmLatencyMs = it.llmLatencyMs ?: 0L,
                    llmTokensUsed = it.llmTokensUsed ?: 0,
                    fieldsResolved = it.fieldsResolved,
                    fieldsTotal = it.fieldsTotal,
                    bestGoldenFrameScore = it.bestGoldenFrameScore,
                    failureReason = when {
                        outcome == "cancelled" && it.framesProcessed == 0 -> "no_text"
                        outcome == "cancelled" -> "low_confidence"
                        else -> null
                    },
                    deviceModel = Build.MODEL,
                    appVersion = appVersion,
                    perfJson = perfJson,
                )
                ScanSessionRingBuffer.save(ctx, summary)
            }
        }

        bestGoldenFrameScore = 0f
        lastLlmLatencyMs = null
        lastLlmSuccess = null
        lastLlmTokensUsed = null
        llmCallCount = 0

        perfStatsJob?.cancel()
        perfStatsJob = null
        perfTracer?.reset()
        perfTracer = null
        _perfStats.value = emptyMap()

        accumulatorEvidenceJob?.cancel()
        accumulatorEvidenceJob = null
        llmEscalationJob?.cancel()
        llmEscalationJob = null
        rejectionJob?.cancel()
        rejectionJob = null
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

        // Pass raw OCR text for LLM context
        if (ocrResult.rawText.isNotBlank()) {
            acc.updateRawOcrText(ocrResult.rawText)
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
     * Called by the camera analyzer when ML Kit returns blank text.
     * Signals an absence of text evidence to the accumulator (Option D).
     */
    fun onBlankFrame() {
        if (!isStarted) return
        accumulator?.submitBlankFrame()
    }

    /**
     * Called when the camera analyzer captures JPEG bytes for a golden-quality frame.
     * Scores the frame and keeps only the best one for LLM escalation.
     */
    fun onGoldenFrameCapture(
        jpegBytes: ByteArray,
        quality: BagCaptureQuality,
        ocrResult: OcrExtractionResult,
    ) {
        val fieldsExtracted = listOfNotNull(
            ocrResult.name, ocrResult.roaster, ocrResult.origin,
            ocrResult.region, ocrResult.variety, ocrResult.processType,
            ocrResult.roastLevel, ocrResult.tastingNotes, ocrResult.altitude,
        ).size
        val score = quality.blurScore * quality.textBlockCount * fieldsExtracted.coerceAtLeast(1)
        if (score > bestGoldenFrameScore) {
            bestGoldenFrameScore = score
            accumulator?.setGoldenFrameBytes(jpegBytes)
            android.util.Log.d(
                "LiveScan",
                "New best golden frame: score=$score, blur=${quality.blurScore}, " +
                    "textBlocks=${quality.textBlockCount}, fields=$fieldsExtracted",
            )
        }
    }

    /**
     * Force-capture the current frame, bypassing IMU gating, throttle, and quality gate.
     * Used by the manual capture fallback button. Submits as a golden frame.
     */
    fun forceCapture(
        ocrResult: OcrExtractionResult,
        quality: BagCaptureQuality,
        jpegBytes: ByteArray? = null,
    ) {
        if (!isStarted) return
        val frame = FrameResult(
            ocrResult = ocrResult,
            quality = quality,
            frameIndex = frameIndex++,
            timestampMs = System.currentTimeMillis(),
            isGoldenFrame = true,
        )
        accumulator?.submitGoldenFrame(frame)
        jpegBytes?.let { onGoldenFrameCapture(it, quality, ocrResult) }
        _liveScanUiState.value = _liveScanUiState.value.copy(
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount + 1,
            lastRejectionReason = null,
        )
    }

    /**
     * Handle manual capture with burst-selected best frame.
     * Fires TWO sequential LLM paths for comparison:
     *   1. OCR+image (existing enrichment path)
     *   2. Image-only (no OCR hints, for benchmarking)
     * Results are logged for benchmarking OCR+LLM vs LLM-only.
     */
    fun onManualCapture(
        jpegBytes: ByteArray,
        quality: BagCaptureQuality,
        ocrResult: OcrExtractionResult,
    ) {
        if (!isStarted) return

        // Submit as golden frame (existing path — feeds OCR into accumulator)
        val frame = FrameResult(
            ocrResult = ocrResult,
            quality = quality,
            frameIndex = frameIndex++,
            timestampMs = System.currentTimeMillis(),
            isGoldenFrame = true,
        )
        accumulator?.submitGoldenFrame(frame)
        onGoldenFrameCapture(jpegBytes, quality, ocrResult)

        _liveScanUiState.value = _liveScanUiState.value.copy(
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount + 1,
            lastRejectionReason = null,
        )

        // Fire dual LLM paths sequentially (shared session prevents parallelism)
        viewModelScope.launch {
            fireDualLlmPaths(jpegBytes, ocrResult)
        }
    }

    /**
     * Run two sequential LLM extraction passes on the same image:
     * 1. OCR+Image (full context — OCR text, existing fields, known values)
     * 2. Image-only (no OCR, no grounding — pure vision)
     *
     * Sequential because MindlayerLlmInferenceProvider shares a single session.
     * Results are logged for comparison and fed into the accumulator.
     */
    private suspend fun fireDualLlmPaths(
        jpegBytes: ByteArray,
        ocrResult: OcrExtractionResult,
    ) {
        if (!llmProvider.isAvailable()) {
            _liveScanUiState.value = _liveScanUiState.value.copy(
                llmStatus = LlmUiStatus.UNAVAILABLE,
            )
            return
        }

        _liveScanUiState.value = _liveScanUiState.value.copy(
            llmStatus = LlmUiStatus.PROCESSING,
        )

        val fieldsNeeded = config.allFields

        // Build existing-fields context so the LLM can cross-reference what other
        // sources (user-confirmed, barcode/QR lookup, earlier OCR consensus) have
        // already concluded. The prompt treats these with source-specific trust
        // levels — this is what makes the LLM the *primary* reasoning layer.
        val existingFields = buildExistingFieldsMap(_evidence.value)

        // PATH 1: OCR+Image (full context)
        val ocrImageRequest = LlmExtractionRequest(
            imageBytes = jpegBytes,
            existingFields = existingFields,
            fieldsNeeded = fieldsNeeded,
            rawOcrText = ocrResult.rawText.takeIf { it.isNotBlank() },
            knownFieldValues = currentKnownValues,
        )
        val ocrStartMs = System.currentTimeMillis()
        val ocrImageResult = llmProvider.extractBagFields(ocrImageRequest)
        val ocrLatencyMs = System.currentTimeMillis() - ocrStartMs

        // PATH 2: Image-only (no OCR hints, no grounding) — pure vision control,
        // used as a benchmark signal and as a low-priority fallback.
        val imageOnlyRequest = LlmExtractionRequest(
            imageBytes = jpegBytes,
            existingFields = emptyMap(),
            fieldsNeeded = fieldsNeeded,
            rawOcrText = null,
            knownFieldValues = null,
        )
        val imgStartMs = System.currentTimeMillis()
        val imageOnlyResult = llmProvider.extractBagFields(imageOnlyRequest)
        val imgLatencyMs = System.currentTimeMillis() - imgStartMs

        // Log comparison for benchmarking
        val ocrFields = (ocrImageResult as? LlmExtractionResult.Success)
            ?.fieldCandidates?.associate { it.fieldName to it.value } ?: emptyMap()
        val imgFields = (imageOnlyResult as? LlmExtractionResult.Success)
            ?.fieldCandidates?.associate { it.fieldName to it.value } ?: emptyMap()

        Log.i("ManualCapture", buildString {
            append("=== DUAL LLM COMPARISON ===\n")
            append("OCR+Image (${ocrLatencyMs}ms): $ocrFields\n")
            append("Image-only (${imgLatencyMs}ms): $imgFields\n")
            val allFields = (ocrFields.keys + imgFields.keys).distinct()
            append("Differences:\n")
            for (field in allFields) {
                val ocrVal = ocrFields[field]
                val imgVal = imgFields[field]
                if (ocrVal != imgVal) {
                    append("  $field: OCR+IMG='$ocrVal' vs IMG='$imgVal'\n")
                }
            }
        })

        // Feed OCR+Image results into the accumulator (primary path)
        if (ocrImageResult is LlmExtractionResult.Success) {
            feedLlmResultsToAccumulator(ocrImageResult.fieldCandidates)
        }

        // Feed image-only results as LLM-source fallback for fields OCR+Image missed.
        // IMPORTANT: submit as LLM (not OCR) so the source attribution and weight
        // stay honest — these are LLM-derived values, just with less context.
        if (imageOnlyResult is LlmExtractionResult.Success) {
            val ocrResolvedFields = ocrFields.keys
            val imageOnlyNew = imageOnlyResult.fieldCandidates
                .filter { it.fieldName !in ocrResolvedFields }
            if (imageOnlyNew.isNotEmpty()) {
                val fallbackValues = imageOnlyNew
                    .associate { it.fieldName to it.value }
                    .filter { it.value.isNotBlank() }
                if (fallbackValues.isNotEmpty()) {
                    accumulator?.submitEnrichment(
                        fieldValues = fallbackValues,
                        sourceType = BagFieldSourceType.LLM,
                    )
                }
            }
        }

        // Update LLM UI status based on results
        val allContributed = (ocrFields.keys + imgFields.keys).toSet()
        val anySuccess = ocrImageResult is LlmExtractionResult.Success ||
            imageOnlyResult is LlmExtractionResult.Success
        if (anySuccess) {
            _liveScanUiState.value = _liveScanUiState.value.copy(
                llmStatus = LlmUiStatus.COMPLETED,
                llmContributedFields = _liveScanUiState.value.llmContributedFields + allContributed,
            )
        } else {
            _liveScanUiState.value = _liveScanUiState.value.copy(
                llmStatus = LlmUiStatus.FAILED,
            )
        }
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

        _liveScanUiState.value = _liveScanUiState.value.copy(
            barcodeDetectedMessage = "✓ Barcode detected — prefilling fields",
        )

        accumulator?.submitEnrichment(
            fieldValues = lookupFields,
            sourceType = sourceType,
            barcode = barcode,
        )

        // Boost OCR priors with barcode-resolved roaster name
        viewModelScope.launch {
            val boosted = BarcodeInsights.buildBarcodeOcrBoost(
                barcode = barcode,
                currentKnownValues = currentKnownValues,
            )
            if (boosted !== currentKnownValues) {
                currentKnownValues = boosted
                accumulator?.boostKnownValues(boosted)
            }
        }

        // Resolve barcode roaster for cross-validation
        val stemMatch = BarcodeInsights.findObservedStemMatch(barcode)
        val barcodeRoaster = lookupFields["roaster"] ?: stemMatch?.roasterCandidate
        if (barcodeRoaster != null) {
            barcodeRoasterName = barcodeRoaster
            checkCrossValidation(_evidence.value)
        }
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

    // --- LLM escalation ---

    private suspend fun handleLlmEscalation(escalation: LlmEscalationRequest) {
        if (!llmProvider.isAvailable()) {
            _liveScanUiState.value = _liveScanUiState.value.copy(
                llmStatus = LlmUiStatus.UNAVAILABLE,
            )
            return
        }
        val bytes = escalation.goldenFrameBytes
        if (bytes == null) {
            android.util.Log.d("LiveScan", "LLM escalation: no golden frame bytes available")
            return
        }

        llmCallCount++
        ScanAnalyticsTracker.trackLlmFired(
            callNumber = llmCallCount,
            fieldsNeeded = escalation.fieldsNeeded.size,
        )

        val imageHash = bytes.contentHashCode()
        val cached = llmCache.get(imageHash)
        if (cached != null) {
            val contributedFields = cached.fieldCandidates.map { it.fieldName }.toSet()
            _liveScanUiState.value = _liveScanUiState.value.copy(
                llmStatus = LlmUiStatus.COMPLETED,
                llmContributedFields = _liveScanUiState.value.llmContributedFields + contributedFields,
            )
            feedLlmResultsToAccumulator(cached.fieldCandidates)
            return
        }

        _liveScanUiState.value = _liveScanUiState.value.copy(
            llmStatus = LlmUiStatus.PROCESSING,
        )

        val request = LlmExtractionRequest(
            imageBytes = bytes,
            existingFields = escalation.existingFields,
            fieldsNeeded = escalation.fieldsNeeded,
            rawOcrText = escalation.rawOcrText,
            knownFieldValues = escalation.knownFieldValues ?: currentKnownValues,
        )
        val startMs = System.currentTimeMillis()
        perfTracer?.startTimer("llm_total_ms")
        when (val result = llmProvider.extractBagFields(request)) {
            is LlmExtractionResult.Success -> {
                lastLlmLatencyMs = System.currentTimeMillis() - startMs
                perfTracer?.stopTimer("llm_total_ms")
                lastLlmSuccess = true
                lastLlmTokensUsed = result.tokensUsed
                llmCache.put(imageHash, result)
                val contributedFields = result.fieldCandidates.map { it.fieldName }.toSet()
                _liveScanUiState.value = _liveScanUiState.value.copy(
                    llmStatus = LlmUiStatus.COMPLETED,
                    llmContributedFields = _liveScanUiState.value.llmContributedFields + contributedFields,
                )
                feedLlmResultsToAccumulator(result.fieldCandidates)
            }
            is LlmExtractionResult.Unavailable -> {
                lastLlmLatencyMs = System.currentTimeMillis() - startMs
                perfTracer?.stopTimer("llm_total_ms")
                lastLlmSuccess = false
                _liveScanUiState.value = _liveScanUiState.value.copy(
                    llmStatus = LlmUiStatus.UNAVAILABLE,
                )
                android.util.Log.d("LiveScan", "LLM escalation: unavailable — ${result.reason}")
            }
            is LlmExtractionResult.Failed -> {
                lastLlmLatencyMs = System.currentTimeMillis() - startMs
                perfTracer?.stopTimer("llm_total_ms")
                lastLlmSuccess = false
                _liveScanUiState.value = _liveScanUiState.value.copy(
                    llmStatus = LlmUiStatus.FAILED,
                )
                android.util.Log.d("LiveScan", "LLM escalation: failed — ${result.error}")
            }
        }
    }

    private fun feedLlmResultsToAccumulator(candidates: List<BagFieldCandidate>) {
        // All LLM-derived candidates are submitted as LLM source. Confidence is
        // already encoded on the candidate itself and is honoured by the
        // ConsensusEngine — we must NOT re-label low-confidence LLM output as OCR,
        // because that corrupts source attribution for cross-validation, for the
        // next LLM escalation's prompt, and for UI treatment.
        val values = candidates
            .associate { it.fieldName to it.value }
            .filter { it.value.isNotBlank() }
        if (values.isNotEmpty()) {
            accumulator?.submitEnrichment(
                fieldValues = values,
                sourceType = BagFieldSourceType.LLM,
            )
        }
    }

    /**
     * Build a `FieldContext` map from the current accumulated evidence, matching
     * the attribution rules used in [FrameEvidenceAccumulator.buildExistingFieldsMap].
     *
     * This is passed to the LLM so the primary-reasoning prompt can trust or
     * challenge each prior value based on *how* it was obtained.
     */
    private fun buildExistingFieldsMap(evidence: AccumulatedEvidence): Map<String, FieldContext> {
        return evidence.fields
            .filter { it.value.isResolved }
            .mapValues { (_, field) ->
                val value = field.resolvedValue
                    ?: field.topCandidate?.normalizedValue
                    ?: ""
                val source = when {
                    field.status == FieldStatus.USER_LOCKED -> FieldSource.USER
                    field.topCandidate?.sourceTypes?.contains(BagFieldSourceType.LLM) == true ->
                        FieldSource.LLM
                    field.topCandidate?.sourceTypes?.any {
                        it == BagFieldSourceType.BARCODE_LOOKUP ||
                            it == BagFieldSourceType.LOCAL_BARCODE_MATCH ||
                            it == BagFieldSourceType.QR_LINK_LOOKUP
                    } == true -> FieldSource.LOOKUP
                    else -> FieldSource.OCR
                }
                val confidence = field.resolvedEvidence?.confidence?.name
                FieldContext(value = value, source = source, confidence = confidence)
            }
            .filter { it.value.value.isNotBlank() }
    }

    // --- Cross-validation logic ---

    /**
     * Compare barcode-derived roaster name against OCR-derived roaster name.
     * When both exist and differ significantly, emit a warning about potential
     * repackaged or imported product.
     */
    private fun checkCrossValidation(evidence: AccumulatedEvidence) {
        if (crossValidationDismissed) return

        val barcodeRoaster = barcodeRoasterName ?: return
        val roasterField = evidence.fields["roaster"] ?: return
        val ocrRoaster = roasterField.topCandidate?.normalizedValue ?: return

        // Only compare when OCR has reasonable confidence (multiple observations)
        if ((roasterField.topCandidate?.observationCount ?: 0) < 2) return
        // Only compare when OCR source includes actual OCR data (not just barcode enrichment)
        val hasOcrSource = roasterField.topCandidate?.sourceTypes?.contains(
            BagFieldSourceType.OCR,
        ) == true
        if (!hasOcrSource) return

        val bTrimmed = barcodeRoaster.trim().lowercase()
        val oTrimmed = ocrRoaster.trim().lowercase()

        // Match: one contains the other (handles "Rebelbean" vs "Rebelbean s.r.o.")
        if (bTrimmed.contains(oTrimmed) || oTrimmed.contains(bTrimmed)) {
            _crossValidationWarning.value = null
            return
        }

        // Match: Levenshtein distance ≤ 2 (handles minor OCR typos)
        if (levenshteinDistance(bTrimmed, oTrimmed) <= 2) {
            _crossValidationWarning.value = null
            return
        }

        // Genuine disagreement — surface warning
        _crossValidationWarning.value = CrossValidationWarning(
            field = "roaster",
            barcodeValue = barcodeRoaster.trim(),
            ocrValue = ocrRoaster.trim(),
            message = "Barcode suggests '${barcodeRoaster.trim()}' but label reads " +
                "'${ocrRoaster.trim()}' — this may be repackaged or imported",
        )
    }

    /**
     * User chose to resolve the cross-validation warning using the barcode value.
     */
    fun resolveCrossValidationWithBarcode() {
        val warning = _crossValidationWarning.value ?: return
        accumulator?.userResolveField(warning.field, warning.barcodeValue)
        crossValidationDismissed = true
        _crossValidationWarning.value = null
    }

    /**
     * User chose to resolve the cross-validation warning using the OCR/label value.
     */
    fun resolveCrossValidationWithOcr() {
        val warning = _crossValidationWarning.value ?: return
        accumulator?.userResolveField(warning.field, warning.ocrValue)
        crossValidationDismissed = true
        _crossValidationWarning.value = null
    }

    /**
     * User dismissed the cross-validation warning without choosing a resolution.
     */
    fun dismissCrossValidation() {
        crossValidationDismissed = true
        _crossValidationWarning.value = null
    }

    // --- Session state ---

    private fun resetSessionState() {
        frameIndex = 0
        processedBarcodes.clear()
        processedQrUrls.clear()
        barcodeRoasterName = null
        crossValidationDismissed = false
        _crossValidationWarning.value = null
        _evidence.value = AccumulatedEvidence.EMPTY
        _liveScanUiState.value = LiveScanUiState()
    }

    // --- Debug info ---

    fun debugInfo(): DebugInfo {
        return DebugInfo(
            frameIndex = frameIndex,
            goldenFrameCount = _liveScanUiState.value.goldenFrameCount,
            bestGoldenFrameScore = bestGoldenFrameScore,
            llmCallCount = llmCallCount,
            lastLlmLatencyMs = lastLlmLatencyMs,
            llmAvailable = llmProvider.isAvailable(),
        )
    }

    data class DebugInfo(
        val frameIndex: Int,
        val goldenFrameCount: Int,
        val bestGoldenFrameScore: Float,
        val llmCallCount: Int,
        val lastLlmLatencyMs: Long?,
        val llmAvailable: Boolean,
    )
}

/**
 * Cross-validation warning when barcode-derived and OCR-derived values disagree
 * for the same field. Surfaces potential repackaged or imported product.
 */
data class CrossValidationWarning(
    val field: String,
    val barcodeValue: String,
    val ocrValue: String,
    val message: String,
)

/**
 * Visible status of the LLM subsystem during a live scan session.
 */
enum class LlmUiStatus {
    IDLE,          // Not yet triggered
    CONNECTING,    // Pre-warming / connecting to service
    WAITING,       // Connected, trigger conditions not yet met
    PROCESSING,    // LLM inference in progress
    COMPLETED,     // LLM returned results
    FAILED,        // LLM failed
    UNAVAILABLE,   // Mindlayer not installed or not connected
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
    val barcodeDetectedMessage: String? = null,
    val llmStatus: LlmUiStatus = LlmUiStatus.IDLE,
    val llmContributedFields: Set<String> = emptySet(),
)

/** Levenshtein edit distance between two strings. */
internal fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)

    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(
                prev[j] + 1,
                curr[j - 1] + 1,
                prev[j - 1] + cost,
            )
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}
