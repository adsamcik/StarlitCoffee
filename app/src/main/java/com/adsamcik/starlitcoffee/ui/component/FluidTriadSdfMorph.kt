package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import java.util.LinkedHashMap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Request for a normalized, cached triad level-set atlas. */
internal data class FluidTriadSdfAtlasRequest(
    val railAspect: Float,
    val gridHeight: Int = DefaultGridHeight,
    val frameCount: Int = DefaultFrameCount,
) {
    init {
        require(railAspect.isFinite() && railAspect > 0f)
    }
}

/**
 * Bounded cache identity. Aspect is quantized to sixteenth increments to avoid one atlas per px.
 */
internal data class FluidTriadSdfCacheKey(
    val aspectBucket: Int,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val modelVersion: Int,
) {
    val railAspect: Float get() = aspectBucket / AspectBucketsPerUnit.toFloat()
}

internal enum class FluidTriadSdfSignConvention {
    NEGATIVE_INSIDE,
}

internal enum class FluidTriadSdfDistanceUnit {
    COMPONENT_HEIGHT,
}

/** Row-major field layout over the authored rail plus its handle overflow. */
internal data class FluidTriadSdfFieldLayout(
    val width: Int,
    val height: Int,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val railAspect: Float,
    val distanceClamp: Float,
) {
    init {
        require(width >= 2)
        require(height >= 2)
        require(minX < maxX)
        require(minY < maxY)
        require(railAspect > 0f)
        require(distanceClamp > 0f)
    }

    val valueCount: Int get() = width * height
    val xStep: Float get() = (maxX - minX) / (width - 1)
    val yStep: Float get() = (maxY - minY) / (height - 1)
    val physicalCellArea: Float get() = xStep * railAspect * yStep
    val signConvention: FluidTriadSdfSignConvention
        get() = FluidTriadSdfSignConvention.NEGATIVE_INSIDE
    val distanceUnit: FluidTriadSdfDistanceUnit
        get() = FluidTriadSdfDistanceUnit.COMPONENT_HEIGHT

    fun index(column: Int, row: Int): Int = row * width + column

    fun xAt(column: Int): Float = minX + column * xStep

    fun yAt(row: Int): Float = minY + row * yStep
}

/** Immutable-by-convention normalized signed-distance or corrected level-set frame. */
internal class FluidTriadSdfFrame internal constructor(
    val layout: FluidTriadSdfFieldLayout,
    private val values: FloatArray,
    val insideCellCount: Int,
    val area: Float,
    val centroidX: Float,
    val centroidY: Float,
) {
    init {
        require(values.size == layout.valueCount)
        require(values.all { it.isFinite() })
    }

    fun valueAt(index: Int): Float = values[index]

    fun copyValuesInto(destination: FloatArray) {
        require(destination.size >= values.size)
        values.copyInto(destination)
    }

    fun sampleLogical(x: Float, y: Float): Float = sampleField(values, layout, x, y)

    internal fun valuesForSolver(): FloatArray = values
}

/** Caller-owned hot-path result; [FluidTriadSdfRoute.resolve] mutates it without allocation. */
internal class FluidTriadSdfResolvedFrames {
    lateinit var lower: FluidTriadSdfFrame
        private set
    lateinit var upper: FluidTriadSdfFrame
        private set
    var fraction: Float = 0f
        private set

    internal fun set(
        lower: FluidTriadSdfFrame,
        upper: FluidTriadSdfFrame,
        fraction: Float,
    ) {
        this.lower = lower
        this.upper = upper
        this.fraction = fraction
    }

    fun sampleLogical(x: Float, y: Float): Float = lerp(
        lower.sampleLogical(x, y),
        upper.sampleLogical(x, y),
        fraction,
    )
}

internal class FluidTriadSdfRoute internal constructor(
    val source: CalculatorQuantityTarget,
    val destination: CalculatorQuantityTarget,
    private val frames: Array<FluidTriadSdfFrame>,
) {
    init {
        require(source != destination)
        require(frames.size >= MinimumFrameCount)
    }

    val frameCount: Int get() = frames.size

    fun frame(index: Int): FluidTriadSdfFrame = frames[index]

    fun resolve(progress: Float, out: FluidTriadSdfResolvedFrames) {
        val normalized = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val scaled = normalized * frames.lastIndex
        val lowerIndex = floor(scaled).toInt().coerceIn(0, frames.lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(frames.lastIndex)
        out.set(
            lower = frames[lowerIndex],
            upper = frames[upperIndex],
            fraction = scaled - lowerIndex,
        )
    }
}

/** Precomputed calm -> gust -> calm sequence for occasional selected-water motion. */
internal class FluidTriadSdfCyclicRoute internal constructor(
    private val frames: Array<FluidTriadSdfFrame>,
) {
    init {
        require(frames.size >= MinimumCyclicFrameCount)
        require(frames.first() === frames.last())
    }

    val frameCount: Int get() = frames.size

    fun frame(index: Int): FluidTriadSdfFrame = frames[index]

    fun resolve(phase: Float, out: FluidTriadSdfResolvedFrames) {
        val normalized = if (phase.isFinite()) phase.coerceIn(0f, 1f) else 0f
        val scaled = normalized * frames.lastIndex
        val lowerIndex = floor(scaled).toInt().coerceIn(0, frames.lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(frames.lastIndex)
        out.set(frames[lowerIndex], frames[upperIndex], scaled - lowerIndex)
    }
}

internal class FluidTriadSdfAtlas internal constructor(
    val key: FluidTriadSdfCacheKey,
    val layout: FluidTriadSdfFieldLayout,
    val waterIdle: FluidTriadSdfCyclicRoute,
    private val endpoints: Array<FluidTriadSdfFrame>,
    private val routes: Array<FluidTriadSdfRoute?>,
) {
    val signConvention: FluidTriadSdfSignConvention
        get() = FluidTriadSdfSignConvention.NEGATIVE_INSIDE
    val distanceUnit: FluidTriadSdfDistanceUnit
        get() = FluidTriadSdfDistanceUnit.COMPONENT_HEIGHT
    val includesCupHandleAndHole: Boolean get() = true

    fun endpoint(target: CalculatorQuantityTarget): FluidTriadSdfFrame =
        endpoints[targetIndex(target)]

    fun route(
        source: CalculatorQuantityTarget,
        destination: CalculatorQuantityTarget,
    ): FluidTriadSdfRoute {
        require(source != destination)
        return requireNotNull(routes[routeIndex(source, destination)])
    }
}

/** Thread-safe, access-ordered atlas cache with a small fixed memory bound. */
internal object FluidTriadSdfMorphCache {
    private val lock = Any()
    private val atlases = LinkedHashMap<FluidTriadSdfCacheKey, FluidTriadSdfAtlas>(
        MaximumCacheEntries,
        CacheLoadFactor,
        true,
    )

    fun get(request: FluidTriadSdfAtlasRequest): FluidTriadSdfAtlas = synchronized(lock) {
        val key = request.normalizedKey()
        atlases[key] ?: FluidTriadSdfAtlasBuilder(key).build().also { atlas ->
            atlases[key] = atlas
            trimToBound()
        }
    }

    internal fun clearForTests() = synchronized(lock) {
        atlases.clear()
    }

    internal fun entryCountForTests(): Int = synchronized(lock) { atlases.size }

    internal fun maximumEntryCountForTests(): Int = MaximumCacheEntries

    private fun trimToBound() {
        while (atlases.size > MaximumCacheEntries) {
            val iterator = atlases.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }
}

private class FluidTriadSdfAtlasBuilder(
    private val key: FluidTriadSdfCacheKey,
) {
    private val layout = FluidTriadSdfFieldLayout(
        width = key.width,
        height = key.height,
        minX = DomainMinX,
        maxX = DomainMaxX,
        minY = DomainMinY,
        maxY = DomainMaxY,
        railAspect = key.railAspect,
        distanceClamp = DistanceClamp,
    )

    fun build(): FluidTriadSdfAtlas {
        val endpoints = Array(TargetCount) { index -> buildEndpoint(Targets[index]) }
        val routes = arrayOfNulls<FluidTriadSdfRoute>(RouteSlotCount)
        Targets.forEach { source ->
            Targets.forEach { destination ->
                if (source != destination) {
                    routes[routeIndex(source, destination)] = buildRoute(
                        source = source,
                        destination = destination,
                        sourceFrame = endpoints[targetIndex(source)],
                        destinationFrame = endpoints[targetIndex(destination)],
                    )
                }
            }
        }
        val waterIdle = buildWaterIdle(endpoints[targetIndex(CalculatorQuantityTarget.WATER_IN)])
        return FluidTriadSdfAtlas(key, layout, waterIdle, endpoints, routes)
    }

    private fun buildEndpoint(target: CalculatorQuantityTarget): FluidTriadSdfFrame {
        val body = flattenContour(FluidTriadVisualSpec.panel(target).selectedContour)
        val handle = if (target == CalculatorQuantityTarget.IN_CUP) attachedHandle() else null
        val values = FloatArray(layout.valueCount)
        for (row in 0 until layout.height) {
            val y = layout.yAt(row)
            for (column in 0 until layout.width) {
                val x = layout.xAt(column)
                values[layout.index(column, row)] = endpointDistance(x, y, body, handle)
            }
        }
        return analyze(values)
    }

    private fun endpointDistance(
        x: Float,
        y: Float,
        body: FloatArray,
        handle: HandlePolylines?,
    ): Float {
        val bodyDistance = signedDistanceToPolygon(x, y, body, layout.railAspect)
        if (handle == null) return bodyDistance.coerceIn(-DistanceClamp, DistanceClamp)
        val outerDistance = signedDistanceToPolygon(x, y, handle.outer, layout.railAspect)
        val innerDistance = signedDistanceToPolygon(x, y, handle.inner, layout.railAspect)
        val handleRingDistance = maxOf(outerDistance, -innerDistance)
        return minOf(bodyDistance, handleRingDistance).coerceIn(-DistanceClamp, DistanceClamp)
    }

    private fun attachedHandle(): HandlePolylines = HandlePolylines(
        outer = flattenLoop(FluidTriadVisualSpec.CupHandle.outerAttached),
        inner = flattenLoop(FluidTriadVisualSpec.CupHandle.innerAttached),
    )

    private fun buildRoute(
        source: CalculatorQuantityTarget,
        destination: CalculatorQuantityTarget,
        sourceFrame: FluidTriadSdfFrame,
        destinationFrame: FluidTriadSdfFrame,
    ): FluidTriadSdfRoute {
        val frames = Array(key.frameCount) { frameIndex ->
            when (frameIndex) {
                0 -> sourceFrame
                key.frameCount - 1 -> destinationFrame
                else -> buildIntermediate(
                    source = sourceFrame,
                    destination = destinationFrame,
                    timeline = frameIndex / (key.frameCount - 1f),
                )
            }
        }
        return FluidTriadSdfRoute(source, destination, frames)
    }

    private fun buildWaterIdle(calm: FluidTriadSdfFrame): FluidTriadSdfCyclicRoute {
        val frames = Array(WaterIdleFrameCount) { frameIndex ->
            when (frameIndex) {
                0, WaterIdleFrameCount - 1 -> calm
                else -> buildWaterIdleIntermediate(
                    calm = calm,
                    phase = frameIndex / (WaterIdleFrameCount - 1f),
                )
            }
        }
        return FluidTriadSdfCyclicRoute(frames)
    }

    private fun buildWaterIdleIntermediate(
        calm: FluidTriadSdfFrame,
        phase: Float,
    ): FluidTriadSdfFrame {
        val field = FloatArray(layout.valueCount)
        val scratch = FloatArray(layout.valueCount)
        advectWaterGust(calm, phase, field)
        applySurfaceTension(field, scratch, phase)
        correctArea(field, calm.insideCellCount)
        return analyze(field)
    }

    private fun advectWaterGust(
        calm: FluidTriadSdfFrame,
        phase: Float,
        out: FloatArray,
    ) {
        val energy = sin(PI.toFloat() * phase).coerceAtLeast(0f)
        for (row in 0 until layout.height) {
            val y = layout.yAt(row)
            for (column in 0 until layout.width) {
                val x = layout.xAt(column)
                val displacement = waterGustDisplacement(x, y, phase, energy)
                out[layout.index(column, row)] = calm.sampleLogical(x, y - displacement)
            }
        }
    }

    private fun waterGustDisplacement(
        x: Float,
        y: Float,
        phase: Float,
        energy: Float,
    ): Float {
        val position = ((x - WaterFieldLeft) / (WaterFieldRight - WaterFieldLeft))
            .coerceIn(0f, 1f)
        val pinned = sin(PI.toFloat() * position).coerceAtLeast(0f)
        val traveling = sin(TwoPi * (position - WaterGustTravel * phase))
        val verticalResponse = when {
            y < WaterUpperResponseEnd -> 1f
            y > WaterLowerResponseStart -> WaterLowerResponse
            else -> WaterCenterResponse
        }
        return WaterGustAmplitude * energy * pinned * traveling * verticalResponse
    }

    private fun buildIntermediate(
        source: FluidTriadSdfFrame,
        destination: FluidTriadSdfFrame,
        timeline: Float,
    ): FluidTriadSdfFrame {
        val motion = easeOutCubic(timeline)
        val field = FloatArray(layout.valueCount)
        val scratch = FloatArray(layout.valueCount)
        advectAndBlend(source, destination, motion, field)
        applySurfaceTension(field, scratch, timeline)
        val targetArea = lerp(source.insideCellCount.toFloat(), destination.insideCellCount.toFloat(), motion)
            .roundToInt()
        correctArea(field, targetArea)
        return analyze(field)
    }

    private fun advectAndBlend(
        source: FluidTriadSdfFrame,
        destination: FluidTriadSdfFrame,
        motion: Float,
        out: FloatArray,
    ) {
        val centerX = lerp(source.centroidX, destination.centroidX, motion)
        val centerY = lerp(source.centroidY, destination.centroidY, motion)
        val sourceDx = centerX - source.centroidX
        val sourceDy = centerY - source.centroidY
        val destinationDx = centerX - destination.centroidX
        val destinationDy = centerY - destination.centroidY
        for (row in 0 until layout.height) {
            val y = layout.yAt(row)
            for (column in 0 until layout.width) {
                val x = layout.xAt(column)
                val sourceValue = source.sampleLogical(x - sourceDx, y - sourceDy)
                val destinationValue = destination.sampleLogical(
                    x - destinationDx,
                    y - destinationDy,
                )
                out[layout.index(column, row)] = lerp(sourceValue, destinationValue, motion)
            }
        }
    }

    private fun applySurfaceTension(
        field: FloatArray,
        scratch: FloatArray,
        progress: Float,
    ) {
        val envelope = sin(PI.toFloat() * progress)
        val strength = SurfaceTensionStrength * envelope * envelope
        var source = field
        var destination = scratch
        repeat(SurfaceTensionPassCount) {
            smoothPass(source, destination, strength)
            val previousSource = source
            source = destination
            destination = previousSource
        }
        if (source !== field) source.copyInto(field)
    }

    private fun smoothPass(source: FloatArray, destination: FloatArray, strength: Float) {
        source.copyInto(destination)
        for (row in 1 until layout.height - 1) {
            for (column in 1 until layout.width - 1) {
                val index = layout.index(column, row)
                val neighbors = source[index - 1] + source[index + 1] +
                    source[index - layout.width] + source[index + layout.width]
                destination[index] = lerp(source[index], neighbors * NeighborAverage, strength)
            }
        }
    }

    private fun correctArea(field: FloatArray, desiredInsideCells: Int) {
        val desired = desiredInsideCells.coerceIn(1, field.lastIndex)
        val ordered = field.copyOf()
        ordered.sort()
        val threshold = midpoint(ordered[desired - 1], ordered[desired])
        for (index in field.indices) {
            field[index] = (field[index] - threshold).coerceIn(-DistanceClamp, DistanceClamp)
        }
    }

    private fun analyze(values: FloatArray): FluidTriadSdfFrame {
        var insideCount = 0
        var xMoment = 0f
        var yMoment = 0f
        for (row in 0 until layout.height) {
            val y = layout.yAt(row)
            for (column in 0 until layout.width) {
                if (values[layout.index(column, row)] < 0f) {
                    insideCount += 1
                    xMoment += layout.xAt(column)
                    yMoment += y
                }
            }
        }
        val centroidX = if (insideCount == 0) 0.5f else xMoment / insideCount
        val centroidY = if (insideCount == 0) 0.5f else yMoment / insideCount
        return FluidTriadSdfFrame(
            layout = layout,
            values = values,
            insideCellCount = insideCount,
            area = insideCount * layout.physicalCellArea,
            centroidX = centroidX,
            centroidY = centroidY,
        )
    }
}

private data class HandlePolylines(
    val outer: FloatArray,
    val inner: FloatArray,
)

private fun FluidTriadSdfAtlasRequest.normalizedKey(): FluidTriadSdfCacheKey {
    val aspectBucket = (railAspect.coerceIn(MinimumRailAspect, MaximumRailAspect) *
        AspectBucketsPerUnit).roundToInt()
    val normalizedAspect = aspectBucket / AspectBucketsPerUnit.toFloat()
    val height = gridHeight.coerceIn(MinimumGridHeight, MaximumGridHeight)
    val normalizedDomainAspect = normalizedAspect * (DomainMaxX - DomainMinX) /
        (DomainMaxY - DomainMinY)
    val width = (height * normalizedDomainAspect).roundToInt()
        .coerceIn(MinimumGridWidth, MaximumGridWidth)
    return FluidTriadSdfCacheKey(
        aspectBucket = aspectBucket,
        width = width,
        height = height,
        frameCount = frameCount.coerceIn(MinimumFrameCount, MaximumFrameCount),
        modelVersion = ModelVersion,
    )
}

private fun flattenContour(contour: FluidTriadContourSpec): FloatArray = flattenCubics(
    segmentCount = FluidTriadVisualSpec.CubicSegmentCount,
    coordinateAt = contour::get,
)

private fun flattenLoop(loop: FluidTriadCubicLoopSpec): FloatArray = flattenCubics(
    segmentCount = FluidTriadCubicLoopSpec.CubicSegmentCount,
    coordinateAt = loop::get,
)

private inline fun flattenCubics(
    segmentCount: Int,
    coordinateAt: (Int) -> Float,
): FloatArray {
    val points = FloatArray(segmentCount * CurveSubdivisions * 2)
    var outputIndex = 0
    repeat(segmentCount) { segment ->
        val point = segment * CubicPointStride
        repeat(CurveSubdivisions) { subdivision ->
            val progress = subdivision / CurveSubdivisions.toFloat()
            points[outputIndex++] = cubic(
                coordinateAt(point * 2),
                coordinateAt((point + 1) * 2),
                coordinateAt((point + 2) * 2),
                coordinateAt((point + 3) * 2),
                progress,
            )
            points[outputIndex++] = cubic(
                coordinateAt(point * 2 + 1),
                coordinateAt((point + 1) * 2 + 1),
                coordinateAt((point + 2) * 2 + 1),
                coordinateAt((point + 3) * 2 + 1),
                progress,
            )
        }
    }
    return points
}

private fun cubic(start: Float, first: Float, second: Float, end: Float, progress: Float): Float {
    val inverse = 1f - progress
    return inverse * inverse * inverse * start +
        Three * inverse * inverse * progress * first +
        Three * inverse * progress * progress * second +
        progress * progress * progress * end
}

private fun signedDistanceToPolygon(
    x: Float,
    y: Float,
    polygon: FloatArray,
    railAspect: Float,
): Float {
    var minimumSquaredDistance = Float.POSITIVE_INFINITY
    var inside = false
    val pointCount = polygon.size / 2
    var previousPoint = pointCount - 1
    for (point in 0 until pointCount) {
        val startX = polygon[previousPoint * 2]
        val startY = polygon[previousPoint * 2 + 1]
        val endX = polygon[point * 2]
        val endY = polygon[point * 2 + 1]
        minimumSquaredDistance = minOf(
            minimumSquaredDistance,
            squaredDistanceToSegment(x, y, startX, startY, endX, endY, railAspect),
        )
        if ((startY > y) != (endY > y)) {
            val intersectionX = (endX - startX) * (y - startY) / (endY - startY) + startX
            if (x < intersectionX) inside = !inside
        }
        previousPoint = point
    }
    val distance = sqrt(minimumSquaredDistance)
    return if (inside) -distance else distance
}

private fun squaredDistanceToSegment(
    x: Float,
    y: Float,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    railAspect: Float,
): Float {
    val pointX = x * railAspect
    val physicalStartX = startX * railAspect
    val physicalEndX = endX * railAspect
    val segmentX = physicalEndX - physicalStartX
    val segmentY = endY - startY
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val projection = if (lengthSquared == 0f) {
        0f
    } else {
        ((pointX - physicalStartX) * segmentX + (y - startY) * segmentY) / lengthSquared
    }.coerceIn(0f, 1f)
    val nearestX = physicalStartX + segmentX * projection
    val nearestY = startY + segmentY * projection
    val deltaX = pointX - nearestX
    val deltaY = y - nearestY
    return deltaX * deltaX + deltaY * deltaY
}

private fun sampleField(
    values: FloatArray,
    layout: FluidTriadSdfFieldLayout,
    x: Float,
    y: Float,
): Float {
    val clampedX = x.coerceIn(layout.minX, layout.maxX)
    val clampedY = y.coerceIn(layout.minY, layout.maxY)
    val gridX = (clampedX - layout.minX) / layout.xStep
    val gridY = (clampedY - layout.minY) / layout.yStep
    val left = floor(gridX).toInt().coerceIn(0, layout.width - 1)
    val top = floor(gridY).toInt().coerceIn(0, layout.height - 1)
    val right = (left + 1).coerceAtMost(layout.width - 1)
    val bottom = (top + 1).coerceAtMost(layout.height - 1)
    val horizontal = gridX - left
    val vertical = gridY - top
    val topValue = lerp(
        values[layout.index(left, top)],
        values[layout.index(right, top)],
        horizontal,
    )
    val bottomValue = lerp(
        values[layout.index(left, bottom)],
        values[layout.index(right, bottom)],
        horizontal,
    )
    val edgeDistance = hypot(
        (x - clampedX) * layout.railAspect,
        y - clampedY,
    )
    return (lerp(topValue, bottomValue, vertical) + edgeDistance)
        .coerceIn(-layout.distanceClamp, layout.distanceClamp)
}

private fun targetIndex(target: CalculatorQuantityTarget): Int = when (target) {
    CalculatorQuantityTarget.COFFEE -> 0
    CalculatorQuantityTarget.WATER_IN -> 1
    CalculatorQuantityTarget.IN_CUP -> 2
}

private fun routeIndex(
    source: CalculatorQuantityTarget,
    destination: CalculatorQuantityTarget,
): Int = targetIndex(source) * TargetCount + targetIndex(destination)

private fun easeOutCubic(value: Float): Float {
    val normalized = value.coerceIn(0f, 1f)
    val inverse = 1f - normalized
    return 1f - inverse * inverse * inverse
}

private fun midpoint(first: Float, second: Float): Float = first + (second - first) * Half

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private val Targets = arrayOf(
    CalculatorQuantityTarget.COFFEE,
    CalculatorQuantityTarget.WATER_IN,
    CalculatorQuantityTarget.IN_CUP,
)

private const val TargetCount = 3
private const val RouteSlotCount = TargetCount * TargetCount
private const val ModelVersion = 1
private const val AspectBucketsPerUnit = 16
private const val DefaultGridHeight = 48
private const val DefaultFrameCount = 14
private const val MinimumGridHeight = 24
private const val MaximumGridHeight = 72
private const val MinimumGridWidth = 96
private const val MaximumGridWidth = 384
private const val MinimumFrameCount = 12
private const val MaximumFrameCount = 16
private const val MinimumCyclicFrameCount = 4
private const val WaterIdleFrameCount = 8
private const val MinimumRailAspect = 2f
private const val MaximumRailAspect = 8f
private const val MaximumCacheEntries = 2
private const val CacheLoadFactor = 0.75f
private const val DomainMinX = -0.05f
private const val DomainMaxX = 1.10f
private const val DomainMinY = -0.05f
private const val DomainMaxY = 1.05f
private const val DistanceClamp = 0.35f
private const val CurveSubdivisions = 12
private const val CubicPointStride = 3
private const val SurfaceTensionPassCount = 2
private const val SurfaceTensionStrength = 0.18f
private const val NeighborAverage = 0.25f
private const val WaterFieldLeft = 0.308f
private const val WaterFieldRight = 0.690f
private const val WaterUpperResponseEnd = 0.46f
private const val WaterLowerResponseStart = 0.54f
private const val WaterLowerResponse = -0.30f
private const val WaterCenterResponse = 0.10f
private const val WaterGustAmplitude = 0.014f
private const val WaterGustTravel = 0.72f
private const val TwoPi = (PI * 2.0).toFloat()
private const val Three = 3f
private const val Half = 0.5f
