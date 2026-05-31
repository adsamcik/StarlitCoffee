package com.adsamcik.starlitcoffee

import android.app.Application
import android.util.Log
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService

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
