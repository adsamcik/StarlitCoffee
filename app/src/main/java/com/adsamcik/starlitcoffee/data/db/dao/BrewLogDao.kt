package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrewLogDao {
    @Insert
    suspend fun insert(log: BrewLogEntity): Long

    @Query("UPDATE brew_logs SET rating = :rating, freeformNotes = :notes WHERE id = :logId")
    suspend fun updateRating(logId: Long, rating: Float, notes: String?)

    @Query("SELECT * FROM brew_logs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BrewLogEntity>>

    @Query("SELECT * FROM brew_logs WHERE coffeeBagId = :bagId ORDER BY createdAt DESC")
    fun getByBag(bagId: Long): Flow<List<BrewLogEntity>>

    @Query("SELECT * FROM brew_logs WHERE recipeId = :recipeId ORDER BY createdAt DESC")
    fun getByRecipe(recipeId: Long): Flow<List<BrewLogEntity>>

    @Delete
    suspend fun delete(log: BrewLogEntity)
}
