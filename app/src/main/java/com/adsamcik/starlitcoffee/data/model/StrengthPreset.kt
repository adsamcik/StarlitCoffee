package com.adsamcik.starlitcoffee.data.model

enum class StrengthPreset(
    val displayName: String,
    val ratioOffset: Int,
) {
    LIGHT("Light", ratioOffset = 1),
    BALANCED("Balanced", ratioOffset = 0),
    STRONG("Strong", ratioOffset = -1),
}
