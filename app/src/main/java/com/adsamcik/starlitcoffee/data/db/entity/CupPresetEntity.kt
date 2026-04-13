package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cup_presets")
data class CupPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val doseG: Float,
    val waterMl: Float,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val colorHex: String? = null,
)
