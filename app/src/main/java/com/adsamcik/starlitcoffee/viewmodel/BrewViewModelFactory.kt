package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository

class BrewViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            return BrewViewModel(
                recipeRepository = RecipeRepository(database.recipeDao()),
                brewLogRepository = BrewLogRepository(database.brewLogDao()),
                coffeeBagRepository = CoffeeBagRepository(database.coffeeBagDao()),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
