package com.adsamcik.starlitcoffee.data.model

data class GrindRecommendation(
    val grinderId: String,
    val methodId: String,
    val filterType: FilterType? = null,
    val rangeStart: Float,
    val rangeEnd: Float,
    val suggestedStart: Float,
    val adjustmentStepSize: Float,
    val adjustmentNote: String,
)
