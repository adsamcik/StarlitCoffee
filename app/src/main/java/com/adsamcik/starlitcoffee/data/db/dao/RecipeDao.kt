package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(recipe: SavedRecipeEntity): Long

    @Update
    suspend fun update(recipe: SavedRecipeEntity)

    @Delete
    suspend fun delete(recipe: SavedRecipeEntity)

    @Query("SELECT * FROM saved_recipes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavedRecipeEntity>>

    @Query("SELECT * FROM saved_recipes WHERE id = :id")
    fun getById(id: Long): Flow<SavedRecipeEntity?>
}
