package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CupPresetDao {
    @Query("SELECT * FROM cup_presets ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<CupPresetEntity>>

    @Query("SELECT COUNT(*) FROM cup_presets")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: CupPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<CupPresetEntity>)

    @Update
    suspend fun update(preset: CupPresetEntity)

    @Delete
    suspend fun delete(preset: CupPresetEntity)

    @Query("DELETE FROM cup_presets")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(presets: List<CupPresetEntity>) {
        deleteAll()
        insertAll(presets)
    }
}
