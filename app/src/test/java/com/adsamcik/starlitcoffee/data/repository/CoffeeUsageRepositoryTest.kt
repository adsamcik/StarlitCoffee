package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.inventory.CoffeeUsageRejection
import com.adsamcik.starlitcoffee.testutil.FakeCoffeeBagDao
import com.adsamcik.starlitcoffee.testutil.FakeCoffeeUsageDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeUsageRepositoryTest {
    @Test
    fun `log use stores entry and inventory change in one transaction`() = runTest {
        val bagDao = FakeCoffeeBagDao()
        val usageDao = FakeCoffeeUsageDao()
        val transactions = RecordingTransactionRunner()
        val bagId = bagDao.insert(bag(weightG = 250f))
        val repository = CoffeeUsageRepository(usageDao, bagDao, transactions)

        val result = repository.logUse(bagId, amountG = 18f, usedAt = USED_AT)

        assertTrue(result is CoffeeUsageLogResult.Logged)
        assertEquals(1, transactions.invocations)
        assertEquals(18f, usageDao.getAll().first().single().amountG, 0.01f)
        assertEquals(232f, bagDao.getByIdOnce(bagId)?.weightG ?: 0f, 0.01f)
    }

    @Test
    fun `rejected use does not write history or inventory`() = runTest {
        val bagDao = FakeCoffeeBagDao()
        val usageDao = FakeCoffeeUsageDao()
        val bagId = bagDao.insert(bag(weightG = 10f))
        val repository = CoffeeUsageRepository(usageDao, bagDao)

        val result = repository.logUse(bagId, amountG = 18f, usedAt = USED_AT)
            as CoffeeUsageLogResult.Rejected

        assertEquals(CoffeeUsageRejection.EXCEEDS_REMAINING, result.reason)
        assertTrue(usageDao.getAll().first().isEmpty())
        assertEquals(10f, bagDao.getByIdOnce(bagId)?.weightG ?: 0f, 0.01f)
    }

    @Test
    fun `undo removes entry and restores exact bag snapshot`() = runTest {
        val bagDao = FakeCoffeeBagDao()
        val usageDao = FakeCoffeeUsageDao()
        val bagId = bagDao.insert(bag(status = "SEALED", weightG = 18f))
        val repository = CoffeeUsageRepository(usageDao, bagDao)
        val logged = repository.logUse(bagId, amountG = 18f, usedAt = USED_AT)
            as CoffeeUsageLogResult.Logged

        val undone = repository.undo(logged)

        assertTrue(undone)
        assertTrue(usageDao.getAll().first().isEmpty())
        assertEquals("SEALED", bagDao.getByIdOnce(bagId)?.status)
        assertEquals(18f, bagDao.getByIdOnce(bagId)?.weightG ?: 0f, 0.01f)
    }

    @Test
    fun `undo refuses to overwrite a newer bag change`() = runTest {
        val bagDao = FakeCoffeeBagDao()
        val usageDao = FakeCoffeeUsageDao()
        val bagId = bagDao.insert(bag(weightG = 100f))
        val repository = CoffeeUsageRepository(usageDao, bagDao)
        val logged = repository.logUse(bagId, amountG = 20f, usedAt = USED_AT)
            as CoffeeUsageLogResult.Logged
        bagDao.update(requireNotNull(bagDao.getByIdOnce(bagId)).copy(weightG = 75f))

        val undone = repository.undo(logged)

        assertFalse(undone)
        assertEquals(75f, bagDao.getByIdOnce(bagId)?.weightG ?: 0f, 0.01f)
        assertEquals(1, usageDao.getAll().first().size)
    }

    private fun bag(
        status: String = "OPEN",
        weightG: Float,
    ) = CoffeeBagEntity(
        name = "Test coffee",
        status = status,
        weightG = weightG,
        initialWeightG = weightG,
    )

    private class RecordingTransactionRunner : TransactionRunner {
        var invocations = 0
            private set

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            invocations++
            return block()
        }
    }

    private companion object {
        const val USED_AT = 1_723_808_000_000L
    }
}
