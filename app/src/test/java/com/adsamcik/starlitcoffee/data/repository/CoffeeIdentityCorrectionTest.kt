package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CoffeeIdentityDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBarcodeEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeIdentityEntity
import com.adsamcik.starlitcoffee.testutil.FakeCoffeeBagDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeIdentityCorrectionTest {
    private val identities = FakeCoffeeIdentityDao()
    private val repository = CoffeeBagRepository(FakeCoffeeBagDao(), identities)

    @Test
    fun `correcting a pack updates reusable coffee without replacing its identity or sibling inventory`() = runTest {
        val firstId = repository.insertBag(CoffeeBagEntity(name = "Scanned name", roaster = "Wrong roaster",
            barcode = "123456789012", weightG = 120f, roastDate = 100L, notes = "First lot"))
        val first = requireNotNull(repository.getBagByIdOnce(firstId))
        val coffeeId = requireNotNull(first.coffeeId)
        val siblingId = repository.insertBag(CoffeeBagEntity(name = "Ignored", coffeeId = coffeeId,
            weightG = 250f, roastDate = 200L, notes = "Second lot"))
        val sibling = repository.getBagByIdOnce(siblingId)
        val originalIdentity = requireNotNull(repository.getCoffee(coffeeId))

        val corrected = first.copy(name = "Correct coffee", roaster = null, isDecaf = true)
        repository.updateBag(corrected)

        assertEquals(corrected, repository.getBagByIdOnce(firstId))
        assertEquals(sibling, repository.getBagByIdOnce(siblingId))
        assertEquals(originalIdentity.copy(name = corrected.name, roaster = null, isDecaf = true),
            repository.findCoffeeIdentitiesByBarcode("123456789012").single())
        val newId = repository.insertBag(CoffeeBagEntity(name = "Ignored", coffeeId = coffeeId, weightG = 500f))
        val newPack = requireNotNull(repository.getBagByIdOnce(newId))
        assertEquals(coffeeId, newPack.coffeeId)
        assertEquals(3, newPack.packNumber)
        assertEquals(corrected.name, newPack.name)
        assertNull(newPack.roaster)
        assertTrue(newPack.isDecaf)
    }

    @Test
    fun `editing a sibling preserves corrected shared fields that the sibling did not change`() = runTest {
        val firstId = repository.insertBag(CoffeeBagEntity(name = "Old name", roaster = "Old roaster"))
        val first = requireNotNull(repository.getBagByIdOnce(firstId))
        val coffeeId = requireNotNull(first.coffeeId)
        val siblingId = repository.insertBag(CoffeeBagEntity(name = "Ignored", coffeeId = coffeeId, weightG = 250f))
        val sibling = requireNotNull(repository.getBagByIdOnce(siblingId))
        repository.updateBag(first.copy(name = "Correct name", isDecaf = true))

        repository.updateBag(sibling.copy(weightG = 200f, status = "OPEN", roastDate = 300L))
        assertEquals("Correct name", repository.getCoffee(coffeeId)?.name)
        assertTrue(requireNotNull(repository.getCoffee(coffeeId)).isDecaf)

        repository.updateBag(requireNotNull(repository.getBagByIdOnce(siblingId)).copy(roaster = "Correct roaster"))
        val identity = requireNotNull(repository.getCoffee(coffeeId))
        assertEquals("Correct name", identity.name)
        assertEquals("Correct roaster", identity.roaster)
        assertTrue(identity.isDecaf)
    }

    private class FakeCoffeeIdentityDao : CoffeeIdentityDao {
        private val coffees = MutableStateFlow<List<CoffeeIdentityEntity>>(emptyList())
        private val barcodes = mutableSetOf<CoffeeBarcodeEntity>()

        override fun getAll(): Flow<List<CoffeeIdentityEntity>> = coffees
        override suspend fun getById(id: Long): CoffeeIdentityEntity? = coffees.value.find { it.id == id }
        override suspend fun insert(coffee: CoffeeIdentityEntity): Long {
            val id = (coffees.value.maxOfOrNull { it.id } ?: 0L) + 1
            coffees.value += coffee.copy(id = id)
            return id
        }
        override suspend fun update(coffee: CoffeeIdentityEntity) {
            coffees.value = coffees.value.map { if (it.id == coffee.id) coffee else it }
        }
        override suspend fun addBarcode(code: CoffeeBarcodeEntity) { barcodes += code }
        override suspend fun findByBarcode(code: String): List<CoffeeIdentityEntity> = coffees.value.filter { coffee ->
            barcodes.any { it.coffeeId == coffee.id && it.normalizedCode == code }
        }
    }
}
