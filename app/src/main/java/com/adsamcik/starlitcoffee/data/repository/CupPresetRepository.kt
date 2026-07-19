package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.model.CupPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface CupPresetResetter {
    suspend fun resetToDefaults()
}

class CupPresetRepository(private val dao: CupPresetDao) : CupPresetResetter {

    val presets: Flow<List<CupPreset>> = dao.getAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getCount() > 0) return
        dao.insertAll(defaultPresets.mapIndexed { index, preset ->
            CupPresetEntity(
                name = preset.name,
                iconName = preset.iconName,
                doseG = preset.doseG,
                waterMl = preset.waterMl,
                sortOrder = index,
                isDefault = true,
                colorHex = preset.colorHex,
            )
        })
    }

    suspend fun addPreset(preset: CupPreset): Long {
        return dao.insert(preset.toEntity())
    }

    suspend fun updatePreset(preset: CupPreset) {
        dao.update(preset.toEntity())
    }

    suspend fun deletePreset(preset: CupPreset) {
        dao.delete(preset.toEntity())
    }

    override suspend fun resetToDefaults() {
        dao.replaceAll(defaultPresetEntities())
    }

    companion object {
        val defaultPresets = listOf(
            CupPreset(name = "Espresso", iconName = "espresso", doseG = 18f, waterMl = 36f, colorHex = "#8B4513"),
            CupPreset(name = "Cortado", iconName = "cortado", doseG = 18f, waterMl = 130f, colorHex = "#D2691E"),
            CupPreset(name = "Cappuccino", iconName = "cappuccino", doseG = 18f, waterMl = 180f, colorHex = "#CD853F"),
            CupPreset(name = "Mug", iconName = "mug", doseG = 22f, waterMl = 374f, colorHex = "#4682B4"),
            CupPreset(name = "Travel", iconName = "travel", doseG = 25f, waterMl = 425f, colorHex = "#2E8B57"),
        )
    }

    private fun defaultPresetEntities(): List<CupPresetEntity> =
        defaultPresets.mapIndexed { index, preset ->
            CupPresetEntity(
                name = preset.name,
                iconName = preset.iconName,
                doseG = preset.doseG,
                waterMl = preset.waterMl,
                sortOrder = index,
                isDefault = true,
                colorHex = preset.colorHex,
            )
        }
}

private fun CupPresetEntity.toDomain() = CupPreset(
    id = id,
    name = name,
    iconName = iconName,
    doseG = doseG,
    waterMl = waterMl,
    sortOrder = sortOrder,
    isDefault = isDefault,
    colorHex = colorHex,
)

private fun CupPreset.toEntity() = CupPresetEntity(
    id = id,
    name = name,
    iconName = iconName,
    doseG = doseG,
    waterMl = waterMl,
    sortOrder = sortOrder,
    isDefault = isDefault,
    colorHex = colorHex,
)
