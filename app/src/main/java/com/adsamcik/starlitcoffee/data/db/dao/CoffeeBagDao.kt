package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoffeeBagDao {
    @Insert
    suspend fun insert(bag: CoffeeBagEntity): Long

    @Update
    suspend fun update(bag: CoffeeBagEntity)

    @Delete
    suspend fun delete(bag: CoffeeBagEntity)

    @Query("SELECT * FROM coffee_bags WHERE status != 'FINISHED' ORDER BY createdAt DESC")
    fun getActive(): Flow<List<CoffeeBagEntity>>

    @Query("SELECT * FROM coffee_bags ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CoffeeBagEntity>>

    @Query("SELECT * FROM coffee_bags WHERE id = :id")
    fun getById(id: Long): Flow<CoffeeBagEntity?>

    @Query("SELECT * FROM coffee_bags WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): CoffeeBagEntity?

    @Query(
        "SELECT * FROM coffee_bags WHERE name = :name AND " +
            "(roaster = :roaster OR (roaster IS NULL AND :roaster IS NULL)) AND " +
            "status = 'SEALED' ORDER BY createdAt ASC LIMIT 1",
    )
    suspend fun findNextSealed(name: String, roaster: String?): CoffeeBagEntity?
}
