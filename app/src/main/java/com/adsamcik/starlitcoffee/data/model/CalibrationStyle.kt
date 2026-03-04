package com.adsamcik.starlitcoffee.data.model

enum class CalibrationStyle(
    val displayName: String,
    val rangeWidthMultiplier: Float,
) {
    FACTORY("Factory", rangeWidthMultiplier = 1.0f),
    BURR_LOCK("Burr Lock", rangeWidthMultiplier = 1.0f),
    FIRST_TOUCH("First Touch", rangeWidthMultiplier = 1.0f),
    UNKNOWN("Unknown", rangeWidthMultiplier = 1.5f),
}
