package com.adsamcik.starlitcoffee.ui.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * A unified custom-rendered material selector for Coffee / Water in / In cup.
 *
 * The visual tree is deliberately a single renderer: section backgrounds, icons, labels, values,
 * state layers, and material accents are drawn together. Transparent hit-test cells provide only
 * stable input regions and accessibility semantics; they never paint visual content.
 */
@Composable
fun FluidTriadSelector(
    items: List<FluidTriadItem>,
    selected: CalculatorQuantityTarget,
    onSelect: (CalculatorQuantityTarget) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    idleMotionEnabled: Boolean = true,
) {
    require(items.map { it.target } == FluidTriadTargetOrder)

    val transitionProgress = remember { Animatable(1f) }
    var transitionSnapshot by remember {
        mutableStateOf(FluidTriadFrameResolver.between(selected, selected))
    }
    val waterGust = remember { Animatable(0f) }
    var renderedSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(selected) {
        val currentFrame = FluidTriadFrameResolver.resolve(
            snapshot = transitionSnapshot,
            progress = transitionProgress.value,
        )
        if (currentFrame.stableTarget == selected && transitionProgress.value >= 1f) {
            return@LaunchedEffect
        }
        transitionSnapshot = FluidTriadFrameResolver.retarget(
            sourceFrame = currentFrame,
            destinationTarget = selected,
        )
        transitionProgress.snapTo(0f)
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = transitionSnapshot.durationMillis,
                easing = LinearEasing,
            ),
        )
    }

    LaunchedEffect(selected, idleMotionEnabled) {
        val motionScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (
            selected != CalculatorQuantityTarget.WATER_IN ||
            !idleMotionEnabled ||
            motionScale == 0f
        ) {
            finishWaterGust(waterGust)
            return@LaunchedEffect
        }
        waterGust.snapTo(0f)
        delay(WaterInitialRestMillis)
        while (true) {
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
                waterGust.snapTo(0f)
                return@LaunchedEffect
            }
            waterGust.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = WaterGustDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
            waterGust.snapTo(0f)
            delay(WaterBetweenGustsMillis)
        }
    }

    val interactionSources = remember {
        List(FluidTriadTargetOrder.size) { MutableInteractionSource() }
    }
    val coffeePressed = interactionSources[0].collectIsPressedAsState()
    val waterPressed = interactionSources[1].collectIsPressedAsState()
    val cupPressed = interactionSources[2].collectIsPressedAsState()
    val pressedReaders = remember(coffeePressed, waterPressed, cupPressed) {
        listOf(
            { coffeePressed.value },
            { waterPressed.value },
            { cupPressed.value },
        )
    }
    val coffeeFocused = interactionSources[0].collectIsFocusedAsState()
    val waterFocused = interactionSources[1].collectIsFocusedAsState()
    val cupFocused = interactionSources[2].collectIsFocusedAsState()
    val focusReaders = remember(coffeeFocused, waterFocused, cupFocused) {
        listOf(
            { coffeeFocused.value },
            { waterFocused.value },
            { cupFocused.value },
        )
    }

    val iconPainters = rememberIconPainters(items)
    val textMeasurer = rememberTextMeasurer(cacheSize = TextMeasureCacheSize)
    val shaderContentLayerCache = remember { FluidTriadShaderContentLayerCache() }
    DisposableEffect(shaderContentLayerCache) {
        onDispose(shaderContentLayerCache::close)
    }
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val view = LocalView.current
    val controlHeight = when {
        fontScale >= LargeFontScaleThreshold -> 112.dp
        compact -> 80.dp
        else -> 96.dp
    }
    val baseInset = when {
        fontScale >= LargeFontScaleThreshold -> 9.dp
        compact -> 7.dp
        else -> 8.dp
    }
    val valueStyle = when {
        fontScale >= CompactTypographyThreshold -> MaterialTheme.typography.titleMedium
        compact -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val labelStyle = if (compact || fontScale >= CompactTypographyThreshold) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    val canUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        view.isHardwareAccelerated
    val railAspect = remember(renderedSize, density.density, canUseRuntimeShader) {
        if (!canUseRuntimeShader || renderedSize.height <= 0) {
            null
        } else {
            val overflow = with(density) {
                FluidTriadVisualSpec.Rail.logicalTrailingOverflow.toPx()
            }
            ((renderedSize.width - overflow).coerceAtLeast(1f) / renderedSize.height)
        }
    }
    val shaderResources by produceState<FluidTriadShaderResources?>(
        initialValue = null,
        key1 = railAspect,
        key2 = canUseRuntimeShader,
    ) {
        value = if (
            railAspect != null &&
            canUseRuntimeShader
        ) {
            withContext(Dispatchers.Default) {
                val atlas = FluidTriadSdfMorphCache.get(
                    FluidTriadSdfAtlasRequest(railAspect = requireNotNull(railAspect)),
                )
                FluidTriadShaderResources(
                    atlas = atlas,
                    initialTarget = selected,
                ).also(FluidTriadShaderResources::prewarmStableTextures)
            }
        } else {
            null
        }
    }
    var shaderActivated by remember(shaderResources) { mutableStateOf(false) }
    LaunchedEffect(shaderResources) {
        if (shaderResources == null) {
            shaderActivated = false
            return@LaunchedEffect
        }
        if (transitionProgress.value < 1f || transitionProgress.isRunning) {
            snapshotFlow { transitionProgress.value }.first { it >= 1f }
        }
        shaderActivated = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(controlHeight)
            .onSizeChanged { renderedSize = it }
            .selectableGroup()
            .fluidTriadRenderer(
                content = FluidTriadRendererContent(
                    items = items,
                    icons = iconPainters,
                    textMeasurer = textMeasurer,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    iconSize = if (compact) 18.dp else 20.dp,
                ),
                animation = FluidTriadRendererAnimation(
                    transition = { transitionSnapshot },
                    transitionProgress = { transitionProgress.value },
                    waterGust = { waterGust.value },
                    selected = { selected },
                    pressed = pressedReaders,
                    focused = focusReaders,
                    mirrorHorizontally = mirrored,
                    shaderResources = shaderResources.takeIf { shaderActivated },
                ),
                colors = fluidTriadSurfaceColors(scheme),
                baseInset = baseInset,
                shaderContentLayerCache = shaderContentLayerCache,
            ),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = item.target == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("fluid_triad_${item.target.name.lowercase()}")
                        .selectable(
                            selected = isSelected,
                            enabled = item.enabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSources[index],
                            indication = null,
                            onClick = {
                                if (!isSelected) onSelect(item.target)
                            },
                        )
                        .semantics {
                            contentDescription = "${item.label}, ${item.spokenValue}"
                        },
                )
            }
        }
    }
}

@Composable
private fun rememberIconPainters(items: List<FluidTriadItem>): List<Painter> {
    val coffee = painterResource(items[0].icon.drawableRes)
    val water = painterResource(items[1].icon.drawableRes)
    val cup = painterResource(items[2].icon.drawableRes)
    return remember(coffee, water, cup) { listOf(coffee, water, cup) }
}

private suspend fun finishWaterGust(gust: Animatable<Float, *>) {
    if (gust.value <= 0f) return
    gust.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = WaterGustExitMillis),
    )
    gust.snapTo(0f)
}

internal val FluidTriadTargetOrder = listOf(
    CalculatorQuantityTarget.COFFEE,
    CalculatorQuantityTarget.WATER_IN,
    CalculatorQuantityTarget.IN_CUP,
)

private const val WaterInitialRestMillis = 3_200L
private const val WaterBetweenGustsMillis = 5_600L
private const val WaterGustDurationMillis = 2_000
private const val WaterGustExitMillis = 280
private const val TextMeasureCacheSize = 12
private const val LargeFontScaleThreshold = 1.5f
private const val CompactTypographyThreshold = 1.35f
