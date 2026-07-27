package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.PersistedRecipeRecord
import kotlinx.coroutines.flow.map
import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import kotlinx.coroutines.flow.Flow

class RecipeRepository(
    private val recipeDao: RecipeDao,
) {
    fun getAllRecipes(): Flow<List<SavedRecipeEntity>> = recipeDao.getAll()

    fun getRecipeById(id: Long): Flow<SavedRecipeEntity?> = recipeDao.getById(id)

    fun getAllRecipeRecords(): Flow<List<PersistedRecipeRecord>> =
        recipeDao.getAll().map { entities -> entities.map(BrewingPersistenceMapper::recipeRecord) }

    fun getRecipeRecordById(id: Long): Flow<PersistedRecipeRecord?> =
        recipeDao.getById(id).map { entity -> entity?.let(BrewingPersistenceMapper::recipeRecord) }

    suspend fun insertVersionedRecipe(
        legacyFields: SavedRecipeEntity,
        snapshot: BrewRecipeSnapshotV1,
    ): Long = recipeDao.insert(BrewingPersistenceMapper.withRecipeSnapshot(legacyFields, snapshot))

    suspend fun updateVersionedRecipe(
        legacyFields: SavedRecipeEntity,
        snapshot: BrewRecipeSnapshotV1,
    ) = recipeDao.update(BrewingPersistenceMapper.withRecipeSnapshot(legacyFields, snapshot))

    suspend fun insertRecipe(entity: SavedRecipeEntity): Long = recipeDao.insert(entity)

    suspend fun updateRecipe(entity: SavedRecipeEntity) = recipeDao.update(entity)

    suspend fun deleteRecipe(entity: SavedRecipeEntity) = recipeDao.delete(entity)
}
