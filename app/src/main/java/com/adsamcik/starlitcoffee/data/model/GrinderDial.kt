package com.adsamcik.starlitcoffee.data.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

@Serializable
enum class GrinderDialNotation { DECIMAL_CLICKS, CLICK_COUNT, FELLOW_ODE, NUMBERED }

/** Values are linear dial positions. Ode's printed 5.2 means 5 + 2/3, not decimal 5.2. */
@Serializable
data class GrinderDial(
    val notation: GrinderDialNotation,
    val minimum: Float,
    val maximum: Float? = null,
    val stepSize: Float? = null,
    val numbersPerRotation: Int? = null,
) {
    fun accepts(value: Float): Boolean {
        val inBounds = value.isFinite() && value >= minimum - TOLERANCE &&
            (maximum == null || value <= maximum + TOLERANCE)
        val steps = stepSize?.let { (value - minimum) / it }
        return inBounds && (steps == null || abs(steps - round(steps)) < TOLERANCE)
    }

    fun normalize(value: Float): Float = normalize(value, ::round)

    fun lowerBound(value: Float): Float = normalize(value, ::floor)

    fun upperBound(value: Float): Float = normalize(value, ::ceil)

    private fun normalize(value: Float, rounding: (Float) -> Float): Float {
        val bounded = value.coerceIn(minimum, maximum ?: Float.MAX_VALUE)
        val stepped = stepSize?.let {
            val steps = (bounded - minimum) / it
            val nearest = round(steps)
            val stableSteps = if (abs(steps - nearest) < TOLERANCE) nearest else steps
            minimum + rounding(stableSteps) * it
        } ?: bounded
        return stepped.coerceIn(minimum, maximum ?: Float.MAX_VALUE)
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
