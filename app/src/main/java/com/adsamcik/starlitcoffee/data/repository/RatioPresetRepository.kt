package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.RatioPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.RatioPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RatioPresetRepository(private val dao: RatioPresetDao) {

    fun getPresetsForMethod(method: BrewMethod): Flow<List<RatioPreset>> {
        return dao.getByMethod(method.name).map { entities ->
            if (entities.isEmpty()) {
                method.defaultRatioPresets
            } else {
                entities.map { entity ->
                    RatioPreset(
                        ratio = entity.ratio,
                        label = entity.label,
                        isDefault = entity.ratio == method.defaultRatio,
                    )
                }
            }
        }
    }

    suspend fun savePresetsForMethod(method: BrewMethod, presets: List<RatioPreset>) {
        dao.deleteByMethod(method.name)
        dao.insertAll(
            presets.mapIndexed { index, preset ->
                RatioPresetEntity(
                    methodName = method.name,
                    ratio = preset.ratio,
                    label = preset.label,
                    sortOrder = index,
                )
            },
        )
    }

    suspend fun resetToDefaults(method: BrewMethod) {
        dao.deleteByMethod(method.name)
    }
}
