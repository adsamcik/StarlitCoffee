package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadTransitionTest {
    @Test
    fun `semantic endpoints exactly use the authored selected contours`() {
        targets.forEach { target ->
            val endpoint = FluidTriadFrameResolver.endpoint(target)
            val authored = FluidTriadVisualSpec.panel(target).selectedContour

            assertEquals(target, endpoint.stableTarget)
            assertEquals(1f, endpoint.material.weights[target], Epsilon)
            assertEquals(1f, endpoint.content.weights[target], Epsilon)
            assertEquals(StableElevation, endpoint.material.elevation, Epsilon)
            assertEquals(authored.coordinateCount, endpoint.contour.coordinateCount)
            for (index in 0 until authored.coordinateCount) {
                assertEquals(authored[index], endpoint.contour[index], Epsilon)
            }
        }
    }

    @Test
    fun `every route owns six finite closed matched-topology contour frames`() {
        targets.forEach { source ->
            targets.forEach { destination ->
                val snapshot = FluidTriadFrameResolver.between(source, destination)
                assertEquals(MotionStudyFrameCount, snapshot.contourKeyframeCount)
                for (index in 0 until snapshot.contourKeyframeCount) {
                    val keyframe = snapshot.contourKeyframeAt(index)
                    val contour = keyframe.contour
                    assertEquals(
                        index / (MotionStudyFrameCount - 1f),
                        keyframe.fraction,
                        Epsilon,
                    )
                    assertEquals(
                        FluidTriadVisualSpec.ContourCoordinateCount,
                        contour.coordinateCount,
                    )
                    for (coordinate in 0 until contour.coordinateCount) {
                        assertTrue(contour[coordinate].isFinite())
                    }
                    assertEquals(
                        contour[0],
                        contour[contour.coordinateCount - CoordinateStride],
                        Epsilon,
                    )
                    assertEquals(
                        contour[1],
                        contour[contour.coordinateCount - 1],
                        Epsilon,
                    )
                }
            }
        }
    }

    @Test
    fun `resolved travel remains one closed contour inside its drawing envelope`() {
        targets.forEach { source ->
            targets.forEach { destination ->
                val snapshot = FluidTriadFrameResolver.between(source, destination)
                for (step in 0..SampleCount) {
                    val frame = FluidTriadFrameResolver.resolve(snapshot, step / SampleCount.toFloat())
                    val contour = frame.contour
                    assertEquals(
                        FluidTriadVisualSpec.ContourCoordinateCount,
                        contour.coordinateCount,
                    )
                    for (index in 0 until contour.coordinateCount) {
                        val coordinate = contour[index]
                        assertTrue(
                            "$source to $destination escaped at coordinate $index: $coordinate",
                            coordinate in DrawingEnvelope,
                        )
                    }
                    assertEquals(
                        contour[0],
                        contour[contour.coordinateCount - CoordinateStride],
                        Epsilon,
                    )
                    assertEquals(
                        contour[1],
                        contour[contour.coordinateCount - 1],
                        Epsilon,
                    )
                }
            }
        }
    }

    @Test
    fun `water to cup follows the six-frame material and foreground study`() {
        val snapshot = FluidTriadFrameResolver.between(
            CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.IN_CUP,
        )
        val frames = (0 until MotionStudyFrameCount).map { index ->
            FluidTriadFrameResolver.resolve(
                snapshot,
                index / (MotionStudyFrameCount - 1f),
            )
        }

        frames.zipWithNext().forEach { (first, second) ->
            assertTrue(second.contour.centerX() >= first.contour.centerX() - Epsilon)
        }
        assertTrue(frames[1].material.weights.water > frames[1].material.weights.cup)
        assertTrue(frames[1].content.weights.water > frames[1].content.weights.cup)
        assertTrue(frames[2].material.weights.water > MinimumVisibleMix)
        assertTrue(frames[2].material.weights.cup > MinimumVisibleMix)
        assertTrue(frames[2].content.weights.cup > frames[2].content.weights.water)
        assertEquals(1f, frames[3].material.weights.cup, Epsilon)
        assertEquals(1f, frames[3].content.weights.cup, Epsilon)
        assertEquals(0f, frames[3].effects.cupHandle, Epsilon)
        assertTrue(frames[4].effects.cupHandle in PartialHandleRange)
        assertEquals(1f, frames.last().effects.cupHandle, Epsilon)
        frames.forEach { frame ->
            assertEquals(StableElevation, frame.material.elevation, Epsilon)
        }
    }

    @Test
    fun `semantic handoff starts within the first rendered frame while total duration stays authored`() {
        val timing = FluidTriadTransitionTimings.MaterialTravel
        assertEquals(AuthoredDurationMillis, timing.durationMillis)
        assertEquals(0.00f, timing.materialMix.startFraction, Epsilon)
        assertEquals(0.60f, timing.materialMix.endFraction, Epsilon)
        assertEquals(0.04f, timing.contentMix.startFraction, Epsilon)
        assertEquals(0.54f, timing.contentMix.endFraction, Epsilon)
        assertEquals(0.00f, timing.outgoingEffectFade.startFraction, Epsilon)
        assertEquals(0.34f, timing.outgoingEffectFade.endFraction, Epsilon)
        assertEquals(0.16f, timing.incomingEffectReveal.startFraction, Epsilon)
        assertEquals(0.68f, timing.incomingEffectReveal.endFraction, Epsilon)

        val snapshot = FluidTriadFrameResolver.between(
            CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.IN_CUP,
        )
        val firstFrame = FluidTriadFrameResolver.resolve(snapshot, FirstRenderedFrameProgress)

        assertTrue(firstFrame.material.weights.cup > 0f)
        assertTrue(firstFrame.content.weights.cup > 0f)
        assertTrue(firstFrame.effects.coffeeCrease < 1f)
        assertEquals(0f, firstFrame.effects.cupRim, Epsilon)

        val incomingDetail = FluidTriadFrameResolver.resolve(snapshot, EarlyDetailProgress)
        assertTrue(incomingDetail.effects.cupRim > 0f)
    }

    @Test
    fun `cup handle remains a late finishing detail`() {
        val timing = FluidTriadTransitionTimings.MaterialTravel
        assertEquals(0.72f, timing.incomingCupHandleReveal.startFraction, Epsilon)
        assertEquals(1.00f, timing.incomingCupHandleReveal.endFraction, Epsilon)

        val snapshot = FluidTriadFrameResolver.between(
            CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.IN_CUP,
        )
        assertEquals(
            0f,
            FluidTriadFrameResolver.resolve(snapshot, HandleRevealStartProgress).effects.cupHandle,
            Epsilon,
        )
        assertTrue(
            FluidTriadFrameResolver.resolve(snapshot, HandlePartialProgress).effects.cupHandle in
                PartialHandleRange,
        )
        assertEquals(
            1f,
            FluidTriadFrameResolver.resolve(snapshot, 1f).effects.cupHandle,
            Epsilon,
        )
    }

    @Test
    fun `material and content mixtures stay normalized through every route`() {
        targets.forEach { source ->
            targets.forEach { destination ->
                val snapshot = FluidTriadFrameResolver.between(source, destination)
                for (step in 0..SampleCount) {
                    val frame = FluidTriadFrameResolver.resolve(snapshot, step / SampleCount.toFloat())
                    assertEquals(1f, frame.material.weights.sum, Epsilon)
                    assertEquals(1f, frame.content.weights.sum, Epsilon)
                    targets.forEach { target ->
                        assertTrue(frame.material.weights[target] in 0f..1f)
                        assertTrue(frame.content.weights[target] in 0f..1f)
                    }
                }
            }
        }
    }

    @Test
    fun `retargeting begins at the exact interrupted rendered frame`() {
        val initial = FluidTriadFrameResolver.between(
            CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.IN_CUP,
        )
        val interrupted = FluidTriadFrameResolver.resolve(initial, InterruptedProgress)
        val retargeted = FluidTriadFrameResolver.retarget(
            sourceFrame = interrupted,
            destinationTarget = CalculatorQuantityTarget.WATER_IN,
        )

        assertSame(interrupted, FluidTriadFrameResolver.resolve(retargeted, 0f))
        assertSame(interrupted, FluidTriadFrameResolver.resolve(retargeted, -1f))
        assertSame(
            FluidTriadFrameResolver.endpoint(CalculatorQuantityTarget.WATER_IN),
            FluidTriadFrameResolver.resolve(retargeted, 1f),
        )
        assertSame(
            FluidTriadFrameResolver.endpoint(CalculatorQuantityTarget.WATER_IN),
            FluidTriadFrameResolver.resolve(retargeted, 2f),
        )
    }

    @Test
    fun `contour copies cannot mutate an immutable frame`() {
        val contour = FluidTriadFrameResolver.endpoint(CalculatorQuantityTarget.COFFEE).contour
        val copy = FloatArray(contour.coordinateCount)
        contour.copyInto(copy)
        val original = contour[0]

        copy[0] += 1f

        assertNotEquals(copy[0], contour[0])
        assertEquals(original, contour[0], Epsilon)
    }

    @Test
    fun `same-target transitions remain the cached stable endpoint`() {
        targets.forEach { target ->
            val endpoint = FluidTriadFrameResolver.endpoint(target)
            val snapshot = FluidTriadFrameResolver.between(target, target)
            for (step in 0..SampleCount) {
                assertSame(
                    endpoint,
                    FluidTriadFrameResolver.resolve(snapshot, step / SampleCount.toFloat()),
                )
            }
        }
    }

    @Test
    fun `reusable metadata exactly matches immutable resolution for every route`() {
        val metadata = FluidTriadRenderMetadata()
        val snapshots = buildList {
            targets.forEach { source ->
                targets.forEach { destination ->
                    add(FluidTriadFrameResolver.between(source, destination))
                }
            }
            val interrupted = FluidTriadFrameResolver.resolve(
                FluidTriadFrameResolver.between(
                    CalculatorQuantityTarget.COFFEE,
                    CalculatorQuantityTarget.IN_CUP,
                ),
                InterruptedProgress,
            )
            add(
                FluidTriadFrameResolver.retarget(
                    sourceFrame = interrupted,
                    destinationTarget = CalculatorQuantityTarget.WATER_IN,
                ),
            )
        }

        snapshots.forEach { snapshot ->
            MetadataSampleProgress.forEach { progress ->
                val immutable = FluidTriadFrameResolver.resolve(snapshot, progress)
                val resolved = FluidTriadFrameResolver.resolveMetadata(
                    snapshot = snapshot,
                    progress = progress,
                    out = metadata,
                )

                assertSame(metadata, resolved)
                targets.forEach { target ->
                    assertFloatBitsEqual(
                        immutable.material.weights[target],
                        metadata.materialWeight(target),
                    )
                    assertFloatBitsEqual(
                        immutable.content.weights[target],
                        metadata.contentWeight(target),
                    )
                }
                assertFloatBitsEqual(immutable.material.elevation, metadata.materialElevation)
                assertFloatBitsEqual(immutable.content.anchorX, metadata.contentAnchorX)
                assertFloatBitsEqual(immutable.content.anchorY, metadata.contentAnchorY)
                assertFloatBitsEqual(immutable.content.groupScale, metadata.contentGroupScale)
                assertFloatBitsEqual(
                    immutable.content.rotationDegrees,
                    metadata.contentRotationDegrees,
                )
                assertFloatBitsEqual(immutable.content.iconScale, metadata.contentIconScale)
                assertFloatBitsEqual(immutable.effects.coffeeCrease, metadata.coffeeCrease)
                assertFloatBitsEqual(immutable.effects.waterMotion, metadata.waterMotion)
                assertFloatBitsEqual(immutable.effects.cupRim, metadata.cupRim)
                assertFloatBitsEqual(immutable.effects.cupHandle, metadata.cupHandle)
                assertEquals(immutable.stableTarget, metadata.stableTarget)
            }
        }
    }

    @Test
    fun `caller-owned contour buffer exactly matches immutable resolution`() {
        val coordinateCount = FluidTriadVisualSpec.ContourCoordinateCount
        val untouchedSentinel = -123.456f
        val contourBuffer = FloatArray(coordinateCount + 1) { untouchedSentinel }

        targets.forEach { source ->
            targets.forEach { destination ->
                val snapshot = FluidTriadFrameResolver.between(source, destination)
                ContourSampleProgress.forEach { progress ->
                    val immutable = FluidTriadFrameResolver.resolve(snapshot, progress).contour
                    FluidTriadFrameResolver.resolveContourInto(
                        snapshot = snapshot,
                        progress = progress,
                        out = contourBuffer,
                    )

                    for (index in 0 until coordinateCount) {
                        assertFloatBitsEqual(immutable[index], contourBuffer[index])
                    }
                    assertFloatBitsEqual(untouchedSentinel, contourBuffer[coordinateCount])
                }
            }
        }
    }

    @Test
    fun `caller-owned contour buffer preserves interrupted retarget source exactly`() {
        val interrupted = FluidTriadFrameResolver.resolve(
            FluidTriadFrameResolver.between(
                CalculatorQuantityTarget.COFFEE,
                CalculatorQuantityTarget.IN_CUP,
            ),
            InterruptedProgress,
        )
        val snapshot = FluidTriadFrameResolver.retarget(
            sourceFrame = interrupted,
            destinationTarget = CalculatorQuantityTarget.WATER_IN,
        )
        val contourBuffer = FloatArray(FluidTriadVisualSpec.ContourCoordinateCount)

        FluidTriadFrameResolver.resolveContourInto(snapshot, 0f, contourBuffer)

        for (index in contourBuffer.indices) {
            assertFloatBitsEqual(interrupted.contour[index], contourBuffer[index])
        }
    }

    private fun assertFloatBitsEqual(expected: Float, actual: Float) {
        assertEquals(expected.toRawBits(), actual.toRawBits())
    }

    private fun FluidTriadFrameContour.centerX(): Float = (minX() + maxX()) * 0.5f

    private companion object {
        val targets = CalculatorQuantityTarget.entries
        val MetadataSampleProgress = floatArrayOf(
            -1f,
            0f,
            0.05f,
            0.1f,
            0.2f,
            0.34f,
            InterruptedProgress,
            0.56f,
            0.6f,
            0.72f,
            0.8f,
            0.9f,
            1f,
            2f,
        )
        val ContourSampleProgress = floatArrayOf(
            -1f,
            0f,
            0.07f,
            0.2f,
            0.31f,
            0.4f,
            InterruptedProgress,
            0.6f,
            0.79f,
            0.8f,
            0.93f,
            1f,
            2f,
        )
        val DrawingEnvelope = -0.05f..1.05f
        val PartialHandleRange = 0.05f..0.50f
        const val AuthoredDurationMillis = 300
        const val FirstRenderedFrameProgress = 16f / AuthoredDurationMillis
        const val EarlyDetailProgress = 0.20f
        const val HandleRevealStartProgress = 0.72f
        const val HandlePartialProgress = 0.80f
        const val MotionStudyFrameCount = 6
        const val CoordinateStride = 2
        const val SampleCount = 100
        const val StableElevation = 1f
        const val MinimumVisibleMix = 0.25f
        const val InterruptedProgress = 0.43f
        const val Epsilon = 0.000_01f
    }
}
