package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.GrindInputError
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource

data class PreparedGrind(
    val value: com.adsamcik.starlitcoffee.data.model.RememberedGrindValue? = null,
    val inputText: String = "",
    val displayValue: String = "",
    val rotations: Int? = null,
    val clicks: Int? = null,
    val source: GrindSettingSource = GrindSettingSource.GENERIC,
    val availableScopes: List<GrindSaveScope> = emptyList(),
    val canReset: Boolean = false,
    val isSaving: Boolean = false,
    val error: GrindInputError? = null,
    val saveRevision: Long = 0,
)

enum class PackAddError { INVALID_DETAILS, UNKNOWN_COFFEE, SAVE_FAILED }

data class PackAddState(
    val isSaving: Boolean = false,
    val addedPackId: Long? = null,
    val error: PackAddError? = null,
)
