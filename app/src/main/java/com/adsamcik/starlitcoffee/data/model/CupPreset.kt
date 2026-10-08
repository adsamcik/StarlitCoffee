package com.adsamcik.starlitcoffee.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CupPreset(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val doseG: Float,
    val waterMl: Float,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val colorHex: String? = null,
)
