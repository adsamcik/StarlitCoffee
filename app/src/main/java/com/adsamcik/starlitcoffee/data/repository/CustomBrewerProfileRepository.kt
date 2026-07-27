package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CustomBrewerProfileDao
import com.adsamcik.starlitcoffee.data.db.entity.CustomBrewerProfileEntity
import kotlinx.coroutines.flow.Flow

/** Keeps user-owned brewer profiles distinct from the validated built-in catalogue. */
class CustomBrewerProfileRepository(
    private val dao: CustomBrewerProfileDao,
) {
    fun getAll(): Flow<List<CustomBrewerProfileEntity>> = dao.getAll()

    suspend fun getById(id: String): CustomBrewerProfileEntity? = dao.getById(id)

    suspend fun save(profile: CustomBrewerProfileEntity) = dao.upsert(profile)

    suspend fun delete(profile: CustomBrewerProfileEntity) = dao.delete(profile)
}
