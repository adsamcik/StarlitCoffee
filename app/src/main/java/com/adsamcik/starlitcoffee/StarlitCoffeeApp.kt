package com.adsamcik.starlitcoffee

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.starlitcoffee.data.network.llm.LlmCombineRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.LlmRefineRequest
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.HierarchicalOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.data.network.ocr.RecognizedText
import com.adsamcik.starlitcoffee.data.work.BagExtractionScheduler
import com.adsamcik.starlitcoffee.scan.observability.PersistentLlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.util.MindlayerAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class StarlitCoffeeApp : Application() {

    val llmProvider: LlmInferenceProvider = RefreshingMindlayerLlmProvider(this)
    val ocrService: OcrService = RefreshingMindlayerOcrService(this)

    @Volatile
    private var mindlayerServices: MindlayerServices? = null
    private val mindlayerServicesLock = Any()

    /**
     * Application-scoped supervisor for one-shot warmup launches that
     * outlive any single ViewModel. Cancelled implicitly when the process
     * dies; nothing here should hold cancellable long-lived resources.
     */
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun onCreate() {
        super.onCreate()
        warmupScope.launch {
            BagExtractionScheduler.reconcilePersistedState(applicationContext)
        }
        if (!MindlayerAvailability.isInstalled(this)) {
            Log.i(TAG, "Mindlayer is not installed; skipping connection warmup")
            return
        }

        // Eagerly warm the Mindlayer SDK off the main thread. Initialization
        // triggers Mindlayer.connect() → HistoryStore →
        // Room/SQLCipher open. SQLCipher PBKDF2 key derivation is
        // intentionally slow (~100–500 ms on cold start); doing it
        // synchronously from BrewViewModelFactory.create() (which runs
        // on Main when Compose first resolves the BrewViewModel) caused
        // visible main-thread hitches and Choreographer frame skips on
        // first navigation to the scan flow.
        warmupScope.launch {
            getOrCreateMindlayerServices()
        }
    }

    /**
     * Called only by emulated process environments. Real devices normally kill
     * the process without invoking this callback; Mindlayer binder resources
     * are released by process death in that path.
     */
    override fun onTerminate() {
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
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
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
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.extractBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer is not available")
            }

        override fun supportsVision(): Boolean =
            app.currentMindlayerServices()?.llmProvider?.supportsVision()
                ?: MindlayerAvailability.isInstalled(app)

        override suspend fun extractBagFieldsWithVision(
            request: LlmExtractionRequest,
        ): LlmExtractionResult = withContext(Dispatchers.IO) {
            app.getOrCreateMindlayerServices()
                ?.llmProvider
                ?.extractBagFieldsWithVision(request)
                ?: LlmExtractionResult.Unavailable("Mindlayer vision is not available")
        }

        override fun supportsCombine(): Boolean =
            app.currentMindlayerServices()?.llmProvider?.supportsCombine()
                ?: MindlayerAvailability.isInstalled(app)

        override suspend fun combineBagFields(request: LlmCombineRequest): LlmExtractionResult =
            withContext(Dispatchers.IO) {
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.combineBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer combine is not available")
            }

        override fun supportsRefine(): Boolean =
            app.currentMindlayerServices()?.llmProvider?.supportsRefine()
                ?: MindlayerAvailability.isInstalled(app)

        override suspend fun refineBagFields(request: LlmRefineRequest): LlmExtractionResult =
            withContext(Dispatchers.IO) {
                app.getOrCreateMindlayerServices()
                    ?.llmProvider
                    ?.refineBagFields(request)
                    ?: LlmExtractionResult.Unavailable("Mindlayer refine is not available")
            }

        override fun isAvailable(): Boolean {
            if (!MindlayerAvailability.isInstalled(app)) return false
            return app.currentMindlayerServices()?.llmProvider?.isAvailable() ?: true
        }

        override suspend fun prewarm() {
            withContext(Dispatchers.IO) {
                app.getOrCreateMindlayerServices()?.llmProvider?.prewarm()
            }
        }
    }

    private class RefreshingMindlayerOcrService(
        private val app: StarlitCoffeeApp,
    ) : OcrService {
        override fun close() = Unit

        override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
            app.getOrCreateMindlayerServices()?.ocrService?.isAvailable() == true
        }

        override suspend fun recognize(bitmap: Bitmap): RecognizedText? = withContext(Dispatchers.IO) {
            app.getOrCreateMindlayerServices()?.ocrService?.recognize(bitmap)
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
