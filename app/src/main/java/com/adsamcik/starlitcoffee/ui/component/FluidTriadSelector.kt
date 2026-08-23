package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A stable-layout, deforming-presentation selector for Coffee / Water in / In cup.
 *
 * Selection and hit testing live in three fixed equal cells. Only the background
 * presentation morphs; icons, labels, values, focus regions and semantics stay put.
 */
@Composable
fun FluidTriadSelector(
    items: List<FluidTriadItem>,
    selected: CalculatorQuantityTarget,
    onSelect: (CalculatorQuantityTarget) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    require(items.size == 3)
    require(items.map { it.target }.toSet().size == 3)

    val sourceInitial = remember(selected) { FluidTriadGeometry.profileFor(selected) }
    var source by remember { mutableStateOf(sourceInitial) }
    var destination by remember { mutableStateOf(sourceInitial) }
    var initialized by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }
    val transient = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (!initialized) {
            val stable = FluidTriadGeometry.profileFor(selected)
            source = stable
            destination = stable
            progress.snapTo(1f)
            transient.snapTo(0f)
            initialized = true
            return@LaunchedEffect
        }

        val rendered = FluidTriadGeometry.interpolate(source, destination, progress.value)
        progress.stop()
        transient.stop()
        source = rendered
        destination = FluidTriadGeometry.profileFor(selected)
        progress.snapTo(0f)
        transient.snapTo(1f)

        coroutineScope {
            launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = transitionSpring(selected),
                )
            }
            launch {
                transient.animateTo(
                    targetValue = 0f,
                    animationSpec = transientSpring(selected),
                )
            }
        }
        source = destination
        progress.snapTo(1f)
        transient.snapTo(0f)
    }

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val controlHeight = when {
        fontScale >= 1.5f -> 104.dp
        compact -> 76.dp
        else -> 88.dp
    }
    val valueStyle = if (compact || fontScale >= 1.35f) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleMedium
    }
    val labelStyle = if (compact || fontScale >= 1.35f) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(controlHeight)
            .selectableGroup()
            .fluidTriadSurface(
                animation = FluidTriadSurfaceAnimation(
                    source = { source },
                    destination = { destination },
                    progress = { progress.value },
                    transient = { transient.value },
                    selected = { selected },
                ),
                colors = FluidTriadSurfaceColors(
                    coffee = scheme.primaryContainer,
                    water = scheme.secondaryContainer,
                    cup = scheme.tertiaryContainer,
                    outline = scheme.outlineVariant,
                    base = scheme.surfaceContainerLow,
                ),
            ),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { item ->
                val isSelected = item.target == selected
                val contentColor = when (item.target) {
                    CalculatorQuantityTarget.COFFEE -> if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    CalculatorQuantityTarget.WATER_IN -> if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                    CalculatorQuantityTarget.IN_CUP -> if (isSelected) scheme.onTertiaryContainer else scheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("fluid_triad_${item.target.name.lowercase()}")
                        .selectable(
                            selected = isSelected,
                            enabled = item.enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) onSelect(item.target)
                            },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${item.label}, ${item.spokenValue}"
                        }
                        .padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        )
                        Text(
                            text = item.label,
                            style = labelStyle,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        Text(
                            text = if (item.approximate) "≈${item.value}" else item.value,
                            style = valueStyle,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private class FluidTriadSurfaceAnimation(
    val source: () -> FluidTriadVisualProfile,
    val destination: () -> FluidTriadVisualProfile,
    val progress: () -> Float,
    val transient: () -> Float,
    val selected: () -> CalculatorQuantityTarget,
)

private data class FluidTriadSurfaceColors(
    val coffee: Color,
    val water: Color,
    val cup: Color,
    val outline: Color,
    val base: Color,
)

private class FluidTriadSurfaceCache {
    val path = Path()
    val accentPath = Path()
    val points = FloatArray(FluidTriadGeometry.PathPointCount * 2)
    val resolved = MutableFluidTriadVisualProfile()
}

private data class FluidTriadSurfaceMetrics(
    val top: Float,
    val bottom: Float,
    val radius: Float,
    val lineWidth: Float,
)

private fun Modifier.fluidTriadSurface(
    animation: FluidTriadSurfaceAnimation,
    colors: FluidTriadSurfaceColors,
): Modifier = this.drawWithCache {
    val cache = FluidTriadSurfaceCache()
    val metrics = FluidTriadSurfaceMetrics(
        top = 8.dp.toPx(),
        bottom = size.height - 8.dp.toPx(),
        radius = 22.dp.toPx(),
        lineWidth = 1.dp.toPx(),
    )

    onDrawBehind {
        drawFluidTriadBase(colors, metrics)
        FluidTriadGeometry.resolve(
            animation.source(),
            animation.destination(),
            animation.progress(),
            cache.resolved,
        )
        val currentTarget = animation.selected()
        val transient = animation.transient()
        val waterWave = if (currentTarget == CalculatorQuantityTarget.WATER_IN) transient else 0f
        FluidTriadGeometry.writePathPoints(cache.resolved, waterWave, cache.points)
        cache.path.updateFluidTriadPoints(cache.points, size.width, size.height)

        val materialColor = blendMaterialColor(
            resolved = cache.resolved,
            coffeeColor = colors.coffee,
            waterColor = colors.water,
            cupColor = colors.cup,
        )
        drawFluidTriadShape(cache, currentTarget, transient, materialColor)
        drawFluidTriadAccents(cache, materialColor)
        drawFluidTriadOutline(colors, metrics)
    }
}

private fun DrawScope.drawFluidTriadBase(
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadSurfaceMetrics,
) {
    drawRoundRect(
        color = colors.base,
        topLeft = Offset(0f, metrics.top),
        size = Size(size.width, metrics.bottom - metrics.top),
        cornerRadius = CornerRadius(metrics.radius, metrics.radius),
    )
    val dividerTop = metrics.top + 10.dp.toPx()
    val dividerBottom = metrics.bottom - 10.dp.toPx()
    for (divider in 1..2) {
        val x = size.width * divider / 3f
        drawLine(
            color = colors.outline.copy(alpha = 0.42f),
            start = Offset(x, dividerTop),
            end = Offset(x, dividerBottom),
            strokeWidth = metrics.lineWidth,
        )
    }
}

private fun Path.updateFluidTriadPoints(points: FloatArray, width: Float, height: Float) {
    reset()
    moveTo(points[0] * width, points[1] * height)
    var point = 1
    repeat(4) {
        cubicTo(
            points[point * 2] * width,
            points[point * 2 + 1] * height,
            points[(point + 1) * 2] * width,
            points[(point + 1) * 2 + 1] * height,
            points[(point + 2) * 2] * width,
            points[(point + 2) * 2 + 1] * height,
        )
        point += 3
    }
    close()
}

private fun DrawScope.drawFluidTriadShape(
    cache: FluidTriadSurfaceCache,
    currentTarget: CalculatorQuantityTarget,
    transient: Float,
    materialColor: Color,
) {
    val rigidWeight = (cache.resolved.coffeeWeight + cache.resolved.cupWeight).coerceIn(0f, 1f)
    if (rigidWeight > 0.05f) {
        val shadowOffset = 1.5.dp.toPx() * rigidWeight
        withTransform({ translate(top = shadowOffset) }) {
            drawPath(path = cache.path, color = materialColor.copy(alpha = 0.13f))
        }
    }

    when (currentTarget) {
        CalculatorQuantityTarget.COFFEE -> {
            val squeeze = 1f - transient * 0.012f
            withTransform({
                scale(
                    scaleX = squeeze,
                    scaleY = 1f,
                    pivot = Offset(cache.resolved.centerX * size.width, size.height / 2f),
                )
            }) {
                drawPath(path = cache.path, color = materialColor)
            }
        }
        CalculatorQuantityTarget.IN_CUP -> {
            val liftOffset = -2.dp.toPx() * transient
            withTransform({ translate(top = liftOffset) }) {
                drawPath(path = cache.path, color = materialColor)
            }
        }
        CalculatorQuantityTarget.WATER_IN -> drawPath(path = cache.path, color = materialColor)
    }
}

private fun DrawScope.drawFluidTriadAccents(
    cache: FluidTriadSurfaceCache,
    materialColor: Color,
) {
    if (cache.resolved.coffeeWeight > 0.12f) {
        cache.accentPath.reset()
        val x = cache.resolved.centerX * size.width
        cache.accentPath.moveTo(x - 4.dp.toPx(), size.height * 0.30f)
        cache.accentPath.cubicTo(
            x + 3.dp.toPx(), size.height * 0.40f,
            x - 3.dp.toPx(), size.height * 0.60f,
            x + 4.dp.toPx(), size.height * 0.70f,
        )
        drawPath(
            color = schemeContrast(materialColor).copy(alpha = 0.14f * cache.resolved.coffeeWeight),
            path = cache.accentPath,
            style = Stroke(width = 1.25.dp.toPx()),
        )
    }

    if (cache.resolved.cupWeight > 0.12f) {
        val lipHalf = size.width * 0.095f
        val lipY = size.height * (0.215f - cache.resolved.topLift - cache.resolved.lip * 0.12f)
        drawLine(
            color = schemeContrast(materialColor).copy(alpha = 0.17f * cache.resolved.cupWeight),
            start = Offset(cache.resolved.centerX * size.width - lipHalf, lipY),
            end = Offset(cache.resolved.centerX * size.width + lipHalf, lipY),
            strokeWidth = 1.25.dp.toPx(),
        )
    }
}

private fun DrawScope.drawFluidTriadOutline(
    colors: FluidTriadSurfaceColors,
    metrics: FluidTriadSurfaceMetrics,
) {
    drawRoundRect(
        color = colors.outline.copy(alpha = 0.62f),
        topLeft = Offset(0f, metrics.top),
        size = Size(size.width, metrics.bottom - metrics.top),
        cornerRadius = CornerRadius(metrics.radius, metrics.radius),
        style = Stroke(width = metrics.lineWidth),
    )
}

private fun blendMaterialColor(
    resolved: MutableFluidTriadVisualProfile,
    coffeeColor: Color,
    waterColor: Color,
    cupColor: Color,
): Color {
    val coffee = resolved.coffeeWeight.coerceIn(0f, 1f)
    val water = resolved.waterWeight.coerceIn(0f, 1f)
    val cup = resolved.cupWeight.coerceIn(0f, 1f)
    val firstTotal = coffee + water
    val coffeeWater = if (firstTotal > 0.0001f) {
        lerp(coffeeColor, waterColor, water / firstTotal)
    } else {
        cupColor
    }
    return lerp(coffeeWater, cupColor, cup)
}

private fun schemeContrast(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

private fun transitionSpring(target: CalculatorQuantityTarget) = when (target) {
    CalculatorQuantityTarget.COFFEE -> spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 760f,
    )
    CalculatorQuantityTarget.WATER_IN -> spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 420f,
    )
    CalculatorQuantityTarget.IN_CUP -> spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 620f,
    )
}

private fun transientSpring(target: CalculatorQuantityTarget) = when (target) {
    CalculatorQuantityTarget.COFFEE -> spring<Float>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessHigh,
    )
    CalculatorQuantityTarget.WATER_IN -> spring<Float>(
        dampingRatio = 0.48f,
        stiffness = 280f,
    )
    CalculatorQuantityTarget.IN_CUP -> spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 680f,
    )
}
