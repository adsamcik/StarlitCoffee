package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CupPresetRepositoryTest {

    @Test
    fun `reset delegates all replacement work to one DAO transaction`() = runTest {
        val dao = RecordingCupPresetDao()
        val repository = CupPresetRepository(dao)

        repository.resetToDefaults()

        assertEquals(1, dao.replaceCalls)
        assertEquals(CupPresetRepository.defaultPresets.size, dao.data.value.size)
        assertEquals(
            CupPresetRepository.defaultPresets.map { it.name },
            dao.data.value.map { it.name },
        )
    }

    private class RecordingCupPresetDao : CupPresetDao {
        val data = MutableStateFlow<List<CupPresetEntity>>(emptyList())
        var replaceCalls = 0

        override fun getAll(): Flow<List<CupPresetEntity>> = data
        override suspend fun getCount(): Int = data.value.size
        override suspend fun insert(preset: CupPresetEntity): Long = 1L
        override suspend fun insertAll(presets: List<CupPresetEntity>) = Unit
        override suspend fun update(preset: CupPresetEntity) = Unit
        override suspend fun delete(preset: CupPresetEntity) = Unit
        override suspend fun deleteAll() = Unit

        override suspend fun replaceAll(presets: List<CupPresetEntity>) {
            replaceCalls++
            data.value = presets
        }
    }
}
