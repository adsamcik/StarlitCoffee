package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget

/**
 * Canonical visual endpoints for the unified Coffee / Water in / In cup renderer.
 *
 * All horizontal contour coordinates are logical positions inside [Rail]. The logical trailing
 * edge owns a small overflow gutter so the cup handle can grow beyond the neutral rail without
 * shrinking the mug body. Contours deliberately share one eight-cubic topology, allowing a
 * transition resolver to interpolate them without path replacement or allocation.
 */
internal object FluidTriadVisualSpec {
    const val CubicSegmentCount = 8
    const val ContourPointCount = CubicSegmentCount * 3 + 1
    const val ContourCoordinateCount = ContourPointCount * 2

    val Rail = FluidTriadRailViewportSpec(
        topFraction = 0.083f,
        bottomFraction = 0.917f,
        logicalTrailingOverflow = 12.dp,
        cornerRadius = 20.dp,
        outlineWidth = 0.75.dp,
        dividerInset = 12.dp,
    )

    val Lighting = FluidTriadLightingSpec(
        contactShadowOffsetY = 1.dp,
        contactShadowAlpha = 0.08f,
        penumbraShadowOffsetY = 2.5.dp,
        penumbraShadowAlpha = 0.04f,
        edgeWidth = 1.dp,
        edgeShadeBlend = 0.16f,
        edgeHighlightBlend = 0.18f,
        edgeHighlightAlpha = 0.42f,
    )

    val CupHandle = FluidTriadCupHandleVisualSpec(
        revealStart = 0.35f,
        revealEnd = 0.88f,
        outerCollapsed = cubicLoop(
            0.965f, 0.270f,
            0.965f, 0.270f, 0.965f, 0.380f, 0.965f, 0.500f,
            0.965f, 0.660f, 0.965f, 0.780f, 0.965f, 0.780f,
        ),
        outerAttached = cubicLoop(
            0.965f, 0.270f,
            1.035f, 0.270f, 1.035f, 0.380f, 1.035f, 0.500f,
            1.035f, 0.660f, 1.035f, 0.780f, 0.965f, 0.780f,
        ),
        innerCollapsed = cubicLoop(
            0.965f, 0.360f,
            0.965f, 0.360f, 0.965f, 0.430f, 0.965f, 0.510f,
            0.965f, 0.610f, 0.965f, 0.690f, 0.965f, 0.690f,
        ),
        innerAttached = cubicLoop(
            0.975f, 0.350f,
            1.022f, 0.350f, 1.022f, 0.430f, 1.022f, 0.510f,
            1.022f, 0.610f, 1.022f, 0.705f, 0.975f, 0.705f,
        ),
    )

    private val coffee = FluidTriadPanelVisualSpec(
        target = CalculatorQuantityTarget.COFFEE,
        neutralContour = contour(
            0.000f, 0.500f,
            0.000f, 0.300f, 0.015f, 0.130f, 0.065f, 0.083f,
            0.090f, 0.083f, 0.130f, 0.083f, 0.167f, 0.083f,
            0.220f, 0.083f, 0.290f, 0.083f, OneThird, 0.083f,
            OneThird, 0.220f, OneThird, 0.360f, OneThird, 0.500f,
            OneThird, 0.640f, OneThird, 0.780f, OneThird, 0.917f,
            0.290f, 0.917f, 0.220f, 0.917f, 0.167f, 0.917f,
            0.130f, 0.917f, 0.090f, 0.917f, 0.065f, 0.917f,
            0.015f, 0.870f, 0.000f, 0.700f, 0.000f, 0.500f,
        ),
        selectedContour = contour(
            0.002f, 0.500f,
            0.002f, 0.380f, 0.010f, 0.170f, 0.050f, 0.110f,
            0.090f, 0.050f, 0.160f, 0.055f, 0.205f, 0.060f,
            0.265f, 0.060f, 0.320f, 0.082f, 0.335f, 0.145f,
            0.345f, 0.275f, 0.307f, 0.405f, 0.310f, 0.500f,
            0.307f, 0.600f, 0.340f, 0.730f, 0.320f, 0.830f,
            0.292f, 0.905f, 0.225f, 0.945f, 0.170f, 0.940f,
            0.110f, 0.940f, 0.080f, 0.925f, 0.045f, 0.865f,
            0.010f, 0.805f, 0.002f, 0.620f, 0.002f, 0.500f,
        ),
        content = FluidTriadContentSafeSpec(
            anchorX = 0.160f,
            anchorY = 0.500f,
            safeLeft = 0.045f,
            safeTop = 0.165f,
            safeRight = 0.285f,
            safeBottom = 0.840f,
            endpointGroupScale = 1.018f,
            endpointRotationDegrees = -0.35f,
            endpointIconScale = 1.06f,
        ),
        palette = FluidTriadMaterialPaletteSpec(
            lightFill = Color(0xFFE1CCBD),
            lightContent = Color(0xFF3A2115),
            darkFill = Color(0xFF684A3A),
            darkContent = Color(0xFFFFE0CF),
            schemeBlend = 0.94f,
        ),
        lightingStrength = 1f,
    )

    private val water = FluidTriadPanelVisualSpec(
        target = CalculatorQuantityTarget.WATER_IN,
        neutralContour = contour(
            OneThird, 0.500f,
            OneThird, 0.360f, OneThird, 0.220f, OneThird, 0.083f,
            0.390f, 0.083f, 0.445f, 0.083f, 0.500f, 0.083f,
            0.555f, 0.083f, 0.610f, 0.083f, TwoThirds, 0.083f,
            TwoThirds, 0.220f, TwoThirds, 0.360f, TwoThirds, 0.500f,
            TwoThirds, 0.640f, TwoThirds, 0.780f, TwoThirds, 0.917f,
            0.610f, 0.917f, 0.555f, 0.917f, 0.500f, 0.917f,
            0.445f, 0.917f, 0.390f, 0.917f, OneThird, 0.917f,
            OneThird, 0.780f, OneThird, 0.640f, OneThird, 0.500f,
        ),
        selectedContour = contour(
            OneThird, 0.500f,
            OneThird, 0.400f, 0.308f, 0.255f, 0.318f, 0.205f,
            0.328f, 0.155f, 0.415f, 0.035f, 0.495f, 0.035f,
            0.575f, 0.035f, 0.690f, 0.160f, 0.682f, 0.215f,
            0.674f, 0.270f, TwoThirds, 0.400f, TwoThirds, 0.500f,
            TwoThirds, 0.600f, 0.688f, 0.750f, 0.678f, 0.800f,
            0.668f, 0.850f, 0.585f, 0.975f, 0.505f, 0.975f,
            0.425f, 0.975f, 0.328f, 0.860f, 0.318f, 0.810f,
            0.308f, 0.760f, OneThird, 0.600f, OneThird, 0.500f,
        ),
        content = FluidTriadContentSafeSpec(
            anchorX = 0.500f,
            anchorY = 0.500f,
            safeLeft = 0.340f,
            safeTop = 0.170f,
            safeRight = 0.660f,
            safeBottom = 0.830f,
            endpointGroupScale = 1.012f,
            endpointRotationDegrees = 0f,
            endpointIconScale = 1.06f,
        ),
        palette = FluidTriadMaterialPaletteSpec(
            lightFill = Color(0xFF5C72B5),
            lightContent = Color.White,
            darkFill = Color(0xFF405A8C),
            darkContent = Color(0xFFE1E7FF),
            schemeBlend = 0.94f,
        ),
        lightingStrength = 0.70f,
    )

    private val cup = FluidTriadPanelVisualSpec(
        target = CalculatorQuantityTarget.IN_CUP,
        neutralContour = contour(
            TwoThirds, 0.500f,
            TwoThirds, 0.360f, TwoThirds, 0.220f, TwoThirds, 0.083f,
            0.710f, 0.083f, 0.780f, 0.083f, 0.833f, 0.083f,
            0.870f, 0.083f, 0.910f, 0.083f, 0.935f, 0.083f,
            0.985f, 0.130f, 1.000f, 0.300f, 1.000f, 0.500f,
            1.000f, 0.700f, 0.985f, 0.870f, 0.935f, 0.917f,
            0.910f, 0.917f, 0.870f, 0.917f, 0.833f, 0.917f,
            0.780f, 0.917f, 0.710f, 0.917f, TwoThirds, 0.917f,
            TwoThirds, 0.780f, TwoThirds, 0.640f, TwoThirds, 0.500f,
        ),
        selectedContour = contour(
            0.645f, 0.500f,
            0.645f, 0.340f, 0.648f, 0.155f, 0.675f, 0.105f,
            0.715f, 0.082f, 0.785f, 0.078f, 0.835f, 0.078f,
            0.895f, 0.078f, 0.965f, 0.082f, 0.985f, 0.108f,
            0.995f, 0.250f, 0.995f, 0.390f, 0.995f, 0.500f,
            0.995f, 0.640f, 0.992f, 0.790f, 0.980f, 0.850f,
            0.940f, 0.895f, 0.875f, 0.910f, 0.830f, 0.910f,
            0.770f, 0.910f, 0.700f, 0.895f, 0.665f, 0.850f,
            0.650f, 0.725f, 0.645f, 0.600f, 0.645f, 0.500f,
        ),
        content = FluidTriadContentSafeSpec(
            anchorX = 5f / 6f,
            anchorY = 0.500f,
            safeLeft = 0.700f,
            safeTop = 0.165f,
            safeRight = 0.960f,
            safeBottom = 0.840f,
            endpointGroupScale = 1.010f,
            endpointRotationDegrees = 0f,
            endpointIconScale = 1.06f,
        ),
        palette = FluidTriadMaterialPaletteSpec(
            lightFill = Color(0xFF916982),
            lightContent = Color.White,
            darkFill = Color(0xFF70485F),
            darkContent = Color(0xFFFFD9E9),
            schemeBlend = 0.94f,
        ),
        lightingStrength = 1f,
    )

    fun panel(target: CalculatorQuantityTarget): FluidTriadPanelVisualSpec = when (target) {
        CalculatorQuantityTarget.COFFEE -> coffee
        CalculatorQuantityTarget.WATER_IN -> water
        CalculatorQuantityTarget.IN_CUP -> cup
    }

    private fun contour(vararg coordinates: Float): FluidTriadContourSpec =
        FluidTriadContourSpec(coordinates)

    private fun cubicLoop(vararg coordinates: Float): FluidTriadCubicLoopSpec =
        FluidTriadCubicLoopSpec(coordinates)

    private const val OneThird = 1f / 3f
    private const val TwoThirds = 2f / 3f
}

internal class FluidTriadContourSpec internal constructor(
    private val coordinates: FloatArray,
) {
    init {
        require(coordinates.size == FluidTriadVisualSpec.ContourCoordinateCount)
        require(coordinates.all(Float::isFinite))
        require(coordinates[0] == coordinates[coordinates.lastIndex - 1])
        require(coordinates[1] == coordinates[coordinates.lastIndex])
    }

    val coordinateCount: Int get() = coordinates.size

    operator fun get(index: Int): Float = coordinates[index]

    fun copyInto(destination: FloatArray) {
        require(destination.size >= coordinates.size)
        coordinates.copyInto(destination)
    }

    fun minX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = true)

    fun maxX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = false)

    fun minY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = true)

    fun maxY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = false)

    fun xAt(pointIndex: Int): Float = coordinates[pointIndex * 2]

    fun yAt(pointIndex: Int): Float = coordinates[pointIndex * 2 + 1]

    private fun coordinateExtremum(startIndex: Int, selectMinimum: Boolean): Float {
        var result = coordinates[startIndex]
        var index = startIndex + 2
        while (index < coordinates.size) {
            val candidate = coordinates[index]
            result = if (selectMinimum) minOf(result, candidate) else maxOf(result, candidate)
            index += 2
        }
        return result
    }
}

internal class FluidTriadCubicLoopSpec internal constructor(
    private val coordinates: FloatArray,
) {
    init {
        require(coordinates.size == CoordinateCount)
        require(coordinates.all(Float::isFinite))
    }

    val coordinateCount: Int get() = coordinates.size

    operator fun get(index: Int): Float = coordinates[index]

    fun copyInto(destination: FloatArray) {
        require(destination.size >= coordinates.size)
        coordinates.copyInto(destination)
    }

    fun minX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = true)

    fun maxX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = false)

    fun minY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = true)

    fun maxY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = false)

    private fun coordinateExtremum(startIndex: Int, selectMinimum: Boolean): Float {
        var result = coordinates[startIndex]
        var index = startIndex + 2
        while (index < coordinates.size) {
            val candidate = coordinates[index]
            result = if (selectMinimum) minOf(result, candidate) else maxOf(result, candidate)
            index += 2
        }
        return result
    }

    companion object {
        const val CubicSegmentCount = 2
        const val PointCount = CubicSegmentCount * 3 + 1
        const val CoordinateCount = PointCount * 2
    }
}

internal data class FluidTriadPanelVisualSpec(
    val target: CalculatorQuantityTarget,
    val neutralContour: FluidTriadContourSpec,
    val selectedContour: FluidTriadContourSpec,
    val content: FluidTriadContentSafeSpec,
    val palette: FluidTriadMaterialPaletteSpec,
    val lightingStrength: Float,
) {
    init {
        require(lightingStrength in 0f..1f)
    }
}

internal data class FluidTriadContentSafeSpec(
    val anchorX: Float,
    val anchorY: Float,
    val safeLeft: Float,
    val safeTop: Float,
    val safeRight: Float,
    val safeBottom: Float,
    val endpointGroupScale: Float,
    val endpointRotationDegrees: Float,
    val endpointIconScale: Float,
) {
    init {
        require(safeLeft < safeRight)
        require(safeTop < safeBottom)
        require(anchorX in safeLeft..safeRight)
        require(anchorY in safeTop..safeBottom)
        require(endpointGroupScale > 0f)
        require(endpointIconScale > 0f)
    }
}

internal data class FluidTriadMaterialPaletteSpec(
    val lightFill: Color,
    val lightContent: Color,
    val darkFill: Color,
    val darkContent: Color,
    val schemeBlend: Float,
) {
    init {
        require(schemeBlend in 0f..1f)
    }
}

internal data class FluidTriadLightingSpec(
    val contactShadowOffsetY: Dp,
    val contactShadowAlpha: Float,
    val penumbraShadowOffsetY: Dp,
    val penumbraShadowAlpha: Float,
    val edgeWidth: Dp,
    val edgeShadeBlend: Float,
    val edgeHighlightBlend: Float,
    val edgeHighlightAlpha: Float,
) {
    init {
        require(contactShadowOffsetY >= 0.dp)
        require(penumbraShadowOffsetY >= contactShadowOffsetY)
        require(contactShadowAlpha in 0f..1f)
        require(penumbraShadowAlpha in 0f..1f)
        require(edgeWidth > 0.dp)
        require(edgeShadeBlend in 0f..1f)
        require(edgeHighlightBlend in 0f..1f)
        require(edgeHighlightAlpha in 0f..1f)
    }
}

internal data class FluidTriadRailViewportSpec(
    val topFraction: Float,
    val bottomFraction: Float,
    val logicalTrailingOverflow: Dp,
    val cornerRadius: Dp,
    val outlineWidth: Dp,
    val dividerInset: Dp,
) {
    init {
        require(topFraction in 0f..1f)
        require(bottomFraction in 0f..1f)
        require(topFraction < bottomFraction)
        require(logicalTrailingOverflow >= 0.dp)
        require(cornerRadius > 0.dp)
        require(outlineWidth > 0.dp)
        require(dividerInset >= 0.dp)
    }

    fun resolve(
        width: Float,
        height: Float,
        trailingOverflowPx: Float,
        mirrorHorizontally: Boolean,
    ): FluidTriadResolvedRailViewport {
        require(width > 0f)
        require(height > 0f)
        require(trailingOverflowPx in 0f..<width)
        return if (mirrorHorizontally) {
            FluidTriadResolvedRailViewport(
                left = trailingOverflowPx,
                top = height * topFraction,
                right = width,
                bottom = height * bottomFraction,
                mirrorHorizontally = true,
            )
        } else {
            FluidTriadResolvedRailViewport(
                left = 0f,
                top = height * topFraction,
                right = width - trailingOverflowPx,
                bottom = height * bottomFraction,
                mirrorHorizontally = false,
            )
        }
    }
}

internal data class FluidTriadResolvedRailViewport(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val mirrorHorizontally: Boolean,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun mapLogicalX(logicalX: Float): Float = if (mirrorHorizontally) {
        right - logicalX * width
    } else {
        left + logicalX * width
    }

    fun mapComponentY(componentY: Float, componentHeight: Float): Float =
        componentY * componentHeight
}

internal data class FluidTriadCupHandleVisualSpec(
    val revealStart: Float,
    val revealEnd: Float,
    val outerCollapsed: FluidTriadCubicLoopSpec,
    val outerAttached: FluidTriadCubicLoopSpec,
    val innerCollapsed: FluidTriadCubicLoopSpec,
    val innerAttached: FluidTriadCubicLoopSpec,
) {
    init {
        require(revealStart in 0f..1f)
        require(revealEnd in 0f..1f)
        require(revealStart < revealEnd)
        require(outerCollapsed.coordinateCount == outerAttached.coordinateCount)
        require(innerCollapsed.coordinateCount == innerAttached.coordinateCount)
    }
}
