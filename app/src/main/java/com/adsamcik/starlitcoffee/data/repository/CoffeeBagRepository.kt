package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeIdentityDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeIdentityEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBarcodeEntity
import com.adsamcik.starlitcoffee.util.coffeeBarcodeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CoffeeBagRepository(
    private val coffeeBagDao: CoffeeBagDao,
    private val identityDao: CoffeeIdentityDao? = null,
    private val transactionRunner: TransactionRunner = TransactionRunner.Direct,
) {
    fun getActiveBags(): Flow<List<CoffeeBagEntity>> = coffeeBagDao.getActive()

    fun getAllBags(): Flow<List<CoffeeBagEntity>> = coffeeBagDao.getAll()

    fun getBagById(id: Long): Flow<CoffeeBagEntity?> = coffeeBagDao.getById(id)

    suspend fun getBagByIdOnce(id: Long): CoffeeBagEntity? = coffeeBagDao.getByIdOnce(id)

    suspend fun insertBag(entity: CoffeeBagEntity): Long = transactionRunner.runInTransaction {
        entity.scanSessionId?.let { token ->
            coffeeBagDao.findByScanSessionId(token)?.let { return@runInTransaction -1L }
        }
        val coffeeDao = identityDao ?: return@runInTransaction coffeeBagDao.insert(entity)
        val identity = entity.coffeeId?.let { id ->
            requireNotNull(coffeeDao.getById(id)) { "Unknown coffee identity" }
        } ?: CoffeeIdentityEntity(name = entity.name, roaster = entity.roaster, isDecaf = entity.isDecaf)
            .let { it.copy(id = coffeeDao.insert(it)) }
        val pack = entity.copy(
            coffeeId = identity.id, name = identity.name, roaster = identity.roaster, isDecaf = identity.isDecaf,
            packNumber = coffeeBagDao.nextPackNumber(identity.id),
        )
        val id = coffeeBagDao.insert(pack)
        check(id > 0) { "Pack could not be added" }
        coffeeBarcodeKey(pack.barcode)?.let { code ->
            coffeeDao.addBarcode(CoffeeBarcodeEntity(identity.id, code, requireNotNull(pack.barcode)))
        }
        id
    }

    suspend fun updateBag(entity: CoffeeBagEntity) = transactionRunner.runInTransaction {
        coffeeBagDao.update(entity)
        val coffeeId = entity.coffeeId
        if (coffeeId != null) coffeeBarcodeKey(entity.barcode)?.let { code ->
            identityDao?.addBarcode(CoffeeBarcodeEntity(coffeeId, code, requireNotNull(entity.barcode)))
        }
    }

    suspend fun deleteBag(entity: CoffeeBagEntity) = coffeeBagDao.delete(entity)

    suspend fun findByBarcode(barcode: String): CoffeeBagEntity? = coffeeBagDao.findByBarcode(barcode)

    suspend fun findByScanSessionId(scanSessionId: String): CoffeeBagEntity? =
        coffeeBagDao.findByScanSessionId(scanSessionId)

    suspend fun findNextSealed(name: String, roaster: String?): CoffeeBagEntity? =
        coffeeBagDao.findNextSealed(name, roaster)

    fun coffeeIdentities(): Flow<List<CoffeeIdentityEntity>> = identityDao?.getAll() ?: flowOf(emptyList())

    suspend fun getCoffee(id: Long): CoffeeIdentityEntity? = identityDao?.getById(id)

    suspend fun findCoffeeIdentitiesByBarcode(barcode: String): List<CoffeeIdentityEntity> =
        coffeeBarcodeKey(barcode)?.let { identityDao?.findByBarcode(it) }.orEmpty()

    suspend fun findNextPack(coffeeId: Long, excludedId: Long): CoffeeBagEntity? =
        coffeeBagDao.findNextPack(coffeeId, excludedId)

    suspend fun markOpenedOnUse(id: Long, openedAt: Long) = coffeeBagDao.markOpenedOnUse(id, openedAt)

    suspend fun getDistinctOrigins(): List<String> = coffeeBagDao.getDistinctOrigins()

    suspend fun getDistinctRegions(): List<String> = coffeeBagDao.getDistinctRegions()

    suspend fun getDistinctVarieties(): List<String> = coffeeBagDao.getDistinctVarieties()

    suspend fun getDistinctProcessTypes(): List<String> = coffeeBagDao.getDistinctProcessTypes()

    suspend fun getDistinctRoastLevels(): List<String> = coffeeBagDao.getDistinctRoastLevels()

    suspend fun getDistinctFarms(): List<String> = coffeeBagDao.getDistinctFarms()
}
