package com.adsamcik.starlitcoffee.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.FlavorTagDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import kotlinx.coroutines.flow.Flow

class BrewLogRepository(
    private val database: RoomDatabase? = null,
    private val brewLogDao: BrewLogDao,
    private val flavorTagDao: FlavorTagDao,
) {
    fun getAllLogs(): Flow<List<BrewLogEntity>> = brewLogDao.getAll()

    fun getAllFlavorTags(): Flow<List<FlavorTagEntity>> = flavorTagDao.getAll()

    fun getLogsByBag(bagId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByBag(bagId)

    fun getLogsByRecipe(recipeId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByRecipe(recipeId)

    suspend fun insertLog(entity: BrewLogEntity): Long = brewLogDao.insert(entity)

    suspend fun updateRating(logId: Long, rating: Float, notes: String?) =
        brewLogDao.updateRating(logId, rating, notes)

    suspend fun updateFeedback(
        logId: Long,
        rating: Float?,
        notes: String?,
        tasteFeedback: String?,
        flavorTags: List<FlavorTagEntity>,
    ) {
        val block: suspend () -> Unit = {
            brewLogDao.updateFeedback(logId, rating, notes, tasteFeedback)
            flavorTagDao.deleteForBrewLog(logId)
            if (flavorTags.isNotEmpty()) {
                flavorTagDao.insertAll(flavorTags.map { it.copy(brewLogId = logId) })
            }
        }
        if (database != null) database.withTransaction { block() } else block()
    }

    suspend fun getLogById(logId: Long): BrewLogEntity? = brewLogDao.getById(logId)

    suspend fun insertFlavorTags(tags: List<FlavorTagEntity>) =
        flavorTagDao.insertAll(tags)

    suspend fun deleteLog(entity: BrewLogEntity) = brewLogDao.delete(entity)

    suspend fun saveBrewWithTags(log: BrewLogEntity, tags: List<FlavorTagEntity>): Long {
        val block: suspend () -> Long = {
            val logId = brewLogDao.insert(log)
            if (tags.isNotEmpty()) {
                flavorTagDao.insertAll(tags.map { it.copy(brewLogId = logId) })
            }
            logId
        }
        return if (database != null) database.withTransaction { block() } else block()
    }

    fun getFlavorTagsForBag(bagId: Long): Flow<List<FlavorTagEntity>> =
        flavorTagDao.getForBag(bagId)

    fun getFlavorTagsForBrewLog(brewLogId: Long): Flow<List<FlavorTagEntity>> =
        flavorTagDao.getForBrewLog(brewLogId)
}

