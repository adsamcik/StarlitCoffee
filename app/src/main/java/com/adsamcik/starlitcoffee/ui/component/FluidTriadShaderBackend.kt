package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader.TileMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Logical area represented by an SDF grid.
 *
 * X is expressed as a fraction of the rail width and Y as a fraction of the full component
 * height. The domain may extend beyond 0..1 so the selected silhouette (notably the cup handle)
 * can outgrow the neutral rail. SDF distances use component-height units and are negative inside.
 */
internal data class FluidTriadSdfLogicalDomain(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right > left)
        require(bottom > top)
    }
}

/** Immutable, cached transition data consumed by the API 33 shader renderer. */
internal interface FluidTriadNormalizedSdfFrameSource {
    val gridWidth: Int
    val gridHeight: Int
    val frameCount: Int
    val logicalDomain: FluidTriadSdfLogicalDomain

    /** Returns one immutable, row-major grid containing [gridWidth] * [gridHeight] distances. */
    fun signedDistances(frameIndex: Int): FloatArray
}

/** Stable, zero-copy adapters from the cached solver model to the bitmap-atlas encoder. */
internal fun FluidTriadSdfRoute.asShaderFrameSource(): FluidTriadNormalizedSdfFrameSource =
    FluidTriadShaderFrameSourceAdapters.forRoute(this)

internal fun FluidTriadSdfCyclicRoute.asShaderFrameSource(): FluidTriadNormalizedSdfFrameSource =
    FluidTriadShaderFrameSourceAdapters.forCyclicRoute(this)

internal fun FluidTriadSdfFrame.asShaderFrameSource(): FluidTriadNormalizedSdfFrameSource =
    FluidTriadShaderFrameSourceAdapters.forEndpoint(this)

private object FluidTriadShaderFrameSourceAdapters {
    private val lock = Any()
    private val adapters = WeakHashMap<Any, WeakReference<FluidTriadNormalizedSdfFrameSource>>()

    fun forRoute(route: FluidTriadSdfRoute): FluidTriadNormalizedSdfFrameSource =
        stableAdapter(route) { FluidTriadRouteShaderFrameSource(route) }

    fun forCyclicRoute(route: FluidTriadSdfCyclicRoute): FluidTriadNormalizedSdfFrameSource =
        stableAdapter(route) { FluidTriadCyclicShaderFrameSource(route) }

    fun forEndpoint(frame: FluidTriadSdfFrame): FluidTriadNormalizedSdfFrameSource =
        stableAdapter(frame) { FluidTriadEndpointShaderFrameSource(frame) }

    private fun stableAdapter(
        key: Any,
        create: () -> FluidTriadNormalizedSdfFrameSource,
    ): FluidTriadNormalizedSdfFrameSource = synchronized(lock) {
        adapters[key]?.get() ?: create().also { adapters[key] = WeakReference(it) }
    }
}

private abstract class FluidTriadShaderFrameSource(
    layout: FluidTriadSdfFieldLayout,
) : FluidTriadNormalizedSdfFrameSource {
    final override val gridWidth: Int = layout.width
    final override val gridHeight: Int = layout.height
    final override val logicalDomain = FluidTriadSdfLogicalDomain(
        left = layout.minX,
        top = layout.minY,
        right = layout.maxX,
        bottom = layout.maxY,
    )
}

private class FluidTriadRouteShaderFrameSource(
    private val route: FluidTriadSdfRoute,
) : FluidTriadShaderFrameSource(route.frame(0).layout) {
    override val frameCount: Int = route.frameCount

    override fun signedDistances(frameIndex: Int): FloatArray =
        route.frame(frameIndex).valuesForSolver()
}

private class FluidTriadCyclicShaderFrameSource(
    private val route: FluidTriadSdfCyclicRoute,
) : FluidTriadShaderFrameSource(route.frame(0).layout) {
    override val frameCount: Int = route.frameCount

    override fun signedDistances(frameIndex: Int): FloatArray =
        route.frame(frameIndex).valuesForSolver()
}

private class FluidTriadEndpointShaderFrameSource(
    private val frame: FluidTriadSdfFrame,
) : FluidTriadShaderFrameSource(frame.layout) {
    override val frameCount: Int = 1

    override fun signedDistances(frameIndex: Int): FloatArray {
        require(frameIndex == 0)
        return frame.valuesForSolver()
    }
}

/**
 * Two-frame source created only when a running morph is retargeted.
 *
 * Frame zero is the exact field that the shader displayed on the preceding frame; frame one is
 * the new endpoint. The pair is packed once and then sampled for the rest of the interruption, so
 * rapid taps stay on the integral shader path without running a solver in the frame loop.
 */
internal class FluidTriadInterruptedShaderFrameSource private constructor(
    override val gridWidth: Int,
    override val gridHeight: Int,
    override val logicalDomain: FluidTriadSdfLogicalDomain,
    private val sourceValues: FloatArray,
    private val destinationValues: FloatArray,
    val sourceCentroidX: Float,
    val sourceCentroidY: Float,
    val destinationCentroidX: Float,
    val destinationCentroidY: Float,
) : FluidTriadNormalizedSdfFrameSource {
    override val frameCount: Int = INTERRUPTED_FRAME_COUNT

    override fun signedDistances(frameIndex: Int): FloatArray = when (frameIndex) {
        0 -> sourceValues
        1 -> destinationValues
        else -> throw IndexOutOfBoundsException("Interrupted SDF frame $frameIndex")
    }

    internal companion object {
        private const val INTERRUPTED_FRAME_COUNT = 2

        fun capture(
            activeSource: FluidTriadNormalizedSdfFrameSource,
            activeBlend: FluidTriadShaderFrameBlend,
            destination: FluidTriadNormalizedSdfFrameSource,
        ): FluidTriadInterruptedShaderFrameSource {
            require(activeSource.gridWidth == destination.gridWidth)
            require(activeSource.gridHeight == destination.gridHeight)
            require(activeSource.logicalDomain == destination.logicalDomain)
            require(activeBlend.firstFrame in 0 until activeSource.frameCount)
            require(activeBlend.secondFrame in 0 until activeSource.frameCount)
            val captured = if (activeSource is FluidTriadInterruptedShaderFrameSource) {
                activeSource.captureDisplayedField(activeBlend)
            } else {
                val first = activeSource.signedDistances(activeBlend.firstFrame)
                val second = activeSource.signedDistances(activeBlend.secondFrame)
                blendFields(first, second, activeBlend.fraction)
            }
            val destinationValues = destination.signedDistances(0)
            val sourceCentroid = fieldCentroid(
                values = captured,
                width = activeSource.gridWidth,
                height = activeSource.gridHeight,
                domain = activeSource.logicalDomain,
            )
            val destinationCentroid = fieldCentroid(
                values = destinationValues,
                width = destination.gridWidth,
                height = destination.gridHeight,
                domain = destination.logicalDomain,
            )
            return FluidTriadInterruptedShaderFrameSource(
                gridWidth = activeSource.gridWidth,
                gridHeight = activeSource.gridHeight,
                logicalDomain = activeSource.logicalDomain,
                sourceValues = captured,
                destinationValues = destinationValues,
                sourceCentroidX = sourceCentroid.first,
                sourceCentroidY = sourceCentroid.second,
                destinationCentroidX = destinationCentroid.first,
                destinationCentroidY = destinationCentroid.second,
            )
        }

        private fun blendFields(first: FloatArray, second: FloatArray, fraction: Float): FloatArray {
            require(first.size == second.size)
            if (first === second || fraction <= 0f) return first
            if (fraction >= 1f) return second
            return FloatArray(first.size) { index ->
                first[index] + (second[index] - first[index]) * fraction
            }
        }

        /**
         * Reconstructs the field produced by the interrupted AGSL branch at [activeBlend].
         *
         * A nested retarget cannot linearly mix the two stored grids: AGSL first transports both
         * grids toward an eased moving centroid, samples them at shifted logical coordinates, and
         * only then blends them. Mirroring those operations here makes the next interruption's
         * frame zero equal to the field that was visible immediately before the tap.
         */
        private fun FluidTriadInterruptedShaderFrameSource.captureDisplayedField(
            activeBlend: FluidTriadShaderFrameBlend,
        ): FloatArray {
            val first = signedDistances(activeBlend.firstFrame)
            val second = signedDistances(activeBlend.secondFrame)
            val fraction = activeBlend.fraction
            if (fraction <= 0f) return first
            if (fraction >= 1f) return second

            val inverse = 1f - fraction
            val motion = 1f - inverse * inverse * inverse
            val movingCentroidX = lerp(sourceCentroidX, destinationCentroidX, motion)
            val movingCentroidY = lerp(sourceCentroidY, destinationCentroidY, motion)
            val sourceOffsetX = movingCentroidX - sourceCentroidX
            val sourceOffsetY = movingCentroidY - sourceCentroidY
            val destinationOffsetX = movingCentroidX - destinationCentroidX
            val destinationOffsetY = movingCentroidY - destinationCentroidY
            val xStep = (logicalDomain.right - logicalDomain.left) / (gridWidth - 1)
            val yStep = (logicalDomain.bottom - logicalDomain.top) / (gridHeight - 1)
            val captured = FloatArray(gridWidth * gridHeight)

            for (row in 0 until gridHeight) {
                val logicalY = logicalDomain.top + row * yStep
                for (column in 0 until gridWidth) {
                    val logicalX = logicalDomain.left + column * xStep
                    val sourceDistance = sampleLogicalField(
                        values = first,
                        width = gridWidth,
                        height = gridHeight,
                        domain = logicalDomain,
                        x = logicalX - sourceOffsetX,
                        y = logicalY - sourceOffsetY,
                    )
                    val destinationDistance = sampleLogicalField(
                        values = second,
                        width = gridWidth,
                        height = gridHeight,
                        domain = logicalDomain,
                        x = logicalX - destinationOffsetX,
                        y = logicalY - destinationOffsetY,
                    )
                    captured[row * gridWidth + column] =
                        lerp(sourceDistance, destinationDistance, motion)
                }
            }
            return captured
        }

        private fun sampleLogicalField(
            values: FloatArray,
            width: Int,
            height: Int,
            domain: FluidTriadSdfLogicalDomain,
            x: Float,
            y: Float,
        ): Float {
            require(values.size == width * height)
            val normalizedX = ((x - domain.left) / (domain.right - domain.left)).coerceIn(0f, 1f)
            val normalizedY = ((y - domain.top) / (domain.bottom - domain.top)).coerceIn(0f, 1f)
            val gridX = normalizedX * (width - 1)
            val gridY = normalizedY * (height - 1)
            val leftColumn = floor(gridX).toInt().coerceIn(0, width - 1)
            val topRow = floor(gridY).toInt().coerceIn(0, height - 1)
            val rightColumn = (leftColumn + 1).coerceAtMost(width - 1)
            val bottomRow = (topRow + 1).coerceAtMost(height - 1)
            val horizontal = gridX - leftColumn
            val vertical = gridY - topRow
            val top = lerp(
                values[topRow * width + leftColumn],
                values[topRow * width + rightColumn],
                horizontal,
            )
            val bottom = lerp(
                values[bottomRow * width + leftColumn],
                values[bottomRow * width + rightColumn],
                horizontal,
            )
            return lerp(top, bottom, vertical)
        }

        private fun lerp(start: Float, stop: Float, fraction: Float): Float =
            start + (stop - start) * fraction

        private fun fieldCentroid(
            values: FloatArray,
            width: Int,
            height: Int,
            domain: FluidTriadSdfLogicalDomain,
        ): Pair<Float, Float> {
            require(values.size == width * height)
            var insideCount = 0
            var xMoment = 0f
            var yMoment = 0f
            val xStep = (domain.right - domain.left) / (width - 1)
            val yStep = (domain.bottom - domain.top) / (height - 1)
            values.indices.forEach { index ->
                if (values[index] < 0f) {
                    insideCount += 1
                    xMoment += domain.left + index.mod(width) * xStep
                    yMoment += domain.top + index / width * yStep
                }
            }
            if (insideCount == 0) return 0.5f to 0.5f
            return xMoment / insideCount to yMoment / insideCount
        }
    }
}

/** The only two cached atlas slices sampled for a rendered transition frame. */
internal data class FluidTriadShaderFrameBlend(
    val firstFrame: Int,
    val secondFrame: Int,
    val fraction: Float,
) {
    init {
        require(firstFrame >= 0)
        require(secondFrame >= 0)
        require(abs(secondFrame - firstFrame) <= 1) {
            "The shader may only interpolate adjacent cached SDF frames"
        }
        require(fraction.isFinite() && fraction in 0f..1f)
    }
}

/** Pixel geometry for the integral selector render pass. */
internal data class FluidTriadShaderGeometry(
    val componentWidth: Float,
    val componentHeight: Float,
    val railLeft: Float,
    val railTop: Float,
    val railWidth: Float,
    val railHeight: Float,
    val railOutlineWidth: Float,
    val isRtl: Boolean,
    val contactShadowOffsetY: Float = 1.5f,
    val contactShadowSoftness: Float = 2f,
    val penumbraShadowOffsetY: Float = 3f,
    val penumbraShadowSoftness: Float = 5f,
    val focusOutlineWidth: Float = 2f,
) {
    init {
        require(componentWidth > 0f && componentHeight > 0f)
        require(railWidth > 0f && railHeight > 0f)
        require(railOutlineWidth >= 0f)
        require(contactShadowOffsetY >= 0f && contactShadowSoftness > 0f)
        require(penumbraShadowOffsetY >= 0f && penumbraShadowSoftness > 0f)
        require(focusOutlineWidth >= 0f)
    }

    /** Aspect ratio required when requesting a matching normalized SDF source. */
    val sdfAspectRatio: Float
        get() = railWidth / componentHeight
}

/** Color inputs for one material state. Material colors may already be transition-weighted. */
internal data class FluidTriadShaderPalette(
    val neutralTop: Color,
    val neutralBottom: Color,
    val railOutline: Color,
    val materialTop: Color,
    val materialBottom: Color,
    val materialOutline: Color,
    val materialHighlight: Color,
    val shadow: Color = Color.Black.copy(alpha = 0.16f),
    val pressedOverlay: Color = Color.Black.copy(alpha = 0.12f),
    val focus: Color = Color.White.copy(alpha = 0.72f),
)

/** Shared bounds contract for the API 33 handle SDF and the authored Canvas handle. */
internal object FluidTriadShaderCupHandleSpec {
    const val BodyAttachmentX = 0.995f
    const val CupContentAnchorX = 5f / 6f
    const val AttachmentOffsetFromContent = BodyAttachmentX - CupContentAnchorX

    const val OuterCollapsedLeftOffset = -0.030f
    const val OuterCollapsedRightOffset = -0.030f
    const val OuterAttachedLeftOffset = -0.030f
    const val OuterAttachedRightOffset = 0.040f
    const val OuterTop = 0.270f
    const val OuterBottom = 0.780f

    const val InnerCollapsedLeftOffset = -0.030f
    const val InnerCollapsedRightOffset = -0.030f
    const val InnerAttachedLeftOffset = -0.020f
    const val InnerAttachedRightOffset = 0.027f
    const val InnerCollapsedTop = 0.360f
    const val InnerAttachedTop = 0.350f
    const val InnerCollapsedBottom = 0.690f
    const val InnerAttachedBottom = 0.705f

    fun attachmentX(contentAnchorX: Float): Float = contentAnchorX + AttachmentOffsetFromContent

    fun outerBounds(progress: Float, contentAnchorX: Float): FluidTriadShaderHandleBounds {
        val fraction = progress.coerceIn(0f, 1f)
        val attachment = attachmentX(contentAnchorX)
        return FluidTriadShaderHandleBounds(
            left = attachment + lerp(OuterCollapsedLeftOffset, OuterAttachedLeftOffset, fraction),
            top = OuterTop,
            right = attachment + lerp(OuterCollapsedRightOffset, OuterAttachedRightOffset, fraction),
            bottom = OuterBottom,
        )
    }

    fun innerBounds(progress: Float, contentAnchorX: Float): FluidTriadShaderHandleBounds {
        val fraction = progress.coerceIn(0f, 1f)
        val attachment = attachmentX(contentAnchorX)
        return FluidTriadShaderHandleBounds(
            left = attachment + lerp(InnerCollapsedLeftOffset, InnerAttachedLeftOffset, fraction),
            top = lerp(InnerCollapsedTop, InnerAttachedTop, fraction),
            right = attachment + lerp(
                InnerCollapsedRightOffset,
                InnerAttachedRightOffset,
                fraction,
            ),
            bottom = lerp(InnerCollapsedBottom, InnerAttachedBottom, fraction),
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction
}

internal data class FluidTriadShaderHandleBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Cached transparent foreground endpoints sampled by the same AGSL pass as the selector surface.
 *
 * Values, labels, and vector icons only change when Compose invalidates the draw cache. They are
 * therefore rasterized once per cache generation rather than repainted over the shader every
 * frame. AGSL blends the three complete endpoint layers using the resolved foreground weights,
 * keeping interrupted transitions continuous without introducing another visual layer.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class FluidTriadShaderContentLayers(
    coffee: Bitmap,
    water: Bitmap,
    cup: Bitmap,
) : AutoCloseable {
    private val coffeeShader = BitmapShader(coffee, TileMode.CLAMP, TileMode.CLAMP)
    private val waterShader = BitmapShader(water, TileMode.CLAMP, TileMode.CLAMP)
    private val cupShader = BitmapShader(cup, TileMode.CLAMP, TileMode.CLAMP)
    private var releasedAfterBind: FluidTriadShaderContentLayers? = null
    private var closed = false

    init {
        require(coffee.width == water.width && coffee.width == cup.width)
        require(coffee.height == water.height && coffee.height == cup.height)
    }

    /** Defers releasing the old layer until this replacement has displaced its shader inputs. */
    internal fun releaseAfterNextBind(previous: FluidTriadShaderContentLayers?) {
        if (previous == null || previous === this) return
        releasedAfterBind?.close()
        releasedAfterBind = previous
    }

    internal fun bind(runtimeShader: RuntimeShader) {
        check(!closed) { "Cannot bind released fluid-triad foreground textures" }
        runtimeShader.setInputShader(UNIFORM_CONTENT_COFFEE, coffeeShader)
        runtimeShader.setInputShader(UNIFORM_CONTENT_WATER, waterShader)
        runtimeShader.setInputShader(UNIFORM_CONTENT_CUP, cupShader)
        releasedAfterBind?.close()
        releasedAfterBind = null
    }

    override fun close() {
        if (closed) return
        closed = true
        releasedAfterBind?.close()
        releasedAfterBind = null
        // Do not call Bitmap.recycle(): the hardware renderer may still hold the preceding
        // frame's display list on RenderThread. Once the cache and backend drop this layer, the
        // platform releases its bitmap/native texture after both threads are done.
    }

    private companion object {
        const val UNIFORM_CONTENT_COFFEE = "contentCoffee"
        const val UNIFORM_CONTENT_WATER = "contentWater"
        const val UNIFORM_CONTENT_CUP = "contentCup"
    }
}

internal data class FluidTriadShaderCellFractions(
    val coffee: Float,
    val water: Float,
    val cup: Float,
) {
    init {
        require(coffee.isFinite() && coffee in 0f..1f)
        require(water.isFinite() && water in 0f..1f)
        require(cup.isFinite() && cup in 0f..1f)
    }

    companion object {
        val Zero = FluidTriadShaderCellFractions(0f, 0f, 0f)
    }
}

/** Interaction overlays partitioned by the same selected SDF and neutral-cell masks as the rail. */
internal data class FluidTriadShaderInteraction(
    val selectedPress: Float = 0f,
    val selectedFocus: Float = 0f,
    val neutralPress: FluidTriadShaderCellFractions = FluidTriadShaderCellFractions.Zero,
    val neutralFocus: FluidTriadShaderCellFractions = FluidTriadShaderCellFractions.Zero,
) {
    init {
        require(selectedPress.isFinite() && selectedPress in 0f..1f)
        require(selectedFocus.isFinite() && selectedFocus in 0f..1f)
    }

    companion object {
        val None = FluidTriadShaderInteraction()
    }
}

/**
 * API 33+ AGSL backend for the selector's integral background/material pixel partition.
 *
 * The expensive fluid model lives upstream and supplies immutable SDF frames. This class packs
 * those frames into one bitmap once. Animation only changes two frame indices and a blend uniform;
 * no geometry, fluid solve, bitmap upload, or allocation occurs in [draw].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class FluidTriadShaderBackend(
    initialFrameSource: FluidTriadNormalizedSdfFrameSource,
    private val encodedDistanceRange: Float = DEFAULT_DISTANCE_RANGE,
) {
    private val atlasCache = FluidTriadSdfBitmapAtlasCache(
        distanceRange = encodedDistanceRange,
        capacity = ROUTE_ATLAS_CACHE_SIZE,
    )
    private val runtimeShader = RuntimeShader(FLUID_TRIAD_AGSL).apply {
        setFloatUniform(UNIFORM_DISTANCE_RANGE, encodedDistanceRange)
    }
    val brush: ShaderBrush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = runtimeShader
    }

    private var lastGeometry: FluidTriadShaderGeometry? = null
    private var lastPalette: FluidTriadShaderPalette? = null
    private var lastInteraction: FluidTriadShaderInteraction? = null
    private var lastContentLayers: FluidTriadShaderContentLayers? = null
    private var lastMaterialCoffeeWeight = Float.NaN
    private var lastMaterialWaterWeight = Float.NaN
    private var lastMaterialCupWeight = Float.NaN
    private var lastContentCoffeeWeight = Float.NaN
    private var lastContentWaterWeight = Float.NaN
    private var lastContentCupWeight = Float.NaN
    private var lastCoffeeCrease = Float.NaN
    private var lastWaterMotion = Float.NaN
    private var lastCupRim = Float.NaN
    private var lastWaterGust = Float.NaN
    private var lastCupHandle = Float.NaN
    private var lastContentAnchorX = Float.NaN
    private var lastFirstFrame = -1
    private var lastSecondFrame = -1
    private var lastFrameFraction = Float.NaN
    private var activeFrameSource: FluidTriadNormalizedSdfFrameSource? = null

    /** Approximate strong-reference GPU texture storage retained by the bounded route LRU. */
    val estimatedTextureBytes: Int
        get() = atlasCache.estimatedTextureBytes

    init {
        require(encodedDistanceRange.isFinite() && encodedDistanceRange > 0f)
        bindFrameSource(initialFrameSource)
    }

    /**
     * Binds a cached route (or cached water idle sequence) without recompiling AGSL.
     *
     * Ten packed textures cover three endpoints, every directed route, and water idle. Ordinary
     * value/text recomposition should keep this backend instance and therefore does no bitmap work.
     */
    fun bindFrameSource(frameSource: FluidTriadNormalizedSdfFrameSource) {
        if (activeFrameSource === frameSource) return
        val entry = atlasCache.getOrCreate(frameSource)
        runtimeShader.setInputBuffer(UNIFORM_SDF_ATLAS, entry.bitmapShader)
        runtimeShader.setFloatUniform(
            UNIFORM_GRID_SIZE,
            entry.layout.frameWidth.toFloat(),
            entry.layout.frameHeight.toFloat(),
        )
        runtimeShader.setFloatUniform(
            UNIFORM_SDF_DOMAIN,
            frameSource.logicalDomain.left,
            frameSource.logicalDomain.top,
            frameSource.logicalDomain.right,
            frameSource.logicalDomain.bottom,
        )
        val interrupted = frameSource as? FluidTriadInterruptedShaderFrameSource
        runtimeShader.setFloatUniform(
            UNIFORM_CENTROID_ADVECTION,
            if (interrupted == null) 0f else 1f,
        )
        if (interrupted != null) {
            runtimeShader.setFloatUniform(
                UNIFORM_SOURCE_CENTROID,
                interrupted.sourceCentroidX,
                interrupted.sourceCentroidY,
            )
            runtimeShader.setFloatUniform(
                UNIFORM_DESTINATION_CENTROID,
                interrupted.destinationCentroidX,
                interrupted.destinationCentroidY,
            )
        }
        activeFrameSource = frameSource
        lastFirstFrame = -1
        lastSecondFrame = -1
        lastFrameFraction = Float.NaN
    }

    /** Encodes and retains a texture without changing the currently bound route. */
    fun prewarmFrameSource(frameSource: FluidTriadNormalizedSdfFrameSource) {
        atlasCache.getOrCreate(frameSource)
    }

    /** Captures the currently displayed SDF as frame zero of a cached retarget pair. */
    fun captureTransitionTo(
        destination: FluidTriadNormalizedSdfFrameSource,
    ): FluidTriadInterruptedShaderFrameSource? {
        val source = activeFrameSource ?: return null
        if (lastFirstFrame < 0 || lastSecondFrame < 0 || !lastFrameFraction.isFinite()) return null
        return FluidTriadInterruptedShaderFrameSource.capture(
            activeSource = source,
            activeBlend = FluidTriadShaderFrameBlend(
                firstFrame = lastFirstFrame,
                secondFrame = lastSecondFrame,
                fraction = lastFrameFraction,
            ),
            destination = destination,
        )
    }

    /** Updates uniforms only when their immutable input group changes. */
    fun update(
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        frameBlend: FluidTriadShaderFrameBlend,
        contentLayers: FluidTriadShaderContentLayers,
        metadata: FluidTriadRenderMetadata,
        waterGust: Float,
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        updateNonFrameUniforms(
            geometry = geometry,
            palette = palette,
            interaction = interaction,
            contentLayers = contentLayers,
            metadata = metadata,
            waterGust = waterGust,
        )
        updateFrame(
            firstFrame = frameBlend.firstFrame,
            secondFrame = frameBlend.secondFrame,
            fraction = frameBlend.fraction,
        )
    }

    /** Allocation-free hot path for transition progress and cyclic water-idle phase. */
    fun update(
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        progress: Float,
        contentLayers: FluidTriadShaderContentLayers,
        metadata: FluidTriadRenderMetadata,
        waterGust: Float,
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        updateNonFrameUniforms(
            geometry = geometry,
            palette = palette,
            interaction = interaction,
            contentLayers = contentLayers,
            metadata = metadata,
            waterGust = waterGust,
        )
        val frameCount = checkNotNull(activeFrameSource).frameCount
        val normalized = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val scaled = normalized * (frameCount - 1)
        val firstFrame = floor(scaled).toInt().coerceIn(0, frameCount - 1)
        val secondFrame = (firstFrame + 1).coerceAtMost(frameCount - 1)
        updateFrame(firstFrame, secondFrame, scaled - firstFrame)
    }

    /** Draws the complete rail/material background in one GPU pass. */
    fun draw(
        scope: DrawScope,
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        frameBlend: FluidTriadShaderFrameBlend,
        contentLayers: FluidTriadShaderContentLayers,
        metadata: FluidTriadRenderMetadata,
        waterGust: Float,
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        update(
            geometry = geometry,
            palette = palette,
            frameBlend = frameBlend,
            contentLayers = contentLayers,
            metadata = metadata,
            waterGust = waterGust,
            interaction = interaction,
        )
        with(scope) {
            drawRect(brush = brush)
        }
    }

    /** Allocation-free draw overload for route progress or stable-water idle phase. */
    fun draw(
        scope: DrawScope,
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        progress: Float,
        contentLayers: FluidTriadShaderContentLayers,
        metadata: FluidTriadRenderMetadata,
        waterGust: Float,
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        update(
            geometry = geometry,
            palette = palette,
            progress = progress,
            contentLayers = contentLayers,
            metadata = metadata,
            waterGust = waterGust,
            interaction = interaction,
        )
        with(scope) {
            drawRect(brush = brush)
        }
    }

    private fun updateNonFrameUniforms(
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        interaction: FluidTriadShaderInteraction,
        contentLayers: FluidTriadShaderContentLayers,
        metadata: FluidTriadRenderMetadata,
        waterGust: Float,
    ) {
        if (lastGeometry != geometry) {
            updateGeometry(geometry)
            lastGeometry = geometry
        }
        if (lastPalette != palette) {
            updatePalette(palette)
            lastPalette = palette
        }
        if (lastInteraction != interaction) {
            updateInteraction(interaction)
        }
        if (lastContentLayers !== contentLayers) {
            contentLayers.bind(runtimeShader)
            lastContentLayers = contentLayers
        }
        updateVisualState(metadata, waterGust)
    }

    private fun updateVisualState(metadata: FluidTriadRenderMetadata, waterGust: Float) {
        if (
            lastMaterialCoffeeWeight != metadata.materialCoffeeWeight ||
            lastMaterialWaterWeight != metadata.materialWaterWeight ||
            lastMaterialCupWeight != metadata.materialCupWeight
        ) {
            runtimeShader.setFloatUniform(
                UNIFORM_MATERIAL_WEIGHTS,
                metadata.materialCoffeeWeight,
                metadata.materialWaterWeight,
                metadata.materialCupWeight,
            )
            lastMaterialCoffeeWeight = metadata.materialCoffeeWeight
            lastMaterialWaterWeight = metadata.materialWaterWeight
            lastMaterialCupWeight = metadata.materialCupWeight
        }
        if (
            lastContentCoffeeWeight != metadata.contentCoffeeWeight ||
            lastContentWaterWeight != metadata.contentWaterWeight ||
            lastContentCupWeight != metadata.contentCupWeight
        ) {
            runtimeShader.setFloatUniform(
                UNIFORM_CONTENT_WEIGHTS,
                metadata.contentCoffeeWeight,
                metadata.contentWaterWeight,
                metadata.contentCupWeight,
            )
            lastContentCoffeeWeight = metadata.contentCoffeeWeight
            lastContentWaterWeight = metadata.contentWaterWeight
            lastContentCupWeight = metadata.contentCupWeight
        }
        val normalizedGust = if (waterGust.isFinite()) waterGust.coerceIn(0f, 1f) else 0f
        if (materialEffectsChanged(metadata, normalizedGust)) {
            runtimeShader.setFloatUniform(
                UNIFORM_MATERIAL_EFFECTS,
                metadata.coffeeCrease,
                metadata.waterMotion,
                metadata.cupRim,
                normalizedGust,
            )
            lastCoffeeCrease = metadata.coffeeCrease
            lastWaterMotion = metadata.waterMotion
            lastCupRim = metadata.cupRim
            lastWaterGust = normalizedGust
        }
        if (
            lastCupHandle != metadata.cupHandle ||
            lastContentAnchorX != metadata.contentAnchorX
        ) {
            runtimeShader.setFloatUniform(
                UNIFORM_CUP_HANDLE_GEOMETRY,
                metadata.cupHandle,
                metadata.contentAnchorX,
            )
            lastCupHandle = metadata.cupHandle
            lastContentAnchorX = metadata.contentAnchorX
        }
    }

    private fun materialEffectsChanged(
        metadata: FluidTriadRenderMetadata,
        normalizedGust: Float,
    ): Boolean {
        if (lastCoffeeCrease != metadata.coffeeCrease) return true
        if (lastWaterMotion != metadata.waterMotion) return true
        if (lastCupRim != metadata.cupRim) return true
        return lastWaterGust != normalizedGust
    }

    /** Primitive frame binding for callers already using an allocation-free SDF resolver. */
    fun updateFrame(
        firstFrame: Int,
        secondFrame: Int,
        fraction: Float,
    ) {
        val frameCount = checkNotNull(activeFrameSource).frameCount
        require(firstFrame in 0 until frameCount)
        require(secondFrame in 0 until frameCount)
        require(abs(secondFrame - firstFrame) <= 1)
        require(fraction.isFinite() && fraction in 0f..1f)
        if (lastFirstFrame != firstFrame) {
            runtimeShader.setFloatUniform(UNIFORM_FRAME_A, firstFrame.toFloat())
            lastFirstFrame = firstFrame
        }
        if (lastSecondFrame != secondFrame) {
            runtimeShader.setFloatUniform(UNIFORM_FRAME_B, secondFrame.toFloat())
            lastSecondFrame = secondFrame
        }
        if (lastFrameFraction != fraction) {
            runtimeShader.setFloatUniform(UNIFORM_FRAME_BLEND, fraction)
            lastFrameFraction = fraction
        }
    }

    private fun updateGeometry(geometry: FluidTriadShaderGeometry) {
        runtimeShader.setFloatUniform(
            UNIFORM_COMPONENT_SIZE,
            geometry.componentWidth,
            geometry.componentHeight,
        )
        runtimeShader.setFloatUniform(
            UNIFORM_RAIL_RECT,
            geometry.railLeft,
            geometry.railTop,
            geometry.railWidth,
            geometry.railHeight,
        )
        runtimeShader.setFloatUniform(UNIFORM_RAIL_OUTLINE, geometry.railOutlineWidth)
        runtimeShader.setFloatUniform(UNIFORM_IS_RTL, if (geometry.isRtl) 1f else 0f)
        runtimeShader.setFloatUniform(UNIFORM_CONTACT_SHADOW_OFFSET, geometry.contactShadowOffsetY)
        runtimeShader.setFloatUniform(UNIFORM_CONTACT_SHADOW_SOFTNESS, geometry.contactShadowSoftness)
        runtimeShader.setFloatUniform(UNIFORM_PENUMBRA_SHADOW_OFFSET, geometry.penumbraShadowOffsetY)
        runtimeShader.setFloatUniform(UNIFORM_PENUMBRA_SHADOW_SOFTNESS, geometry.penumbraShadowSoftness)
        runtimeShader.setFloatUniform(UNIFORM_FOCUS_OUTLINE, geometry.focusOutlineWidth)
    }

    private fun updatePalette(palette: FluidTriadShaderPalette) {
        runtimeShader.setColorUniform(UNIFORM_NEUTRAL_TOP, palette.neutralTop.toArgb())
        runtimeShader.setColorUniform(UNIFORM_NEUTRAL_BOTTOM, palette.neutralBottom.toArgb())
        runtimeShader.setColorUniform(UNIFORM_RAIL_COLOR, palette.railOutline.toArgb())
        runtimeShader.setColorUniform(UNIFORM_MATERIAL_TOP, palette.materialTop.toArgb())
        runtimeShader.setColorUniform(UNIFORM_MATERIAL_BOTTOM, palette.materialBottom.toArgb())
        runtimeShader.setColorUniform(UNIFORM_MATERIAL_OUTLINE, palette.materialOutline.toArgb())
        runtimeShader.setColorUniform(UNIFORM_MATERIAL_HIGHLIGHT, palette.materialHighlight.toArgb())
        runtimeShader.setColorUniform(UNIFORM_SHADOW_COLOR, palette.shadow.toArgb())
        runtimeShader.setColorUniform(UNIFORM_PRESSED_COLOR, palette.pressedOverlay.toArgb())
        runtimeShader.setColorUniform(UNIFORM_FOCUS_COLOR, palette.focus.toArgb())
    }

    /** Mutable interaction uniform update; it does not invalidate the bitmap atlas or AGSL. */
    fun updateInteraction(interaction: FluidTriadShaderInteraction) {
        runtimeShader.setFloatUniform(UNIFORM_SELECTED_PRESS, interaction.selectedPress)
        runtimeShader.setFloatUniform(UNIFORM_SELECTED_FOCUS, interaction.selectedFocus)
        runtimeShader.setFloatUniform(
            UNIFORM_CELL_PRESS,
            interaction.neutralPress.coffee,
            interaction.neutralPress.water,
            interaction.neutralPress.cup,
        )
        runtimeShader.setFloatUniform(
            UNIFORM_CELL_FOCUS,
            interaction.neutralFocus.coffee,
            interaction.neutralFocus.water,
            interaction.neutralFocus.cup,
        )
        lastInteraction = interaction
    }

    private companion object {
        const val DEFAULT_DISTANCE_RANGE = 0.125f
        const val ROUTE_ATLAS_CACHE_SIZE = 10

        const val UNIFORM_SDF_ATLAS = "sdfAtlas"
        const val UNIFORM_GRID_SIZE = "gridSize"
        const val UNIFORM_DISTANCE_RANGE = "distanceRange"
        const val UNIFORM_SDF_DOMAIN = "sdfDomain"
        const val UNIFORM_COMPONENT_SIZE = "componentSize"
        const val UNIFORM_RAIL_RECT = "railRect"
        const val UNIFORM_RAIL_OUTLINE = "railOutlineWidth"
        const val UNIFORM_IS_RTL = "isRtl"
        const val UNIFORM_CONTACT_SHADOW_OFFSET = "contactShadowOffset"
        const val UNIFORM_CONTACT_SHADOW_SOFTNESS = "contactShadowSoftness"
        const val UNIFORM_PENUMBRA_SHADOW_OFFSET = "penumbraShadowOffset"
        const val UNIFORM_PENUMBRA_SHADOW_SOFTNESS = "penumbraShadowSoftness"
        const val UNIFORM_FOCUS_OUTLINE = "focusOutlineWidth"
        const val UNIFORM_FRAME_A = "frameA"
        const val UNIFORM_FRAME_B = "frameB"
        const val UNIFORM_FRAME_BLEND = "frameBlend"
        const val UNIFORM_CENTROID_ADVECTION = "centroidAdvection"
        const val UNIFORM_SOURCE_CENTROID = "sourceCentroid"
        const val UNIFORM_DESTINATION_CENTROID = "destinationCentroid"
        const val UNIFORM_NEUTRAL_TOP = "neutralTop"
        const val UNIFORM_NEUTRAL_BOTTOM = "neutralBottom"
        const val UNIFORM_RAIL_COLOR = "railColor"
        const val UNIFORM_MATERIAL_TOP = "materialTop"
        const val UNIFORM_MATERIAL_BOTTOM = "materialBottom"
        const val UNIFORM_MATERIAL_OUTLINE = "materialOutline"
        const val UNIFORM_MATERIAL_HIGHLIGHT = "materialHighlight"
        const val UNIFORM_SHADOW_COLOR = "shadowColor"
        const val UNIFORM_PRESSED_COLOR = "pressedColor"
        const val UNIFORM_FOCUS_COLOR = "focusColor"
        const val UNIFORM_SELECTED_PRESS = "selectedPress"
        const val UNIFORM_SELECTED_FOCUS = "selectedFocus"
        const val UNIFORM_CELL_PRESS = "cellPress"
        const val UNIFORM_CELL_FOCUS = "cellFocus"
        const val UNIFORM_MATERIAL_WEIGHTS = "materialWeights"
        const val UNIFORM_CONTENT_WEIGHTS = "contentWeights"
        const val UNIFORM_MATERIAL_EFFECTS = "materialEffects"
        const val UNIFORM_CUP_HANDLE_GEOMETRY = "cupHandleGeometry"

        val FLUID_TRIAD_AGSL =
            """
            uniform shader sdfAtlas;
            uniform shader contentCoffee;
            uniform shader contentWater;
            uniform shader contentCup;
            uniform float2 gridSize;
            uniform float distanceRange;
            uniform float4 sdfDomain;

            uniform float2 componentSize;
            uniform float4 railRect;
            uniform float railOutlineWidth;
            uniform float isRtl;
            uniform float contactShadowOffset;
            uniform float contactShadowSoftness;
            uniform float penumbraShadowOffset;
            uniform float penumbraShadowSoftness;
            uniform float focusOutlineWidth;

            uniform float frameA;
            uniform float frameB;
            uniform float frameBlend;
            uniform float centroidAdvection;
            uniform float2 sourceCentroid;
            uniform float2 destinationCentroid;
            uniform float selectedPress;
            uniform float selectedFocus;
            uniform float3 cellPress;
            uniform float3 cellFocus;
            uniform float3 materialWeights;
            uniform float3 contentWeights;
            uniform float4 materialEffects;
            // x = resolved cupHandle effect, y = moving foreground/body anchor.
            uniform float2 cupHandleGeometry;

            layout(color) uniform half4 neutralTop;
            layout(color) uniform half4 neutralBottom;
            layout(color) uniform half4 railColor;
            layout(color) uniform half4 materialTop;
            layout(color) uniform half4 materialBottom;
            layout(color) uniform half4 materialOutline;
            layout(color) uniform half4 materialHighlight;
            layout(color) uniform half4 shadowColor;
            layout(color) uniform half4 pressedColor;
            layout(color) uniform half4 focusColor;

            float2 logicalPosition(float2 point) {
                float railX = (point.x - railRect.x) / railRect.z;
                railX = mix(railX, 1.0 - railX, isRtl);
                return float2(railX, point.y / componentSize.y);
            }

            float2 atlasPosition(float2 logical, float frame) {
                float2 domainSize = sdfDomain.zw - sdfDomain.xy;
                float2 uv = clamp((logical - sdfDomain.xy) / domainSize, 0.0, 1.0);
                float frameX = frame * gridSize.x;
                return float2(
                    frameX + 0.5 + uv.x * (gridSize.x - 1.0),
                    0.5 + uv.y * (gridSize.y - 1.0)
                );
            }

            float decodedDistance(float2 logical, float frame) {
                half encoded = sdfAtlas.eval(atlasPosition(logical, frame)).r;
                return (float(encoded) * 2.0 - 1.0) * distanceRange;
            }

            float ellipseSdf(float2 point, float4 logicalBounds) {
                float2 minimum = float2(
                    logicalBounds.x * railRect.z,
                    logicalBounds.y * componentSize.y
                );
                float2 maximum = float2(
                    logicalBounds.z * railRect.z,
                    logicalBounds.w * componentSize.y
                );
                float2 center = (minimum + maximum) * 0.5;
                float2 radius = max((maximum - minimum) * 0.5, float2(0.001));
                float2 normalized = (point - center) / radius;
                return (length(normalized) - 1.0) * min(radius.x, radius.y);
            }

            float resolvedCupHandleDistance(float2 logical, float progress, float attachmentX) {
                float outerLeft = attachmentX + mix(
                    ${FluidTriadShaderCupHandleSpec.OuterCollapsedLeftOffset},
                    ${FluidTriadShaderCupHandleSpec.OuterAttachedLeftOffset},
                    progress
                );
                float outerRight = attachmentX + mix(
                    ${FluidTriadShaderCupHandleSpec.OuterCollapsedRightOffset},
                    ${FluidTriadShaderCupHandleSpec.OuterAttachedRightOffset},
                    progress
                );
                float innerLeft = attachmentX + mix(
                    ${FluidTriadShaderCupHandleSpec.InnerCollapsedLeftOffset},
                    ${FluidTriadShaderCupHandleSpec.InnerAttachedLeftOffset},
                    progress
                );
                float innerRight = attachmentX + mix(
                    ${FluidTriadShaderCupHandleSpec.InnerCollapsedRightOffset},
                    ${FluidTriadShaderCupHandleSpec.InnerAttachedRightOffset},
                    progress
                );
                float4 outerBounds = float4(
                    outerLeft,
                    ${FluidTriadShaderCupHandleSpec.OuterTop},
                    outerRight,
                    ${FluidTriadShaderCupHandleSpec.OuterBottom}
                );
                float4 innerBounds = float4(
                    innerLeft,
                    mix(
                        ${FluidTriadShaderCupHandleSpec.InnerCollapsedTop},
                        ${FluidTriadShaderCupHandleSpec.InnerAttachedTop},
                        progress
                    ),
                    innerRight,
                    mix(
                        ${FluidTriadShaderCupHandleSpec.InnerCollapsedBottom},
                        ${FluidTriadShaderCupHandleSpec.InnerAttachedBottom},
                        progress
                    )
                );
                float2 point = float2(logical.x * railRect.z, logical.y * componentSize.y);
                float outer = ellipseSdf(point, outerBounds);
                float inner = ellipseSdf(point, innerBounds);
                return max(outer, -inner);
            }

            float envelopeDistance(float2 logical) {
                float inverse = 1.0 - frameBlend;
                float motion = 1.0 - inverse * inverse * inverse;
                float2 movingCentroid = mix(sourceCentroid, destinationCentroid, motion);
                float2 sourceLogical = logical -
                    (movingCentroid - sourceCentroid) * centroidAdvection;
                float2 destinationLogical = logical -
                    (movingCentroid - destinationCentroid) * centroidAdvection;
                float first = decodedDistance(sourceLogical, frameA);
                float second = decodedDistance(destinationLogical, frameB);
                float blend = mix(frameBlend, motion, centroidAdvection);
                float distance = mix(first, second, blend) * componentSize.y;

                float handleProgress = clamp(cupHandleGeometry.x, 0.0, 1.0);
                float cupInfluence = materialWeights.z + handleProgress;
                if (cupInfluence > 0.0001) {
                    float attachmentX = cupHandleGeometry.y +
                        ${FluidTriadShaderCupHandleSpec.AttachmentOffsetFromContent};
                    // The cached cup field contains its attached handle. Intersect it with the
                    // moving body edge first, then union the handle reconstructed at the exact
                    // metadata progress used by the Canvas fallback.
                    float bodyClip = (logical.x - attachmentX) * railRect.z;
                    distance = max(distance, bodyClip);
                    if (handleProgress > 0.0001) {
                        distance = min(
                            distance,
                            resolvedCupHandleDistance(logical, handleProgress, attachmentX)
                        );
                    }
                }
                return distance;
            }

            float2 envelopeNormal(float2 logical) {
                float2 logicalStep = (sdfDomain.zw - sdfDomain.xy) / (gridSize - 1.0);
                float horizontal = envelopeDistance(logical + float2(logicalStep.x, 0.0))
                    - envelopeDistance(logical - float2(logicalStep.x, 0.0));
                float vertical = envelopeDistance(logical + float2(0.0, logicalStep.y))
                    - envelopeDistance(logical - float2(0.0, logicalStep.y));
                return normalize(float2(horizontal, vertical) + float2(0.0001));
            }

            float centerPressure(float logicalY) {
                float y = clamp(logicalY, 0.0, 1.0);
                return 4.0 * y * (1.0 - y);
            }

            float coffeeOwnershipDistance(float2 logical) {
                float pressure = centerPressure(logical.y);
                float boundary = ${FluidTriadWholeControlField.CoffeeBoundaryOuter}
                    - ${FluidTriadWholeControlField.CoffeeBoundaryPinch}
                    * pressure * pressure;
                return (logical.x - boundary) * railRect.z;
            }

            float waterOwnershipDistance(float2 logical) {
                float pressure = centerPressure(logical.y);
                float halfWidth = ${FluidTriadWholeControlField.WaterHalfWidthOuter}
                    - ${FluidTriadWholeControlField.WaterHalfWidthPinch}
                    * pressure * pressure;
                return (abs(logical.x - 0.5) - halfWidth) * railRect.z;
            }

            float cupOwnershipDistance(float2 logical) {
                float y = clamp(logical.y, 0.0, 1.0);
                float pressure = centerPressure(y);
                float lower = smoothstep(0.0, 1.0, clamp((y - 0.5) * 2.0, 0.0, 1.0));
                float boundary = ${FluidTriadWholeControlField.CupBoundaryOuter}
                    - ${FluidTriadWholeControlField.CupBoundaryPinch}
                    * pressure * pressure
                    - ${FluidTriadWholeControlField.CupLowerSettle} * lower;
                return (boundary - logical.x) * railRect.z;
            }

            float phaseCoverage(float distancePx) {
                return 1.0 - smoothstep(
                    -${FluidTriadWholeControlField.OwnershipFeatherPx},
                    ${FluidTriadWholeControlField.OwnershipFeatherPx},
                    distancePx
                );
            }

            float materialOwnership(float2 logical) {
                float3 phases = float3(
                    phaseCoverage(coffeeOwnershipDistance(logical)),
                    phaseCoverage(waterOwnershipDistance(logical)),
                    phaseCoverage(cupOwnershipDistance(logical))
                );
                return clamp(dot(materialWeights, phases), 0.0, 1.0);
            }

            float cellFraction(float3 values, float localX) {
                float normalizedX = clamp(localX, 0.0, 0.99999);
                float first = 1.0 - step(0.3333333, normalizedX);
                float second = step(0.3333333, normalizedX)
                    * (1.0 - step(0.6666667, normalizedX));
                float third = step(0.6666667, normalizedX);
                return dot(values, float3(first, second, third));
            }

            float neutralFocusBand(
                float2 point,
                float railDistance,
                float railCoverage,
                float selectedCoverage,
                float focusAmount
            ) {
                float outerBand = 1.0 - smoothstep(
                    focusOutlineWidth - 1.0,
                    focusOutlineWidth + 1.0,
                    abs(railDistance)
                );
                return outerBand * railCoverage
                    * (1.0 - selectedCoverage) * focusAmount;
            }

            float softCoverage(float distancePx, float softnessPx) {
                return 1.0 - smoothstep(-softnessPx, softnessPx, distancePx);
            }

            float envelopeShadowCoverage(float2 point, float offsetY, float softnessPx) {
                float2 shadowLogical = logicalPosition(point - float2(0.0, offsetY));
                return softCoverage(envelopeDistance(shadowLogical), softnessPx);
            }

            half4 coveredColor(half4 color, float coverage) {
                half alpha = color.a * half(clamp(coverage, 0.0, 1.0));
                return half4(color.rgb * alpha, alpha);
            }

            half4 compositeOver(half4 under, half4 over) {
                return over + under * (half(1.0) - over.a);
            }

            half4 main(float2 point) {
                float antialiasPx = 1.0;
                float2 logical = logicalPosition(point);
                float outerDistance = envelopeDistance(logical);
                float outerCoverage = 1.0
                    - smoothstep(-antialiasPx, antialiasPx, outerDistance);
                float ownership = materialOwnership(logical);

                float railY = clamp((point.y - railRect.y) / railRect.w, 0.0, 1.0);
                half4 neutral = mix(neutralTop, neutralBottom, half(railY));
                half4 material = mix(
                    materialTop,
                    materialBottom,
                    half(smoothstep(0.05, 0.95, railY))
                );
                float2 materialNormal = envelopeNormal(logical);
                float innerBevel = (
                    1.0 - smoothstep(0.0, max(4.0, railOutlineWidth * 3.0), -outerDistance)
                ) * outerCoverage;
                float2 keyLight = normalize(float2(-0.38, -0.92));
                float lightFacing = max(dot(materialNormal, keyLight), 0.0);
                float shadeFacing = max(dot(materialNormal, -keyLight), 0.0);
                float lightingStrength = dot(materialWeights, float3(1.0, 0.72, 1.0));
                float broadTopLight = (1.0 - smoothstep(0.02, 0.68, railY))
                    * outerCoverage * ownership;
                float waterGlint = materialEffects.y * materialEffects.w
                    * lightFacing * innerBevel * ownership;
                float highlightAmount = ownership * lightingStrength * (
                    lightFacing * innerBevel * 0.42 +
                    broadTopLight * 0.045 +
                    waterGlint * 0.12
                );
                material = mix(
                    material,
                    materialHighlight,
                    half(clamp(highlightAmount, 0.0, 0.42))
                );
                material = mix(
                    material,
                    materialOutline,
                    half(clamp(
                        shadeFacing * innerBevel * lightingStrength * ownership * 0.10,
                        0.0,
                        0.14
                    ))
                );
                half4 result = half4(0.0);

                float envelopePenumbra = envelopeShadowCoverage(
                    point,
                    penumbraShadowOffset,
                    penumbraShadowSoftness
                );
                float envelopeContact = envelopeShadowCoverage(
                    point,
                    contactShadowOffset,
                    contactShadowSoftness
                );
                result = compositeOver(
                    result,
                    coveredColor(
                        shadowColor,
                        envelopePenumbra * 0.62 * (1.0 - outerCoverage)
                    )
                );
                result = compositeOver(
                    result,
                    coveredColor(
                        shadowColor,
                        envelopeContact * 0.42 * (1.0 - outerCoverage)
                    )
                );
                half4 surface = mix(neutral, material, half(ownership));
                result = compositeOver(result, coveredColor(surface, outerCoverage));

                float localCellX = (point.x - railRect.x) / railRect.z;
                localCellX = mix(localCellX, 1.0 - localCellX, isRtl);
                float neutralPressAmount = cellFraction(cellPress, localCellX);
                float neutralFocusAmount = cellFraction(cellFocus, localCellX);
                result = compositeOver(
                    result,
                    coveredColor(pressedColor, outerCoverage * neutralPressAmount)
                );
                result = compositeOver(
                    result,
                    coveredColor(focusColor, outerCoverage * neutralFocusAmount * 0.08)
                );
                float neutralFocusOutline = neutralFocusBand(
                    point,
                    outerDistance,
                    outerCoverage,
                    0.0,
                    neutralFocusAmount
                );
                result = compositeOver(result, coveredColor(focusColor, neutralFocusOutline));

                result = compositeOver(
                    result,
                    coveredColor(
                        pressedColor,
                        outerCoverage * ownership * selectedPress
                    )
                );

                float outerOutline = 1.0 - smoothstep(
                    railOutlineWidth - antialiasPx,
                    railOutlineWidth + antialiasPx,
                    abs(outerDistance)
                );
                half4 outerEdge = mix(railColor, materialOutline, half(ownership));
                result = compositeOver(result, coveredColor(outerEdge, outerOutline));
                result = compositeOver(
                    result,
                    coveredColor(
                        focusColor,
                        outerOutline * ownership * selectedFocus
                    )
                );

                // Icons, labels, and values are cached transparent endpoint images. Blending
                // them here makes foreground and surface one GPU result instead of painting a
                // second Canvas layer after the selector has already been rendered.
                half4 foreground = contentCoffee.eval(point) * half(contentWeights.x)
                    + contentWater.eval(point) * half(contentWeights.y)
                    + contentCup.eval(point) * half(contentWeights.z);
                result = compositeOver(result, foreground);
                return result;
            }
            """.trimIndent()
    }
}

/**
 * Selector-lifetime owner for stable endpoint, directed-route, and water-idle bindings.
 *
 * Keep one instance remembered independently of displayed values. Routes are packed lazily on
 * first use, then remain eligible for the backend's ten-entry texture LRU.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class FluidTriadShaderResources(
    atlas: FluidTriadSdfAtlas,
    initialTarget: CalculatorQuantityTarget,
) {
    private val endpointSources = Array(TARGET_COUNT) { index ->
        atlas.endpoint(TARGETS[index]).asShaderFrameSource()
    }
    private val routeSources = arrayOfNulls<FluidTriadNormalizedSdfFrameSource>(ROUTE_SLOT_COUNT)
    val waterIdleSource: FluidTriadNormalizedSdfFrameSource =
        atlas.waterIdle.asShaderFrameSource()
    val backend = FluidTriadShaderBackend(endpointSource(initialTarget))
    private var interruptedSource: FluidTriadInterruptedShaderFrameSource? = null
    private var interruptedDestination: CalculatorQuantityTarget? = null
    private var bindingKind = FluidTriadShaderBindingKind.ENDPOINT

    init {
        TARGETS.forEach { source ->
            TARGETS.forEach { destination ->
                if (source != destination) {
                    routeSources[routeIndex(source, destination)] =
                        atlas.route(source, destination).asShaderFrameSource()
                }
            }
        }
    }

    fun endpointSource(target: CalculatorQuantityTarget): FluidTriadNormalizedSdfFrameSource =
        endpointSources[targetIndex(target)]

    fun routeSource(
        source: CalculatorQuantityTarget,
        destination: CalculatorQuantityTarget,
    ): FluidTriadNormalizedSdfFrameSource {
        require(source != destination)
        return requireNotNull(routeSources[routeIndex(source, destination)])
    }

    fun bindEndpoint(target: CalculatorQuantityTarget) {
        clearInterruptedBinding()
        backend.bindFrameSource(endpointSource(target))
        backend.updateFrame(firstFrame = 0, secondFrame = 0, fraction = 0f)
        bindingKind = FluidTriadShaderBindingKind.ENDPOINT
    }

    fun bindRoute(
        source: CalculatorQuantityTarget,
        destination: CalculatorQuantityTarget,
    ) {
        clearInterruptedBinding()
        backend.bindFrameSource(routeSource(source, destination))
        bindingKind = FluidTriadShaderBindingKind.ROUTE
    }

    fun bindWaterIdle() {
        clearInterruptedBinding()
        backend.bindFrameSource(waterIdleSource)
        bindingKind = FluidTriadShaderBindingKind.WATER_IDLE
    }

    /** Packs all stable sources before this resource owner is exposed to the draw loop. */
    fun prewarmStableTextures() {
        endpointSources.forEach(backend::prewarmFrameSource)
        routeSources.filterNotNull().forEach(backend::prewarmFrameSource)
        backend.prewarmFrameSource(waterIdleSource)
    }

    fun shouldCaptureActiveSource(source: CalculatorQuantityTarget): Boolean =
        bindingKind == FluidTriadShaderBindingKind.WATER_IDLE &&
            source == CalculatorQuantityTarget.WATER_IN

    /**
     * Keeps a rapid retarget on the shader by capturing its last displayed cached slices once.
     * Repeated calls for the same interrupted transition reuse the same packed two-frame texture.
     */
    fun bindInterrupted(destination: CalculatorQuantityTarget): Boolean {
        val existing = interruptedSource
        if (existing != null && interruptedDestination == destination) {
            backend.bindFrameSource(existing)
            bindingKind = FluidTriadShaderBindingKind.INTERRUPTED
            return true
        }
        val captured = backend.captureTransitionTo(endpointSource(destination)) ?: return false
        interruptedSource = captured
        interruptedDestination = destination
        backend.bindFrameSource(captured)
        bindingKind = FluidTriadShaderBindingKind.INTERRUPTED
        return true
    }

    private fun clearInterruptedBinding() {
        interruptedSource = null
        interruptedDestination = null
    }

    private fun targetIndex(target: CalculatorQuantityTarget): Int = when (target) {
        CalculatorQuantityTarget.COFFEE -> 0
        CalculatorQuantityTarget.WATER_IN -> 1
        CalculatorQuantityTarget.IN_CUP -> 2
    }

    private fun routeIndex(
        source: CalculatorQuantityTarget,
        destination: CalculatorQuantityTarget,
    ): Int = targetIndex(source) * TARGET_COUNT + targetIndex(destination)

    private companion object {
        const val TARGET_COUNT = 3
        const val ROUTE_SLOT_COUNT = TARGET_COUNT * TARGET_COUNT
        val TARGETS = arrayOf(
            CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.IN_CUP,
        )
    }

    private enum class FluidTriadShaderBindingKind {
        ENDPOINT,
        ROUTE,
        WATER_IDLE,
        INTERRUPTED,
    }
}

/** Pure atlas addressing helper used by the encoder and JVM tests. */
internal data class FluidTriadSdfAtlasLayout(
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
) {
    init {
        require(frameWidth >= 2)
        require(frameHeight >= 2)
        require(frameCount >= 1)
        require(frameWidth <= Int.MAX_VALUE / frameCount)
    }

    val atlasWidth: Int = frameWidth * frameCount
    val atlasHeight: Int = frameHeight
    val estimatedArgb8888Bytes: Int = Math.multiplyExact(
        Math.multiplyExact(atlasWidth, atlasHeight),
        ARGB_8888_BYTES_PER_PIXEL,
    )

    fun pixelIndex(frame: Int, x: Int, y: Int): Int {
        require(frame in 0 until frameCount)
        require(x in 0 until frameWidth)
        require(y in 0 until frameHeight)
        return y * atlasWidth + frame * frameWidth + x
    }

    fun sampleCenterX(frame: Int, normalizedX: Float): Float {
        require(frame in 0 until frameCount)
        require(normalizedX.isFinite() && normalizedX in 0f..1f)
        return frame * frameWidth + 0.5f + normalizedX * (frameWidth - 1f)
    }

    private companion object {
        const val ARGB_8888_BYTES_PER_PIXEL = 4
    }
}

/** Deterministic signed-distance quantizer. Zero always maps to the coverage threshold. */
internal object FluidTriadSdfEncoding {
    private const val BYTE_MAX = 255

    fun encode(distance: Float, distanceRange: Float): Int {
        require(distanceRange.isFinite() && distanceRange > 0f)
        require(distance.isFinite())
        val normalized = (0.5f + distance / (2f * distanceRange)).coerceIn(0f, 1f)
        return (normalized * BYTE_MAX).roundToInt()
    }

    fun decode(encoded: Int, distanceRange: Float): Float {
        require(encoded in 0..BYTE_MAX)
        require(distanceRange.isFinite() && distanceRange > 0f)
        return (encoded / BYTE_MAX.toFloat() * 2f - 1f) * distanceRange
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class FluidTriadSdfBitmapAtlasCache(
    private val distanceRange: Float,
    private val capacity: Int,
) {
    private val entries = mutableListOf<FluidTriadSdfBitmapAtlasEntry>()

    init {
        require(capacity > 0)
    }

    val estimatedTextureBytes: Int
        get() = entries.sumOf { it.textureBytes }

    fun getOrCreate(source: FluidTriadNormalizedSdfFrameSource): FluidTriadSdfBitmapAtlasEntry {
        val existingIndex = entries.indexOfFirst { it.source === source }
        if (existingIndex >= 0) {
            return entries.removeAt(existingIndex).also { entries.add(0, it) }
        }

        val layout = FluidTriadSdfAtlasLayout(
            frameWidth = source.gridWidth,
            frameHeight = source.gridHeight,
            frameCount = source.frameCount,
        )
        val bitmap = FluidTriadSdfBitmapAtlas.create(source, layout, distanceRange)
        val bitmapShader = BitmapShader(bitmap, TileMode.CLAMP, TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        val entry = FluidTriadSdfBitmapAtlasEntry(
            source = source,
            layout = layout,
            bitmapShader = bitmapShader,
            textureBytes = layout.estimatedArgb8888Bytes,
        )
        entries.add(0, entry)
        if (entries.size > capacity) entries.removeAt(entries.lastIndex)
        return entry
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private data class FluidTriadSdfBitmapAtlasEntry(
    val source: FluidTriadNormalizedSdfFrameSource,
    val layout: FluidTriadSdfAtlasLayout,
    val bitmapShader: BitmapShader,
    val textureBytes: Int,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object FluidTriadSdfBitmapAtlas {
    fun create(
        source: FluidTriadNormalizedSdfFrameSource,
        layout: FluidTriadSdfAtlasLayout,
        distanceRange: Float,
    ): Bitmap {
        val pixels = IntArray(layout.atlasWidth * layout.atlasHeight)
        for (frameIndex in 0 until layout.frameCount) {
            val distances = source.signedDistances(frameIndex)
            require(distances.size == layout.frameWidth * layout.frameHeight)
            encodeFrame(
                distances = distances,
                frameIndex = frameIndex,
                layout = layout,
                distanceRange = distanceRange,
                destination = pixels,
            )
        }

        return createBitmap(layout.atlasWidth, layout.atlasHeight, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
            setHasAlpha(false)
            setPixels(pixels, 0, layout.atlasWidth, 0, 0, layout.atlasWidth, layout.atlasHeight)
            prepareToDraw()
        }
    }

    private fun encodeFrame(
        distances: FloatArray,
        frameIndex: Int,
        layout: FluidTriadSdfAtlasLayout,
        distanceRange: Float,
        destination: IntArray,
    ) {
        for (y in 0 until layout.frameHeight) {
            for (x in 0 until layout.frameWidth) {
                val sourceIndex = y * layout.frameWidth + x
                val encoded = FluidTriadSdfEncoding.encode(distances[sourceIndex], distanceRange)
                destination[layout.pixelIndex(frameIndex, x, y)] =
                    OPAQUE_ALPHA or (encoded shl RED_SHIFT) or (encoded shl GREEN_SHIFT) or encoded
            }
        }
    }

    private const val OPAQUE_ALPHA = -0x1000000
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
