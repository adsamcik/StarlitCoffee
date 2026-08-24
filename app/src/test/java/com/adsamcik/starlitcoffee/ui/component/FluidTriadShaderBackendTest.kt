package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadShaderBackendTest {
    @Test
    fun atlasLayout_keepsSlicesContiguousWithoutSharingSampleCenters() {
        val layout = FluidTriadSdfAtlasLayout(
            frameWidth = 4,
            frameHeight = 3,
            frameCount = 3,
        )

        assertEquals(12, layout.atlasWidth)
        assertEquals(144, layout.estimatedArgb8888Bytes)
        assertEquals(3.5f, layout.sampleCenterX(frame = 0, normalizedX = 1f))
        assertEquals(4.5f, layout.sampleCenterX(frame = 1, normalizedX = 0f))
        assertEquals(35, layout.pixelIndex(frame = 2, x = 3, y = 2))
    }

    @Test
    fun encoding_isMonotonicAndPreservesBoundaryWithinHalfAQuantizationStep() {
        val distanceRange = 0.125f
        val encodedInside = FluidTriadSdfEncoding.encode(-0.02f, distanceRange)
        val encodedBoundary = FluidTriadSdfEncoding.encode(0f, distanceRange)
        val encodedOutside = FluidTriadSdfEncoding.encode(0.02f, distanceRange)

        assertTrue(encodedInside < encodedBoundary)
        assertTrue(encodedBoundary < encodedOutside)
        val decodedBoundary = FluidTriadSdfEncoding.decode(encodedBoundary, distanceRange)
        assertTrue(kotlin.math.abs(decodedBoundary) <= distanceRange / 255f + 0.000001f)
    }

    @Test
    fun frameBlend_rejectsSkippingUncachedIntermediateSlices() {
        assertThrows(IllegalArgumentException::class.java) {
            FluidTriadShaderFrameBlend(firstFrame = 1, secondFrame = 3, fraction = 0.5f)
        }
    }

    @Test
    fun geometry_requestsSdfAspectFromRailWidthAndFullComponentHeight() {
        val geometry = FluidTriadShaderGeometry(
            componentWidth = 400f,
            componentHeight = 100f,
            railLeft = 20f,
            railTop = 10f,
            railWidth = 360f,
            railHeight = 80f,
            railCornerRadius = 20f,
            railOutlineWidth = 1f,
            selectedOutlineWidth = 1f,
            dividerWidth = 1f,
            dividerInset = 8f,
            isRtl = false,
        )

        assertEquals(3.6f, geometry.sdfAspectRatio)
    }

    @Test
    fun routeAdapter_isStableAndReadsSolverFramesWithoutCopying() {
        val atlas = FluidTriadSdfMorphCache.get(
            FluidTriadSdfAtlasRequest(
                railAspect = 3.6f,
                gridHeight = 24,
                frameCount = 12,
            ),
        )
        val route = atlas.route(
            CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.WATER_IN,
        )

        val firstAdapter = route.asShaderFrameSource()
        val secondAdapter = route.asShaderFrameSource()

        assertSame(firstAdapter, secondAdapter)
        assertSame(route.frame(0).valuesForSolver(), firstAdapter.signedDistances(0))
        assertEquals(route.frameCount, firstAdapter.frameCount)
        assertEquals(atlas.layout.minY, firstAdapter.logicalDomain.top)
        assertEquals(atlas.layout.maxY, firstAdapter.logicalDomain.bottom)
    }

    @Test
    fun interruptedSource_capturesDisplayedFieldOnceAndReusesItsTwoFrames() {
        val domain = FluidTriadSdfLogicalDomain(-0.05f, -0.05f, 1.10f, 1.05f)
        val first = floatArrayOf(-1f, -0.5f, 0.5f, 1f)
        val second = floatArrayOf(-0.5f, 0f, 1f, 1.5f)
        val destinationValues = floatArrayOf(1f, 0.5f, -0.5f, -1f)
        val active = TestFrameSource(domain, first, second)
        val destination = TestFrameSource(domain, destinationValues)

        val captured = FluidTriadInterruptedShaderFrameSource.capture(
            activeSource = active,
            activeBlend = FluidTriadShaderFrameBlend(0, 1, 0.25f),
            destination = destination,
        )

        assertArrayEquals(
            floatArrayOf(-0.875f, -0.375f, 0.625f, 1.125f),
            captured.signedDistances(0),
            0f,
        )
        assertSame(captured.signedDistances(0), captured.signedDistances(0))
        assertSame(destinationValues, captured.signedDistances(1))
        assertEquals(2, captured.frameCount)
    }

    private class TestFrameSource(
        override val logicalDomain: FluidTriadSdfLogicalDomain,
        private vararg val frames: FloatArray,
    ) : FluidTriadNormalizedSdfFrameSource {
        override val gridWidth: Int = 2
        override val gridHeight: Int = 2
        override val frameCount: Int = frames.size

        override fun signedDistances(frameIndex: Int): FloatArray = frames[frameIndex]
    }
}
