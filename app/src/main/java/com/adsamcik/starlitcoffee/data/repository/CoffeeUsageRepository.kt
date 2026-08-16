package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeUsageDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeUsageEntryEntity
import com.adsamcik.starlitcoffee.data.inventory.CoffeeUsagePlanResult
import com.adsamcik.starlitcoffee.data.inventory.CoffeeUsagePlanner
import com.adsamcik.starlitcoffee.data.inventory.CoffeeUsageRejection
import kotlinx.coroutines.flow.Flow

sealed interface CoffeeUsageLogResult {
    data class Logged(
        val entry: CoffeeUsageEntryEntity,
        val previousBag: CoffeeBagEntity,
        val updatedBag: CoffeeBagEntity,
    ) : CoffeeUsageLogResult

    data class Rejected(
        val reason: CoffeeUsageRejection,
        val remainingG: Float? = null,
    ) : CoffeeUsageLogResult

    data object Failed : CoffeeUsageLogResult
}

class CoffeeUsageRepository(
    private val coffeeUsageDao: CoffeeUsageDao,
    private val coffeeBagDao: CoffeeBagDao,
    private val transactionRunner: TransactionRunner = TransactionRunner.Direct,
) {
    fun getAllEntries(): Flow<List<CoffeeUsageEntryEntity>> = coffeeUsageDao.getAll()

    fun getEntriesByBag(bagId: Long): Flow<List<CoffeeUsageEntryEntity>> =
        coffeeUsageDao.getByBag(bagId)

    suspend fun logUse(
        bagId: Long,
        amountG: Float,
        usedAt: Long = System.currentTimeMillis(),
    ): CoffeeUsageLogResult = transactionRunner {
        val bag = coffeeBagDao.getByIdOnce(bagId)
            ?: return@transactionRunner CoffeeUsageLogResult.Rejected(
                CoffeeUsageRejection.BAG_NOT_FOUND,
            )
        when (val result = CoffeeUsagePlanner.plan(bag, amountG, usedAt)) {
            is CoffeeUsagePlanResult.Rejected -> CoffeeUsageLogResult.Rejected(
                reason = result.reason,
                remainingG = result.remainingG,
            )
            is CoffeeUsagePlanResult.Planned -> {
                val plan = result.plan
                val draft = CoffeeUsageEntryEntity(
                    coffeeBagId = bag.id,
                    amountG = plan.amountG,
                    createdAt = plan.usedAt,
                )
                val entry = draft.copy(id = coffeeUsageDao.insert(draft))
                coffeeBagDao.update(plan.updatedBag)
                CoffeeUsageLogResult.Logged(
                    entry = entry,
                    previousBag = plan.previousBag,
                    updatedBag = plan.updatedBag,
                )
            }
        }
    }

    /**
     * Reverts an immediate snackbar action only while the bag still matches the
     * state produced by that entry. A later edit or use is never overwritten.
     */
    suspend fun undo(logged: CoffeeUsageLogResult.Logged): Boolean = transactionRunner {
        val storedEntry = coffeeUsageDao.getByIdOnce(logged.entry.id)
        val currentBag = coffeeBagDao.getByIdOnce(logged.entry.coffeeBagId)
        if (storedEntry != logged.entry || currentBag != logged.updatedBag) {
            return@transactionRunner false
        }
        coffeeUsageDao.delete(storedEntry)
        coffeeBagDao.update(logged.previousBag)
        true
    }
}
