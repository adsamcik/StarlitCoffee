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
}
