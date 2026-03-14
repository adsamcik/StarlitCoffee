package com.adsamcik.starlitcoffee.testutil

import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.FlavorTagDao
import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeRecipeDao : RecipeDao {
    private val recipes = mutableListOf<SavedRecipeEntity>()
    private val flow = MutableStateFlow<List<SavedRecipeEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(recipe: SavedRecipeEntity): Long {
        val id = nextId++
        recipes.add(recipe.copy(id = id))
        flow.value = recipes.toList()
        return id
    }

    override suspend fun update(recipe: SavedRecipeEntity) {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index >= 0) {
            recipes[index] = recipe
            flow.value = recipes.toList()
        }
    }

    override suspend fun delete(recipe: SavedRecipeEntity) {
        recipes.removeAll { it.id == recipe.id }
        flow.value = recipes.toList()
    }

    override fun getAll(): Flow<List<SavedRecipeEntity>> = flow

    override fun getById(id: Long): Flow<SavedRecipeEntity?> = flow.map { list -> list.find { it.id == id } }
}

internal class FakeBrewLogDao : BrewLogDao {
    private val logs = mutableListOf<BrewLogEntity>()
    private val flow = MutableStateFlow<List<BrewLogEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(log: BrewLogEntity): Long {
        val id = nextId++
        logs.add(log.copy(id = id))
        flow.value = logs.toList()
        return id
    }

    override suspend fun delete(log: BrewLogEntity) {
        logs.removeAll { it.id == log.id }
        flow.value = logs.toList()
    }

    override suspend fun updateRating(logId: Long, rating: Float, notes: String?) {
        val index = logs.indexOfFirst { it.id == logId }
        if (index >= 0) {
            logs[index] = logs[index].copy(rating = rating, freeformNotes = notes)
            flow.value = logs.toList()
        }
    }

    override suspend fun updateFeedback(logId: Long, rating: Float?, notes: String?, tasteFeedback: String?) {
        val index = logs.indexOfFirst { it.id == logId }
        if (index >= 0) {
            logs[index] = logs[index].copy(rating = rating, freeformNotes = notes, tasteFeedback = tasteFeedback)
            flow.value = logs.toList()
        }
    }

    override suspend fun getById(logId: Long): BrewLogEntity? {
        return logs.find { it.id == logId }
    }

    override fun getAll(): Flow<List<BrewLogEntity>> = flow

    override fun getByBag(bagId: Long): Flow<List<BrewLogEntity>> = flow.map { list ->
        list.filter { it.coffeeBagId == bagId }
    }

    override fun getByRecipe(recipeId: Long): Flow<List<BrewLogEntity>> = flow.map { list ->
        list.filter { it.recipeId == recipeId }
    }

    override suspend fun getLastUnrated(): BrewLogEntity? {
        return logs.filter { it.rating == null }
            .maxByOrNull { it.createdAt }
    }
}

internal class FakeCoffeeBagDao : CoffeeBagDao {
    private val bags = mutableListOf<CoffeeBagEntity>()
    private val flow = MutableStateFlow<List<CoffeeBagEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(bag: CoffeeBagEntity): Long {
        val id = nextId++
        bags.add(bag.copy(id = id))
        flow.value = bags.toList()
        return id
    }

    override suspend fun update(bag: CoffeeBagEntity) {
        val index = bags.indexOfFirst { it.id == bag.id }
        if (index >= 0) {
            bags[index] = bag
            flow.value = bags.toList()
        }
    }

    override suspend fun delete(bag: CoffeeBagEntity) {
        bags.removeAll { it.id == bag.id }
        flow.value = bags.toList()
    }

    override fun getActive(): Flow<List<CoffeeBagEntity>> = flow.map { list ->
        list.filter { it.status != "FINISHED" }
    }

    override fun getAll(): Flow<List<CoffeeBagEntity>> = flow

    override fun getById(id: Long): Flow<CoffeeBagEntity?> = flow.map { list -> list.find { it.id == id } }

    override suspend fun findByBarcode(barcode: String): CoffeeBagEntity? = bags.find { it.barcode == barcode }

    override suspend fun findNextSealed(name: String, roaster: String?): CoffeeBagEntity? =
        bags.find { it.name == name && it.roaster == roaster && it.status == "SEALED" }

    override suspend fun getDistinctOrigins(): List<String> = bags.mapNotNull { it.origin }.distinct()

    override suspend fun getDistinctRegions(): List<String> = bags.mapNotNull { it.region }.distinct()

    override suspend fun getDistinctVarieties(): List<String> = bags.mapNotNull { it.variety }.distinct()

    override suspend fun getDistinctProcessTypes(): List<String> = bags.mapNotNull { it.processType }.distinct()

    override suspend fun getDistinctRoastLevels(): List<String> = bags.mapNotNull { it.roastLevel }.distinct()

    override suspend fun getDistinctFarms(): List<String> = bags.mapNotNull { it.farm }.distinct()
}

internal class FakeFlavorTagDao : FlavorTagDao {
    private val tags = mutableListOf<FlavorTagEntity>()
    private val flow = MutableStateFlow<List<FlavorTagEntity>>(emptyList())

    override suspend fun insertAll(tags: List<FlavorTagEntity>) {
        this.tags.addAll(tags)
        flow.value = this.tags.toList()
    }

    override fun getAll(): Flow<List<FlavorTagEntity>> = flow

    override fun getForBrewLog(brewLogId: Long): Flow<List<FlavorTagEntity>> =
        flow.map { list -> list.filter { it.brewLogId == brewLogId } }

    override fun getForBag(bagId: Long): Flow<List<FlavorTagEntity>> =
        flow

    override suspend fun deleteForBrewLog(brewLogId: Long) {
        tags.removeAll { it.brewLogId == brewLogId }
        flow.value = tags.toList()
    }
}
