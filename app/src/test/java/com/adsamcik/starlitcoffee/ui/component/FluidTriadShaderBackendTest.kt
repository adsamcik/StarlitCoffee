package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
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
            railOutlineWidth = 1f,
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

    @Test
    fun nestedRouteInterruption_capturesTheAgslDisplayedFieldInsteadOfANaiveBlend() {
        val fixture = nestedInterruptionFixture()
        val activeBlend = FluidTriadShaderFrameBlend(0, 1, 0.43f)
        val expected = expectedInterruptedAgslField(fixture.active, activeBlend)

        val captured = FluidTriadInterruptedShaderFrameSource.capture(
            activeSource = fixture.active,
            activeBlend = activeBlend,
            destination = fixture.destination,
        )

        assertArrayEquals(expected, captured.signedDistances(0), FieldParityEpsilon)
        val naive = linearlyBlend(
            fixture.active.signedDistances(0),
            fixture.active.signedDistances(1),
            activeBlend.fraction,
        )
        assertTrue(maxAbsoluteDifference(expected, naive) > MeaningfulAdvectionDelta)
    }

    @Test
    fun nestedRouteInterruption_preservesAgslParityThroughEarlyMiddleAndLateMotion() {
        val fixture = nestedInterruptionFixture()

        NestedProgressSamples.forEach { progress ->
            val activeBlend = FluidTriadShaderFrameBlend(0, 1, progress)
            val expected = expectedInterruptedAgslField(fixture.active, activeBlend)
            val captured = FluidTriadInterruptedShaderFrameSource.capture(
                activeSource = fixture.active,
                activeBlend = activeBlend,
                destination = fixture.destination,
            )

            assertArrayEquals(
                "Nested capture diverged from AGSL at progress $progress",
                expected,
                captured.signedDistances(0),
                FieldParityEpsilon,
            )
        }
    }

    @Test
    fun shaderCupHandleBounds_followTheCanvasHandleAtEveryRevealStage() {
        val handle = FluidTriadVisualSpec.CupHandle
        val cupAnchor = FluidTriadVisualSpec.panel(CalculatorQuantityTarget.IN_CUP).content.anchorX
        assertEquals(
            FluidTriadShaderCupHandleSpec.BodyAttachmentX,
            FluidTriadShaderCupHandleSpec.attachmentX(cupAnchor),
            FieldParityEpsilon,
        )

        HandleProgressSamples.forEach { progress ->
            val outer = FluidTriadShaderCupHandleSpec.outerBounds(progress, cupAnchor)
            val inner = FluidTriadShaderCupHandleSpec.innerBounds(progress, cupAnchor)
            assertEquals(
                lerp(handle.outerCollapsed.minX(), handle.outerAttached.minX(), progress),
                outer.left,
                FieldParityEpsilon,
            )
            assertEquals(
                lerp(handle.outerCollapsed.maxX(), handle.outerAttached.maxX(), progress),
                outer.right,
                FieldParityEpsilon,
            )
            assertEquals(
                lerp(handle.innerCollapsed.minX(), handle.innerAttached.minX(), progress),
                inner.left,
                FieldParityEpsilon,
            )
            assertEquals(
                lerp(handle.innerCollapsed.maxX(), handle.innerAttached.maxX(), progress),
                inner.right,
                FieldParityEpsilon,
            )
            assertEquals(
                lerp(handle.innerCollapsed.minY(), handle.innerAttached.minY(), progress),
                inner.top,
                FieldParityEpsilon,
            )
            assertEquals(
                lerp(handle.innerCollapsed.maxY(), handle.innerAttached.maxY(), progress),
                inner.bottom,
                FieldParityEpsilon,
            )
        }
    }

    private fun nestedInterruptionFixture(): NestedInterruptionFixture {
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
        ).asShaderFrameSource()
        val firstInterruption = FluidTriadInterruptedShaderFrameSource.capture(
            activeSource = route,
            activeBlend = FluidTriadShaderFrameBlend(4, 5, 0.35f),
            destination = atlas.endpoint(CalculatorQuantityTarget.IN_CUP).asShaderFrameSource(),
        )
        return NestedInterruptionFixture(
            active = firstInterruption,
            destination = atlas.endpoint(CalculatorQuantityTarget.COFFEE).asShaderFrameSource(),
        )
    }

    /** Independent CPU statement of the interrupted branch in FLUID_TRIAD_AGSL. */
    private fun expectedInterruptedAgslField(
        source: FluidTriadInterruptedShaderFrameSource,
        blend: FluidTriadShaderFrameBlend,
    ): FloatArray {
        val first = source.signedDistances(blend.firstFrame)
        val second = source.signedDistances(blend.secondFrame)
        val inverse = 1f - blend.fraction
        val motion = 1f - inverse * inverse * inverse
        val movingX = lerp(source.sourceCentroidX, source.destinationCentroidX, motion)
        val movingY = lerp(source.sourceCentroidY, source.destinationCentroidY, motion)
        val sourceDx = movingX - source.sourceCentroidX
        val sourceDy = movingY - source.sourceCentroidY
        val destinationDx = movingX - source.destinationCentroidX
        val destinationDy = movingY - source.destinationCentroidY
        val domain = source.logicalDomain
        val xStep = (domain.right - domain.left) / (source.gridWidth - 1)
        val yStep = (domain.bottom - domain.top) / (source.gridHeight - 1)

        return FloatArray(source.gridWidth * source.gridHeight) { index ->
            val row = index / source.gridWidth
            val column = index - row * source.gridWidth
            val x = domain.left + column * xStep
            val y = domain.top + row * yStep
            val firstDistance = sampleBilinear(
                first,
                source.gridWidth,
                source.gridHeight,
                domain,
                x - sourceDx,
                y - sourceDy,
            )
            val secondDistance = sampleBilinear(
                second,
                source.gridWidth,
                source.gridHeight,
                domain,
                x - destinationDx,
                y - destinationDy,
            )
            lerp(firstDistance, secondDistance, motion)
        }
    }

    private fun sampleBilinear(
        values: FloatArray,
        width: Int,
        height: Int,
        domain: FluidTriadSdfLogicalDomain,
        x: Float,
        y: Float,
    ): Float {
        val normalizedX = ((x - domain.left) / (domain.right - domain.left)).coerceIn(0f, 1f)
        val normalizedY = ((y - domain.top) / (domain.bottom - domain.top)).coerceIn(0f, 1f)
        val gridX = normalizedX * (width - 1)
        val gridY = normalizedY * (height - 1)
        val left = floor(gridX).toInt().coerceIn(0, width - 1)
        val top = floor(gridY).toInt().coerceIn(0, height - 1)
        val right = (left + 1).coerceAtMost(width - 1)
        val bottom = (top + 1).coerceAtMost(height - 1)
        val horizontal = gridX - left
        val vertical = gridY - top
        val topValue = lerp(values[top * width + left], values[top * width + right], horizontal)
        val bottomValue = lerp(
            values[bottom * width + left],
            values[bottom * width + right],
            horizontal,
        )
        return lerp(topValue, bottomValue, vertical)
    }

    private fun linearlyBlend(first: FloatArray, second: FloatArray, fraction: Float): FloatArray =
        FloatArray(first.size) { index -> lerp(first[index], second[index], fraction) }

    private fun maxAbsoluteDifference(first: FloatArray, second: FloatArray): Float {
        var maximum = 0f
        first.indices.forEach { index -> maximum = max(maximum, abs(first[index] - second[index])) }
        return maximum
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction

    private data class NestedInterruptionFixture(
        val active: FluidTriadInterruptedShaderFrameSource,
        val destination: FluidTriadNormalizedSdfFrameSource,
    )

    private class TestFrameSource(
        override val logicalDomain: FluidTriadSdfLogicalDomain,
        private vararg val frames: FloatArray,
    ) : FluidTriadNormalizedSdfFrameSource {
        override val gridWidth: Int = 2
        override val gridHeight: Int = 2
        override val frameCount: Int = frames.size

        override fun signedDistances(frameIndex: Int): FloatArray = frames[frameIndex]
    }

    private companion object {
        val NestedProgressSamples = floatArrayOf(0.12f, 0.43f, 0.76f)
        val HandleProgressSamples = floatArrayOf(0f, 0.20f, 0.50f, 0.80f, 1f)
        const val FieldParityEpsilon = 0.000_001f
        const val MeaningfulAdvectionDelta = 0.001f
    }
}
