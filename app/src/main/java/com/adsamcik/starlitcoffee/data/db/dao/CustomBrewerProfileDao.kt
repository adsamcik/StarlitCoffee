package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.adsamcik.starlitcoffee.data.db.entity.CustomBrewerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomBrewerProfileDao {
    @Upsert
    suspend fun upsert(profile: CustomBrewerProfileEntity)

    @Query("SELECT * FROM custom_brewer_profiles ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CustomBrewerProfileEntity>>

    @Query("SELECT * FROM custom_brewer_profiles WHERE id = :id")
    suspend fun getById(id: String): CustomBrewerProfileEntity?

    @Delete
    suspend fun delete(profile: CustomBrewerProfileEntity)
}
