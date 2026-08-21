package com.adsamcik.starlitcoffee

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmRefineRequest
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.FallbackOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.HierarchicalOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MlKitOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.data.network.ocr.RecognizedText
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.data.work.BagExtractionScheduler
import com.adsamcik.starlitcoffee.data.work.BagExtractionStartupRecovery
import com.adsamcik.starlitcoffee.scan.observability.PersistentLlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.util.MindlayerAvailability
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class StarlitCoffeeApp : Application() {

    val llmProvider: LlmInferenceProvider = RefreshingMindlayerLlmProvider(this)
    private val bundledOcrService: OcrService = MlKitOcrService()
    val ocrService: OcrService = PreferenceAwareOcrService(this, bundledOcrService)

    @Volatile
    private var recognitionPreference: RecognitionPreference = RecognitionPreference.UNDECIDED

    @Volatile
    private var mindlayerServices: MindlayerServices? = null
    private val mindlayerServicesLock = Any()

    /**
     * Application-scoped supervisor for one-shot warmup launches that
     * outlive any single ViewModel. Cancelled implicitly when the process
     * dies; nothing here should hold cancellable long-lived resources.
     */
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bagExtractionStartupRecovery = BagExtractionStartupRecovery(warmupScope) {
        BagExtractionScheduler.reconcilePersistedState(applicationContext)
    }

    /** Single-flights consent-triggered reconnects from multiple UI entry points. */
    private val reconnectMutex = Mutex()

    /**
     * Resume the Mindlayer connection after the user grants consent.
     *
     * With the process-shared client (PR #172) there is a single binding for
     * LLM + OCR, so resume is just one `awaitConnected()` — it rebinds the
     * terminal `REJECTED_NOT_APPROVED` state once consent lands and recovers
     * every feature together. Returns whether the client is available
     * afterwards (the gate the live scan checks). Single-flighted so a double
     * tap, or scan + settings both triggering it, can't race two re-binds.
     */
    suspend fun reconnectMindlayer(): Boolean = reconnectMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!MindlayerAvailability.isInstalled(this@StarlitCoffeeApp)) {
                return@withContext false
            }
            val services = recreateMindlayerServices() ?: return@withContext false
            awaitSuccessfulMindlayerReconnect(
                awaitConnection = { services.client.awaitConnected(RECONNECT_TIMEOUT) },
                checkAvailability = { services.llmProvider.isAvailable() },
                onFailure = { error -> Log.w(TAG, "Mindlayer reconnect failed", error) },
            )
        }
    }

    /** Makes an explicit, contextual user opt-in effective before DataStore emits it. */
    fun enableMindlayerForCurrentSession() {
        recognitionPreference = RecognitionPreference.ENABLED
    }

    /** Stops new Mindlayer calls immediately while DataStore persists the opt-out. */
    fun disableMindlayerForCurrentSession() {
        recognitionPreference = RecognitionPreference.DISABLED
    }

    internal fun isMindlayerEnrichmentEnabled(): Boolean =
        recognitionPreference == RecognitionPreference.ENABLED

    override fun onCreate() {
        super.onCreate()
        warmupScope.launch {
            awaitBagExtractionStartupRecovery()
        }

        warmupScope.launch {
            try {
                BrewSessionRuntime.create(applicationContext).reconcileRecoverableSessions()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Durable brew-session recovery failed", error)
            }
        }
        // Warm Mindlayer only after the user has enabled label recognition.
        // The bundled recognizer remains available without this connection.
        warmupScope.launch {
            UserPreferencesRepository(applicationContext).userPreferences.collectLatest { preferences ->
                recognitionPreference = preferences.labelRecognitionPreference
                if (recognitionPreference == RecognitionPreference.ENABLED &&
                    MindlayerAvailability.isInstalled(this@StarlitCoffeeApp)
                ) {
                    getOrCreateMindlayerServices()
                }
            }
        }
    }

    /** Shares the application-owned startup reconciliation with dependent UI initialization. */
    internal suspend fun awaitBagExtractionStartupRecovery(): Set<String> =
        bagExtractionStartupRecovery.await()

    /**
     * Called only by emulated process environments. Real devices normally kill
     * the process without invoking this callback; Mindlayer binder resources
     * are released by process death in that path.
     */
    override fun onTerminate() {
        bundledOcrService.close()
        synchronized(mindlayerServicesLock) {
            mindlayerServices = null
            Mindlayer.disconnectShared()
        }
        super.onTerminate()
    }

    internal fun getOrCreateMindlayerServices(): MindlayerServices? {
        if (!MindlayerAvailability.isInstalled(this)) return null
        mindlayerServices?.let { return it }
        return synchronized(mindlayerServicesLock) {
            mindlayerServices ?: try {
                val client = Mindlayer.shared(this)
                MindlayerServices(
                    client = client,
                    llmProvider = MindlayerLlmInferenceProvider(
                        client,
                        PersistentLlmDiagnosticsRecorder(applicationContext),
                    ),
                    ocrService = HierarchicalOcrService(MindlayerOcrService(client)),
                ).also { mindlayerServices = it }
            } catch (error: Exception) {
                Log.e(TAG, "Mindlayer service initialization failed", error)
                null
            }
        }
    }

    internal fun currentMindlayerServices(): MindlayerServices? = mindlayerServices

    private fun recreateMindlayerServices(): MindlayerServices? =
        synchronized(mindlayerServicesLock) {
            mindlayerServices = null
            Mindlayer.disconnectShared()
            getOrCreateMindlayerServices()
        }

    private companion object {
        private const val TAG = "StarlitCoffeeApp"
        private val RECONNECT_TIMEOUT = 10.seconds
    }

    internal data class MindlayerServices(
        val client: Mindlayer,
        val llmProvider: MindlayerLlmInferenceProvider,
        val ocrService: OcrService,
    )

    private class RefreshingMindlayerLlmProvider(
        private val app: StarlitCoffeeApp,
    ) : LlmInferenceProvider {
        override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult =
            withContext(Dispatchers.IO) {
                if (!app.isMindlayerEnrichmentEnabled()) {
                    return@withContext LlmExtractionResult.Unavailable("Label recognition enrichment is disabled")
                }
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.extractBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer is not available")
            }

        override fun supportsVision(): Boolean =
            app.isMindlayerEnrichmentEnabled() && (
                app.currentMindlayerServices()?.llmProvider?.supportsVision()
                ?: MindlayerAvailability.isInstalled(app)
                )

        override suspend fun extractBagFieldsWithVision(
            request: LlmExtractionRequest,
        ): LlmExtractionResult = withContext(Dispatchers.IO) {
            if (!app.isMindlayerEnrichmentEnabled()) {
                return@withContext LlmExtractionResult.Unavailable("Label recognition enrichment is disabled")
            }
            app.getOrCreateMindlayerServices()
                ?.llmProvider
                ?.extractBagFieldsWithVision(request)
                ?: LlmExtractionResult.Unavailable("Mindlayer vision is not available")
        }

        override fun supportsCombine(): Boolean =
            app.isMindlayerEnrichmentEnabled() && (
                app.currentMindlayerServices()?.llmProvider?.supportsCombine()
                ?: MindlayerAvailability.isInstalled(app)
                )

        override suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult =
            withContext(Dispatchers.IO) {
                if (!app.isMindlayerEnrichmentEnabled()) {
                    return@withContext LlmExtractionResult.Unavailable("Label recognition enrichment is disabled")
                }
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.combineBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer combine is not available")
            }

        override fun supportsRefine(): Boolean =
            app.isMindlayerEnrichmentEnabled() && (
                app.currentMindlayerServices()?.llmProvider?.supportsRefine()
                ?: MindlayerAvailability.isInstalled(app)
                )

        override suspend fun refineBagFields(request: LlmRefineRequest): LlmExtractionResult =
            withContext(Dispatchers.IO) {
                if (!app.isMindlayerEnrichmentEnabled()) {
                    return@withContext LlmExtractionResult.Unavailable("Label recognition enrichment is disabled")
                }
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.refineBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer refine is not available")
            }

        override fun isAvailable(): Boolean {
            if (!app.isMindlayerEnrichmentEnabled()) return false
            if (!MindlayerAvailability.isInstalled(app)) return false
            return app.currentMindlayerServices()?.llmProvider?.isAvailable() ?: true
        }

        override suspend fun prewarm() {
            if (!app.isMindlayerEnrichmentEnabled()) return
            withContext(Dispatchers.IO) {
                app.getOrCreateMindlayerServices()?.llmProvider?.prewarm()
            }
        }
    }

    private class PreferenceAwareOcrService(
        private val app: StarlitCoffeeApp,
        private val fallback: OcrService,
    ) : OcrService {
        override fun close() = fallback.close()

        override suspend fun isAvailable(): Boolean = fallback.isAvailable()

        override suspend fun recognize(bitmap: Bitmap): RecognizedText? = withContext(Dispatchers.IO) {
            val primary = if (app.isMindlayerEnrichmentEnabled()) {
                app.getOrCreateMindlayerServices()?.ocrService
            } else {
                null
            }
            if (primary == null) {
                fallback.recognize(bitmap)
            } else {
                FallbackOcrService(primary, fallback).recognize(bitmap)
            }
        }
    }
}

internal suspend fun awaitSuccessfulMindlayerReconnect(
    awaitConnection: suspend () -> Unit,
    checkAvailability: suspend () -> Boolean,
    onFailure: (Exception) -> Unit = {},
): Boolean = try {
    awaitConnection()
    checkAvailability()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    onFailure(error)
    false
}
