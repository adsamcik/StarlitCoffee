package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup

interface BrewingSetWriter {
    suspend fun initializeBrewingSets(savedRecipes: List<SavedRecipeEntity>? = null)
    suspend fun saveBrewingSet(set: BrewingSet)
    suspend fun selectBrewingSet(id: String)
    suspend fun deleteBrewingSet(id: String)
    suspend fun updateBrewingSetSetup(id: String, revision: Int, setup: CalculatorSetup)
}
