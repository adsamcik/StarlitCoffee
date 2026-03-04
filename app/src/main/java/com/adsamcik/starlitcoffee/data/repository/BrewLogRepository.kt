package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import kotlinx.coroutines.flow.Flow

class BrewLogRepository(
    private val brewLogDao: BrewLogDao,
) {
    fun getAllLogs(): Flow<List<BrewLogEntity>> = brewLogDao.getAll()

    fun getLogsByBag(bagId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByBag(bagId)

    fun getLogsByRecipe(recipeId: Long): Flow<List<BrewLogEntity>> = brewLogDao.getByRecipe(recipeId)

    suspend fun insertLog(entity: BrewLogEntity): Long = brewLogDao.insert(entity)

    suspend fun deleteLog(entity: BrewLogEntity) = brewLogDao.delete(entity)
}

