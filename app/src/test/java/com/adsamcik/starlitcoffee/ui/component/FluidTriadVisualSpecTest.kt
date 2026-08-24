package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadVisualSpecTest {
    @Test
    fun `all panel endpoints use one finite closed cubic topology`() {
        CalculatorQuantityTarget.entries.forEach { target ->
            val panel = FluidTriadVisualSpec.panel(target)
            listOf(panel.neutralContour, panel.selectedContour).forEach { contour ->
                assertEquals(FluidTriadVisualSpec.ContourCoordinateCount, contour.coordinateCount)
                val copy = FloatArray(contour.coordinateCount)
                contour.copyInto(copy)
                assertTrue(copy.all { it.isFinite() })
                assertEquals(copy[0], copy[copy.lastIndex - 1], Epsilon)
                assertEquals(copy[1], copy[copy.lastIndex], Epsilon)
            }
        }
    }

    @Test
    fun `neutral contours are exact thirds of the canonical rail`() {
        val coffee = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.COFFEE).neutralContour
        val water = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.WATER_IN).neutralContour
        val cup = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.IN_CUP).neutralContour

        assertBounds(coffee, 0f, OneThird)
        assertBounds(water, OneThird, TwoThirds)
        assertBounds(cup, TwoThirds, 1f)
        listOf(coffee, water, cup).forEach { contour ->
            assertEquals(FluidTriadVisualSpec.Rail.topFraction, contour.minY(), Epsilon)
            assertEquals(FluidTriadVisualSpec.Rail.bottomFraction, contour.maxY(), Epsilon)
        }
    }

    @Test
    fun `selected silhouettes preserve reference proportions`() {
        val railHeight = FluidTriadVisualSpec.Rail.bottomFraction -
            FluidTriadVisualSpec.Rail.topFraction
        val coffee = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.COFFEE).selectedContour
        val water = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.WATER_IN).selectedContour
        val cup = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.IN_CUP).selectedContour

        assertTrue(coffee.minX() in 0f..0.002f)
        assertTrue(coffee.maxX() in 0.335f..0.350f)
        assertTrue((coffee.maxY() - coffee.minY()) / railHeight in 1.06f..1.10f)
        assertTrue(coffee.xAt(RightMidpoint) < coffee.xAt(TopRightEndpoint))
        assertTrue(coffee.xAt(RightMidpoint) < coffee.xAt(BottomRightEndpoint))

        assertTrue(water.minX() in 0.305f..0.310f)
        assertTrue(water.maxX() in 0.688f..0.692f)
        assertTrue((water.maxY() - water.minY()) / railHeight in 1.12f..1.14f)
        assertTrue(water.xAt(TopCenterEndpoint) < 0.5f)

        assertTrue(cup.minX() in 0.640f..0.650f)
        assertTrue(cup.maxX() in 0.990f..1.000f)
        assertTrue((cup.maxY() - cup.minY()) / railHeight in 0.99f..1.02f)
        val topWidth = cup.xAt(TopRightEndpoint) - cup.xAt(TopLeftEndpoint)
        val bottomWidth = cup.xAt(BottomRightEndpoint) - cup.xAt(BottomLeftEndpoint)
        assertTrue(bottomWidth >= topWidth)
        val cupPixelAspect = (cup.maxX() - cup.minX()) * RepresentativeRailAspect /
            (cup.maxY() - cup.minY())
        assertTrue(cupPixelAspect in 1.85f..1.95f)
    }

    @Test
    fun `water selected contour has smooth liquid waists crown and bottom`() {
        val water = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.WATER_IN).selectedContour

        assertTrue(water.xAt(LeftMidpoint) > water.minX())
        assertTrue(water.xAt(RightMidpoint) < water.maxX())
        assertTrue(water.yAt(TopCenterEndpoint) < water.yAt(TopLeftEndpoint))
        assertTrue(water.yAt(BottomCenterEndpoint) > water.yAt(BottomLeftEndpoint))

        repeat(FluidTriadVisualSpec.CubicSegmentCount) { segment ->
            val anchor = segment * CubicPointStride
            val previousControl = if (anchor == LeftMidpoint) {
                FluidTriadVisualSpec.ContourPointCount - 2
            } else {
                anchor - 1
            }
            val nextControl = anchor + 1
            assertEquals(
                water.xAt(anchor) - water.xAt(previousControl),
                water.xAt(nextControl) - water.xAt(anchor),
                Epsilon,
            )
            assertEquals(
                water.yAt(anchor) - water.yAt(previousControl),
                water.yAt(nextControl) - water.yAt(anchor),
                Epsilon,
            )
        }
    }

    @Test
    fun `canonical rail reserves only the logical cup handle edge`() {
        val leftToRight = FluidTriadVisualSpec.Rail.resolve(
            width = ViewportWidth,
            height = ViewportHeight,
            trailingOverflowPx = Overflow,
            mirrorHorizontally = false,
        )
        assertEquals(0f, leftToRight.left, Epsilon)
        assertEquals(ViewportWidth - Overflow, leftToRight.right, Epsilon)
        assertEquals(0f, leftToRight.mapLogicalX(0f), Epsilon)
        assertEquals(ViewportWidth - Overflow, leftToRight.mapLogicalX(1f), Epsilon)

        val rightToLeft = FluidTriadVisualSpec.Rail.resolve(
            width = ViewportWidth,
            height = ViewportHeight,
            trailingOverflowPx = Overflow,
            mirrorHorizontally = true,
        )
        assertEquals(Overflow, rightToLeft.left, Epsilon)
        assertEquals(ViewportWidth, rightToLeft.right, Epsilon)
        assertEquals(ViewportWidth, rightToLeft.mapLogicalX(0f), Epsilon)
        assertEquals(Overflow, rightToLeft.mapLogicalX(1f), Epsilon)
        assertEquals(ViewportHeight * FluidTriadVisualSpec.Rail.topFraction, rightToLeft.top, Epsilon)
        assertEquals(
            ViewportHeight * FluidTriadVisualSpec.Rail.bottomFraction,
            rightToLeft.bottom,
            Epsilon,
        )
    }

    @Test
    fun `cup handle stays collapsed until a late geometric attachment`() {
        val handle = FluidTriadVisualSpec.CupHandle
        assertTrue(handle.revealStart >= 0.30f)
        assertTrue(handle.revealEnd > handle.revealStart)
        assertEquals(
            FluidTriadCubicLoopSpec.CoordinateCount,
            handle.outerAttached.coordinateCount,
        )
        assertEquals(handle.outerCollapsed.minX(), handle.outerCollapsed.maxX(), Epsilon)
        assertEquals(handle.innerCollapsed.minX(), handle.innerCollapsed.maxX(), Epsilon)
        assertTrue(handle.outerAttached.maxX() > 1f)
        assertTrue(handle.outerAttached.maxY() - handle.outerAttached.minY() >
            handle.outerAttached.maxX() - handle.outerAttached.minX())
        assertTrue(handle.innerAttached.minX() > handle.outerAttached.minX())
        assertTrue(handle.innerAttached.maxX() < handle.outerAttached.maxX())
    }

    @Test
    fun `content anchors remain inside material safe regions`() {
        CalculatorQuantityTarget.entries.forEach { target ->
            val content = FluidTriadVisualSpec.panel(target).content
            assertTrue(content.anchorX in content.safeLeft..content.safeRight)
            assertTrue(content.anchorY in content.safeTop..content.safeBottom)
        }
        assertEquals(
            5f / 6f,
            FluidTriadVisualSpec.panel(CalculatorQuantityTarget.IN_CUP).content.anchorX,
            Epsilon,
        )
    }

    @Test
    fun `palette endpoint pairs preserve readable contrast`() {
        CalculatorQuantityTarget.entries.forEach { target ->
            val palette = FluidTriadVisualSpec.panel(target).palette
            assertTrue(contrast(palette.lightFill, palette.lightContent) >= MinimumContrast)
            assertTrue(contrast(palette.darkFill, palette.darkContent) >= MinimumContrast)
        }
    }

    @Test
    fun `theme blended material colors preserve readable selected content`() {
        val schemes = listOf(
            lightColorScheme(
                primaryContainer = Color(0xFFF7F5FF),
                secondaryContainer = Color(0xFFF1F4FF),
                tertiaryContainer = Color(0xFFFFF2F8),
            ),
            darkColorScheme(
                primaryContainer = Color(0xFF17151D),
                secondaryContainer = Color(0xFF111722),
                tertiaryContainer = Color(0xFF1D1319),
            ),
        )

        schemes.forEach { scheme ->
            val resolved = fluidTriadSurfaceColors(scheme)
            listOf(resolved.coffee, resolved.water, resolved.cup).forEach { material ->
                assertTrue(contrast(material.fill, material.content) >= MinimumContrast)
            }
        }
    }

    private fun assertBounds(contour: FluidTriadContourSpec, minX: Float, maxX: Float) {
        assertEquals(minX, contour.minX(), Epsilon)
        assertEquals(maxX, contour.maxX(), Epsilon)
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + ContrastOffset) /
            (minOf(firstLuminance, secondLuminance) + ContrastOffset)
    }

    private companion object {
        const val LeftMidpoint = 0
        const val TopLeftEndpoint = 3
        const val TopCenterEndpoint = 6
        const val TopRightEndpoint = 9
        const val RightMidpoint = 12
        const val BottomRightEndpoint = 15
        const val BottomCenterEndpoint = 18
        const val BottomLeftEndpoint = 21
        const val CubicPointStride = 3
        const val OneThird = 1f / 3f
        const val TwoThirds = 2f / 3f
        const val RepresentativeRailAspect = 4.5f
        const val ViewportWidth = 1_000f
        const val ViewportHeight = 100f
        const val Overflow = 40f
        const val MinimumContrast = 4.5f
        const val ContrastOffset = 0.05f
        const val Epsilon = 0.000_01f
    }
}
