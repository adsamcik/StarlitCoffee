package com.adsamcik.starlitcoffee.data.model

import kotlinx.serialization.Serializable

enum class GrindSaveScope { BREW, PACK, COFFEE, TYPE }
enum class GrindSettingSource { BREW, PACK, COFFEE, TYPE, RECOMMENDATION, GENERIC }
enum class GrindInputError { INVALID_FORMAT, OUT_OF_RANGE, SAVE_FAILED }

@Serializable
data class GrindContext(
    val grinderId: String,
    val methodId: String,
    val filterKey: String = "",
)

/** User values are final settings; recommendation offsets are never applied again. */
@Serializable
data class RememberedGrindValue(
    val scaleType: String,
    val clicksPerRotation: Int?,
    val totalClicks: Int?,
    val dialValue: String?,
    val updatedAt: Long,
)

data class RememberedGrind(
    val scope: GrindSaveScope,
    val owner: String,
    val context: GrindContext,
    val value: RememberedGrindValue,
)
