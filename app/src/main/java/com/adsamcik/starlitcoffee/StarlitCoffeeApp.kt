package com.adsamcik.starlitcoffee

import android.app.Application
import android.util.Log
import com.adsamcik.starlitcoffee.data.network.llm.LlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider

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
     * Called only by emulated process environments. Real devices normally kill
     * the process without invoking this callback; Mindlayer binder resources
     * are released by process death in that path.
     */
    override fun onTerminate() {
        (llmProvider as? MindlayerLlmInferenceProvider)?.close()
        super.onTerminate()
    }

    private companion object {
        private const val TAG = "StarlitCoffeeApp"
    }
}
