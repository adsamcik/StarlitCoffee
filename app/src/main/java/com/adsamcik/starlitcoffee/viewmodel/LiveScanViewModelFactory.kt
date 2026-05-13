package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider

/**
 * Manual factory for [LiveScanViewModel]. The repo intentionally avoids a DI
 * framework today (see project conventions: "No DI framework yet; factories /
 * manual wiring are intentional"); when DI lands this can be replaced with a
 * Hilt `@Provides` / `@AssistedInject` binding.
 */
class LiveScanViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveScanViewModel::class.java)) {
            return LiveScanViewModel(
                llmProvider = (application as? StarlitCoffeeApp)?.llmProvider
                    ?: StubLlmInferenceProvider(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
