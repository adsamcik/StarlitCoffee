package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBarcodeEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoffeeIdentityDao {
    @Query("SELECT * FROM coffee_identities ORDER BY name COLLATE NOCASE, id")
    fun getAll(): Flow<List<CoffeeIdentityEntity>>

    @Query("SELECT * FROM coffee_identities WHERE id = :id")
    suspend fun getById(id: Long): CoffeeIdentityEntity?

    @Insert suspend fun insert(coffee: CoffeeIdentityEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBarcode(code: CoffeeBarcodeEntity)

    @Query("SELECT c.* FROM coffee_identities c JOIN coffee_barcodes b ON c.id = b.coffeeId " +
        "WHERE b.normalizedCode = :code ORDER BY c.name, c.id")
    suspend fun findByBarcode(code: String): List<CoffeeIdentityEntity>
}
