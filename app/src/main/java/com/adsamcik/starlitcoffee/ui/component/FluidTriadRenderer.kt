package com.adsamcik.starlitcoffee.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

internal data class FluidTriadRendererContent(
    val items: List<FluidTriadItem>,
    val icons: List<Painter>,
    val textMeasurer: TextMeasurer,
    val labelStyle: TextStyle,
    val valueStyle: TextStyle,
    val iconSize: Dp,
)

internal class FluidTriadRendererAnimation(
    val transition: () -> FluidTriadTransitionSnapshot,
    val transitionProgress: () -> Float,
    val waterGust: () -> Float,
    val selected: () -> CalculatorQuantityTarget,
    val pressed: List<() -> Boolean>,
    val focused: List<() -> Boolean>,
    val mirrorHorizontally: Boolean,
    val shaderResources: FluidTriadShaderResources?,
)

internal data class FluidTriadMaterialColors(
    val fill: Color,
    val content: Color,
    val edge: Color,
    val highlight: Color,
    val shade: Color,
)

internal data class FluidTriadSurfaceColors(
    val coffee: FluidTriadMaterialColors,
    val water: FluidTriadMaterialColors,
    val cup: FluidTriadMaterialColors,
    val railTop: Color,
    val railBottom: Color,
    val railOutline: Color,
    val neutralContent: Color,
    val neutralCoffeeIcon: Color,
    val neutralWaterIcon: Color,
    val neutralCupIcon: Color,
    val stateLayer: Color,
    val focus: Color,
    val shadow: Color,
)

private data class FluidTriadTextLayouts(
    val label: TextLayoutResult,
    val value: TextLayoutResult,
)

private data class FluidTriadRendererMetrics(
    val viewport: FluidTriadResolvedRailViewport,
    val radius: Float,
    val lineWidth: Float,
    val iconSize: Float,
    val contentGap: Float,
    val contactShadowOffset: Float,
    val penumbraShadowOffset: Float,
)

private data class FluidTriadShaderContentLayerKey(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
    val items: List<FluidTriadItem>,
    val icons: List<Painter>,
    val textMeasurer: TextMeasurer,
    val labelStyle: TextStyle,
    val valueStyle: TextStyle,
    val iconSize: Dp,
    val colors: FluidTriadSurfaceColors,
    val metrics: FluidTriadRendererMetrics,
)

/**
 * Selector-lifetime foreground texture owner.
 *
 * The Compose draw cache is intentionally free to rebuild when animation inputs change. This
 * smaller cache survives those rebuilds and only replaces the three endpoint textures when an
 * input that changes their pixels changes.
 */
internal class FluidTriadShaderContentLayerCache : AutoCloseable {
    private var key: Any? = null
    private var layers: FluidTriadShaderContentLayers? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getOrCreate(
        requestedKey: Any,
        create: () -> FluidTriadShaderContentLayers,
    ): FluidTriadShaderContentLayers {
        val current = layers
        if (current != null && key == requestedKey) return current

        val replacement = create()
        replacement.releaseAfterNextBind(current)
        key = requestedKey
        layers = replacement
        return replacement
    }

    override fun close() {
        layers?.close()
        layers = null
        key = null
    }
}

private data class FluidTriadRendererBrushes(
    val rail: Brush,
    val surfaceLight: Brush,
)

private data class FluidTriadContentGroupVisual(
    val icon: Painter,
    val iconFilter: MutableIconFilter,
    val layouts: FluidTriadTextLayouts,
    val neutralIconColor: Color,
    val neutralTextColor: Color,
)

private data class FluidTriadMovingContentState(
    val anchor: Offset,
    val color: Color,
    val groupScale: Float,
    val rotationDegrees: Float,
    val iconScale: Float,
)

private data class FluidTriadSelectedSurfaceStyle(
    val fill: Color,
    val edge: Color,
    val highlight: Color,
    val shade: Color,
)

private class MutableIconFilter {
    private var tint = Color.Unspecified
    private var filter: ColorFilter? = null

    fun resolve(color: Color): ColorFilter {
        if (filter == null || tint != color) {
            tint = color
            filter = ColorFilter.tint(color)
        }
        return requireNotNull(filter)
    }
}

private class FluidTriadRendererCache {
    val railPath = Path()
    val bodyPath = Path()
    val topEdgePath = Path()
    val handlePath = Path().apply { fillType = PathFillType.EvenOdd }
    val selectedMaterialPath = Path()
    val selectedMaterialScratchPath = Path()
    val selectorUnionPath = Path()
    val accentPath = Path()
    val points = FloatArray(FluidTriadVisualSpec.ContourCoordinateCount)
    val waterWaveNodes = FloatArray(WaterWaveNodeCount)
    val neutralPaths = Array(FluidTriadTargetOrder.size) { Path() }
    val materialPhasePaths = Array(FluidTriadTargetOrder.size) { Path() }
    val iconFilters = Array(FluidTriadTargetOrder.size) { MutableIconFilter() }
    val contentDrawOrder = IntArray(FluidTriadTargetOrder.size) { it }
    val metadata = FluidTriadRenderMetadata()
    lateinit var contentGroups: Array<FluidTriadContentGroupVisual>

    fun updateContentDrawOrder(metadata: FluidTriadRenderMetadata) {
        contentDrawOrder.indices.forEach { contentDrawOrder[it] = it }
        for (index in 1 until contentDrawOrder.size) {
            val candidate = contentDrawOrder[index]
            val candidateWeight = metadata.contentWeight(FluidTriadTargetOrder[candidate])
            var insertionIndex = index
            while (
                insertionIndex > 0 &&
                metadata.contentWeight(
                    FluidTriadTargetOrder[contentDrawOrder[insertionIndex - 1]],
                ) > candidateWeight
            ) {
                contentDrawOrder[insertionIndex] = contentDrawOrder[insertionIndex - 1]
                insertionIndex -= 1
            }
            contentDrawOrder[insertionIndex] = candidate
        }
    }
}

internal fun Modifier.fluidTriadRenderer(
    content: FluidTriadRendererContent,
    animation: FluidTriadRendererAnimation,
    colors: FluidTriadSurfaceColors,
    baseInset: Dp,
    shaderContentLayerCache: FluidTriadShaderContentLayerCache,
): Modifier = drawWithCache {
    val cache = FluidTriadRendererCache()
    val metrics = FluidTriadRendererCachePreparer.resolveMetrics(
        scope = this,
        content = content,
        animation = animation,
        baseInset = baseInset,
    )
    val layouts = FluidTriadRendererCachePreparer.measureTextLayouts(this, content, metrics)
    FluidTriadRendererCachePreparer.prepareContentGroups(cache, content, layouts, colors)
    FluidTriadRendererCachePreparer.prepareNeutralPaths(cache, metrics, size.height)
    FluidTriadRendererCachePreparer.prepareMaterialPhasePaths(cache, metrics, size.height)
    val brushes = FluidTriadRendererCachePreparer.createBrushes(this, colors, metrics)
    val shaderContentLayers = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        animation.shaderResources != null
    ) {
        resolveShaderContentLayers(
            layerCache = shaderContentLayerCache,
            rendererCache = cache,
            content = content,
            colors = colors,
            metrics = metrics,
        )
    } else {
        null
    }
    val shaderGeometry = FluidTriadShaderGeometry(
        componentWidth = size.width,
        componentHeight = size.height,
        railLeft = metrics.viewport.left,
        railTop = metrics.viewport.top,
        railWidth = metrics.viewport.width,
        railHeight = metrics.viewport.height,
        railOutlineWidth = metrics.lineWidth,
        isRtl = metrics.viewport.mirrorHorizontally,
        contactShadowOffsetY = metrics.contactShadowOffset,
        contactShadowSoftness = metrics.lineWidth * ShaderContactShadowSoftnessMultiplier,
        penumbraShadowOffsetY = metrics.penumbraShadowOffset,
        penumbraShadowSoftness = metrics.lineWidth * ShaderPenumbraShadowSoftnessMultiplier,
        focusOutlineWidth = metrics.lineWidth * FocusOutlineWidthMultiplier,
    )

    onDrawBehind {
        val progress = animation.transitionProgress().coerceIn(0f, 1f)
        val snapshot = animation.transition()
        FluidTriadFrameResolver.resolveMetadata(snapshot, progress, cache.metadata)
        updateWaterWaveNodes(
            out = cache.waterWaveNodes,
            transitionProgress = progress,
            gustProgress = animation.waterGust().coerceIn(0f, 1f),
            waterStrength = cache.metadata.waterMotion,
        )
        val usedShader = drawShaderPartition(
            cache = cache,
            animation = animation,
            colors = colors,
            geometry = shaderGeometry,
            snapshot = snapshot,
            progress = progress,
            contentLayers = shaderContentLayers,
        )
        if (!usedShader) {
            updateFluidTriadFallbackGeometry(cache, metrics, snapshot, progress)
            drawFallbackSelectorShadow(cache, colors.shadow, metrics)
            drawWholeControlSurface(
                cache = cache,
                colors = colors,
                metrics = metrics,
                railBrush = brushes.rail,
                surfaceLightBrush = brushes.surfaceLight,
            )
            drawIntegralContent(cache, content, colors, metrics, cache.metadata)
            drawInteractionLayers(cache, animation, colors, metrics)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun CacheDrawScope.resolveShaderContentLayers(
    layerCache: FluidTriadShaderContentLayerCache,
    rendererCache: FluidTriadRendererCache,
    content: FluidTriadRendererContent,
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadRendererMetrics,
): FluidTriadShaderContentLayers {
    val pixelWidth = ceil(size.width).toInt().coerceAtLeast(1)
    val pixelHeight = ceil(size.height).toInt().coerceAtLeast(1)
    val contentKey = FluidTriadShaderContentLayerKey(
        pixelWidth = pixelWidth,
        pixelHeight = pixelHeight,
        density = density,
        fontScale = fontScale,
        layoutDirection = layoutDirection,
        items = content.items.toList(),
        icons = content.icons.toList(),
        textMeasurer = content.textMeasurer,
        labelStyle = content.labelStyle,
        valueStyle = content.valueStyle,
        iconSize = content.iconSize,
        colors = colors,
        metrics = metrics,
    )
    return layerCache.getOrCreate(contentKey) {
        val canvasDrawScope = CanvasDrawScope()
        val endpointMetadata = FluidTriadRenderMetadata()
        val cacheScope = this
        val layers = Array(FluidTriadTargetOrder.size) { index ->
            val bitmap = createBitmap(pixelWidth, pixelHeight)
            FluidTriadFrameResolver.resolveEndpointMetadata(
                target = FluidTriadTargetOrder[index],
                out = endpointMetadata,
            )
            rendererCache.waterWaveNodes.fill(0f)
            canvasDrawScope.draw(
                density = cacheScope,
                layoutDirection = layoutDirection,
                canvas = Canvas(bitmap.asImageBitmap()),
                size = size,
            ) {
                drawIntegralContent(
                    rendererCache,
                    content,
                    colors,
                    metrics,
                    endpointMetadata,
                )
            }
            bitmap
        }
        FluidTriadShaderContentLayers(
            coffee = layers[0],
            water = layers[1],
            cup = layers[2],
        )
    }
}

private object FluidTriadRendererCachePreparer {
    fun resolveMetrics(
        scope: CacheDrawScope,
        content: FluidTriadRendererContent,
        animation: FluidTriadRendererAnimation,
        baseInset: Dp,
    ): FluidTriadRendererMetrics = with(scope) {
        val rail = FluidTriadVisualSpec.Rail
        val horizontalViewport = rail.resolve(
            width = size.width,
            height = size.height,
            trailingOverflowPx = rail.logicalTrailingOverflow.toPx(),
            mirrorHorizontally = animation.mirrorHorizontally,
        )
        val verticalInset = baseInset.toPx()
        val viewport = horizontalViewport.copy(
            top = verticalInset,
            bottom = size.height - verticalInset,
        )
        FluidTriadRendererMetrics(
            viewport = viewport,
            radius = minOf(rail.cornerRadius.toPx(), viewport.height / 2f),
            lineWidth = rail.outlineWidth.toPx(),
            iconSize = content.iconSize.toPx(),
            contentGap = 1.dp.toPx(),
            contactShadowOffset = FluidTriadVisualSpec.Lighting.contactShadowOffsetY.toPx(),
            penumbraShadowOffset = FluidTriadVisualSpec.Lighting.penumbraShadowOffsetY.toPx(),
        )
    }

    fun measureTextLayouts(
        scope: CacheDrawScope,
        content: FluidTriadRendererContent,
        metrics: FluidTriadRendererMetrics,
    ): Array<FluidTriadTextLayouts> = with(scope) {
        val viewport = metrics.viewport
        val neutralTextWidth = (viewport.width / FluidTriadTargetOrder.size - 16.dp.toPx())
            .toInt()
            .coerceAtLeast(1)
        Array(FluidTriadTargetOrder.size) { index ->
            val item = content.items[index]
            val panel = FluidTriadVisualSpec.panel(item.target)
            val materialTextWidth = (
                viewport.width * (panel.content.safeRight - panel.content.safeLeft) - 8.dp.toPx()
                ).toInt().coerceAtLeast(1)
            val maxTextWidth = minOf(neutralTextWidth, materialTextWidth)
            FluidTriadTextLayouts(
                label = content.textMeasurer.measure(
                    text = AnnotatedString(item.label),
                    style = content.labelStyle.copy(
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = maxTextWidth),
                ),
                value = content.textMeasurer.measure(
                    text = item.formattedValue(content.valueStyle, FontWeight.Bold),
                    style = content.valueStyle.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxTextWidth),
                ),
            )
        }
    }

    fun prepareContentGroups(
        cache: FluidTriadRendererCache,
        content: FluidTriadRendererContent,
        layouts: Array<FluidTriadTextLayouts>,
        colors: FluidTriadSurfaceColors,
    ) {
        cache.contentGroups = Array(FluidTriadTargetOrder.size) { index ->
            val target = FluidTriadTargetOrder[index]
            FluidTriadContentGroupVisual(
                icon = content.icons[index],
                iconFilter = cache.iconFilters[index],
                layouts = layouts[index],
                neutralIconColor = colors.neutralIconColor(target),
                neutralTextColor = colors.neutralContent,
            )
        }
    }

    fun prepareNeutralPaths(
        cache: FluidTriadRendererCache,
        metrics: FluidTriadRendererMetrics,
        componentHeight: Float,
    ) {
        FluidTriadTargetOrder.forEachIndexed { index, target ->
            cache.neutralPaths[index].updateContour(
                contour = FluidTriadVisualSpec.panel(target).neutralContour,
                viewport = metrics.viewport,
                componentHeight = componentHeight,
            )
        }
    }

    fun prepareMaterialPhasePaths(
        cache: FluidTriadRendererCache,
        metrics: FluidTriadRendererMetrics,
        componentHeight: Float,
    ) {
        FluidTriadTargetOrder.forEachIndexed { index, target ->
            cache.materialPhasePaths[index].updateMaterialPhase(
                target = target,
                viewport = metrics.viewport,
                componentHeight = componentHeight,
            )
        }
    }

    fun createBrushes(
        scope: CacheDrawScope,
        colors: FluidTriadSurfaceColors,
        metrics: FluidTriadRendererMetrics,
    ): FluidTriadRendererBrushes = with(scope) {
        val railBrush = Brush.verticalGradient(
            colors = listOf(colors.railTop, colors.railBottom),
            startY = metrics.viewport.top,
            endY = metrics.viewport.bottom,
        )
        val surfaceLightBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = SurfaceTopLightAlpha),
                0.52f to Color.Transparent,
                1f to Color.Black.copy(alpha = SurfaceBottomShadeAlpha),
            ),
            startY = 0f,
            endY = size.height,
        )
        FluidTriadRendererBrushes(railBrush, surfaceLightBrush)
    }
}

private fun FluidTriadItem.formattedValue(
    baseStyle: TextStyle,
    weight: FontWeight,
): AnnotatedString {
    val prefix = if (approximate && !value.startsWith(ApproximationSign)) ApproximationSign else ""
    val displayed = prefix + value
    if (!displayed.endsWith(UnitSuffix) || displayed == UnitSuffix) {
        return AnnotatedString(displayed)
    }
    return buildAnnotatedString {
        append(displayed.dropLast(UnitSuffix.length))
        withStyle(
            SpanStyle(
                fontSize = baseStyle.fontSize * UnitScale,
                fontWeight = weight,
            ),
        ) {
            append(ThinSpace)
            append(UnitSuffix)
        }
    }
}

private fun DrawScope.updateFluidTriadFallbackGeometry(
    cache: FluidTriadRendererCache,
    metrics: FluidTriadRendererMetrics,
    snapshot: FluidTriadTransitionSnapshot,
    progress: Float,
) {
    val metadata = cache.metadata
    FluidTriadFrameResolver.resolveContourInto(snapshot, progress, cache.points)
    applyWaterDisplacement(
        points = cache.points,
        nodes = cache.waterWaveNodes,
        strength = cache.metadata.waterMotion,
    )
    cache.bodyPath.updateContour(cache.points, metrics.viewport, size.height)
    cache.topEdgePath.updateTopEdge(cache.points, metrics.viewport, size.height)
    cache.handlePath.updateCupHandle(
        spec = FluidTriadVisualSpec.CupHandle,
        progress = metadata.cupHandle,
        attachmentLogicalX = cache.points[RightMidpointCoordinateIndex],
        viewport = metrics.viewport,
        componentHeight = size.height,
    )
    cache.selectedMaterialPath.reset()
    cache.selectedMaterialPath.addPath(cache.bodyPath)
    if (metadata.cupHandle > HandleVisibilityThreshold) {
        check(
            cache.selectedMaterialScratchPath.op(
                cache.bodyPath,
                cache.handlePath,
                PathOperation.Union,
            ),
        )
        cache.selectedMaterialPath.reset()
        cache.selectedMaterialPath.addPath(cache.selectedMaterialScratchPath)
    }
    cache.railPath.reset()
    cache.railPath.addRoundRect(
        RoundRect(
            rect = Rect(
                left = metrics.viewport.left,
                top = metrics.viewport.top,
                right = metrics.viewport.right,
                bottom = metrics.viewport.bottom,
            ),
            cornerRadius = CornerRadius(metrics.radius, metrics.radius),
        ),
    )
    check(
        cache.selectorUnionPath.op(
            cache.railPath,
            cache.selectedMaterialPath,
            PathOperation.Union,
        ),
    )
}

private fun DrawScope.drawShaderPartition(
    cache: FluidTriadRendererCache,
    animation: FluidTriadRendererAnimation,
    colors: FluidTriadSurfaceColors,
    geometry: FluidTriadShaderGeometry,
    snapshot: FluidTriadTransitionSnapshot,
    progress: Float,
    contentLayers: FluidTriadShaderContentLayers?,
): Boolean {
    val resources = animation.shaderResources ?: return false
    val resolvedContentLayers = contentLayers ?: return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return drawShaderPartitionApi33(
        resources = resources,
        cache = cache,
        animation = animation,
        colors = colors,
        geometry = geometry,
        snapshot = snapshot,
        progress = progress,
        contentLayers = resolvedContentLayers,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawShaderPartitionApi33(
    resources: FluidTriadShaderResources,
    cache: FluidTriadRendererCache,
    animation: FluidTriadRendererAnimation,
    colors: FluidTriadSurfaceColors,
    geometry: FluidTriadShaderGeometry,
    snapshot: FluidTriadTransitionSnapshot,
    progress: Float,
    contentLayers: FluidTriadShaderContentLayers,
): Boolean {
    val sourceTarget = snapshot.sourceFrame.stableTarget
    val destinationTarget = snapshot.destinationTarget
    val shaderProgress = when {
        progress >= 1f && destinationTarget == CalculatorQuantityTarget.WATER_IN &&
            animation.waterGust() > 0f -> {
            resources.bindWaterIdle()
            animation.waterGust().coerceIn(0f, 1f)
        }

        progress >= 1f || sourceTarget == destinationTarget -> {
            resources.bindEndpoint(destinationTarget)
            0f
        }

        sourceTarget != null -> {
            if (resources.shouldCaptureActiveSource(sourceTarget)) {
                if (!resources.bindInterrupted(destinationTarget)) return false
            } else {
                resources.bindRoute(sourceTarget, destinationTarget)
            }
            progress
        }

        resources.bindInterrupted(destinationTarget) -> progress

        else -> return false
    }
    val metadata = cache.metadata
    val fill = colors.weightedMaterialColor(metadata) { it.fill }
    val shade = colors.weightedMaterialColor(metadata) { it.shade }
    val highlight = colors.weightedMaterialColor(metadata) { it.highlight }
    val palette = FluidTriadShaderPalette(
        neutralTop = colors.railTop,
        neutralBottom = colors.railBottom,
        railOutline = colors.railOutline.copy(alpha = RailOutlineAlpha),
        materialTop = lerp(fill, highlight, ShaderMaterialTopBlend),
        materialBottom = lerp(fill, shade, ShaderMaterialBottomBlend),
        materialOutline = colors.weightedMaterialColor(metadata) { it.edge }
            .copy(alpha = MaterialOutlineAlpha),
        materialHighlight = highlight,
        shadow = colors.shadow.copy(alpha = ShaderShadowAlpha),
        pressedOverlay = colors.stateLayer.copy(alpha = PressedStateLayerAlpha),
        focus = colors.focus,
    )
    resources.backend.draw(
        scope = this,
        geometry = geometry,
        palette = palette,
        progress = shaderProgress,
        contentLayers = contentLayers,
        metadata = metadata,
        waterGust = animation.waterGust(),
        interaction = resolveShaderInteraction(animation),
    )
    return true
}

private fun resolveShaderInteraction(
    animation: FluidTriadRendererAnimation,
): FluidTriadShaderInteraction {
    val selectedIndex = FluidTriadTargetOrder.indexOf(animation.selected())
    val selectedPress = if (animation.pressed[selectedIndex]()) 1f else 0f
    val selectedFocus = if (animation.focused[selectedIndex]()) 1f else 0f
    val coffeePress = if (selectedIndex != 0 && animation.pressed[0]()) 1f else 0f
    val waterPress = if (selectedIndex != 1 && animation.pressed[1]()) 1f else 0f
    val cupPress = if (selectedIndex != 2 && animation.pressed[2]()) 1f else 0f
    val coffeeFocus = if (selectedIndex != 0 && animation.focused[0]()) 1f else 0f
    val waterFocus = if (selectedIndex != 1 && animation.focused[1]()) 1f else 0f
    val cupFocus = if (selectedIndex != 2 && animation.focused[2]()) 1f else 0f
    val interactionSum = selectedPress + selectedFocus + coffeePress + waterPress + cupPress +
        coffeeFocus + waterFocus + cupFocus
    if (interactionSum == 0f) {
        return FluidTriadShaderInteraction.None
    }
    return FluidTriadShaderInteraction(
        selectedPress = selectedPress,
        selectedFocus = selectedFocus,
        neutralPress = FluidTriadShaderCellFractions(coffeePress, waterPress, cupPress),
        neutralFocus = FluidTriadShaderCellFractions(coffeeFocus, waterFocus, cupFocus),
    )
}

private fun DrawScope.drawFallbackSelectorShadow(
    cache: FluidTriadRendererCache,
    shadow: Color,
    metrics: FluidTriadRendererMetrics,
) {
    // Both neutral and selected fills cover this path afterwards.  Rendering one union shadow
    // first prevents either material from becoming a card layered over the other.
    withTransform({ translate(top = metrics.penumbraShadowOffset) }) {
        drawPath(
            path = cache.selectorUnionPath,
            color = shadow.copy(alpha = RailPenumbraAlpha),
            style = Stroke(
                width = metrics.lineWidth * PenumbraStrokeMultiplier,
                join = StrokeJoin.Round,
            ),
        )
    }
    withTransform({ translate(top = metrics.contactShadowOffset) }) {
        drawPath(
            path = cache.selectorUnionPath,
            color = shadow.copy(alpha = RailContactAlpha),
        )
    }
}

private fun DrawScope.drawWholeControlSurface(
    cache: FluidTriadRendererCache,
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadRendererMetrics,
    railBrush: Brush,
    surfaceLightBrush: Brush,
) {
    val metadata = cache.metadata
    val style = FluidTriadSelectedSurfaceStyle(
        fill = colors.weightedMaterialColor(metadata) { it.fill },
        edge = colors.weightedMaterialColor(metadata) { it.edge },
        highlight = colors.weightedMaterialColor(metadata) { it.highlight },
        shade = colors.weightedMaterialColor(metadata) { it.shade },
    )
    clipPath(cache.selectorUnionPath) {
        drawRect(brush = railBrush)
        FluidTriadTargetOrder.forEachIndexed { index, target ->
            val weight = metadata.materialWeight(target).coerceIn(0f, 1f)
            if (weight > ContentVisibilityThreshold) {
                drawPath(
                    path = cache.materialPhasePaths[index],
                    color = style.fill.copy(alpha = weight),
                )
                drawPath(
                    path = cache.materialPhasePaths[index],
                    brush = surfaceLightBrush,
                    alpha = weight,
                )
            }
        }
        drawMaterialEffects(cache, style.edge, style.highlight, metrics)

        clipRect(bottom = size.height / 2f) {
            drawPath(
                path = cache.selectorUnionPath,
                color = Color.White.copy(alpha = WholeControlTopHighlightAlpha),
                style = Stroke(
                    width = metrics.lineWidth * HighlightStrokeMultiplier,
                    join = StrokeJoin.Round,
                ),
            )
        }
        clipRect(top = size.height / 2f) {
            drawPath(
                path = cache.selectorUnionPath,
                color = style.shade.copy(alpha = WholeControlBottomShadeAlpha),
                style = Stroke(
                    width = metrics.lineWidth * BottomShadeStrokeMultiplier,
                    join = StrokeJoin.Round,
                ),
            )
        }
        drawPath(
            path = cache.selectorUnionPath,
            color = lerp(
                colors.railOutline,
                style.edge,
                WholeControlMaterialEdgeBlend,
            ).copy(alpha = WholeControlOutlineAlpha),
            style = Stroke(width = metrics.lineWidth * 2f, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawMaterialEffects(
    cache: FluidTriadRendererCache,
    edge: Color,
    highlight: Color,
    metrics: FluidTriadRendererMetrics,
) {
    val metadata = cache.metadata
    if (metadata.coffeeCrease > EffectVisibilityThreshold) {
        cache.accentPath.updateCoffeeCrease(metrics.viewport, size.height)
        drawPath(
            path = cache.accentPath,
            color = edge.copy(alpha = CoffeeCreaseAlpha * metadata.coffeeCrease),
            style = Stroke(width = 1.15.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    if (metadata.waterMotion > EffectVisibilityThreshold) {
        drawPath(
            path = cache.topEdgePath,
            color = highlight.copy(alpha = WaterEdgeHighlightAlpha * metadata.waterMotion),
            style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    if (metadata.cupRim > EffectVisibilityThreshold) {
        drawPath(
            path = cache.topEdgePath,
            color = highlight.copy(alpha = CupTopHighlightAlpha * metadata.cupRim),
            style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawIntegralContent(
    cache: FluidTriadRendererCache,
    content: FluidTriadRendererContent,
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadRendererMetrics,
    metadata: FluidTriadRenderMetadata,
) {
    val centerNode = cache.waterWaveNodes.lastIndex / 2
    val centerWave = cache.waterWaveNodes[centerNode] * 0.50f +
        cache.waterWaveNodes[centerNode - 1] * 0.25f +
        cache.waterWaveNodes[centerNode + 1] * 0.25f
    val movingState = FluidTriadMovingContentState(
        anchor = Offset(
            x = metrics.viewport.mapLogicalX(metadata.contentAnchorX),
            y = metadata.contentAnchorY * size.height +
                centerWave * size.height * WaterContentResponse * metadata.waterMotion,
        ),
        color = colors.weightedMaterialColor(metadata) { it.content },
        groupScale = metadata.contentGroupScale,
        rotationDegrees = if (metrics.viewport.mirrorHorizontally) {
            -metadata.contentRotationDegrees
        } else {
            metadata.contentRotationDegrees
        },
        iconScale = metadata.contentIconScale,
    )
    cache.updateContentDrawOrder(metadata)
    cache.contentDrawOrder.forEach { index ->
        val target = FluidTriadTargetOrder[index]
        val item = content.items[index]
        drawContentGroup(
            visual = cache.contentGroups[index],
            materialWeight = metadata.contentWeight(target),
            alpha = if (item.enabled) 1f else DisabledContentAlpha,
            neutralAnchor = Offset(
                x = metrics.viewport.mapLogicalX(neutralAnchorX(target)),
                y = size.height / 2f,
            ),
            movingState = movingState,
            metrics = metrics,
        )
    }
}

private fun DrawScope.drawContentGroup(
    visual: FluidTriadContentGroupVisual,
    materialWeight: Float,
    alpha: Float,
    neutralAnchor: Offset,
    movingState: FluidTriadMovingContentState,
    metrics: FluidTriadRendererMetrics,
) {
    if (alpha <= ContentVisibilityThreshold) return
    val weight = materialWeight.coerceIn(0f, 1f)
    val anchor = Offset(
        x = lerpScalar(neutralAnchor.x, movingState.anchor.x, weight),
        y = lerpScalar(neutralAnchor.y, movingState.anchor.y, weight),
    )
    // One glyph layout travels with the material. Swapping typefaces at mid-transition causes a
    // visible width jump and breaks the illusion that this is the same integral foreground.
    val label = visual.layouts.label
    val value = visual.layouts.value
    val iconColor = lerp(visual.neutralIconColor, movingState.color, weight)
    val textColor = lerp(visual.neutralTextColor, movingState.color, weight)
    val groupScale = lerpScalar(1f, movingState.groupScale, weight)
    val rotationDegrees = movingState.rotationDegrees * weight
    val iconScale = lerpScalar(1f, movingState.iconScale, weight)
    val totalHeight = metrics.iconSize + metrics.contentGap +
        label.size.height + value.size.height
    val iconTop = anchor.y - totalHeight / 2f
    withTransform({
        scale(groupScale, groupScale, anchor)
        rotate(rotationDegrees, anchor)
    }) {
        val iconCenter = Offset(anchor.x, iconTop + metrics.iconSize / 2f)
        withTransform({
            scale(iconScale, iconScale, iconCenter)
            translate(
                left = anchor.x - metrics.iconSize / 2f,
                top = iconTop,
            )
        }) {
            with(visual.icon) {
                draw(
                    size = Size(metrics.iconSize, metrics.iconSize),
                    alpha = alpha,
                    colorFilter = visual.iconFilter.resolve(iconColor),
                )
            }
        }
        val labelTop = iconTop + metrics.iconSize + metrics.contentGap
        drawText(
            textLayoutResult = label,
            color = textColor.copy(alpha = textColor.alpha * alpha),
            topLeft = Offset(
                x = anchor.x - label.size.width / 2f,
                y = labelTop,
            ),
        )
        drawText(
            textLayoutResult = value,
            color = textColor.copy(alpha = textColor.alpha * alpha),
            topLeft = Offset(
                x = anchor.x - value.size.width / 2f,
                y = labelTop + label.size.height,
            ),
        )
    }
}

private fun DrawScope.drawInteractionLayers(
    cache: FluidTriadRendererCache,
    animation: FluidTriadRendererAnimation,
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadRendererMetrics,
) {
    animation.pressed.forEachIndexed { index, pressed ->
        if (!pressed()) return@forEachIndexed
        clipPath(cache.selectorUnionPath) {
            drawPath(
                path = cache.neutralPaths[index],
                color = colors.stateLayer.copy(alpha = PressedStateLayerAlpha),
            )
        }
    }
    var hasFocus = false
    animation.focused.forEachIndexed { index, focused ->
        if (!focused()) return@forEachIndexed
        hasFocus = true
        clipPath(cache.selectorUnionPath) {
            drawPath(
                path = cache.neutralPaths[index],
                color = colors.focus.copy(alpha = FocusStateLayerAlpha),
            )
        }
    }
    if (hasFocus) {
        drawPath(
            path = cache.selectorUnionPath,
            color = colors.focus,
            style = Stroke(
                width = metrics.lineWidth * FocusOutlineWidthMultiplier,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun Path.updateContour(
    contour: FluidTriadContourSpec,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    moveTo(
        viewport.mapLogicalX(contour[0]),
        contour[1] * componentHeight,
    )
    var point = 1
    repeat(FluidTriadVisualSpec.CubicSegmentCount) {
        cubicTo(
            viewport.mapLogicalX(contour[point * 2]),
            contour[point * 2 + 1] * componentHeight,
            viewport.mapLogicalX(contour[(point + 1) * 2]),
            contour[(point + 1) * 2 + 1] * componentHeight,
            viewport.mapLogicalX(contour[(point + 2) * 2]),
            contour[(point + 2) * 2 + 1] * componentHeight,
        )
        point += 3
    }
    close()
}

private fun Path.updateMaterialPhase(
    target: CalculatorQuantityTarget,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    when (target) {
        CalculatorQuantityTarget.COFFEE -> {
            moveTo(viewport.mapLogicalX(OwnershipLogicalStart), 0f)
            lineTo(
                viewport.mapLogicalX(FluidTriadWholeControlField.coffeeBoundary(0f)),
                0f,
            )
            repeat(OwnershipCurveSamples) { sample ->
                val y = (sample + 1f) / OwnershipCurveSamples
                lineTo(
                    viewport.mapLogicalX(FluidTriadWholeControlField.coffeeBoundary(y)),
                    y * componentHeight,
                )
            }
            lineTo(viewport.mapLogicalX(OwnershipLogicalStart), componentHeight)
        }

        CalculatorQuantityTarget.WATER_IN -> {
            moveTo(
                viewport.mapLogicalX(0.5f - FluidTriadWholeControlField.waterHalfWidth(0f)),
                0f,
            )
            lineTo(
                viewport.mapLogicalX(0.5f + FluidTriadWholeControlField.waterHalfWidth(0f)),
                0f,
            )
            repeat(OwnershipCurveSamples) { sample ->
                val y = (sample + 1f) / OwnershipCurveSamples
                lineTo(
                    viewport.mapLogicalX(
                        0.5f + FluidTriadWholeControlField.waterHalfWidth(y),
                    ),
                    y * componentHeight,
                )
            }
            repeat(OwnershipCurveSamples) { sample ->
                val y = 1f - (sample + 1f) / OwnershipCurveSamples
                lineTo(
                    viewport.mapLogicalX(
                        0.5f - FluidTriadWholeControlField.waterHalfWidth(y),
                    ),
                    y * componentHeight,
                )
            }
        }

        CalculatorQuantityTarget.IN_CUP -> {
            moveTo(
                viewport.mapLogicalX(FluidTriadWholeControlField.cupBoundary(0f)),
                0f,
            )
            lineTo(viewport.mapLogicalX(OwnershipLogicalEnd), 0f)
            lineTo(viewport.mapLogicalX(OwnershipLogicalEnd), componentHeight)
            lineTo(
                viewport.mapLogicalX(FluidTriadWholeControlField.cupBoundary(1f)),
                componentHeight,
            )
            repeat(OwnershipCurveSamples) { sample ->
                val y = 1f - (sample + 1f) / OwnershipCurveSamples
                lineTo(
                    viewport.mapLogicalX(FluidTriadWholeControlField.cupBoundary(y)),
                    y * componentHeight,
                )
            }
        }
    }
    close()
}

private fun Path.updateContour(
    points: FloatArray,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    moveTo(viewport.mapLogicalX(points[0]), points[1] * componentHeight)
    var point = 1
    repeat(FluidTriadVisualSpec.CubicSegmentCount) {
        cubicTo(
            viewport.mapLogicalX(points[point * 2]),
            points[point * 2 + 1] * componentHeight,
            viewport.mapLogicalX(points[(point + 1) * 2]),
            points[(point + 1) * 2 + 1] * componentHeight,
            viewport.mapLogicalX(points[(point + 2) * 2]),
            points[(point + 2) * 2 + 1] * componentHeight,
        )
        point += 3
    }
    close()
}

private fun Path.updateTopEdge(
    points: FloatArray,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    moveTo(viewport.mapLogicalX(points[0]), points[1] * componentHeight)
    var point = 1
    repeat(TopEdgeCubicCount) {
        cubicTo(
            viewport.mapLogicalX(points[point * 2]),
            points[point * 2 + 1] * componentHeight,
            viewport.mapLogicalX(points[(point + 1) * 2]),
            points[(point + 1) * 2 + 1] * componentHeight,
            viewport.mapLogicalX(points[(point + 2) * 2]),
            points[(point + 2) * 2 + 1] * componentHeight,
        )
        point += 3
    }
}

private fun Path.updateCupHandle(
    spec: FluidTriadCupHandleVisualSpec,
    progress: Float,
    attachmentLogicalX: Float,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    if (progress <= HandleVisibilityThreshold) return
    val logicalOffsetX = attachmentLogicalX - CupHandleBodyAttachmentX
    appendInterpolatedLoop(
        collapsed = spec.outerCollapsed,
        attached = spec.outerAttached,
        progress = progress,
        logicalOffsetX = logicalOffsetX,
        viewport = viewport,
        componentHeight = componentHeight,
    )
    appendInterpolatedLoop(
        collapsed = spec.innerCollapsed,
        attached = spec.innerAttached,
        progress = progress,
        logicalOffsetX = logicalOffsetX,
        viewport = viewport,
        componentHeight = componentHeight,
    )
}

private fun Path.appendInterpolatedLoop(
    collapsed: FluidTriadCubicLoopSpec,
    attached: FluidTriadCubicLoopSpec,
    progress: Float,
    logicalOffsetX: Float,
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    val t = progress.coerceIn(0f, 1f)
    moveTo(
        viewport.mapLogicalX(lerpScalar(collapsed[0], attached[0], t) + logicalOffsetX),
        lerpScalar(collapsed[1], attached[1], t) * componentHeight,
    )
    var point = 1
    repeat(FluidTriadCubicLoopSpec.CubicSegmentCount) {
        cubicTo(
            viewport.mapLogicalX(
                lerpScalar(collapsed[point * 2], attached[point * 2], t) + logicalOffsetX,
            ),
            lerpScalar(
                collapsed[point * 2 + 1],
                attached[point * 2 + 1],
                t,
            ) * componentHeight,
            viewport.mapLogicalX(
                lerpScalar(
                    collapsed[(point + 1) * 2],
                    attached[(point + 1) * 2],
                    t,
                ) + logicalOffsetX,
            ),
            lerpScalar(
                collapsed[(point + 1) * 2 + 1],
                attached[(point + 1) * 2 + 1],
                t,
            ) * componentHeight,
            viewport.mapLogicalX(
                lerpScalar(
                    collapsed[(point + 2) * 2],
                    attached[(point + 2) * 2],
                    t,
                ) + logicalOffsetX,
            ),
            lerpScalar(
                collapsed[(point + 2) * 2 + 1],
                attached[(point + 2) * 2 + 1],
                t,
            ) * componentHeight,
        )
        point += 3
    }
    close()
}

private fun Path.updateCoffeeCrease(
    viewport: FluidTriadResolvedRailViewport,
    componentHeight: Float,
) {
    reset()
    moveTo(viewport.mapLogicalX(0.246f), componentHeight * 0.24f)
    cubicTo(
        viewport.mapLogicalX(0.274f),
        componentHeight * 0.33f,
        viewport.mapLogicalX(0.278f),
        componentHeight * 0.42f,
        viewport.mapLogicalX(0.258f),
        componentHeight * 0.50f,
    )
    cubicTo(
        viewport.mapLogicalX(0.238f),
        componentHeight * 0.59f,
        viewport.mapLogicalX(0.257f),
        componentHeight * 0.68f,
        viewport.mapLogicalX(0.266f),
        componentHeight * 0.76f,
    )
}

private fun updateWaterWaveNodes(
    out: FloatArray,
    transitionProgress: Float,
    gustProgress: Float,
    waterStrength: Float,
) {
    val transitionEnergy = sin(PI.toFloat() * transitionProgress).coerceAtLeast(0f)
    val gustEnergy = sin(PI.toFloat() * gustProgress).coerceAtLeast(0f)
    for (index in out.indices) {
        if (index == 0 || index == out.lastIndex) {
            out[index] = 0f
            continue
        }
        val position = index.toFloat() / out.lastIndex
        val pinned = sin(PI.toFloat() * position)
        val entry = sin(position * TwoPi + transitionProgress * PI.toFloat() * 1.2f) *
            transitionEnergy * WaterTransitionAmplitude
        val gust = sin((position - gustProgress * 1.15f) * TwoPi) *
            gustEnergy * pinned * WaterGustAmplitude
        out[index] = (entry + gust) * waterStrength
    }
}

private fun applyWaterDisplacement(
    points: FloatArray,
    nodes: FloatArray,
    strength: Float,
) {
    if (strength <= EffectVisibilityThreshold) return
    var minX = points[0]
    var maxX = points[0]
    for (index in points.indices step 2) {
        minX = minOf(minX, points[index])
        maxX = maxOf(maxX, points[index])
    }
    val width = (maxX - minX).coerceAtLeast(MinimumContourWidth)
    for (index in points.indices step 2) {
        val normalizedX = ((points[index] - minX) / width).coerceIn(0f, 1f)
        val nodePosition = normalizedX * nodes.lastIndex
        val left = floor(nodePosition).toInt().coerceAtMost(nodes.lastIndex - 1)
        val local = nodePosition - left
        val wave = lerpScalar(nodes[left], nodes[left + 1], local)
        val y = points[index + 1]
        val response = when {
            y < 0.46f -> 1f
            y > 0.54f -> -0.30f
            else -> 0.10f
        }
        points[index + 1] = y + wave * response
    }
    points[points.lastIndex - 1] = points[0]
    points[points.lastIndex] = points[1]
}

internal fun fluidTriadSurfaceColors(scheme: ColorScheme): FluidTriadSurfaceColors {
    val dark = scheme.surface.luminance() < 0.5f
    val coffee = resolveMaterialColors(
        target = CalculatorQuantityTarget.COFFEE,
        schemeSeed = scheme.primaryContainer,
        dark = dark,
    )
    val water = resolveMaterialColors(
        target = CalculatorQuantityTarget.WATER_IN,
        schemeSeed = scheme.secondaryContainer,
        dark = dark,
    )
    val cup = resolveMaterialColors(
        target = CalculatorQuantityTarget.IN_CUP,
        schemeSeed = scheme.tertiaryContainer,
        dark = dark,
    )
    val warmRail = if (dark) Color(0xFF242123) else Color(0xFFFAF7F4)
    val railBase = lerp(scheme.surfaceContainerLow, warmRail, if (dark) 0.30f else 0.78f)
    val neutral = scheme.onSurfaceVariant
    return FluidTriadSurfaceColors(
        coffee = coffee,
        water = water,
        cup = cup,
        railTop = lerp(railBase, Color.White, if (dark) 0.025f else 0.16f),
        railBottom = lerp(railBase, Color.Black, if (dark) 0.035f else 0.018f),
        railOutline = lerp(
            scheme.outlineVariant,
            Color(0xFFA79E99),
            if (dark) 0.15f else 0.20f,
        ),
        neutralContent = neutral,
        neutralCoffeeIcon = lerp(neutral, coffee.edge, 0.36f),
        neutralWaterIcon = lerp(neutral, water.fill, 0.18f),
        neutralCupIcon = lerp(neutral, cup.fill, 0.30f),
        stateLayer = scheme.onSurface,
        focus = scheme.primary,
        shadow = if (dark) Color.Black else Color(0xFF403631),
    )
}

private fun resolveMaterialColors(
    target: CalculatorQuantityTarget,
    schemeSeed: Color,
    dark: Boolean,
): FluidTriadMaterialColors {
    val palette = FluidTriadVisualSpec.panel(target).palette
    val authoredFill = if (dark) palette.darkFill else palette.lightFill
    val content = if (dark) palette.darkContent else palette.lightContent
    var fill = lerp(schemeSeed, authoredFill, palette.schemeBlend)
    val contentLuminance = content.luminance()
    repeat(ContrastCorrectionSteps) {
        val fillLuminance = fill.luminance()
        val lighter = maxOf(fillLuminance, contentLuminance)
        val darker = minOf(fillLuminance, contentLuminance)
        if ((lighter + ContrastOffset) / (darker + ContrastOffset) < MinimumContentContrast) {
            val correction = if (contentLuminance > fillLuminance) Color.Black else Color.White
            fill = lerp(fill, correction, ContrastCorrectionFraction)
        }
    }
    return FluidTriadMaterialColors(
        fill = fill,
        content = content,
        edge = lerp(fill, Color.Black, if (dark) 0.18f else 0.10f),
        highlight = lerp(fill, Color.White, if (dark) 0.24f else 0.30f),
        shade = lerp(fill, Color.Black, if (dark) 0.20f else 0.16f),
    )
}

private fun FluidTriadSurfaceColors.neutralIconColor(
    target: CalculatorQuantityTarget,
): Color = when (target) {
    CalculatorQuantityTarget.COFFEE -> neutralCoffeeIcon
    CalculatorQuantityTarget.WATER_IN -> neutralWaterIcon
    CalculatorQuantityTarget.IN_CUP -> neutralCupIcon
}

private fun FluidTriadSurfaceColors.weightedMaterialColor(
    metadata: FluidTriadRenderMetadata,
    selector: (FluidTriadMaterialColors) -> Color,
): Color {
    val coffeeColor = selector(coffee)
    val waterColor = selector(water)
    val cupColor = selector(cup)
    return Color(
        red = coffeeColor.red * metadata.materialCoffeeWeight +
            waterColor.red * metadata.materialWaterWeight +
            cupColor.red * metadata.materialCupWeight,
        green = coffeeColor.green * metadata.materialCoffeeWeight +
            waterColor.green * metadata.materialWaterWeight +
            cupColor.green * metadata.materialCupWeight,
        blue = coffeeColor.blue * metadata.materialCoffeeWeight +
            waterColor.blue * metadata.materialWaterWeight +
            cupColor.blue * metadata.materialCupWeight,
        alpha = coffeeColor.alpha * metadata.materialCoffeeWeight +
            waterColor.alpha * metadata.materialWaterWeight +
            cupColor.alpha * metadata.materialCupWeight,
    )
}

private fun neutralAnchorX(target: CalculatorQuantityTarget): Float = when (target) {
    CalculatorQuantityTarget.COFFEE -> 1f / 6f
    CalculatorQuantityTarget.WATER_IN -> 0.5f
    CalculatorQuantityTarget.IN_CUP -> 5f / 6f
}

private fun lerpScalar(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private const val UnitSuffix = "g"
private const val ThinSpace = "\u2009"
private const val ApproximationSign = "≈"
private const val UnitScale = 0.62f
private const val RightMidpointCoordinateIndex = 24
private const val CupHandleBodyAttachmentX = 0.995f
private const val MinimumContentContrast = 4.5f
private const val ContrastOffset = 0.05f
private const val ContrastCorrectionSteps = 12
private const val ContrastCorrectionFraction = 0.04f
private const val WaterWaveNodeCount = 9
private const val TopEdgeCubicCount = 4
private const val MinimumContourWidth = 0.001f
private const val HandleVisibilityThreshold = 0.001f
private const val EffectVisibilityThreshold = 0.001f
private const val ContentVisibilityThreshold = 0.002f
private const val DisabledContentAlpha = 0.38f
private const val RailPenumbraAlpha = 0.035f
private const val RailContactAlpha = 0.075f
private const val RailOutlineAlpha = 0.50f
private const val MaterialOutlineAlpha = 0.46f
private const val SurfaceTopLightAlpha = 0.28f
private const val SurfaceBottomShadeAlpha = 0.050f
private const val WholeControlTopHighlightAlpha = 0.30f
private const val WholeControlBottomShadeAlpha = 0.08f
private const val WholeControlMaterialEdgeBlend = 0.72f
private const val WholeControlOutlineAlpha = 0.58f
private const val ShaderMaterialTopBlend = 0.18f
private const val ShaderMaterialBottomBlend = 0.13f
private const val ShaderShadowAlpha = 0.15f
private const val PenumbraStrokeMultiplier = 3.2f
private const val HighlightStrokeMultiplier = 1.8f
private const val BottomShadeStrokeMultiplier = 1.7f
private const val CoffeeCreaseAlpha = 0.34f
private const val WaterEdgeHighlightAlpha = 0.56f
private const val CupTopHighlightAlpha = 0.44f
private const val WaterContentResponse = 0.10f
private const val WaterTransitionAmplitude = 0.010f
private const val WaterGustAmplitude = 0.017f
private const val PressedStateLayerAlpha = 0.08f
private const val FocusStateLayerAlpha = 0.05f
private const val FocusOutlineWidthMultiplier = 2f
private const val ShaderContactShadowSoftnessMultiplier = 2f
private const val ShaderPenumbraShadowSoftnessMultiplier = 5f
private const val TwoPi = (PI * 2.0).toFloat()
private const val OwnershipCurveSamples = 12
private const val OwnershipLogicalStart = -0.10f
private const val OwnershipLogicalEnd = 1.10f
