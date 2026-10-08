package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeGrindSettingEntity
import com.adsamcik.starlitcoffee.data.db.entity.PackGrindSettingEntity
import com.adsamcik.starlitcoffee.data.db.entity.TypeGrindSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrindMemoryDao {
    @Query("SELECT * FROM type_grind_settings") fun typeSettings(): Flow<List<TypeGrindSettingEntity>>
    @Query("SELECT * FROM coffee_grind_settings") fun coffeeSettings(): Flow<List<CoffeeGrindSettingEntity>>
    @Query("SELECT * FROM pack_grind_settings") fun packSettings(): Flow<List<PackGrindSettingEntity>>
    @Upsert suspend fun saveType(setting: TypeGrindSettingEntity)
    @Upsert suspend fun saveCoffee(setting: CoffeeGrindSettingEntity)
    @Upsert suspend fun savePack(setting: PackGrindSettingEntity)

    @Query("DELETE FROM type_grind_settings WHERE coffeeType = :owner AND grinderId = :grinder " +
        "AND methodId = :method AND filterKey = :filter")
    suspend fun removeType(owner: String, grinder: String, method: String, filter: String)
    @Query("DELETE FROM coffee_grind_settings WHERE coffeeId = :owner AND grinderId = :grinder " +
        "AND methodId = :method AND filterKey = :filter")
    suspend fun removeCoffee(owner: Long, grinder: String, method: String, filter: String)
    @Query("DELETE FROM pack_grind_settings WHERE packId = :owner AND grinderId = :grinder " +
        "AND methodId = :method AND filterKey = :filter")
    suspend fun removePack(owner: Long, grinder: String, method: String, filter: String)
}
