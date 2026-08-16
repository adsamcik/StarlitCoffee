package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeUsageEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoffeeUsageDao {
    @Insert
    suspend fun insert(entry: CoffeeUsageEntryEntity): Long

    @Delete
    suspend fun delete(entry: CoffeeUsageEntryEntity)

    @Query("SELECT * FROM coffee_usage_entries WHERE id = :id")
    suspend fun getByIdOnce(id: Long): CoffeeUsageEntryEntity?

    @Query("SELECT * FROM coffee_usage_entries ORDER BY createdAt DESC, id DESC")
    fun getAll(): Flow<List<CoffeeUsageEntryEntity>>

    @Query(
        "SELECT * FROM coffee_usage_entries " +
            "WHERE coffeeBagId = :bagId ORDER BY createdAt DESC, id DESC",
    )
    fun getByBag(bagId: Long): Flow<List<CoffeeUsageEntryEntity>>
}
