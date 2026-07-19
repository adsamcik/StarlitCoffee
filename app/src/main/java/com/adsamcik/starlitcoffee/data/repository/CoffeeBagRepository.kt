package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import kotlinx.coroutines.flow.Flow

class CoffeeBagRepository(
    private val coffeeBagDao: CoffeeBagDao,
) {
    fun getActiveBags(): Flow<List<CoffeeBagEntity>> = coffeeBagDao.getActive()

    fun getAllBags(): Flow<List<CoffeeBagEntity>> = coffeeBagDao.getAll()

    fun getBagById(id: Long): Flow<CoffeeBagEntity?> = coffeeBagDao.getById(id)

    suspend fun insertBag(entity: CoffeeBagEntity): Long = coffeeBagDao.insert(entity)

    suspend fun updateBag(entity: CoffeeBagEntity) = coffeeBagDao.update(entity)

    suspend fun deleteBag(entity: CoffeeBagEntity) = coffeeBagDao.delete(entity)

    suspend fun findByBarcode(barcode: String): CoffeeBagEntity? = coffeeBagDao.findByBarcode(barcode)

    suspend fun findByScanSessionId(scanSessionId: String): CoffeeBagEntity? =
        coffeeBagDao.findByScanSessionId(scanSessionId)

    suspend fun findNextSealed(name: String, roaster: String?): CoffeeBagEntity? =
        coffeeBagDao.findNextSealed(name, roaster)

    suspend fun getDistinctOrigins(): List<String> = coffeeBagDao.getDistinctOrigins()

    suspend fun getDistinctRegions(): List<String> = coffeeBagDao.getDistinctRegions()

    suspend fun getDistinctVarieties(): List<String> = coffeeBagDao.getDistinctVarieties()

    suspend fun getDistinctProcessTypes(): List<String> = coffeeBagDao.getDistinctProcessTypes()

    suspend fun getDistinctRoastLevels(): List<String> = coffeeBagDao.getDistinctRoastLevels()

    suspend fun getDistinctFarms(): List<String> = coffeeBagDao.getDistinctFarms()
}
