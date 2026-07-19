package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress(
    // Room DAOs are inherently fan-out: one method per CRUD/query pattern
    // against the same table. Splitting them across multiple DAOs would
    // scatter the bag-table API and forces the repository to hold N daos.
    "TooManyFunctions",
)
interface CoffeeBagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    @Query("SELECT * FROM coffee_bags WHERE scanSessionId = :scanSessionId LIMIT 1")
    suspend fun findByScanSessionId(scanSessionId: String): CoffeeBagEntity?

    @Query(
        "SELECT * FROM coffee_bags WHERE name = :name AND " +
            "(roaster = :roaster OR (roaster IS NULL AND :roaster IS NULL)) AND " +
            "status = 'SEALED' ORDER BY createdAt ASC LIMIT 1",
    )
    suspend fun findNextSealed(name: String, roaster: String?): CoffeeBagEntity?

    @Query("SELECT DISTINCT origin FROM coffee_bags WHERE origin IS NOT NULL")
    suspend fun getDistinctOrigins(): List<String>

    @Query("SELECT DISTINCT region FROM coffee_bags WHERE region IS NOT NULL")
    suspend fun getDistinctRegions(): List<String>

    @Query("SELECT DISTINCT variety FROM coffee_bags WHERE variety IS NOT NULL")
    suspend fun getDistinctVarieties(): List<String>

    @Query("SELECT DISTINCT processType FROM coffee_bags WHERE processType IS NOT NULL")
    suspend fun getDistinctProcessTypes(): List<String>

    @Query("SELECT DISTINCT roastLevel FROM coffee_bags WHERE roastLevel IS NOT NULL")
    suspend fun getDistinctRoastLevels(): List<String>

    @Query("SELECT DISTINCT farm FROM coffee_bags WHERE farm IS NOT NULL")
    suspend fun getDistinctFarms(): List<String>
}
