package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.FlavorTagDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import kotlinx.coroutines.flow.Flow

class BrewLogRepository(
    private val brewLogDao: BrewLogDao,
    private val flavorTagDao: FlavorTagDao,
) {
    fun getAllLogs(): Flow<List<BrewLogEntity>> = brewLogDao.getAll()

    fun getLogsByBag(bagId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByBag(bagId)

    fun getLogsByRecipe(recipeId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByRecipe(recipeId)

    suspend fun insertLog(entity: BrewLogEntity): Long = brewLogDao.insert(entity)

    suspend fun updateRating(logId: Long, rating: Int, notes: String?) =
        brewLogDao.updateRating(logId, rating, notes)

    suspend fun insertFlavorTags(tags: List<FlavorTagEntity>) =
        flavorTagDao.insertAll(tags)

    suspend fun deleteLog(entity: BrewLogEntity) = brewLogDao.delete(entity)

    suspend fun saveBrewWithTags(log: BrewLogEntity, tags: List<FlavorTagEntity>): Long {
        val logId = brewLogDao.insert(log)
        if (tags.isNotEmpty()) {
            flavorTagDao.insertAll(tags.map { it.copy(brewLogId = logId) })
        }
        return logId
    }

    fun getFlavorTagsForBag(bagId: Long): Flow<List<FlavorTagEntity>> =
        flavorTagDao.getForBag(bagId)

    fun getFlavorTagsForBrewLog(brewLogId: Long): Flow<List<FlavorTagEntity>> =
        flavorTagDao.getForBrewLog(brewLogId)
}

