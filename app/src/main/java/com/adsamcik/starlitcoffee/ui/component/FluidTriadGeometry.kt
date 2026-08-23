package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget

/**
 * Small normalized parameter set describing the selected material surface.
 * Values are perceptual design parameters, not claims of literal material physics.
 */
internal data class FluidTriadVisualProfile(
    val centerX: Float,
    val halfWidth: Float,
    val topLift: Float,
    val bottomDrop: Float,
    val asymmetry: Float,
    val flow: Float,
    val taper: Float,
    val lip: Float,
    val grounding: Float,
    val neighborLeft: Float,
    val neighborRight: Float,
    val coffeeWeight: Float,
    val waterWeight: Float,
    val cupWeight: Float,
)

internal class MutableFluidTriadVisualProfile {
    var centerX: Float = 0f
    var halfWidth: Float = 0f
    var topLift: Float = 0f
    var bottomDrop: Float = 0f
    var asymmetry: Float = 0f
    var flow: Float = 0f
    var taper: Float = 0f
    var lip: Float = 0f
    var grounding: Float = 0f
    var neighborLeft: Float = 0f
    var neighborRight: Float = 0f
    var coffeeWeight: Float = 0f
    var waterWeight: Float = 0f
    var cupWeight: Float = 0f
}

internal object FluidTriadGeometry {
    const val PathPointCount = 13

    fun profileFor(target: CalculatorQuantityTarget): FluidTriadVisualProfile = when (target) {
        CalculatorQuantityTarget.COFFEE -> FluidTriadVisualProfile(
            centerX = 1f / 6f,
            halfWidth = 0.148f,
            topLift = 0.055f,
            bottomDrop = 0.040f,
            asymmetry = 0.015f,
            flow = 0.010f,
            taper = 0.010f,
            lip = 0f,
            grounding = 0.020f,
            neighborLeft = 0f,
            neighborRight = 0.020f,
            coffeeWeight = 1f,
            waterWeight = 0f,
            cupWeight = 0f,
        )
        CalculatorQuantityTarget.WATER_IN -> FluidTriadVisualProfile(
            centerX = 0.5f,
            halfWidth = 0.153f,
            topLift = 0.022f,
            bottomDrop = 0.022f,
            asymmetry = 0.006f,
            flow = 0.060f,
            taper = 0f,
            lip = 0f,
            grounding = 0f,
            neighborLeft = 0.032f,
            neighborRight = 0.032f,
            coffeeWeight = 0f,
            waterWeight = 1f,
            cupWeight = 0f,
        )
        CalculatorQuantityTarget.IN_CUP -> FluidTriadVisualProfile(
            centerX = 5f / 6f,
            halfWidth = 0.148f,
            topLift = 0.040f,
            bottomDrop = 0.048f,
            asymmetry = 0f,
            flow = 0f,
            taper = 0.024f,
            lip = 0.040f,
            grounding = 0.034f,
            neighborLeft = 0.020f,
            neighborRight = 0f,
            coffeeWeight = 0f,
            waterWeight = 0f,
            cupWeight = 1f,
        )
    }

    fun interpolate(
        source: FluidTriadVisualProfile,
        target: FluidTriadVisualProfile,
        progress: Float,
    ): FluidTriadVisualProfile {
        val t = progress.coerceIn(0f, 1f)
        if (t == 0f) return source
        if (t == 1f) return target
        fun value(a: Float, b: Float) = a + (b - a) * t
        return FluidTriadVisualProfile(
            centerX = value(source.centerX, target.centerX),
            halfWidth = value(source.halfWidth, target.halfWidth),
            topLift = value(source.topLift, target.topLift),
            bottomDrop = value(source.bottomDrop, target.bottomDrop),
            asymmetry = value(source.asymmetry, target.asymmetry),
            flow = value(source.flow, target.flow),
            taper = value(source.taper, target.taper),
            lip = value(source.lip, target.lip),
            grounding = value(source.grounding, target.grounding),
            neighborLeft = value(source.neighborLeft, target.neighborLeft),
            neighborRight = value(source.neighborRight, target.neighborRight),
            coffeeWeight = value(source.coffeeWeight, target.coffeeWeight),
            waterWeight = value(source.waterWeight, target.waterWeight),
            cupWeight = value(source.cupWeight, target.cupWeight),
        )
    }

    fun resolve(
        source: FluidTriadVisualProfile,
        target: FluidTriadVisualProfile,
        progress: Float,
        out: MutableFluidTriadVisualProfile,
    ) {
        val t = progress.coerceIn(0f, 1f)
        fun value(a: Float, b: Float) = a + (b - a) * t
        out.centerX = value(source.centerX, target.centerX)
        out.halfWidth = value(source.halfWidth, target.halfWidth)
        out.topLift = value(source.topLift, target.topLift)
        out.bottomDrop = value(source.bottomDrop, target.bottomDrop)
        out.asymmetry = value(source.asymmetry, target.asymmetry)
        out.flow = value(source.flow, target.flow)
        out.taper = value(source.taper, target.taper)
        out.lip = value(source.lip, target.lip)
        out.grounding = value(source.grounding, target.grounding)
        out.neighborLeft = value(source.neighborLeft, target.neighborLeft)
        out.neighborRight = value(source.neighborRight, target.neighborRight)
        out.coffeeWeight = value(source.coffeeWeight, target.coffeeWeight)
        out.waterWeight = value(source.waterWeight, target.waterWeight)
        out.cupWeight = value(source.cupWeight, target.cupWeight)
    }

    /**
     * Writes a fixed-topology four-cubic closed outline into [out].
     * Each point is an x/y pair in normalized selector coordinates.
     */
    fun writePathPoints(
        profile: MutableFluidTriadVisualProfile,
        waterWave: Float,
        out: FloatArray,
    ) {
        require(out.size >= PathPointCount * 2)
        val left = (profile.centerX - profile.halfWidth - profile.neighborLeft).coerceIn(0.012f, 0.988f)
        val right = (profile.centerX + profile.halfWidth + profile.neighborRight).coerceIn(0.012f, 0.988f)
        val span = right - left

        val topBase = 0.215f - profile.topLift
        val bottomBase = 0.785f + profile.bottomDrop
        val flow = profile.flow
        val wave = waterWave * profile.waterWeight

        val topLeft = topBase + flow * 0.10f + wave * 0.010f
        val topQuarter = topBase + flow * 0.22f - wave * 0.020f
        val topCenter = topBase - flow * 0.24f + wave * 0.024f - profile.lip * 0.18f
        val topThreeQuarter = topBase + flow * 0.08f + wave * 0.014f
        val topRight = topBase + flow * 0.16f - wave * 0.012f

        val bottomLeft = bottomBase - flow * 0.10f - wave * 0.010f
        val bottomQuarter = bottomBase - flow * 0.18f + wave * 0.018f
        val bottomCenter = bottomBase + flow * 0.20f - wave * 0.020f + profile.grounding * 0.20f
        val bottomThreeQuarter = bottomBase - flow * 0.06f - wave * 0.014f
        val bottomRight = bottomBase - flow * 0.14f + wave * 0.012f

        val topCenterX = (profile.centerX + profile.asymmetry).coerceIn(left + span * 0.35f, right - span * 0.35f)
        val bottomCenterX = (profile.centerX - profile.asymmetry * 0.35f).coerceIn(left + span * 0.35f, right - span * 0.35f)
        val topQuarterX = left + span * 0.28f
        val topThreeQuarterX = right - span * 0.28f
        val bottomQuarterX = left + span * 0.30f + profile.taper
        val bottomThreeQuarterX = right - span * 0.30f - profile.taper
        val midY = 0.5f

        put(out, 0, left, midY)
        put(out, 1, left, topLeft)
        put(out, 2, topQuarterX, topQuarter)
        put(out, 3, topCenterX, topCenter)
        put(out, 4, topThreeQuarterX, topThreeQuarter)
        put(out, 5, right, topRight)
        put(out, 6, right, midY)
        put(out, 7, right, bottomRight)
        put(out, 8, bottomThreeQuarterX, bottomThreeQuarter)
        put(out, 9, bottomCenterX, bottomCenter)
        put(out, 10, bottomQuarterX, bottomQuarter)
        put(out, 11, left, bottomLeft)
        put(out, 12, left, midY)
    }

    private fun put(out: FloatArray, index: Int, x: Float, y: Float) {
        out[index * 2] = x
        out[index * 2 + 1] = y
    }
}
