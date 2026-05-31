package com.adsamcik.starlitcoffee

import android.app.Application
import android.util.Log
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StarlitCoffeeApp : Application() {
    val llmProvider: LlmInferenceProvider by lazy {
        try {
            MindlayerLlmInferenceProvider(this)
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
     */
    val ocrService: MindlayerOcrService? by lazy {
        try {
            MindlayerOcrService(this)
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
        (llmProvider as? MindlayerLlmInferenceProvider)?.close()
        ocrService?.close()
        super.onTerminate()
    }

    private companion object {
        private const val TAG = "StarlitCoffeeApp"
    }
}
