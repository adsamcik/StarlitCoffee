package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatioPresetDao {
    @Query("SELECT * FROM ratio_presets WHERE methodName = :methodName ORDER BY sortOrder")
    fun getByMethod(methodName: String): Flow<List<RatioPresetEntity>>

    @Insert
    suspend fun insertAll(presets: List<RatioPresetEntity>)

    @Query("DELETE FROM ratio_presets WHERE methodName = :methodName")
    suspend fun deleteByMethod(methodName: String)

    @Query("SELECT COUNT(*) FROM ratio_presets WHERE methodName = :methodName")
    suspend fun countByMethod(methodName: String): Int
}
