package com.adsamcik.starlitcoffee

import android.app.Application
import android.util.Log
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.HierarchicalOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.data.network.ocr.OcrService
import com.adsamcik.starlitcoffee.scan.observability.PersistentLlmDiagnosticsRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class StarlitCoffeeApp : Application() {

    /**
     * The process-shared Mindlayer client (PR #172). One binding + one
     * consent/resume flow shared by the LLM provider, OCR service, and any
     * future feature — instead of a separate `connect()` per provider.
     */
    val mindlayer: Mindlayer by lazy { Mindlayer.shared(this) }

    val llmProvider: LlmInferenceProvider by lazy {
        try {
            MindlayerLlmInferenceProvider(
                mindlayer,
                PersistentLlmDiagnosticsRecorder(applicationContext),
            )
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Mindlayer init failed — falling back to stub", error)
            StubLlmInferenceProvider()
        }
    }

    /**
     * Single Mindlayer OCR service for the whole app. Lazy so the binder
     * connection is only established once a scan actually needs it.
     * Null when the service binding throws at construction — callers
     * degrade by skipping OCR (LLM still runs against the raw image).
     *
     * Wrapped in [HierarchicalOcrService] so wide-and-mashed back-of-bag
     * sticker regions (which the PaddleOCR detector glues into one
     * unparseable token at low resolution) get re-OCR'd from a cropped +
     * upscaled bitmap. See `HierarchicalOcrService` for the rationale.
     */
    val ocrService: OcrService? by lazy {
        try {
            HierarchicalOcrService(MindlayerOcrService(mindlayer))
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Mindlayer OCR init failed", error)
            null
        }
    }

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
            runCatching { mindlayer.awaitConnected(RECONNECT_TIMEOUT) }
            llmProvider.isAvailable()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly warm the Mindlayer SDK off the main thread. Each
        // dereference triggers Mindlayer.connect() → HistoryStore →
        // Room/SQLCipher open. SQLCipher PBKDF2 key derivation is
        // intentionally slow (~100–500 ms on cold start); doing it
        // synchronously from BrewViewModelFactory.create() (which runs
        // on Main when Compose first resolves the BrewViewModel) caused
        // visible main-thread hitches and Choreographer frame skips on
        // first navigation to the scan flow. Warming here ensures the
        // lazy blocks have already initialised by the time the factory
        // touches the properties.
        warmupScope.launch {
            // Touch both lazy properties on a background thread. The
            // results land in the lazy cache; subsequent main-thread
            // reads are pure field lookups.
            @Suppress("UnusedExpression")
            llmProvider
            @Suppress("UnusedExpression")
            ocrService
        }
    }

    /**
     * Called only by emulated process environments. Real devices normally kill
     * the process without invoking this callback; Mindlayer binder resources
     * are released by process death in that path.
     */
    override fun onTerminate() {
        // One process-shared client — tear it down once here.
        Mindlayer.disconnectShared()
        super.onTerminate()
    }

    private companion object {
        private const val TAG = "StarlitCoffeeApp"
        private val RECONNECT_TIMEOUT = 10.seconds
    }
}
