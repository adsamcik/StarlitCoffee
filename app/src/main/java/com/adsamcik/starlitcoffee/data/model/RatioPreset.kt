package com.adsamcik.starlitcoffee.data.model

import androidx.annotation.StringRes

data class RatioPreset(
    val ratio: Float,
    @StringRes val labelResId: Int,
    val labelArg: Int,
    val isDefault: Boolean = false,
)
