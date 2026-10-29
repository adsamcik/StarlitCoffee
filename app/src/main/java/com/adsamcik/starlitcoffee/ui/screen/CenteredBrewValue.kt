package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.R

/** Shared centered presentation; callers retain clocks, targets, completion and session ownership. */
internal data class CenteredBrewValue(
    val label: String,
    val value: String,
    val contentDescription: String? = null,
    val unitSuffix: String? = null,
    val numeric: Boolean = true,
)

/** One layout for durable sessions and retained timer-route adapters. */
@Composable
internal fun CenteredBrewBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val large = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = if (large) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (large) 16.dp else 24.dp, Alignment.CenterVertically),
            content = content,
        )
    }
}

@Composable
internal fun CenteredBrewHero(
    primary: CenteredBrewValue?,
    secondary: CenteredBrewValue? = null,
    modifier: Modifier = Modifier,
    bloom: @Composable (Modifier) -> Unit,
) {
    val large = LocalDensity.current.fontScale >= 1.5f
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) 16.dp else 28.dp)) {
        bloom(Modifier.size(if (large) 96.dp else 176.dp))
        if (primary != null || secondary != null) Column(Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else 24.dp)) {
            primary?.let { primary ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    primary.contentDescription?.let { contentDescription = it }
                }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (primary.label.isNotEmpty()) {
                        Text(primary.label, Modifier.fillMaxWidth(), fontSize = 14.sp, lineHeight = 19.sp,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CenteredBrewNumber(primary)
                }
            }
            secondary?.let { value ->
                FlowRow(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    value.contentDescription?.let { contentDescription = it }
                }, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    if (value.label.isNotEmpty()) Text(value.label, fontSize = 14.sp, lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value.value, fontSize = if (large) 18.sp else 24.sp, lineHeight = if (large) 24.sp else 29.sp,
                        fontWeight = FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun CenteredBrewNumber(value: CenteredBrewValue) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Normal,
            fontFeatureSettings = "tnum", letterSpacing = (-2).sp)
        val unitStart = value.unitSuffix?.takeIf { value.value.endsWith(it) }
            ?.let { value.value.length - it.length }
        fun annotated(fontSize: Float) = buildAnnotatedString {
            append(value.value)
            if (unitStart != null) addStyle(SpanStyle(fontSize = minOf(28f, fontSize * 0.5f).sp),
                unitStart, length)
        }
        val size = remember(value, width, density.fontScale, style, measurer) {
            var lower = 1f
            var upper = if (value.numeric) { if (density.fontScale >= 1.5f) 64f else 80f } else 26f
            repeat(10) {
                val candidate = (lower + upper) / 2f
                val measured = measurer.measure(annotated(candidate),
                    style = style.copy(fontSize = candidate.sp), maxLines = 1, softWrap = false)
                if (measured.size.width <= width) lower = candidate else upper = candidate
            }
            lower
        }
        Text(annotated(size), Modifier.fillMaxWidth(), style = style,
            fontSize = size.sp, lineHeight = (size * 1.08f).sp, textAlign = TextAlign.Center,
            softWrap = false, maxLines = 1)
    }
}

/** Stable physical Pause/Resume location; Next never substitutes for a clock operation. */
@Composable
internal fun CenteredBrewActions(
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
    paused: Boolean = false,
    finishLabel: String? = null,
    onFinish: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundBrews: @Composable () -> Unit = {},
) {
    val primaryInteraction = remember { MutableInteractionSource() }
    val primaryPressed by primaryInteraction.collectIsPressedAsState()
    val primaryScale by animateFloatAsState(if (primaryPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f), label = "BrewActionPress")
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (primaryLabel != null && onPrimary != null) {
                    Button(onClick = onPrimary, enabled = enabled, interactionSource = primaryInteraction,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                            .graphicsLayer { scaleX = primaryScale; scaleY = primaryScale },
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(primaryLabel, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
                            fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center)
                    }
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (finishLabel != null && onFinish != null) {
                            TextButton(onClick = onFinish, enabled = enabled,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                                modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(finishLabel, fontSize = 16.sp, lineHeight = 20.sp,
                                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                if (onToggle != null) {
                    CenteredBrewPause(onToggle, paused, enabled, secondary = primaryLabel != null)
                    if (primaryLabel == null) Spacer(Modifier.weight(1f))
                }
            }
            backgroundBrews()
        }
    }
}

@Composable
private fun CenteredBrewPause(onToggle: () -> Unit, paused: Boolean, enabled: Boolean, secondary: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f), label = "BrewPausePress")
    FilledIconButton(onClick = onToggle, enabled = enabled, interactionSource = interaction,
        modifier = Modifier.size(if (secondary) 72.dp else 80.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(if (secondary) 24.dp else 28.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (secondary) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
            contentColor = if (secondary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary)) {
        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            stringResource(if (paused) R.string.action_resume else R.string.action_pause_brew_clock),
            Modifier.size(32.dp))
    }
}
