package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewPhase
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.ui.util.emoji
import kotlinx.coroutines.launch
import kotlin.math.sin

// ─── Phase helpers ───────────────────────────────────────────

private data class PhaseVisualState(
    val phaseName: String,
    val phaseType: PhaseType,
    val waterInBrewer: Float,
    val fillFraction: Float,
    val valveOpen: Boolean,
    val instruction: String,
    val valveState: String,
    val waterThisPhase: Float,
    val cumulativeWater: Float,
    val emoji: String,
)

private fun computePhaseVisualStates(
    phases: List<BrewPhase>,
    capacity: Float,
): List<PhaseVisualState> {
    val states = mutableListOf<PhaseVisualState>()
    var waterInBrewer = 0f

    for (phase in phases) {
        when (phase.phaseType) {
            PhaseType.DRAIN_AND_REFILL -> waterInBrewer = 0f
            PhaseType.DRAWDOWN -> waterInBrewer *= 0.1f
            else -> waterInBrewer += phase.waterG
        }
        states.add(
            PhaseVisualState(
                phaseName = phase.name,
                phaseType = phase.phaseType,
                waterInBrewer = waterInBrewer,
                fillFraction = if (capacity > 0f) (waterInBrewer / capacity).coerceIn(0f, 1f) else 0f,
                valveOpen = !phase.valveState.equals("close", ignoreCase = true) &&
                    !phase.valveState.equals("closed", ignoreCase = true),
                instruction = phase.instruction,
                valveState = phase.valveState,
                waterThisPhase = phase.waterG,
                cumulativeWater = phase.cumulativeWaterG,
                emoji = phase.phaseType.emoji(),
            ),
        )
    }
    return states
}

// ─── Wavy water surface ──────────────────────────────────────

private fun DrawScope.drawWavyWaterSurface(
    left: Float,
    right: Float,
    surfaceY: Float,
    amplitude: Float,
    waterBottom: Float,
    brush: Brush,
) {
    val path = Path().apply {
        moveTo(left, waterBottom)
        lineTo(left, surfaceY)
        val waveWidth = right - left
        val steps = 40
        for (i in 0..steps) {
            val frac = i.toFloat() / steps
            val x = left + frac * waveWidth
            val y = surfaceY + sin(frac * Math.PI * 3).toFloat() * amplitude
            lineTo(x, y)
        }
        lineTo(right, waterBottom)
        close()
    }
    drawPath(path, brush, style = Fill)
}

// ─── Pulsar brewer cross-section ─────────────────────────────

@Composable
private fun LiquidPillDiagram(
    waterFillFraction: Float,
    bedFraction: Float,
    valveOpen: Boolean,
    showDrip: Boolean,
    modifier: Modifier = Modifier,
) {
    val waterLight = Color(0xFFE1F5FE) // Light Blue 50
    val waterDark = Color(0xFF81D4FA) // Light Blue 200
    val valveOpenColor = MaterialTheme.colorScheme.primary
    val valveClosedColor = MaterialTheme.colorScheme.error
    val flowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = Color.White.copy(alpha = 0.85f),
        fontWeight = FontWeight.Bold,
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 12f
        val pillH = h * 0.74f
        val pillTop = (h - pillH) / 2f - 4f
        val pillBot = pillTop + pillH
        val cornerR = pillH / 2f
        val pillLeft = pad
        val pillRight = w - pad
        val pillW = pillRight - pillLeft

        // Valve cap zone — right 16%
        val capWidth = pillW * 0.16f
        val capLeft = pillRight - capWidth
        val capRight = pillRight
        val capMidY = (pillTop + pillBot) / 2f

        // ── 1. Unified pill clip path — everything draws inside this ──
        val pillPath = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(Offset(pillLeft, pillTop), Size(pillW, pillH)),
                    CornerRadius(cornerR, cornerR),
                ),
            )
        }

        clipPath(pillPath) {
            // ── 2. Glass chamber background ──
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFEAF7FF).copy(alpha = 0.22f),
                        Color(0xFFCFE8FF).copy(alpha = 0.10f),
                        Color(0xFF0F172A).copy(alpha = 0.08f),
                    ),
                ),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW, pillH),
            )
            // Top highlight (glass reflection)
            drawLine(
                Color.White.copy(alpha = 0.35f),
                Offset(pillLeft + cornerR, pillTop + 5f),
                Offset(capLeft - 8f, pillTop + 5f),
                strokeWidth = 2.5f,
            )

            // ── 3. Coffee bed — curved top, dual-tone, granular ──
            val bedH = pillH * bedFraction.coerceIn(0.08f, 0.16f)
            val bedTop = pillBot - bedH
            val bedPath = Path().apply {
                moveTo(pillLeft, pillBot)
                lineTo(pillLeft, bedTop + 3f)
                quadraticBezierTo(
                    (pillLeft + capLeft) / 2f, bedTop - 2f,
                    capLeft, bedTop + 2f,
                )
                lineTo(capLeft, pillBot)
                close()
            }
            drawPath(
                bedPath,
                Brush.verticalGradient(
                    listOf(Color(0xFF6F5646), Color(0xFF43352C)),
                    bedTop,
                    pillBot,
                ),
            )
            // Granular specks on bed surface
            val speckCount = ((capLeft - pillLeft) / 8f).toInt().coerceIn(8, 30)
            for (i in 0 until speckCount) {
                val sx = pillLeft + 8f + i * ((capLeft - pillLeft - 16f) / speckCount)
                val sy = bedTop + 3f + (i % 3) * 1.5f
                drawCircle(Color(0xFFF2E4D7).copy(alpha = 0.10f), 1.2f, Offset(sx, sy))
            }

            // Filter perforations (at bed top when empty)
            if (waterFillFraction < 0.03f) {
                val perfCount = ((capLeft - pillLeft) / 14f).toInt().coerceIn(4, 16)
                for (i in 0 until perfCount) {
                    val px = pillLeft + 12f + i * ((capLeft - pillLeft - 24f) / perfCount)
                    drawCircle(Color.Black.copy(alpha = 0.06f), 1.4f, Offset(px, bedTop - 5f))
                }
            }

            // ── 4. Water fill ──
            val waterSpace = bedTop - pillTop - 8f
            val waterH = waterSpace * waterFillFraction.coerceIn(0f, 1f)
            if (waterH > 2f) {
                val wTop = bedTop - waterH
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(waterLight, waterDark),
                        wTop,
                        bedTop,
                    ),
                    topLeft = Offset(pillLeft, wTop),
                    size = Size(capLeft - pillLeft, waterH),
                )
                // Meniscus highlight
                drawLine(
                    Color.White.copy(alpha = 0.20f),
                    Offset(pillLeft + cornerR * 0.5f, wTop + 2f),
                    Offset(capLeft - 8f, wTop + 2f),
                    strokeWidth = 1.5f,
                )
            }

            // ── 5. Valve cap (draws inside clip — rounded edges are free) ──
            if (valveOpen) {
                val gap = pillH * 0.14f
                val capDark = valveOpenColor.copy(alpha = 0.9f)
                val capMid = valveOpenColor.copy(alpha = 0.7f)
                // Top half
                drawRect(
                    brush = Brush.verticalGradient(listOf(capMid, capDark)),
                    topLeft = Offset(capLeft, pillTop),
                    size = Size(capWidth, pillH / 2f - gap),
                )
                // Bottom half
                drawRect(
                    brush = Brush.verticalGradient(listOf(capDark, capMid)),
                    topLeft = Offset(capLeft, capMidY + gap),
                    size = Size(capWidth, pillH / 2f - gap),
                )
                // Tapered flow streaks through gap
                for (i in -1..1) {
                    val y = capMidY + i * 3.5f
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, flowColor, Color.Transparent),
                        ),
                        start = Offset(capLeft + 4f, y),
                        end = Offset(capRight - 2f, y),
                        strokeWidth = if (i == 0) 3f else 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            } else {
                val capDark = Color(0xFF7A8791)
                val capDeep = Color(0xFF4A5862)
                drawRect(
                    brush = Brush.verticalGradient(listOf(capDark, capDeep)),
                    topLeft = Offset(capLeft, pillTop),
                    size = Size(capWidth, pillH),
                )
                // Top highlight rib
                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(capLeft + 4f, pillTop + 5f),
                    Offset(capRight - 6f, pillTop + 5f),
                    strokeWidth = 1.5f,
                )
                // Vertical ribs for mechanical feel
                val ribCount = 3
                for (i in 1..ribCount) {
                    val rx = capLeft + capWidth * i / (ribCount + 1)
                    drawLine(
                        Color.Black.copy(alpha = 0.18f),
                        Offset(rx, pillTop + 8f),
                        Offset(rx, pillBot - 8f),
                        strokeWidth = 1.8f,
                    )
                }
                // Seam line at cap/chamber junction
                drawLine(
                    Color.Black.copy(alpha = 0.25f),
                    Offset(capLeft, pillTop + 4f),
                    Offset(capLeft, pillBot - 4f),
                    strokeWidth = 2f,
                )
            }
        }

        // ── 6. Shell outline with depth (drawn outside clip for clean edges) ──
        drawRoundRect(
            Color.Black.copy(alpha = 0.14f),
            Offset(pillLeft, pillTop),
            Size(pillW, pillH),
            CornerRadius(cornerR, cornerR),
            style = Stroke(2.5f),
        )
        // Inner highlight
        drawRoundRect(
            Color.White.copy(alpha = 0.10f),
            Offset(pillLeft + 1.5f, pillTop + 1.5f),
            Size(pillW - 3f, pillH - 3f),
            CornerRadius(cornerR - 1.5f, cornerR - 1.5f),
            style = Stroke(1f),
        )

        // ── Valve label (inside cap) ──
        val vlText = if (valveOpen) "OPEN" else "CLOSED"
        val vlResult = textMeasurer.measure(vlText, labelStyle)
        drawText(
            vlResult,
            topLeft = Offset(
                capLeft + (capWidth - vlResult.size.width) / 2f,
                capMidY - vlResult.size.height / 2f,
            ),
        )

        // ── Drip drops below valve when open ──
        if (showDrip && valveOpen) {
            val dripX = (capLeft + capRight) / 2f
            drawCircle(flowColor, 3.5f, Offset(dripX + 1f, pillBot + 10f))
            drawCircle(Color.White.copy(alpha = 0.15f), 1.2f, Offset(dripX, pillBot + 9f))
            drawCircle(flowColor, 2.5f, Offset(dripX - 4f, pillBot + 18f))
        }
    }
}

// ─── Info bottom sheet ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrewInfoSheet(
    coffeeG: Float,
    waterG: Float,
    refillCount: Int,
    currentPhase: PhaseVisualState?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Summary
            Text(
                text = "Brew Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfoRow("☕", stringResource(R.string.label_coffee), "${"%.1f".format(coffeeG)}g")
            InfoRow("💧", stringResource(R.string.label_water), "${"%.0f".format(waterG)}g")
            InfoRow("⚖️", stringResource(R.string.label_strength), "1:${"%.1f".format(if (coffeeG > 0f) waterG / coffeeG else 0f)}")

            if (refillCount > 0) {
                InfoRow("🔄", stringResource(R.string.phase_drain_refill), "$refillCount — drains between pours")
            }

            // Current phase details
            currentPhase?.let { phase ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Current Step",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (phase.cumulativeWater > 0f) {
                    InfoRow("🎯", "Target", "Pour to ${"%.0f".format(phase.cumulativeWater)}g total")
                }
                if (phase.valveState.isNotEmpty()) {
                    InfoRow("🔧", "Valve", phase.valveState.replaceFirstChar { it.uppercaseChar() })
                }
                if (phase.instruction.isNotEmpty()) {
                    Text(
                        text = phase.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // Bed depth
            val bedDepthMm = coffeeG * 0.7f
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.label_coffee_insights),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("📏", stringResource(R.string.label_coffee_depth), stringResource(R.string.format_coffee_depth, bedDepthMm))

            val bedBarFraction = (bedDepthMm / 25f).coerceIn(0f, 1f)
            val bedBarColor = when {
                bedDepthMm < 14f -> MaterialTheme.colorScheme.error
                bedDepthMm <= 20f -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.errorContainer
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bedBarFraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(bedBarColor),
                )
            }

            val grindNote = when {
                coffeeG <= 20f -> stringResource(R.string.grind_note_medium_fine)
                coffeeG <= 25f -> stringResource(R.string.grind_note_medium)
                coffeeG <= 30f -> stringResource(R.string.grind_note_medium_coarse)
                else -> stringResource(R.string.grind_note_coarse)
            }
            InfoRow("⚙️", stringResource(R.string.label_grind), grindNote)

            val doseNote = when {
                coffeeG < 20f -> stringResource(R.string.dose_note_below_20)
                coffeeG in 20f..25f -> stringResource(R.string.dose_note_sweet_spot)
                coffeeG in 25f..30f -> stringResource(R.string.dose_note_rich)
                else -> stringResource(R.string.dose_note_above_30)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = doseNote,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )

            // Tips
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tips",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val tips = listOf(
                "No gooseneck needed — dispersion cap distributes water",
                "Level the bed before brewing",
                "Gentle swirl after last pour (hold by the base!)",
                "Use 20–25g coffee for best results",
            )
            tips.forEach { tip ->
                Text(
                    text = "• $tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── Segmented phase progress bar ────────────────────────────

@Composable
private fun PhaseSegmentBar(
    states: List<PhaseVisualState>,
    selectedPhase: Int,
    onPhaseSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Gram labels above the bar — show on active and adjacent pour/bloom phases
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            states.forEachIndexed { index, state ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val showLabel = (index in (selectedPhase - 1)..(selectedPhase + 1)) &&
                        state.cumulativeWater > 0f &&
                        state.phaseType in listOf(PhaseType.BLOOM, PhaseType.POUR)
                    if (showLabel) {
                        Text(
                            text = "${"%.0f".format(state.cumulativeWater)}g",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = if (index == selectedPhase) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (index == selectedPhase) 0.9f else 0.5f,
                            ),
                        )
                    }
                }
            }
        }

        // Segment bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            states.forEachIndexed { index, state ->
                val isActive = index == selectedPhase
                val isPast = index < selectedPhase
                val segColor = when (state.phaseType) {
                    PhaseType.BLOOM -> MaterialTheme.colorScheme.tertiary
                    PhaseType.DRAIN_AND_REFILL -> MaterialTheme.colorScheme.error
                    PhaseType.DRAWDOWN -> MaterialTheme.colorScheme.secondary
                    PhaseType.POUR -> MaterialTheme.colorScheme.primary
                }
                val alpha = when {
                    isActive -> 1f
                    isPast -> 0.6f
                    else -> 0.2f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segColor.copy(alpha = alpha))
                        .semantics {
                            contentDescription = "${state.phaseName}: " +
                                if (state.cumulativeWater > 0f) "${"%.0f".format(state.cumulativeWater)}g cumulative"
                                else state.phaseName
                        }
                        .clickable { onPhaseSelected(index) },
                )
            }
        }
    }
}

// ─── Main composable: Minimalist Brew Guide ──────────────────

/**
 * Minimalist brew guide. Hero brewer diagram, phase-semantic display
 * (cumulative target for pours, valve state for Pulsar, bloom ratio),
 * thin segmented progress bar, ⓘ for details.
 */
@Composable
fun BrewGuide(
    phases: List<BrewPhase>,
    coffeeG: Float,
    waterG: Float,
    capacityMaxG: Float?,
    refillCount: Int,
    modifier: Modifier = Modifier,
    activePhaseIndex: Int = -1,
    showNextPreview: Boolean = false,
) {
    // When activePhaseIndex is provided (timer running), follow it.
    // Otherwise use internal state for manual browsing.
    var manualPhase by remember { mutableIntStateOf(0) }
    val selectedPhase = if (activePhaseIndex >= 0) activePhaseIndex else manualPhase
    var showInfoSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val capacity = capacityMaxG ?: waterG
    val states = remember(phases, capacity) { computePhaseVisualStates(phases, capacity) }
    if (states.isEmpty()) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Preparing brew visualization…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val current = states[selectedPhase.coerceIn(0, states.lastIndex)]
    val animatedFill by animateFloatAsState(
        targetValue = current.fillFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "waterFill",
    )
    val bedFraction = (coffeeG * 0.7f / 82f).coerceIn(0.12f, 0.35f)

    val cardColor by animateColorAsState(
        targetValue = when (current.phaseType) {
            // Subtle, polished surface colors instead of strong container colors
            PhaseType.BLOOM -> MaterialTheme.colorScheme.surfaceVariant
            PhaseType.DRAIN_AND_REFILL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            PhaseType.DRAWDOWN -> MaterialTheme.colorScheme.surfaceVariant
            PhaseType.POUR -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardBg",
    )
    val contentColor by animateColorAsState(
        targetValue = when (current.phaseType) {
            PhaseType.BLOOM -> MaterialTheme.colorScheme.onSurfaceVariant
            PhaseType.DRAIN_AND_REFILL -> MaterialTheme.colorScheme.onErrorContainer
            PhaseType.DRAWDOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            PhaseType.POUR -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardContent",
    )

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top row: phase label + info button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current.phaseName, // Removed emoji
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                IconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.cd_brew_details),
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Valve state badge — immediately visible
            if (current.valveState.isNotEmpty()) {
                val isOpen = current.valveOpen
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                        contentDescription = null,
                        tint = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VALVE ${if (isOpen) "OPEN" else "CLOSED"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hero diagram — compact liquid pill
            LiquidPillDiagram(
                waterFillFraction = animatedFill,
                bedFraction = bedFraction,
                valveOpen = current.valveOpen,
                showDrip = current.valveOpen && current.waterInBrewer > 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phase-semantic hero — each phase type shows exactly what the user needs
            when (current.phaseType) {
                PhaseType.BLOOM -> {
                    if (current.waterThisPhase > 0f) {
                        Text(
                            text = "POUR TO ${"%.0f".format(current.cumulativeWater)}g",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                        val bloomRatio = if (coffeeG > 0f) current.waterThisPhase / coffeeG else 0f
                        Text(
                            text = "${"%.0f".format(bloomRatio)}× your coffee",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                    } else {
                        Text(
                            text = current.instruction.uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                        // Subtitle removed to avoid redundancy with circle label
                    }
                }
                PhaseType.POUR -> {
                    Text(
                        text = "POUR TO ${"%.0f".format(current.cumulativeWater)}g",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = "+${"%.0f".format(current.waterThisPhase)}g this step",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                }
                PhaseType.DRAIN_AND_REFILL -> {
                    Text(
                        text = "Let it flow through, then pour again",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = "Open valve · drain completely",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                }
                PhaseType.DRAWDOWN -> {
                    Text(
                        text = "Open valve · let it finish draining",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = "Almost there — wait for the last drops",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Next-up preview — cumulative target for next phase
            val nextState = states.getOrNull(selectedPhase + 1)
            AnimatedVisibility(
                visible = showNextPreview && nextState != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(contentColor.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Next: ${nextState?.phaseName ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                    if (nextState != null && nextState.waterThisPhase > 0f) {
                        Text(
                            text = " · → ${"%.0f".format(nextState.cumulativeWater)}g",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented progress bar
            PhaseSegmentBar(
                states = states,
                selectedPhase = selectedPhase,
                onPhaseSelected = { manualPhase = it },
            )
            Text(
                text = "${"%.0f".format(current.cumulativeWater)}g / ${"%.0f".format(waterG)}g",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    // Info bottom sheet
    if (showInfoSheet) {
        BrewInfoSheet(
            coffeeG = coffeeG,
            waterG = waterG,
            refillCount = refillCount,
            currentPhase = current,
            onDismiss = { showInfoSheet = false },
        )
    }
}
