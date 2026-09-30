package com.adsamcik.starlitcoffee.calculator

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
import com.adsamcik.starlitcoffee.domain.BeverageOutputEstimator

/**
 * The quantity currently controlled by the calculator expression/keypad.
 */
enum class CalculatorQuantityTarget {
    COFFEE, WATER_IN, IN_CUP,

    ;

    fun isAvailableFor(method: BrewMethod): Boolean = when (this) {
        COFFEE -> true
        WATER_IN -> method.outputSemantics != BrewOutputSemantics.BEVERAGE_YIELD
        IN_CUP -> method.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD ||
            BeverageOutputEstimator.modelFor(method) != null
    }
}
