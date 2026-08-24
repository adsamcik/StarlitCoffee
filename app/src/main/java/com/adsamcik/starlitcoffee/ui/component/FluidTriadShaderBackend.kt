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
            val first = activeSource.signedDistances(activeBlend.firstFrame)
            val second = activeSource.signedDistances(activeBlend.secondFrame)
            val captured = blendFields(first, second, activeBlend.fraction)
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
    val railCornerRadius: Float,
    val railOutlineWidth: Float,
    val selectedOutlineWidth: Float,
    val dividerWidth: Float,
    val dividerInset: Float,
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
        require(railCornerRadius >= 0f)
        require(railOutlineWidth >= 0f && selectedOutlineWidth >= 0f)
        require(dividerWidth >= 0f && dividerInset >= 0f)
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
    val divider: Color,
    val materialTop: Color,
    val materialBottom: Color,
    val materialOutline: Color,
    val materialHighlight: Color,
    val shadow: Color = Color.Black.copy(alpha = 0.16f),
    val pressedOverlay: Color = Color.Black.copy(alpha = 0.12f),
    val focus: Color = Color.White.copy(alpha = 0.72f),
)

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
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        updateNonFrameUniforms(geometry, palette, interaction)
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
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        updateNonFrameUniforms(geometry, palette, interaction)
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
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        update(geometry, palette, frameBlend, interaction)
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
        interaction: FluidTriadShaderInteraction = FluidTriadShaderInteraction.None,
    ) {
        update(geometry, palette, progress, interaction)
        with(scope) {
            drawRect(brush = brush)
        }
    }

    private fun updateNonFrameUniforms(
        geometry: FluidTriadShaderGeometry,
        palette: FluidTriadShaderPalette,
        interaction: FluidTriadShaderInteraction,
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
        runtimeShader.setFloatUniform(UNIFORM_RAIL_RADIUS, geometry.railCornerRadius)
        runtimeShader.setFloatUniform(UNIFORM_RAIL_OUTLINE, geometry.railOutlineWidth)
        runtimeShader.setFloatUniform(UNIFORM_SELECTED_OUTLINE, geometry.selectedOutlineWidth)
        runtimeShader.setFloatUniform(UNIFORM_DIVIDER_WIDTH, geometry.dividerWidth)
        runtimeShader.setFloatUniform(UNIFORM_DIVIDER_INSET, geometry.dividerInset)
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
        runtimeShader.setColorUniform(UNIFORM_DIVIDER_COLOR, palette.divider.toArgb())
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
        const val UNIFORM_RAIL_RADIUS = "railRadius"
        const val UNIFORM_RAIL_OUTLINE = "railOutlineWidth"
        const val UNIFORM_SELECTED_OUTLINE = "selectedOutlineWidth"
        const val UNIFORM_DIVIDER_WIDTH = "dividerWidth"
        const val UNIFORM_DIVIDER_INSET = "dividerInset"
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
        const val UNIFORM_DIVIDER_COLOR = "dividerColor"
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

        val FLUID_TRIAD_AGSL =
            """
            uniform shader sdfAtlas;
            uniform float2 gridSize;
            uniform float distanceRange;
            uniform float4 sdfDomain;

            uniform float2 componentSize;
            uniform float4 railRect;
            uniform float railRadius;
            uniform float railOutlineWidth;
            uniform float selectedOutlineWidth;
            uniform float dividerWidth;
            uniform float dividerInset;
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

            layout(color) uniform half4 neutralTop;
            layout(color) uniform half4 neutralBottom;
            layout(color) uniform half4 railColor;
            layout(color) uniform half4 dividerColor;
            layout(color) uniform half4 materialTop;
            layout(color) uniform half4 materialBottom;
            layout(color) uniform half4 materialOutline;
            layout(color) uniform half4 materialHighlight;
            layout(color) uniform half4 shadowColor;
            layout(color) uniform half4 pressedColor;
            layout(color) uniform half4 focusColor;

            float roundedRectSdf(float2 point, float2 center, float2 halfSize, float radius) {
                float safeRadius = min(radius, min(halfSize.x, halfSize.y));
                float2 q = abs(point - center) - halfSize + safeRadius;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - safeRadius;
            }

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

            float selectedDistance(float2 logical) {
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
                return mix(first, second, blend) * componentSize.y;
            }

            float selectedEdgeLight(float2 logical, float distancePx) {
                float2 logicalStep = (sdfDomain.zw - sdfDomain.xy) / (gridSize - 1.0);
                float horizontal = selectedDistance(logical + float2(logicalStep.x, 0.0))
                    - selectedDistance(logical - float2(logicalStep.x, 0.0));
                float vertical = selectedDistance(logical + float2(0.0, logicalStep.y))
                    - selectedDistance(logical - float2(0.0, logicalStep.y));
                float2 normal = normalize(float2(horizontal, vertical) + float2(0.0001));
                float facingLight = max(dot(normal, normalize(float2(-0.42, -0.91))), 0.0);
                float innerEdge = 1.0 - smoothstep(0.0, 5.0, -distancePx);
                return facingLight * innerEdge;
            }

            float dividerCoverage(float2 point, float railCoverage, float selectedCoverage) {
                float localX = (point.x - railRect.x) / railRect.z;
                float localY = point.y - railRect.y;
                float verticalMask = step(dividerInset, localY)
                    * step(localY, railRect.w - dividerInset);
                float first = 1.0 - smoothstep(
                    dividerWidth * 0.5,
                    dividerWidth * 0.5 + 1.0,
                    abs((localX - 0.3333333) * railRect.z)
                );
                float second = 1.0 - smoothstep(
                    dividerWidth * 0.5,
                    dividerWidth * 0.5 + 1.0,
                    abs((localX - 0.6666667) * railRect.z)
                );
                return max(first, second) * verticalMask * railCoverage * (1.0 - selectedCoverage);
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
                float localX = clamp((point.x - railRect.x) / railRect.z, 0.0, 0.99999);
                float cellPosition = fract(localX * 3.0);
                float distanceToVerticalEdge = min(cellPosition, 1.0 - cellPosition)
                    * railRect.z / 3.0;
                float verticalBand = 1.0 - smoothstep(
                    focusOutlineWidth - 1.0,
                    focusOutlineWidth + 1.0,
                    distanceToVerticalEdge
                );
                float outerBand = 1.0 - smoothstep(
                    focusOutlineWidth - 1.0,
                    focusOutlineWidth + 1.0,
                    abs(railDistance)
                );
                return max(verticalBand, outerBand) * railCoverage
                    * (1.0 - selectedCoverage) * focusAmount;
            }

            float softCoverage(float distancePx, float softnessPx) {
                return 1.0 - smoothstep(-softnessPx, softnessPx, distancePx);
            }

            float selectedShadowCoverage(float2 point, float offsetY, float softnessPx) {
                float2 shadowLogical = logicalPosition(point - float2(0.0, offsetY));
                return softCoverage(selectedDistance(shadowLogical), softnessPx);
            }

            float railShadowCoverage(float2 point, float offsetY, float softnessPx) {
                float2 railCenter = railRect.xy + railRect.zw * 0.5 + float2(0.0, offsetY);
                float distance = roundedRectSdf(
                    point,
                    railCenter,
                    railRect.zw * 0.5,
                    railRadius
                );
                return softCoverage(distance, softnessPx);
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
                float2 railCenter = railRect.xy + railRect.zw * 0.5;
                float railDistance = roundedRectSdf(
                    point,
                    railCenter,
                    railRect.zw * 0.5,
                    railRadius
                );
                float railCoverage = 1.0 - smoothstep(-antialiasPx, antialiasPx, railDistance);

                float2 logical = logicalPosition(point);
                float materialDistance = selectedDistance(logical);
                float selectedCoverage = 1.0
                    - smoothstep(-antialiasPx, antialiasPx, materialDistance);
                float selectedOutline = 1.0 - smoothstep(
                    selectedOutlineWidth - antialiasPx,
                    selectedOutlineWidth + antialiasPx,
                    abs(materialDistance)
                );
                float selectedOcclusion = max(selectedCoverage, selectedOutline);
                // The selected material and the neutral rail are complementary pieces of one
                // selector.  Do not paint a complete rail and cover it with the material: that
                // leaves a second surface underneath the selected shape and reads as an overlay.
                float selectorCoverage = max(railCoverage, selectedCoverage);
                float neutralCoverage = selectorCoverage - selectedCoverage;

                float railY = clamp((point.y - railRect.y) / railRect.w, 0.0, 1.0);
                half4 neutral = mix(neutralTop, neutralBottom, half(railY));
                half4 material = mix(
                    materialTop,
                    materialBottom,
                    half(smoothstep(0.05, 0.95, railY))
                );
                float edgeLight = selectedEdgeLight(logical, materialDistance) * selectedCoverage;
                material = mix(material, materialHighlight, half(edgeLight * 0.24));
                half4 result = half4(0.0);

                float railPenumbra = railShadowCoverage(
                    point,
                    penumbraShadowOffset,
                    penumbraShadowSoftness
                );
                float railContact = railShadowCoverage(
                    point,
                    contactShadowOffset,
                    contactShadowSoftness
                );
                float selectedPenumbra = selectedShadowCoverage(
                    point,
                    penumbraShadowOffset,
                    penumbraShadowSoftness
                );
                float selectedContact = selectedShadowCoverage(
                    point,
                    contactShadowOffset,
                    contactShadowSoftness
                );

                // The outside shadow belongs to the union silhouette, not to two stacked cards.
                float selectorPenumbra = max(
                    railPenumbra * 0.32,
                    selectedPenumbra * 0.72
                ) * (1.0 - selectorCoverage);
                float selectorContact = max(
                    railContact * 0.22,
                    selectedContact * 0.48
                ) * (1.0 - selectorCoverage);
                result = compositeOver(result, coveredColor(shadowColor, selectorPenumbra));
                result = compositeOver(result, coveredColor(shadowColor, selectorContact));
                // The two coverages sum to the union silhouette.  Add their premultiplied
                // contributions into one base layer so antialiased boundary pixels are neither
                // stacked nor made translucent by source-over compositing.
                half4 basePartition = coveredColor(neutral, neutralCoverage)
                    + coveredColor(material, selectedCoverage);
                result = compositeOver(result, basePartition);

                float localCellX = (point.x - railRect.x) / railRect.z;
                float neutralPressAmount = cellFraction(cellPress, localCellX);
                float neutralFocusAmount = cellFraction(cellFocus, localCellX);
                result = compositeOver(
                    result,
                    coveredColor(pressedColor, neutralCoverage * neutralPressAmount)
                );
                result = compositeOver(
                    result,
                    coveredColor(focusColor, neutralCoverage * neutralFocusAmount * 0.08)
                );
                float neutralFocusOutline = neutralFocusBand(
                    point,
                    railDistance,
                    railCoverage,
                    selectedOcclusion,
                    neutralFocusAmount
                );
                result = compositeOver(result, coveredColor(focusColor, neutralFocusOutline));

                float divider = dividerCoverage(point, railCoverage, selectedOcclusion);
                result = compositeOver(result, coveredColor(dividerColor, divider));

                result = compositeOver(
                    result,
                    coveredColor(pressedColor, selectedCoverage * selectedPress)
                );

                result = compositeOver(result, coveredColor(materialOutline, selectedOutline));
                float selectedFocusOutline = 1.0 - smoothstep(
                    selectedOutlineWidth + focusOutlineWidth - antialiasPx,
                    selectedOutlineWidth + focusOutlineWidth + antialiasPx,
                    abs(materialDistance)
                );
                result = compositeOver(
                    result,
                    coveredColor(focusColor, selectedFocusOutline * selectedFocus)
                );

                float railOutline = 1.0 - smoothstep(
                    railOutlineWidth - antialiasPx,
                    railOutlineWidth + antialiasPx,
                    abs(railDistance)
                );
                railOutline *= 1.0 - selectedOcclusion;
                result = compositeOver(result, coveredColor(railColor, railOutline));
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
