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
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository

class BrewViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            return BrewViewModel(
                recipeRepository = RecipeRepository(database.recipeDao()),
                brewLogRepository = BrewLogRepository(database, database.brewLogDao(), database.flavorTagDao()),
                coffeeBagRepository = CoffeeBagRepository(database.coffeeBagDao()),
                ratioPresetRepository = RatioPresetRepository(database.ratioPresetDao()),
                userPreferencesRepository = UserPreferencesRepository(application),
                grinderData = GrinderDataSource.getInstance(application),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
