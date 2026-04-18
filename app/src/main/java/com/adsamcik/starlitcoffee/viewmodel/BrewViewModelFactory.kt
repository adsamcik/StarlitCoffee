package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RatioPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository

// TODO: Replace with Hilt @Provides when DI is adopted
class BrewViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            val llm = try {
                MindlayerLlmInferenceProvider(application)
            } catch (t: Throwable) {
                android.util.Log.e("BrewViewModelFactory", "Mindlayer init failed — falling back to stub", t)
                com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider()
            }
            return BrewViewModel(
                recipeRepository = RecipeRepository(database.recipeDao()),
                brewLogRepository = BrewLogRepository(database, database.brewLogDao(), database.flavorTagDao()),
                coffeeBagRepository = CoffeeBagRepository(database.coffeeBagDao()),
                ratioPresetRepository = RatioPresetRepository(database.ratioPresetDao()),
                userPreferencesRepository = UserPreferencesRepository(application),
                grinderData = GrinderDataSource.getInstance(application),
                llmProvider = llm,
                userBarcodeStemDao = database.userBarcodeStemDao(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
