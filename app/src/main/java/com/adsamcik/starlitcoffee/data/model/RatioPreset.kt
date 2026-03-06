package com.adsamcik.starlitcoffee.data.model

data class RatioPreset(
    val ratio: Float,
    val label: String,
    val isDefault: Boolean = false,
)
