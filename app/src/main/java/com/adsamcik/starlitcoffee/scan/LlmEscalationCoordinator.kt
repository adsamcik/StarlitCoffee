package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.data.network.llm.LlmCacheKey
import com.adsamcik.starlitcoffee.data.network.llm.LlmCallGate
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmCallGate
import com.adsamcik.starlitcoffee.scan.model.LlmEscalationRequest
import com.adsamcik.starlitcoffee.scan.observability.ScanAnalyticsTracker
import com.adsamcik.starlitcoffee.scan.observability.ScanPerfTracer
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.scan.model.LlmUiStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LlmCoordinator"

/**
 * Combined snapshot of the LLM subsystem's reactive state. A single
 * [StateFlow] avoids a transient "old contributedFields + new status" gap
 * that two parallel flows would expose.
 */
data class LlmCoordinatorState(
    val status: LlmUiStatus = LlmUiStatus.IDLE,
    val contributedFields: Set<String> = emptySet(),
)

/**
 * Telemetry collected over a scan session. Read once on stop; the
 * `ScanTelemetry` / `ScanSessionSummary` assembly lives in the ViewModel.
 */
data class LlmTelemetrySnapshot(
    val callCount: Int,
    val lastLatencyMs: Long?,
    val lastSuccess: Boolean?,
    val lastTokensUsed: Int?,
)

/**
 * Owns the entire LLM-escalation lifecycle for a single scan session:
 *  * subscribes to [FrameEvidenceAccumulator.llmEscalation] and feeds
 *    successful extractions back via [FrameEvidenceAccumulator.submitEnrichment];
 *  * pre-warms the provider on session start;
 *  * serializes **all** provider calls through [LlmCallGate] so the
 *    auto-escalation path, external benchmark callers, and brew-photo
 *    enrichment can't violate the Mindlayer SDK's "no parallel sessions"
 *    invariant;
 *  * exposes a single [state] flow for UI consumption;
 *  * exposes a [LlmTelemetrySnapshot] read on stop so the ViewModel can
 *    assemble per-session telemetry without holding LLM state itself.
 *
 * ## Why a coordinator instead of inline VM logic
 *
 * `LiveScanViewModel` previously owned all of the above plus the field
 * accumulation, side detection, IMU gating, perf tracing, cross-validation,
 * and telemetry assembly. detekt rightly flagged it as `LargeClass`/
 * `TooManyFunctions`. Pulling LLM concerns into one collaborator shrinks the
 * VM, isolates the "what does the LLM know how to do" surface for testing,
 * and gives the rubber-duck audit pass a cleaner home for the
 * cancellation / serialization / SharedFlow-buffer concerns it identified.
 *
 * ## Lifecycle
 *
 *  * [start] — wire to a session's `accumulator`. Subscribes to the
 *    escalation flow **before** the caller invokes `accumulator.start()` so
 *    the SharedFlow's buffer-of-1 doesn't drop a quickly-emitted first
 *    request. Resets state to IDLE; bumps a session generation token used
 *    to ignore late results from prior sessions.
 *  * [extract] — single-shot helper for the dual-path benchmark caller in
 *    the VM. Goes through the same mutex + state plumbing as the auto path.
 *  * [stopAndSnapshot] — cancels in-flight work and returns telemetry. The
 *    cancellation is *intentional* — providers must let
 *    `CancellationException` propagate (see [LlmInferenceProvider]'s
 *    KDoc) so cancelled work cannot mutate state in the next session.
 */
class LlmEscalationCoordinator(
    private val provider: LlmInferenceProvider,
    private val cache: LlmResultCache = LlmResultCache(),
    private val callGate: LlmCallGate = MindlayerLlmCallGate,
) {

    private val _state = MutableStateFlow(LlmCoordinatorState())
    val state: StateFlow<LlmCoordinatorState> = _state.asStateFlow()

    /**
     * Per-session generation token. Bumped on each [start]; checked before
     * applying any provider result to [state] so a result that arrives after
     * [stopAndSnapshot] (e.g. cancellation latency) is silently dropped.
     */
    @Volatile
    private var sessionGeneration: Int = 0

    @Volatile
    private var sessionScope: CoroutineScope? = null
    private var escalationJob: Job? = null
    private var prewarmJob: Job? = null

    @Volatile
    private var callCount = 0

    @Volatile
    private var lastLatencyMs: Long? = null

    @Volatile
    private var lastSuccess: Boolean? = null

    @Volatile
    private var lastTokensUsed: Int? = null

    /**
     * Start a new LLM session for the given accumulator.
     *
     * @param parentScope typically `viewModelScope`; child scope is created
     *   internally so [stopAndSnapshot] cancels only this session's work.
     * @param accumulator already-constructed accumulator. **Must not have
     *   been [FrameEvidenceAccumulator.start]ed yet** — caller should invoke
     *   `accumulator.start()` *after* this method, so we subscribe to
     *   `accumulator.llmEscalation` before the first emission.
     * @param knownValuesProvider read once per escalation to populate the
     *   request's `knownFieldValues`.
     * @param perfTracer optional tracer for `service_connect_ms` / `llm_total_ms`.
     */
    fun start(
        parentScope: CoroutineScope,
        accumulator: FrameEvidenceAccumulator,
        knownValuesProvider: () -> KnownFieldValues,
        perfTracer: ScanPerfTracer? = null,
    ) {
        check(escalationJob == null) { "LlmEscalationCoordinator already started" }
        val gen = ++sessionGeneration
        _state.value = LlmCoordinatorState()
        callCount = 0
        lastLatencyMs = null
        lastSuccess = null
        lastTokensUsed = null

        // Child supervisor scope — stop() cancels this without unwinding the parent.
        val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        sessionScope = scope

        // Subscribe BEFORE caller invokes accumulator.start(). MutableSharedFlow
        // is replay=0 with a buffer of 1, so a quick first emission from the
        // accumulator could otherwise be dropped if the launch hadn't reached
        // collect() yet.
        escalationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            accumulator.llmEscalation.collect { escalation ->
                handleEscalation(gen, escalation, accumulator, knownValuesProvider, perfTracer)
            }
        }

        prewarmJob = scope.launch {
            runPrewarm(gen, perfTracer)
        }
    }

    /**
     * Cancel in-flight work and return the session's telemetry. After this,
     * provider results from cancelled coroutines are silently ignored even
     * if their `withContext` blocks happen to be uncancellable mid-flight.
     */
    fun stopAndSnapshot(): LlmTelemetrySnapshot {
        sessionScope?.cancel()
        sessionScope = null
        escalationJob = null
        prewarmJob = null
        return LlmTelemetrySnapshot(
            callCount = callCount,
            lastLatencyMs = lastLatencyMs,
            lastSuccess = lastSuccess,
            lastTokensUsed = lastTokensUsed,
        )
    }

    /**
     * One-shot extraction. Used by the dual-path benchmark caller in the VM
     * so its two sequential calls share this coordinator's mutex and state.
     *
     * Updates [state] to PROCESSING/COMPLETED/FAILED/UNAVAILABLE around the
     * provider call. Does **not** read or write the cache (the dual-path
     * caller is exploratory; caching its variants would pollute the
     * production-path cache). Does **not** auto-feed candidates into an
     * accumulator — the dual-path caller has bespoke fallback logic for
     * comparing OCR+image vs image-only results.
     */
    suspend fun extract(
        request: LlmExtractionRequest,
        perfTracer: ScanPerfTracer? = null,
    ): LlmExtractionResult {
        if (sessionScope == null) {
            return LlmExtractionResult.Unavailable("No active LLM session")
        }
        val gen = sessionGeneration
        return callGate.withPermit {
            if (gen != sessionGeneration || sessionScope == null) {
                return@withPermit LlmExtractionResult.Unavailable("LLM session ended")
            }
            if (!provider.isAvailable()) {
                updateState(gen) { it.copy(status = LlmUiStatus.UNAVAILABLE) }
                return@withPermit LlmExtractionResult.Unavailable("Provider not available")
            }
            runExtractionLocked(gen, request, perfTracer)
        }
    }

    /** Whether the underlying provider is configured. Cheap; safe from Main. */
    fun isAvailable(): Boolean = provider.isAvailable()

    // -- internals -----------------------------------------------------------

    private suspend fun runPrewarm(gen: Int, perfTracer: ScanPerfTracer?) {
        // Boundary catch: `provider.prewarm()` is implementation-defined and
        // can fail in many ways (binder, IO, model load). Logging + flipping
        // status to UNAVAILABLE is the right product-side recovery — there's
        // no specific exception type the coordinator can act on differently.
        @Suppress("TooGenericExceptionCaught")
        try {
            updateState(gen) { it.copy(status = LlmUiStatus.CONNECTING) }
            perfTracer?.startTimer("service_connect_ms")
            callGate.withPermit {
                if (gen != sessionGeneration || sessionScope == null) return@withPermit
                provider.prewarm()
            }
            perfTracer?.stopTimer("service_connect_ms")
            // updateState performs the generation check; keeping it centralized
            // prevents stale prewarm completions from reviving a stopped scan.
            updateState(gen) { it.copy(status = LlmUiStatus.WAITING) }
        } catch (e: CancellationException) {
            perfTracer?.stopTimer("service_connect_ms")
            throw e
        } catch (e: Exception) {
            perfTracer?.stopTimer("service_connect_ms")
            android.util.Log.w(TAG, "LLM pre-warm failed: ${e.message}")
            updateState(gen) { it.copy(status = LlmUiStatus.UNAVAILABLE) }
        }
    }

    private suspend fun handleEscalation(
        gen: Int,
        escalation: LlmEscalationRequest,
        accumulator: FrameEvidenceAccumulator,
        knownValuesProvider: () -> KnownFieldValues,
        perfTracer: ScanPerfTracer?,
    ) {
        if (gen != sessionGeneration) return
        if (!provider.isAvailable()) {
            updateState(gen) { it.copy(status = LlmUiStatus.UNAVAILABLE) }
            return
        }
        val bytes = escalation.goldenFrameBytes ?: run {
            android.util.Log.d(TAG, "LLM escalation: no golden frame bytes available")
            return
        }

        callCount++
        ScanAnalyticsTracker.trackLlmFired(
            callNumber = callCount,
            fieldsNeeded = escalation.fieldsNeeded.size,
        )

        // SHA-256 over the JPEG bytes runs off-main so it doesn't add to the
        // camera-preview jank window during an LLM escalation.
        val imageHash = withContext(Dispatchers.Default) {
            LlmCacheKey.compute(
                imageBytes = bytes,
                fieldsNeeded = escalation.fieldsNeeded,
                rawOcrText = escalation.rawOcrText,
                existingFields = escalation.existingFields,
            )
        }
        cache.get(imageHash)?.let { cached ->
            val contributed = cached.fieldCandidates.map { it.fieldName }.toSet()
            updateState(gen) {
                it.copy(
                    status = LlmUiStatus.COMPLETED,
                    contributedFields = it.contributedFields + contributed,
                )
            }
            feedToAccumulator(accumulator, cached.fieldCandidates)
            return
        }

        val request = LlmExtractionRequest(
            imageBytes = bytes,
            existingFields = escalation.existingFields,
            fieldsNeeded = escalation.fieldsNeeded,
            rawOcrText = escalation.rawOcrText,
            knownFieldValues = escalation.knownFieldValues ?: knownValuesProvider(),
        )
        val result = callGate.withPermit {
            if (gen != sessionGeneration || sessionScope == null) {
                return@withPermit LlmExtractionResult.Unavailable("LLM session ended")
            }
            runExtractionLocked(gen, request, perfTracer)
        }
        if (result is LlmExtractionResult.Success) {
            cache.put(imageHash, result)
            feedToAccumulator(accumulator, result.fieldCandidates)
        } else if (result is LlmExtractionResult.Unavailable) {
            android.util.Log.d(TAG, "LLM escalation: unavailable — ${result.reason}")
        } else if (result is LlmExtractionResult.Failed) {
            android.util.Log.d(TAG, "LLM escalation: failed — ${result.error}")
        }
    }

    /**
     * Caller must already hold [callGate].
     *
     * Invokes the provider, applies the result to [state] (gated on the
     * current session generation so cancelled-but-in-flight work can't
     * write to the next session), and returns the raw result so the
     * caller can chain its own per-result logic (cache, accumulator feed,
     * benchmark logging).
     */
    private suspend fun runExtractionLocked(
        gen: Int,
        request: LlmExtractionRequest,
        perfTracer: ScanPerfTracer?,
    ): LlmExtractionResult {
        updateState(gen) { it.copy(status = LlmUiStatus.PROCESSING) }
        val startMs = System.currentTimeMillis()
        perfTracer?.startTimer("llm_total_ms")
        // try/finally guarantees `stopTimer` runs exactly once across all
        // exit paths — including the case where the provider *returns*
        // [LlmExtractionResult.Failed] without throwing. The previous logic
        // only stopped the timer in success / cancellation / exception
        // branches; a naturally-returned `Failed` result leaked the timer.
        val result = try {
            provider.extractBagFields(request)
        } catch (e: CancellationException) {
            // Cancellation must propagate so callers (and the session
            // generation guard) know the work was abandoned.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Boundary catch: `provider.extractBagFields` is API-shaped, but
            // its implementations cover binder calls, native model
            // inference, and JSON parsing — each with its own exception
            // hierarchy. Wrapping any non-cancellation throwable into a
            // `Failed` result is the documented contract callers rely on.
            LlmExtractionResult.Failed("Inference threw: ${e.message}", retryable = true)
        } finally {
            perfTracer?.stopTimer("llm_total_ms")
        }
        lastLatencyMs = System.currentTimeMillis() - startMs
        if (gen == sessionGeneration && sessionScope != null) {
            applyResultToState(gen, result)
        }
        return result
    }

    private fun applyResultToState(gen: Int, result: LlmExtractionResult) {
        when (result) {
            is LlmExtractionResult.Success -> {
                lastSuccess = true
                lastTokensUsed = result.tokensUsed
                val newFields = result.fieldCandidates.map { it.fieldName }.toSet()
                updateState(gen) {
                    it.copy(
                        status = LlmUiStatus.COMPLETED,
                        contributedFields = it.contributedFields + newFields,
                    )
                }
            }
            is LlmExtractionResult.Unavailable -> {
                lastSuccess = false
                updateState(gen) { it.copy(status = LlmUiStatus.UNAVAILABLE) }
            }
            is LlmExtractionResult.Failed -> {
                lastSuccess = false
                updateState(gen) { it.copy(status = LlmUiStatus.FAILED) }
            }
        }
    }

    private fun updateState(gen: Int, transform: (LlmCoordinatorState) -> LlmCoordinatorState) {
        if (gen != sessionGeneration) return
        _state.update(transform)
    }

    private fun feedToAccumulator(
        accumulator: FrameEvidenceAccumulator,
        candidates: List<BagFieldCandidate>,
    ) {
        // All LLM-derived candidates are submitted as LLM source. Confidence is
        // already encoded on the candidate itself and is honoured by the
        // ConsensusEngine — we must NOT re-label low-confidence LLM output as
        // OCR, because that corrupts source attribution for cross-validation,
        // for the next LLM escalation's prompt, and for UI treatment.
        val values = candidates
            .associate { it.fieldName to it.value }
            .filter { it.value.isNotBlank() }
        if (values.isNotEmpty()) {
            accumulator.submitEnrichment(
                fieldValues = values,
                sourceType = BagFieldSourceType.LLM,
            )
        }
    }
}
