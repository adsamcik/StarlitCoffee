package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod

/**
 * Method-level approximation of the difference between brew water input and
 * collected beverage. The coefficient is intentionally named "apparent loss":
 * it combines liquid held by the bed and equipment, extracted solids added to
 * the drink, evaporation, and endpoint-specific residuals. It must not be
 * presented as a literal coffee-ground absorption constant.
 */
object BeverageOutputEstimator {

    enum class Caveat {
        STANDARD,
        PROTOCOL_SENSITIVE,
        EXCLUDES_DECANT_RESIDUAL,
    }

    data class Model(
        val apparentLossGPerCoffeeG: Float,
        val caveat: Caveat = Caveat.STANDARD,
    )

    data class BrewPlan(
        val coffeeDoseG: Float,
        val brewWaterG: Float,
        val beverageOutputG: Float,
        val apparentLossG: Float,
    )

    fun modelFor(method: BrewMethod): Model? = when (method) {
        BrewMethod.PULSAR -> Model(
            apparentLossGPerCoffeeG = 2.2f,
            caveat = Caveat.PROTOCOL_SENSITIVE,
        )
        BrewMethod.V60 -> Model(apparentLossGPerCoffeeG = 2.1f)
        BrewMethod.FRENCH_PRESS -> Model(
            apparentLossGPerCoffeeG = 2.2f,
            caveat = Caveat.EXCLUDES_DECANT_RESIDUAL,
        )
        BrewMethod.AEROPRESS -> Model(
            apparentLossGPerCoffeeG = 1.8f,
            caveat = Caveat.PROTOCOL_SENSITIVE,
        )
        BrewMethod.COLD_BREW -> Model(
            apparentLossGPerCoffeeG = 2.0f,
            caveat = Caveat.PROTOCOL_SENSITIVE,
        )
        // Espresso recipes already target collected beverage by dose-to-yield
        // ratio. Moka output is pot/protocol-specific and has no defensible
        // generic reservoir-water model.
        BrewMethod.ESPRESSO,
        BrewMethod.MOKA_POT,
        -> null
    }

    fun estimateOutput(
        method: BrewMethod,
        coffeeDoseG: Float,
        brewWaterG: Float,
    ): BrewPlan? {
        val model = modelFor(method) ?: return null
        if (coffeeDoseG < 0f || brewWaterG < 0f) return null

        val apparentLossG = coffeeDoseG * model.apparentLossGPerCoffeeG
        return BrewPlan(
            coffeeDoseG = coffeeDoseG,
            brewWaterG = brewWaterG,
            beverageOutputG = (brewWaterG - apparentLossG).coerceAtLeast(0f),
            apparentLossG = apparentLossG,
        )
    }

    fun planForOutput(
        method: BrewMethod,
        beverageOutputG: Float,
        brewRatio: Float,
    ): BrewPlan? {
        val model = modelFor(method) ?: return null
        val outputPerCoffeeG = brewRatio - model.apparentLossGPerCoffeeG
        if (beverageOutputG < 0f || outputPerCoffeeG <= 0f) return null

        val coffeeDoseG = beverageOutputG / outputPerCoffeeG
        val brewWaterG = coffeeDoseG * brewRatio
        return BrewPlan(
            coffeeDoseG = coffeeDoseG,
            brewWaterG = brewWaterG,
            beverageOutputG = beverageOutputG,
            apparentLossG = brewWaterG - beverageOutputG,
        )
    }
}
