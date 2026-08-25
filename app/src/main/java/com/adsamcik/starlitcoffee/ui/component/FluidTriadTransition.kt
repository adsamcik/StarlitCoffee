package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Immutable, matched-topology contour used by one rendered selected material mass.
 *
 * Coordinates use the same logical coordinate space as [FluidTriadVisualSpec]. Callers receive
 * values through indexed access or an explicit copy, so a transition snapshot cannot be mutated
 * by the renderer while an animation is in flight.
 */
internal class FluidTriadFrameContour private constructor(
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

    fun xAt(pointIndex: Int): Float = coordinates[pointIndex * CoordinateStride]

    fun yAt(pointIndex: Int): Float = coordinates[pointIndex * CoordinateStride + 1]

    fun minX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = true)

    fun maxX(): Float = coordinateExtremum(startIndex = 0, selectMinimum = false)

    fun minY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = true)

    fun maxY(): Float = coordinateExtremum(startIndex = 1, selectMinimum = false)

    fun copyInto(destination: FloatArray) {
        require(destination.size >= coordinates.size)
        coordinates.copyInto(destination)
    }

    override fun equals(other: Any?): Boolean =
        other is FluidTriadFrameContour && coordinates.contentEquals(other.coordinates)

    override fun hashCode(): Int = coordinates.contentHashCode()

    override fun toString(): String =
        "FluidTriadFrameContour(points=${FluidTriadVisualSpec.ContourPointCount})"

    private fun coordinateExtremum(startIndex: Int, selectMinimum: Boolean): Float {
        var result = coordinates[startIndex]
        var index = startIndex + CoordinateStride
        while (index < coordinates.size) {
            val candidate = coordinates[index]
            result = if (selectMinimum) minOf(result, candidate) else maxOf(result, candidate)
            index += CoordinateStride
        }
        return result
    }

    companion object {
        fun from(spec: FluidTriadContourSpec): FluidTriadFrameContour {
            val coordinates = FloatArray(spec.coordinateCount)
            spec.copyInto(coordinates)
            return FluidTriadFrameContour(coordinates)
        }

        fun interpolate(
            source: FluidTriadFrameContour,
            destination: FluidTriadFrameContour,
            fraction: Float,
        ): FluidTriadFrameContour {
            val clampedFraction = fraction.coerceIn(0f, 1f)
            if (clampedFraction == 0f) return source
            if (clampedFraction == 1f) return destination

            val coordinates = FloatArray(source.coordinateCount)
            for (index in coordinates.indices) {
                coordinates[index] = lerp(source[index], destination[index], clampedFraction)
            }
            close(coordinates)
            return FluidTriadFrameContour(coordinates)
        }

        /**
         * Produces a single traveling contour with a small direction-aware leading-edge bias.
         * The bias stretches the material while it moves without creating a second surface.
         */
        fun travelingBetween(
            source: FluidTriadFrameContour,
            destination: FluidTriadFrameContour,
            fraction: Float,
        ): FluidTriadFrameContour {
            val clampedFraction = fraction.coerceIn(0f, 1f)
            if (clampedFraction == 0f) return source
            if (clampedFraction == 1f) return destination

            val sourceMinX = source.minX()
            val sourceMaxX = source.maxX()
            val sourceCenterX = (sourceMinX + sourceMaxX) * Half
            val destinationCenterX = (destination.minX() + destination.maxX()) * Half
            val sourceHalfWidth = maxOf((sourceMaxX - sourceMinX) * Half, MinimumHalfWidth)
            val direction = when {
                destinationCenterX > sourceCenterX -> 1f
                destinationCenterX < sourceCenterX -> -1f
                else -> 0f
            }
            val travelEnergy = sin((PI * clampedFraction).toFloat()) * LeadingEdgeBias
            val coordinates = FloatArray(source.coordinateCount)

            var coordinateIndex = 0
            while (coordinateIndex < coordinates.size) {
                val sourceX = source[coordinateIndex]
                val sourceSide = ((sourceX - sourceCenterX) / sourceHalfWidth).coerceIn(-1f, 1f)
                val localFraction = (
                    clampedFraction + direction * sourceSide * travelEnergy
                    ).coerceIn(0f, 1f)
                coordinates[coordinateIndex] = lerp(
                    sourceX,
                    destination[coordinateIndex],
                    localFraction,
                )
                coordinates[coordinateIndex + 1] = lerp(
                    source[coordinateIndex + 1],
                    destination[coordinateIndex + 1],
                    localFraction,
                )
                coordinateIndex += CoordinateStride
            }
            close(coordinates)
            return FluidTriadFrameContour(coordinates)
        }

        private fun close(coordinates: FloatArray) {
            coordinates[coordinates.lastIndex - 1] = coordinates[0]
            coordinates[coordinates.lastIndex] = coordinates[1]
        }

        private const val CoordinateStride = 2
        private const val Half = 0.5f
        private const val MinimumHalfWidth = 0.001f
        private const val LeadingEdgeBias = 0.085f
    }
}

/** Relative contribution of each semantic material or foreground inside the one moving mass. */
internal data class FluidTriadTargetWeights(
    val coffee: Float,
    val water: Float,
    val cup: Float,
) {
    init {
        require(coffee.isFinite() && water.isFinite() && cup.isFinite())
        require(coffee in 0f..1f && water in 0f..1f && cup in 0f..1f)
        require(abs(sum - 1f) <= WeightEpsilon)
    }

    val sum: Float get() = coffee + water + cup

    operator fun get(target: CalculatorQuantityTarget): Float = when (target) {
        CalculatorQuantityTarget.COFFEE -> coffee
        CalculatorQuantityTarget.WATER_IN -> water
        CalculatorQuantityTarget.IN_CUP -> cup
    }

    fun lerp(destination: FluidTriadTargetWeights, fraction: Float): FluidTriadTargetWeights {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        if (clampedFraction == 0f) return this
        if (clampedFraction == 1f) return destination
        return FluidTriadTargetWeights(
            coffee = lerp(coffee, destination.coffee, clampedFraction),
            water = lerp(water, destination.water, clampedFraction),
            cup = lerp(cup, destination.cup, clampedFraction),
        )
    }

    companion object {
        fun oneHot(target: CalculatorQuantityTarget): FluidTriadTargetWeights = when (target) {
            CalculatorQuantityTarget.COFFEE -> FluidTriadTargetWeights(1f, 0f, 0f)
            CalculatorQuantityTarget.WATER_IN -> FluidTriadTargetWeights(0f, 1f, 0f)
            CalculatorQuantityTarget.IN_CUP -> FluidTriadTargetWeights(0f, 0f, 1f)
        }

        private const val WeightEpsilon = 0.000_01f
    }
}

/** Palette contribution and stable relative elevation of the selected material mass. */
internal data class FluidTriadFrameMaterial(
    val weights: FluidTriadTargetWeights,
    val elevation: Float,
) {
    init {
        require(elevation.isFinite() && elevation >= 0f)
    }
}

/**
 * Foreground state rendered inside the moving contour.
 *
 * [weights] crossfade complete icon/label/value groups. Position and transforms travel with the
 * material, so outgoing content never remains behind in the source cell.
 */
internal data class FluidTriadFrameContent(
    val weights: FluidTriadTargetWeights,
    val anchorX: Float,
    val anchorY: Float,
    val groupScale: Float,
    val rotationDegrees: Float,
    val iconScale: Float,
) {
    init {
        require(anchorX.isFinite() && anchorY.isFinite())
        require(groupScale.isFinite() && groupScale > 0f)
        require(rotationDegrees.isFinite())
        require(iconScale.isFinite() && iconScale > 0f)
    }
}

/** Material-specific detail activations, already resolved to the current transition instant. */
internal data class FluidTriadFrameEffects(
    val coffeeCrease: Float,
    val waterMotion: Float,
    val cupRim: Float,
    val cupHandle: Float,
) {
    init {
        require(coffeeCrease in 0f..1f)
        require(waterMotion in 0f..1f)
        require(cupRim in 0f..1f)
        require(cupHandle in 0f..1f)
    }
}

/**
 * Reusable primitive rendering state for a resolved transition instant.
 *
 * Unlike [FluidTriadTransitionFrame], this holder is deliberately mutable: a renderer creates
 * one instance and asks [FluidTriadFrameResolver.resolveMetadata] to overwrite it on every draw.
 * Keeping the three target weights and four effect activations as primitive fields avoids the
 * short-lived nested objects that the immutable authoring API creates for each intermediate
 * frame.
 */
internal class FluidTriadRenderMetadata {
    var materialCoffeeWeight: Float = 0f
        internal set
    var materialWaterWeight: Float = 0f
        internal set
    var materialCupWeight: Float = 0f
        internal set
    var materialElevation: Float = 0f
        internal set

    var contentCoffeeWeight: Float = 0f
        internal set
    var contentWaterWeight: Float = 0f
        internal set
    var contentCupWeight: Float = 0f
        internal set
    var contentAnchorX: Float = 0f
        internal set
    var contentAnchorY: Float = 0f
        internal set
    var contentGroupScale: Float = 1f
        internal set
    var contentRotationDegrees: Float = 0f
        internal set
    var contentIconScale: Float = 1f
        internal set

    var coffeeCrease: Float = 0f
        internal set
    var waterMotion: Float = 0f
        internal set
    var cupRim: Float = 0f
        internal set
    var cupHandle: Float = 0f
        internal set
    var stableTarget: CalculatorQuantityTarget? = null
        internal set

    fun materialWeight(target: CalculatorQuantityTarget): Float = when (target) {
        CalculatorQuantityTarget.COFFEE -> materialCoffeeWeight
        CalculatorQuantityTarget.WATER_IN -> materialWaterWeight
        CalculatorQuantityTarget.IN_CUP -> materialCupWeight
    }

    fun contentWeight(target: CalculatorQuantityTarget): Float = when (target) {
        CalculatorQuantityTarget.COFFEE -> contentCoffeeWeight
        CalculatorQuantityTarget.WATER_IN -> contentWaterWeight
        CalculatorQuantityTarget.IN_CUP -> contentCupWeight
    }
}

/** Complete immutable rendering input for one animation frame. */
internal data class FluidTriadTransitionFrame(
    val contour: FluidTriadFrameContour,
    val material: FluidTriadFrameMaterial,
    val content: FluidTriadFrameContent,
    val effects: FluidTriadFrameEffects,
    val stableTarget: CalculatorQuantityTarget?,
)

/** A cubic-smoothed timing window inside a transition's normalized elapsed time. */
internal data class FluidTriadProgressWindow(
    val startFraction: Float,
    val endFraction: Float,
) {
    init {
        require(startFraction.isFinite() && endFraction.isFinite())
        require(startFraction in 0f..1f)
        require(endFraction in 0f..1f)
        require(startFraction < endFraction)
    }

    fun resolve(progress: Float): Float {
        val linear = ((progress.coerceIn(0f, 1f) - startFraction) /
            (endFraction - startFraction)).coerceIn(0f, 1f)
        return linear * linear * (SmoothStepFactor - SmoothStepScale * linear)
    }

    private companion object {
        const val SmoothStepFactor = 3f
        const val SmoothStepScale = 2f
    }
}

/** Timing contract for geometry, material, foreground, and state-specific detail handoffs. */
internal data class FluidTriadTransitionTimingSpec(
    val durationMillis: Int,
    val materialMix: FluidTriadProgressWindow,
    val contentMix: FluidTriadProgressWindow,
    val outgoingEffectFade: FluidTriadProgressWindow,
    val incomingEffectReveal: FluidTriadProgressWindow,
    val outgoingCupHandleCollapse: FluidTriadProgressWindow,
    val incomingCupHandleReveal: FluidTriadProgressWindow,
) {
    init {
        require(durationMillis > 0)
    }
}

/**
 * Motion-study timing: 300 ms with an immediate semantic response and a deliberately late handle.
 *
 * Geometry already starts traveling at the first animation frame. Material, foreground, and the
 * outgoing detail now hand off with it instead of waiting for the middle of the transition. The
 * cup handle remains a finishing detail so the moving mass resolves into a mug rather than carrying
 * a fully formed handle across the selector.
 */
internal object FluidTriadTransitionTimings {
    val MaterialTravel = FluidTriadTransitionTimingSpec(
        durationMillis = 300,
        materialMix = FluidTriadProgressWindow(0.00f, 0.60f),
        contentMix = FluidTriadProgressWindow(0.04f, 0.54f),
        outgoingEffectFade = FluidTriadProgressWindow(0.00f, 0.34f),
        incomingEffectReveal = FluidTriadProgressWindow(0.16f, 0.68f),
        outgoingCupHandleCollapse = FluidTriadProgressWindow(0.00f, 0.10f),
        incomingCupHandleReveal = FluidTriadProgressWindow(0.72f, 1.00f),
    )
}

/** One of the six authored, matched-topology contour frames in a transition snapshot. */
internal data class FluidTriadContourKeyframe(
    val fraction: Float,
    val contour: FluidTriadFrameContour,
) {
    init {
        require(fraction.isFinite() && fraction in 0f..1f)
    }
}

/**
 * Immutable transition captured at an interaction boundary.
 *
 * Retargeting creates a new snapshot whose [sourceFrame] is the exact currently rendered frame,
 * which makes interrupted taps continuous instead of restarting from a semantic endpoint.
 */
internal class FluidTriadTransitionSnapshot internal constructor(
    val sourceFrame: FluidTriadTransitionFrame,
    val destinationFrame: FluidTriadTransitionFrame,
    val destinationTarget: CalculatorQuantityTarget,
    val timing: FluidTriadTransitionTimingSpec,
    contourKeyframes: Array<FluidTriadContourKeyframe>,
) {
    private val keyframes = contourKeyframes.copyOf()

    init {
        require(keyframes.size >= MinimumKeyframeCount)
        require(keyframes.first().fraction == 0f)
        require(keyframes.last().fraction == 1f)
        require(keyframes.first().contour == sourceFrame.contour)
        require(keyframes.last().contour == destinationFrame.contour)
        for (index in 1 until keyframes.size) {
            require(keyframes[index - 1].fraction < keyframes[index].fraction)
        }
    }

    val durationMillis: Int get() = timing.durationMillis

    val contourKeyframeCount: Int get() = keyframes.size

    fun contourKeyframeAt(index: Int): FluidTriadContourKeyframe = keyframes[index]

    private companion object {
        const val MinimumKeyframeCount = 2
    }
}

/** Resolves one selected material mass from semantic endpoints or an interrupted frame. */
internal object FluidTriadFrameResolver {
    private val coffeeEndpoint = createEndpoint(CalculatorQuantityTarget.COFFEE)
    private val waterEndpoint = createEndpoint(CalculatorQuantityTarget.WATER_IN)
    private val cupEndpoint = createEndpoint(CalculatorQuantityTarget.IN_CUP)

    fun endpoint(target: CalculatorQuantityTarget): FluidTriadTransitionFrame = when (target) {
        CalculatorQuantityTarget.COFFEE -> coffeeEndpoint
        CalculatorQuantityTarget.WATER_IN -> waterEndpoint
        CalculatorQuantityTarget.IN_CUP -> cupEndpoint
    }

    /** Copies one stable endpoint into a caller-owned holder without creating a transition. */
    fun resolveEndpointMetadata(
        target: CalculatorQuantityTarget,
        out: FluidTriadRenderMetadata,
    ): FluidTriadRenderMetadata = copyMetadata(endpoint(target), out)

    fun between(
        sourceTarget: CalculatorQuantityTarget,
        destinationTarget: CalculatorQuantityTarget,
        timing: FluidTriadTransitionTimingSpec = FluidTriadTransitionTimings.MaterialTravel,
    ): FluidTriadTransitionSnapshot = retarget(
        sourceFrame = endpoint(sourceTarget),
        destinationTarget = destinationTarget,
        timing = timing,
    )

    fun retarget(
        sourceFrame: FluidTriadTransitionFrame,
        destinationTarget: CalculatorQuantityTarget,
        timing: FluidTriadTransitionTimingSpec = FluidTriadTransitionTimings.MaterialTravel,
    ): FluidTriadTransitionSnapshot {
        val destinationFrame = endpoint(destinationTarget)
        val keyframes = Array(ContourKeyframeFractions.size) { index ->
            val timelineFraction = ContourKeyframeFractions[index]
            val contour = when (index) {
                0 -> sourceFrame.contour
                ContourKeyframeFractions.lastIndex -> destinationFrame.contour
                else -> FluidTriadFrameContour.travelingBetween(
                    source = sourceFrame.contour,
                    destination = destinationFrame.contour,
                    fraction = ContourTravelFractions[index],
                )
            }
            FluidTriadContourKeyframe(timelineFraction, contour)
        }
        return FluidTriadTransitionSnapshot(
            sourceFrame = sourceFrame,
            destinationFrame = destinationFrame,
            destinationTarget = destinationTarget,
            timing = timing,
            contourKeyframes = keyframes,
        )
    }

    /**
     * Resolves a raw linear elapsed [progress]. The authored keyframes encode the ease-out travel,
     * so callers should not apply a second easing curve to their animation clock.
     */
    fun resolve(
        snapshot: FluidTriadTransitionSnapshot,
        progress: Float,
    ): FluidTriadTransitionFrame {
        require(progress.isFinite())
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress == 0f) return snapshot.sourceFrame
        if (clampedProgress == 1f) return snapshot.destinationFrame
        if (snapshot.sourceFrame === snapshot.destinationFrame) return snapshot.sourceFrame

        val source = snapshot.sourceFrame
        val destination = snapshot.destinationFrame
        val travelFraction = resolveKeyframedValue(
            progress = clampedProgress,
            values = ContourTravelFractions,
        )
        val materialFraction = snapshot.timing.materialMix.resolve(clampedProgress)
        val contentFraction = snapshot.timing.contentMix.resolve(clampedProgress)

        return FluidTriadTransitionFrame(
            contour = resolveContour(snapshot, clampedProgress),
            material = FluidTriadFrameMaterial(
                weights = source.material.weights.lerp(
                    destination.material.weights,
                    materialFraction,
                ),
                elevation = lerp(
                    source.material.elevation,
                    destination.material.elevation,
                    travelFraction,
                ),
            ),
            content = FluidTriadFrameContent(
                weights = source.content.weights.lerp(
                    destination.content.weights,
                    contentFraction,
                ),
                anchorX = lerp(source.content.anchorX, destination.content.anchorX, travelFraction),
                anchorY = lerp(source.content.anchorY, destination.content.anchorY, travelFraction),
                groupScale = lerp(
                    source.content.groupScale,
                    destination.content.groupScale,
                    travelFraction,
                ),
                rotationDegrees = lerp(
                    source.content.rotationDegrees,
                    destination.content.rotationDegrees,
                    travelFraction,
                ),
                iconScale = lerp(
                    source.content.iconScale,
                    destination.content.iconScale,
                    travelFraction,
                ),
            ),
            effects = resolveEffects(
                source = source.effects,
                destination = destination.effects,
                timing = snapshot.timing,
                progress = clampedProgress,
            ),
            stableTarget = null,
        )
    }

    /**
     * Resolves all non-contour rendering values into a caller-owned holder.
     *
     * This follows the same timing and endpoint rules as [resolve], including clamping and the
     * cached identity used by same-target transitions, while performing no per-call allocations.
     */
    fun resolveMetadata(
        snapshot: FluidTriadTransitionSnapshot,
        progress: Float,
        out: FluidTriadRenderMetadata,
    ): FluidTriadRenderMetadata {
        require(progress.isFinite())
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress == 0f) return copyMetadata(snapshot.sourceFrame, out)
        if (clampedProgress == 1f) return copyMetadata(snapshot.destinationFrame, out)
        if (snapshot.sourceFrame === snapshot.destinationFrame) {
            return copyMetadata(snapshot.sourceFrame, out)
        }

        val source = snapshot.sourceFrame
        val destination = snapshot.destinationFrame
        val travelFraction = resolveKeyframedValue(
            progress = clampedProgress,
            values = ContourTravelFractions,
        )
        val materialFraction = snapshot.timing.materialMix.resolve(clampedProgress)
        val contentFraction = snapshot.timing.contentMix.resolve(clampedProgress)
        resolveMaterialMetadata(source, destination, materialFraction, travelFraction, out)
        resolveContentMetadata(source, destination, contentFraction, travelFraction, out)
        resolveEffectsMetadata(source, destination, snapshot.timing, clampedProgress, out)
        out.stableTarget = null
        return out
    }

    /** Copies the resolved matched-topology contour into a caller-owned array. */
    fun resolveContourInto(
        snapshot: FluidTriadTransitionSnapshot,
        progress: Float,
        out: FloatArray,
    ) {
        require(progress.isFinite())
        require(out.size >= FluidTriadVisualSpec.ContourCoordinateCount)
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress == 0f || snapshot.sourceFrame === snapshot.destinationFrame) {
            snapshot.sourceFrame.contour.copyInto(out)
            return
        }
        if (clampedProgress == 1f) {
            snapshot.destinationFrame.contour.copyInto(out)
            return
        }

        var upperIndex = 1
        while (
            upperIndex < snapshot.contourKeyframeCount - 1 &&
            clampedProgress > snapshot.contourKeyframeAt(upperIndex).fraction
        ) {
            upperIndex += 1
        }
        val lower = snapshot.contourKeyframeAt(upperIndex - 1)
        val upper = snapshot.contourKeyframeAt(upperIndex)
        if (clampedProgress == lower.fraction) {
            lower.contour.copyInto(out)
            return
        }
        if (clampedProgress == upper.fraction) {
            upper.contour.copyInto(out)
            return
        }

        val localFraction = (clampedProgress - lower.fraction) /
            (upper.fraction - lower.fraction)
        val coordinateCount = FluidTriadVisualSpec.ContourCoordinateCount
        for (index in 0 until coordinateCount) {
            out[index] = lerp(lower.contour[index], upper.contour[index], localFraction)
        }
        out[coordinateCount - CoordinateStride] = out[0]
        out[coordinateCount - 1] = out[1]
    }

    private fun createEndpoint(target: CalculatorQuantityTarget): FluidTriadTransitionFrame {
        val panel = FluidTriadVisualSpec.panel(target)
        val weights = FluidTriadTargetWeights.oneHot(target)
        return FluidTriadTransitionFrame(
            contour = FluidTriadFrameContour.from(panel.selectedContour),
            material = FluidTriadFrameMaterial(
                weights = weights,
                elevation = StableMaterialElevation,
            ),
            content = FluidTriadFrameContent(
                weights = weights,
                anchorX = panel.content.anchorX,
                anchorY = panel.content.anchorY,
                groupScale = panel.content.endpointGroupScale,
                rotationDegrees = panel.content.endpointRotationDegrees,
                iconScale = panel.content.endpointIconScale,
            ),
            effects = FluidTriadFrameEffects(
                coffeeCrease = if (target == CalculatorQuantityTarget.COFFEE) 1f else 0f,
                waterMotion = if (target == CalculatorQuantityTarget.WATER_IN) 1f else 0f,
                cupRim = if (target == CalculatorQuantityTarget.IN_CUP) 1f else 0f,
                cupHandle = if (target == CalculatorQuantityTarget.IN_CUP) 1f else 0f,
            ),
            stableTarget = target,
        )
    }

    private fun resolveContour(
        snapshot: FluidTriadTransitionSnapshot,
        progress: Float,
    ): FluidTriadFrameContour {
        var upperIndex = 1
        while (
            upperIndex < snapshot.contourKeyframeCount - 1 &&
            progress > snapshot.contourKeyframeAt(upperIndex).fraction
        ) {
            upperIndex += 1
        }
        val lower = snapshot.contourKeyframeAt(upperIndex - 1)
        val upper = snapshot.contourKeyframeAt(upperIndex)
        if (progress == lower.fraction) return lower.contour
        if (progress == upper.fraction) return upper.contour
        val localFraction = (progress - lower.fraction) / (upper.fraction - lower.fraction)
        return FluidTriadFrameContour.interpolate(lower.contour, upper.contour, localFraction)
    }

    private fun resolveEffects(
        source: FluidTriadFrameEffects,
        destination: FluidTriadFrameEffects,
        timing: FluidTriadTransitionTimingSpec,
        progress: Float,
    ): FluidTriadFrameEffects = FluidTriadFrameEffects(
        coffeeCrease = resolveEffect(
            source.coffeeCrease,
            destination.coffeeCrease,
            timing,
            progress,
        ),
        waterMotion = resolveEffect(
            source.waterMotion,
            destination.waterMotion,
            timing,
            progress,
        ),
        cupRim = resolveEffect(
            source.cupRim,
            destination.cupRim,
            timing,
            progress,
        ),
        cupHandle = resolveEffect(
            source.cupHandle,
            destination.cupHandle,
            timing,
            progress,
            isCupHandle = true,
        ),
    )

    private fun resolveEffect(
        source: Float,
        destination: Float,
        timing: FluidTriadTransitionTimingSpec,
        progress: Float,
        isCupHandle: Boolean = false,
    ): Float {
        if (source == destination) return source
        val window = if (destination > source) {
            if (isCupHandle) timing.incomingCupHandleReveal else timing.incomingEffectReveal
        } else {
            if (isCupHandle) timing.outgoingCupHandleCollapse else timing.outgoingEffectFade
        }
        return lerp(source, destination, window.resolve(progress))
    }

    private fun copyMetadata(
        frame: FluidTriadTransitionFrame,
        out: FluidTriadRenderMetadata,
    ): FluidTriadRenderMetadata {
        out.materialCoffeeWeight = frame.material.weights.coffee
        out.materialWaterWeight = frame.material.weights.water
        out.materialCupWeight = frame.material.weights.cup
        out.materialElevation = frame.material.elevation
        out.contentCoffeeWeight = frame.content.weights.coffee
        out.contentWaterWeight = frame.content.weights.water
        out.contentCupWeight = frame.content.weights.cup
        out.contentAnchorX = frame.content.anchorX
        out.contentAnchorY = frame.content.anchorY
        out.contentGroupScale = frame.content.groupScale
        out.contentRotationDegrees = frame.content.rotationDegrees
        out.contentIconScale = frame.content.iconScale
        out.coffeeCrease = frame.effects.coffeeCrease
        out.waterMotion = frame.effects.waterMotion
        out.cupRim = frame.effects.cupRim
        out.cupHandle = frame.effects.cupHandle
        out.stableTarget = frame.stableTarget
        return out
    }

    private fun resolveMaterialMetadata(
        source: FluidTriadTransitionFrame,
        destination: FluidTriadTransitionFrame,
        materialFraction: Float,
        travelFraction: Float,
        out: FluidTriadRenderMetadata,
    ) {
        out.materialCoffeeWeight = resolveWeight(
            source.material.weights.coffee,
            destination.material.weights.coffee,
            materialFraction,
        )
        out.materialWaterWeight = resolveWeight(
            source.material.weights.water,
            destination.material.weights.water,
            materialFraction,
        )
        out.materialCupWeight = resolveWeight(
            source.material.weights.cup,
            destination.material.weights.cup,
            materialFraction,
        )
        out.materialElevation = lerp(
            source.material.elevation,
            destination.material.elevation,
            travelFraction,
        )
    }

    private fun resolveContentMetadata(
        source: FluidTriadTransitionFrame,
        destination: FluidTriadTransitionFrame,
        contentFraction: Float,
        travelFraction: Float,
        out: FluidTriadRenderMetadata,
    ) {
        out.contentCoffeeWeight = resolveWeight(
            source.content.weights.coffee,
            destination.content.weights.coffee,
            contentFraction,
        )
        out.contentWaterWeight = resolveWeight(
            source.content.weights.water,
            destination.content.weights.water,
            contentFraction,
        )
        out.contentCupWeight = resolveWeight(
            source.content.weights.cup,
            destination.content.weights.cup,
            contentFraction,
        )
        out.contentAnchorX = lerp(
            source.content.anchorX,
            destination.content.anchorX,
            travelFraction,
        )
        out.contentAnchorY = lerp(
            source.content.anchorY,
            destination.content.anchorY,
            travelFraction,
        )
        out.contentGroupScale = lerp(
            source.content.groupScale,
            destination.content.groupScale,
            travelFraction,
        )
        out.contentRotationDegrees = lerp(
            source.content.rotationDegrees,
            destination.content.rotationDegrees,
            travelFraction,
        )
        out.contentIconScale = lerp(
            source.content.iconScale,
            destination.content.iconScale,
            travelFraction,
        )
    }

    private fun resolveEffectsMetadata(
        source: FluidTriadTransitionFrame,
        destination: FluidTriadTransitionFrame,
        timing: FluidTriadTransitionTimingSpec,
        progress: Float,
        out: FluidTriadRenderMetadata,
    ) {
        out.coffeeCrease = resolveEffect(
            source.effects.coffeeCrease,
            destination.effects.coffeeCrease,
            timing,
            progress,
        )
        out.waterMotion = resolveEffect(
            source.effects.waterMotion,
            destination.effects.waterMotion,
            timing,
            progress,
        )
        out.cupRim = resolveEffect(
            source.effects.cupRim,
            destination.effects.cupRim,
            timing,
            progress,
        )
        out.cupHandle = resolveEffect(
            source.effects.cupHandle,
            destination.effects.cupHandle,
            timing,
            progress,
            isCupHandle = true,
        )
    }

    private fun resolveWeight(source: Float, destination: Float, fraction: Float): Float = when {
        fraction == 0f -> source
        fraction == 1f -> destination
        else -> lerp(source, destination, fraction)
    }

    private fun resolveKeyframedValue(progress: Float, values: FloatArray): Float {
        var upperIndex = 1
        while (
            upperIndex < ContourKeyframeFractions.lastIndex &&
            progress > ContourKeyframeFractions[upperIndex]
        ) {
            upperIndex += 1
        }
        val lowerFraction = ContourKeyframeFractions[upperIndex - 1]
        val upperFraction = ContourKeyframeFractions[upperIndex]
        val localFraction = (progress - lowerFraction) / (upperFraction - lowerFraction)
        return lerp(values[upperIndex - 1], values[upperIndex], localFraction)
    }

    private val ContourKeyframeFractions = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
    private val ContourTravelFractions = FloatArray(ContourKeyframeFractions.size) { index ->
        easeOutCubic(ContourKeyframeFractions[index])
    }
    private const val CoordinateStride = 2
    private const val StableMaterialElevation = 1f
}

private fun easeOutCubic(fraction: Float): Float {
    val inverse = 1f - fraction.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
