package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.dao.RatioPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RatioPresetRepositoryTest {

    @Test
    fun `save delegates one complete replacement to the DAO transaction`() = runTest {
        val dao = RecordingRatioPresetDao()
        val repository = RatioPresetRepository(dao)
        val presets = listOf(
            RatioPreset(
                ratio = 15f,
                labelResId = R.string.format_ratio_bright,
                labelArg = 15,
            ),
            RatioPreset(
                ratio = 16f,
                labelResId = R.string.format_ratio_balanced,
                labelArg = 16,
                isDefault = true,
            ),
        )

        repository.savePresetsForMethod(BrewMethod.V60, presets)

        assertEquals(1, dao.replaceCalls)
        assertEquals("V60", dao.replacedMethodName)
        assertEquals(0, dao.deleteCalls)
        assertEquals(0, dao.insertCalls)
        assertEquals(
            listOf(
                RatioPresetEntity(
                    methodName = "V60",
                    methodFamilyId = "manual_gravity",
                    brewerProfileId = "v60_unspecified",
                    ratio = 15f,
                    label = "1:15",
                    sortOrder = 0,
                ),
                RatioPresetEntity(
                    methodName = "V60",
                    methodFamilyId = "manual_gravity",
                    brewerProfileId = "v60_unspecified",
                    ratio = 16f,
                    label = "1:16",
                    sortOrder = 1,
                ),
            ),
            dao.replacement,
        )
    }

    private class RecordingRatioPresetDao : RatioPresetDao {
        private val data = MutableStateFlow<List<RatioPresetEntity>>(emptyList())
        var replaceCalls: Int = 0
        var deleteCalls: Int = 0
        var insertCalls: Int = 0
        var replacedMethodName: String? = null
        var replacement: List<RatioPresetEntity> = emptyList()

        override fun getByMethod(methodName: String): Flow<List<RatioPresetEntity>> = data

        override suspend fun insertAll(presets: List<RatioPresetEntity>) {
            insertCalls++
        }

        override suspend fun deleteByMethod(methodName: String) {
            deleteCalls++
        }

        override suspend fun countByMethod(methodName: String): Int = data.value.count { preset ->
            preset.methodName == methodName
        }

        override suspend fun replaceForMethod(
            methodName: String,
            presets: List<RatioPresetEntity>,
        ) {
            replaceCalls++
            replacedMethodName = methodName
            replacement = presets
        }
    }
}
