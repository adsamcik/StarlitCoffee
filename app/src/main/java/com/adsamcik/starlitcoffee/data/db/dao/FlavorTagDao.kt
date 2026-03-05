package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlavorTagDao {
    @Insert
    suspend fun insertAll(tags: List<FlavorTagEntity>)

    @Query("SELECT * FROM flavor_tags WHERE brewLogId = :brewLogId")
    fun getForBrewLog(brewLogId: Long): Flow<List<FlavorTagEntity>>

    @Query("SELECT * FROM flavor_tags WHERE brewLogId IN (SELECT id FROM brew_logs WHERE coffeeBagId = :bagId)")
    fun getForBag(bagId: Long): Flow<List<FlavorTagEntity>>

    @Query("DELETE FROM flavor_tags WHERE brewLogId = :brewLogId")
    suspend fun deleteForBrewLog(brewLogId: Long)
}
