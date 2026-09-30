package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.repository.BrewingSetWriter

/** Legacy preference fixtures deliberately exercise the old method-only representation. */
internal object NoBrewingSetWriter : BrewingSetWriter {
    override suspend fun initializeBrewingSets(savedRecipes: List<SavedRecipeEntity>?) = Unit
    override suspend fun saveBrewingSet(set: BrewingSet) = Unit
    override suspend fun selectBrewingSet(id: String) = Unit
    override suspend fun deleteBrewingSet(id: String) = Unit
    override suspend fun updateBrewingSetSetup(id: String, revision: Int, setup: CalculatorSetup) = Unit
}
