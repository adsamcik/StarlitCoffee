package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratio_presets")
data class RatioPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val methodName: String,
    val ratio: Float,
    val label: String,
    val sortOrder: Int,
)
