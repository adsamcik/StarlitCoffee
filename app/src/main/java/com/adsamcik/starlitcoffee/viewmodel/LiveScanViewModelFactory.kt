package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider

// TODO: Replace with Hilt @Provides when DI is adopted
class LiveScanViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveScanViewModel::class.java)) {
            return LiveScanViewModel(
                llmProvider = MindlayerLlmInferenceProvider(application),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
