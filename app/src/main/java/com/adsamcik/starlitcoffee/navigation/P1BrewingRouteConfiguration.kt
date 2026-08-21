package com.adsamcik.starlitcoffee.navigation

import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate

internal data class P1BrewingRouteConfiguration(
    val unavailableMessage: String,
    val exactRecipeReleaseGate: P1ExactRecipeReleaseGate,
)
