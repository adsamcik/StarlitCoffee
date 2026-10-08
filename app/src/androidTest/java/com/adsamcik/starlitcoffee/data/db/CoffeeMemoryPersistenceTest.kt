package com.adsamcik.starlitcoffee.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.GrindContext
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.data.model.RememberedGrind
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.GrindMemoryRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import com.adsamcik.starlitcoffee.domain.GrindMemoryResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoffeeMemoryPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var bags: CoffeeBagRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        bags = CoffeeBagRepository(db.coffeeBagDao(), db.coffeeIdentityDao(), TransactionRunner.room(db))
    }
    @After fun close() = db.close()

    @Test fun newPackSharesCoffeeButHasFreshInventoryAndNoCopiedOverride() = runBlocking {
        val oldId = bags.insertBag(CoffeeBagEntity(name = "Daily", barcode = "123456789012", weightG = 42f,
            initialWeightG = 250f, status = "OPEN", roastDate = 100L, openedDate = 200L, grindSetting = "legacy"))
        val old = requireNotNull(bags.getBagByIdOnce(oldId))
        val memory = GrindMemoryRepository(db.grindMemoryDao())
        val grinder = Grinder("dial", "Brand", "Model", true, GrinderScaleType.DIAL_CLICKS, 40)
        val context = GrindContext("dial", "V60", "PAPER")
        val setting = RememberedGrind(GrindSaveScope.PACK, oldId.toString(), context,
            requireNotNull(GrindMemoryResolver.parse("1.14", grinder, 1)))
        memory.save(setting)
        val newId = bags.insertBag(CoffeeBagEntity(name = "Reviewed name", coffeeId = old.coffeeId,
            weightG = 500f, initialWeightG = 500f, scanSessionId = "new-purchase", barcode = "0123456789012"))
        val fresh = requireNotNull(bags.getBagByIdOnce(newId))
        assertEquals(old.coffeeId, fresh.coffeeId); assertEquals(2, fresh.packNumber)
        assertEquals("Daily", fresh.name); assertEquals("SEALED", fresh.status)
        assertEquals(500f, fresh.weightG); assertNull(fresh.openedDate); assertNull(fresh.roastDate); assertNull(fresh.grindSetting)
        assertEquals(old, bags.getBagByIdOnce(oldId))
        assertNull(GrindMemoryResolver.resolve(memory.settings().first(), context, grinder, newId, fresh.coffeeId, false))
    }

    @Test fun concurrentRetriesReturnOnePackButNewIntentCreatesAnother() = runBlocking {
        val entity = CoffeeBagEntity(name = "Daily", weightG = 250f, scanSessionId = "one-operation", barcode = "123456789012")
        val results = List(4) { async { bags.insertBag(entity) } }.awaitAll()
        assertEquals(1, results.count { it > 0 }); assertEquals(3, results.count { it == -1L })
        val first = requireNotNull(bags.findByScanSessionId("one-operation"))
        val second = bags.insertBag(entity.copy(coffeeId = first.coffeeId, scanSessionId = "another-purchase"))
        assertTrue(second != first.id); assertEquals(2, bags.getAllBags().first().size)
        assertEquals(1, bags.coffeeIdentities().first().size)
    }

    @Test fun barcodesRecognizeFinishedCoffeeAndKeepAmbiguousIdentities() = runBlocking {
        val id = bags.insertBag(CoffeeBagEntity(name = "Old purchase", barcode = "123456789012", status = "FINISHED", weightG = 0f))
        assertEquals(1, bags.findCoffeeIdentitiesByBarcode("0 123456789012").size)
        val other = bags.insertBag(CoffeeBagEntity(name = "Different coffee", barcode = "0123456789012", isDecaf = true))
        val matches = bags.findCoffeeIdentitiesByBarcode("00123456789012")
        assertEquals(2, matches.size); assertTrue(matches.map { it.id }.distinct().size == 2)
        assertNull(bags.findNextPack(requireNotNull(bags.getBagByIdOnce(id)?.coffeeId), id))
        assertEquals("SEALED", bags.getBagByIdOnce(other)?.status)
        assertTrue(bags.findCoffeeIdentitiesByBarcode("unknown").isEmpty())
    }

    @Test fun openingIsConditionalAndNeverChangesTheNextSealedPack() = runBlocking {
        val firstId = bags.insertBag(CoffeeBagEntity(name = "Daily", weightG = 250f))
        val first = requireNotNull(bags.getBagByIdOnce(firstId))
        val secondId = bags.insertBag(first.copy(id = 0, openedDate = null))
        assertEquals("SEALED", bags.getBagByIdOnce(firstId)?.status)
        bags.markOpenedOnUse(firstId, 100L); bags.markOpenedOnUse(firstId, 200L)
        assertEquals("OPEN", bags.getBagByIdOnce(firstId)?.status); assertEquals(100L, bags.getBagByIdOnce(firstId)?.openedDate)
        assertEquals(secondId, bags.findNextPack(requireNotNull(first.coffeeId), firstId)?.id)
        assertEquals("SEALED", bags.getBagByIdOnce(secondId)?.status)
    }
}
