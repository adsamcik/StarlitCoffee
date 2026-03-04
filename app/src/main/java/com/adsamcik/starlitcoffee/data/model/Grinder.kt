package com.adsamcik.starlitcoffee.data.model

enum class GrinderScaleType {
    DIAL_CLICKS,
    PURE_CLICKS,
    NUMBERED_DIAL,
}

data class Grinder(
    val id: String,
    val brand: String,
    val model: String,
    val isManual: Boolean,
    val scaleType: GrinderScaleType,
    val clicksPerRotation: Int? = null,
)
