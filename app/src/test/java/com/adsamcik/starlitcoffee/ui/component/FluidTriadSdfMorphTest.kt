package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadSdfMorphTest {
    @Test
    fun `all directed routes share exact authored endpoint fields`() {
        val atlas = atlas()

        Targets.forEach { target ->
            val endpoint = atlas.endpoint(target)
            val content = FluidTriadVisualSpec.panel(target).content
            assertTrue(endpoint.sampleLogical(content.anchorX, content.anchorY) < 0f)
        }

        Targets.forEach { source ->
            Targets.filterNot { it == source }.forEach { destination ->
                val route = atlas.route(source, destination)
                assertEquals(TestFrameCount, route.frameCount)
                assertSame(atlas.endpoint(source), route.frame(0))
                assertSame(atlas.endpoint(destination), route.frame(route.frameCount - 1))
            }
        }
    }

    @Test
    fun `cup endpoint contains attached handle ring and preserves its hole`() {
        val atlas = atlas()
        val cup = atlas.endpoint(CalculatorQuantityTarget.IN_CUP)

        assertTrue(atlas.includesCupHandleAndHole)
        assertTrue(atlas.layout.maxX >= 1.035f)
        assertTrue(cup.sampleLogical(0.84f, 0.50f) < 0f)
        assertTrue(cup.sampleLogical(1.026f, 0.50f) < 0f)
        assertTrue(cup.sampleLogical(1.006f, 0.50f) > 0f)
        assertTrue(cup.sampleLogical(1.07f, 0.50f) > 0f)
    }

    @Test
    fun `cached fields remain finite bounded and one connected mass`() {
        val atlas = atlas()

        uniqueFrames(atlas).forEach { frame ->
            repeat(frame.layout.valueCount) { index ->
                val value = frame.valueAt(index)
                assertTrue(value.isFinite())
                assertTrue(abs(value) <= frame.layout.distanceClamp + Epsilon)
            }
            assertTrue(frame.insideCellCount > 0)
            assertEquals(1, negativeComponentCount(frame))
        }
    }

    @Test
    fun `directed transport advances toward its destination`() {
        val atlas = atlas()

        Targets.forEach { source ->
            Targets.filterNot { it == source }.forEach { destination ->
                val route = atlas.route(source, destination)
                val sourceFrame = atlas.endpoint(source)
                val destinationFrame = atlas.endpoint(destination)
                val deltaX = (destinationFrame.centroidX - sourceFrame.centroidX) *
                    atlas.layout.railAspect
                val deltaY = destinationFrame.centroidY - sourceFrame.centroidY
                val lengthSquared = deltaX * deltaX + deltaY * deltaY
                var previousProjection = -ProjectionTolerance

                repeat(route.frameCount) { index ->
                    val frame = route.frame(index)
                    val offsetX = (frame.centroidX - sourceFrame.centroidX) *
                        atlas.layout.railAspect
                    val offsetY = frame.centroidY - sourceFrame.centroidY
                    val projection = (offsetX * deltaX + offsetY * deltaY) / lengthSquared
                    assertTrue(projection in -ProjectionTolerance..(1f + ProjectionTolerance))
                    assertTrue(projection + ProjectionTolerance >= previousProjection)
                    previousProjection = projection
                }

                assertEquals(1f, previousProjection, Epsilon)
            }
        }
    }

    @Test
    fun `area correction follows endpoint volume without material drift`() {
        val atlas = atlas()

        Targets.forEach { source ->
            Targets.filterNot { it == source }.forEach { destination ->
                val route = atlas.route(source, destination)
                val startCount = route.frame(0).insideCellCount.toFloat()
                val endCount = route.frame(route.frameCount - 1).insideCellCount.toFloat()
                repeat(route.frameCount) { index ->
                    val timeline = index / (route.frameCount - 1f)
                    val expected = startCount + (endCount - startCount) * easeOutCubic(timeline)
                    val tolerance = max(MinimumAreaToleranceCells, expected * AreaToleranceFraction)
                    assertTrue(abs(route.frame(index).insideCellCount - expected) <= tolerance)
                }
            }
        }
    }

    @Test
    fun `water idle is a cached calm gust calm cycle with stable volume`() {
        val atlas = atlas()
        val calm = atlas.endpoint(CalculatorQuantityTarget.WATER_IN)
        val idle = atlas.waterIdle

        assertSame(calm, idle.frame(0))
        assertSame(calm, idle.frame(idle.frameCount - 1))
        var maximumChange = 0f
        repeat(idle.frameCount) { frameIndex ->
            val frame = idle.frame(frameIndex)
            assertTrue(
                abs(frame.insideCellCount - calm.insideCellCount) <=
                    max(MinimumAreaToleranceCells, calm.insideCellCount * AreaToleranceFraction),
            )
            repeat(frame.layout.valueCount) { valueIndex ->
                maximumChange = max(
                    maximumChange,
                    abs(frame.valueAt(valueIndex) - calm.valueAt(valueIndex)),
                )
            }
        }
        assertTrue(maximumChange >= MinimumVisibleIdleDelta)
    }

    @Test
    fun `runtime resolver returns only adjacent cached frames`() {
        val route = atlas().route(
            CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.IN_CUP,
        )
        val resolved = FluidTriadSdfResolvedFrames()
        val scaledFrame = 4.25f

        route.resolve(scaledFrame / route.frameCount.minus(1), resolved)

        assertSame(route.frame(4), resolved.lower)
        assertSame(route.frame(5), resolved.upper)
        assertEquals(0.25f, resolved.fraction, Epsilon)
        route.resolve(Float.NaN, resolved)
        assertSame(route.frame(0), resolved.lower)
        assertSame(route.frame(1), resolved.upper)
        assertEquals(0f, resolved.fraction, Epsilon)
        route.resolve(1f, resolved)
        assertSame(route.frame(route.frameCount - 1), resolved.lower)
        assertSame(route.frame(route.frameCount - 1), resolved.upper)
    }

    @Test
    fun `cache quantizes aspect and returns one instance across threads`() {
        FluidTriadSdfMorphCache.clearForTests()
        val firstRequest = testRequest(railAspect = 4.49f)
        val equivalentRequest = testRequest(railAspect = 4.51f)
        val first = FluidTriadSdfMorphCache.get(firstRequest)
        assertSame(first, FluidTriadSdfMorphCache.get(equivalentRequest))

        val executor = Executors.newFixedThreadPool(ThreadCount)
        val start = CountDownLatch(1)
        try {
            val futures = List(ThreadCount) {
                executor.submit<FluidTriadSdfAtlas> {
                    start.await()
                    FluidTriadSdfMorphCache.get(equivalentRequest)
                }
            }
            start.countDown()
            futures.forEach { future -> assertSame(first, future.get(ThreadTimeoutSeconds, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cache evicts least recently used atlas at its fixed bound`() {
        FluidTriadSdfMorphCache.clearForTests()
        val maximumEntries = FluidTriadSdfMorphCache.maximumEntryCountForTests()
        val requests = List(maximumEntries + 1) { index ->
            testRequest(railAspect = 2.5f + index)
        }
        val first = FluidTriadSdfMorphCache.get(requests.first())
        requests.drop(1).forEach(FluidTriadSdfMorphCache::get)

        assertEquals(maximumEntries, FluidTriadSdfMorphCache.entryCountForTests())
        assertNotSame(first, FluidTriadSdfMorphCache.get(requests.first()))
        assertEquals(maximumEntries, FluidTriadSdfMorphCache.entryCountForTests())
    }

    @Test
    fun `default atlas has bounded immutable field storage`() {
        FluidTriadSdfMorphCache.clearForTests()
        lateinit var atlas: FluidTriadSdfAtlas
        val buildNanos = measureNanoTime {
            atlas = FluidTriadSdfMorphCache.get(
                FluidTriadSdfAtlasRequest(railAspect = RepresentativeRailAspect),
            )
        }
        val frames = uniqueFrames(atlas)
        val rawFieldBytes = frames.size.toLong() * atlas.layout.valueCount * Float.SIZE_BYTES

        assertTrue(rawFieldBytes <= MaximumDefaultRawFieldBytes)
        println(
            "FluidTriad default SDF atlas: ${atlas.layout.width}x${atlas.layout.height}, " +
                "${frames.size} unique fields, $rawFieldBytes raw bytes, " +
                "${buildNanos / NanosPerMillisecond} ms build",
        )
    }

    private fun atlas(): FluidTriadSdfAtlas = FluidTriadSdfMorphCache.get(testRequest())

    private fun testRequest(
        railAspect: Float = RepresentativeRailAspect,
    ): FluidTriadSdfAtlasRequest = FluidTriadSdfAtlasRequest(
        railAspect = railAspect,
        gridHeight = TestGridHeight,
        frameCount = TestFrameCount,
    )

    private fun uniqueFrames(atlas: FluidTriadSdfAtlas): Set<FluidTriadSdfFrame> {
        val frames = Collections.newSetFromMap(
            IdentityHashMap<FluidTriadSdfFrame, Boolean>(),
        )
        Targets.forEach { frames += atlas.endpoint(it) }
        Targets.forEach { source ->
            Targets.filterNot { it == source }.forEach { destination ->
                val route = atlas.route(source, destination)
                repeat(route.frameCount) { frames += route.frame(it) }
            }
        }
        repeat(atlas.waterIdle.frameCount) { frames += atlas.waterIdle.frame(it) }
        return frames
    }

    private fun negativeComponentCount(frame: FluidTriadSdfFrame): Int {
        val layout = frame.layout
        val visited = BooleanArray(layout.valueCount)
        val queue = IntArray(layout.valueCount)
        var componentCount = 0

        repeat(layout.valueCount) { seed ->
            if (visited[seed] || frame.valueAt(seed) >= 0f) return@repeat
            componentCount += 1
            var read = 0
            var write = 0
            queue[write++] = seed
            visited[seed] = true
            while (read < write) {
                val index = queue[read++]
                val row = index / layout.width
                val column = index - row * layout.width
                for (rowOffset in -1..1) {
                    for (columnOffset in -1..1) {
                        val neighborRow = row + rowOffset
                        val neighborColumn = column + columnOffset
                        if (isGridNeighbor(
                                rowOffset = rowOffset,
                                columnOffset = columnOffset,
                                row = neighborRow,
                                column = neighborColumn,
                                layout = layout,
                            )
                        ) {
                            val neighbor = layout.index(neighborColumn, neighborRow)
                            if (!visited[neighbor] && frame.valueAt(neighbor) < 0f) {
                                visited[neighbor] = true
                                queue[write++] = neighbor
                            }
                        }
                    }
                }
            }
        }
        return componentCount
    }

    private fun isGridNeighbor(
        rowOffset: Int,
        columnOffset: Int,
        row: Int,
        column: Int,
        layout: FluidTriadSdfFieldLayout,
    ): Boolean {
        if (rowOffset == 0 && columnOffset == 0) return false
        if (row !in 0 until layout.height) return false
        return column in 0 until layout.width
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    private companion object {
        val Targets = CalculatorQuantityTarget.entries
        const val RepresentativeRailAspect = 4.5f
        const val TestGridHeight = 24
        const val TestFrameCount = 12
        const val ThreadCount = 6
        const val ThreadTimeoutSeconds = 30L
        const val ProjectionTolerance = 0.08f
        const val AreaToleranceFraction = 0.02f
        const val MinimumAreaToleranceCells = 2f
        const val MinimumVisibleIdleDelta = 0.001f
        const val MaximumDefaultRawFieldBytes = 4L * 1024L * 1024L
        const val NanosPerMillisecond = 1_000_000L
        const val Epsilon = 0.000_01f
    }
}
