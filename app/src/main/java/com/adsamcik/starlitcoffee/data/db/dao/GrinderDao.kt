package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.GrinderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrinderDao {
    @Insert
    suspend fun insert(grinder: GrinderEntity): Long

    @Delete
    suspend fun delete(grinder: GrinderEntity)

    @Query("SELECT * FROM user_grinders ORDER BY createdAt DESC")
    fun getAll(): Flow<List<GrinderEntity>>
}
