package com.adsamcik.starlitcoffee.data.model

data class BrewPhase(
    val name: String,
    val phaseType: PhaseType,
    val mode: PhaseMode,
    val waterG: Float,
    val cumulativeWaterG: Float,
    val durationSeconds: Int,
    val instruction: String = "",
    val valveState: String = "",
)
