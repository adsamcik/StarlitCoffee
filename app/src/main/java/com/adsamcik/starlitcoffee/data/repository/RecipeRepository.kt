package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import kotlinx.coroutines.flow.Flow

class RecipeRepository(
    private val recipeDao: RecipeDao,
) {
    fun getAllRecipes(): Flow<List<SavedRecipeEntity>> = recipeDao.getAll()

    fun getRecipeById(id: Long): Flow<SavedRecipeEntity?> = recipeDao.getById(id)

    suspend fun insertRecipe(entity: SavedRecipeEntity): Long = recipeDao.insert(entity)

    suspend fun updateRecipe(entity: SavedRecipeEntity) = recipeDao.update(entity)

    suspend fun deleteRecipe(entity: SavedRecipeEntity) = recipeDao.delete(entity)
}
