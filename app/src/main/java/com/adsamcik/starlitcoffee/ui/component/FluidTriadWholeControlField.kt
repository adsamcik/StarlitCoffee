package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlin.math.abs

/**
 * Authored scalar fields for the single-body Fluid Triad renderer.
 *
 * The outer level set is built separately. These functions only decide which part of that one
 * body inherits the active material. A negative value is inside the target's material phase.
 * Nothing here describes a closed selected surface, outline, shadow, or elevation layer.
 */
internal object FluidTriadWholeControlField {
    const val RailTop = 0.083f
    const val RailBottom = 0.917f
    const val RailCornerRadius = 0.20f

    const val CoffeeBoundaryOuter = 0.335f
    const val CoffeeBoundaryPinch = 0.028f
    const val WaterHalfWidthOuter = 0.177f
    const val WaterHalfWidthPinch = 0.010f
    const val CupBoundaryOuter = 0.675f
    const val CupBoundaryPinch = 0.030f
    const val CupLowerSettle = 0.010f

    /** Width of the unoutlined material handoff in physical pixels. */
    const val OwnershipFeatherPx = 2.25f

    fun ownershipDistance(
        target: CalculatorQuantityTarget,
        logicalX: Float,
        logicalY: Float,
    ): Float = when (target) {
        CalculatorQuantityTarget.COFFEE -> logicalX - coffeeBoundary(logicalY)
        CalculatorQuantityTarget.WATER_IN ->
            abs(logicalX - 0.5f) - waterHalfWidth(logicalY)
        CalculatorQuantityTarget.IN_CUP -> cupBoundary(logicalY) - logicalX
    }

    fun coffeeBoundary(logicalY: Float): Float {
        val pressure = centerPressure(logicalY)
        return CoffeeBoundaryOuter - CoffeeBoundaryPinch * pressure * pressure
    }

    fun waterHalfWidth(logicalY: Float): Float {
        val pressure = centerPressure(logicalY)
        return WaterHalfWidthOuter - WaterHalfWidthPinch * pressure * pressure
    }

    fun cupBoundary(logicalY: Float): Float {
        val y = logicalY.coerceIn(0f, 1f)
        val pressure = centerPressure(y)
        val lowerSettle = smoothstep(((y - 0.5f) * 2f).coerceIn(0f, 1f))
        return CupBoundaryOuter - CupBoundaryPinch * pressure * pressure -
            CupLowerSettle * lowerSettle
    }

    private fun centerPressure(logicalY: Float): Float {
        val y = logicalY.coerceIn(0f, 1f)
        return 4f * y * (1f - y)
    }

    private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)
}
