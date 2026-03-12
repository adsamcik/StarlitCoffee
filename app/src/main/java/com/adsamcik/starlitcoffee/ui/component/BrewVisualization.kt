package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.adsamcik.starlitcoffee.viewmodel.BrewPhase
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
                valveOpen = phase.phaseType != PhaseType.BLOOM,
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
private fun PulsarBrewerDiagram(
    waterFillFraction: Float,
    bedFraction: Float,
    valveOpen: Boolean,
    showDrip: Boolean,
    modifier: Modifier = Modifier,
) {
    val waterTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val waterBot = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val bedColor = MaterialTheme.colorScheme.tertiary
    val bedHighlight = MaterialTheme.colorScheme.tertiaryContainer
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val filterColor = MaterialTheme.colorScheme.outlineVariant
    val capColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val valveOpenColor = MaterialTheme.colorScheme.primary
    val valveClosedColor = MaterialTheme.colorScheme.error
    val dripColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 4f
        val thick = 6f

        val bL = w * 0.15f; val bR = w * 0.85f
        val bT = h * 0.04f; val bB = h * 0.62f
        val bW = bR - bL; val bH = bB - bT

        val xL = bL - w * 0.08f; val xR = bR + w * 0.08f
        val xT = bB + 4f; val xB = h * 0.78f

        // Coffee bed
        val bedH = bH * bedFraction.coerceIn(0.1f, 0.4f)
        val bedTop = bB - bedH
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(bedHighlight, bedColor), bedTop, bB),
            topLeft = Offset(bL + 4f, bedTop),
            size = Size(bW - 8f, bedH - 2f),
            cornerRadius = CornerRadius(4f, 4f),
        )

        // Water
        val waterSpace = bedTop - bT - bH * 0.08f
        val waterH = waterSpace * waterFillFraction.coerceIn(0f, 1f)
        if (waterH > 2f) {
            val wSurfaceY = bedTop - waterH
            drawWavyWaterSurface(
                left = bL + 4f, right = bR - 4f,
                surfaceY = wSurfaceY,
                amplitude = 3f.coerceAtMost(waterH * 0.2f),
                waterBottom = bedTop,
                brush = Brush.verticalGradient(listOf(waterTop, waterBot), wSurfaceY, bedTop),
            )
        }

        // Filter line
        drawLine(filterColor, Offset(bL + 8f, bedTop), Offset(bR - 8f, bedTop), 2.5f)

        // Dispersion cap
        val capY = bT + bH * 0.06f
        drawLine(
            capColor, Offset(bL + 14f, capY), Offset(bR - 14f, capY), 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )

        // Barrel walls
        drawLine(outlineColor, Offset(bL, bT), Offset(bL, bB), thick)
        drawLine(outlineColor, Offset(bR, bT), Offset(bR, bB), thick)
        drawLine(outlineColor, Offset(bL, bB), Offset(bR, bB), thick)
        drawRoundRect(
            outlineColor, Offset(bL - 6f, bT - 4f), Size(bW + 12f, 10f),
            cornerRadius = CornerRadius(5f, 5f),
        )

        // Base walls
        drawLine(outlineColor, Offset(xL, xT), Offset(xL, xB), thick)
        drawLine(outlineColor, Offset(xR, xT), Offset(xR, xB), thick)
        drawLine(outlineColor, Offset(xL, xT), Offset(bL, xT), stroke)
        drawLine(outlineColor, Offset(xR, xT), Offset(bR, xT), stroke)
        drawRoundRect(
            outlineColor, Offset(xL - 2f, xB - 2f), Size(xR - xL + 4f, 6f),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Valve
        val vR = w * 0.055f
        val vCenter = Offset(w * 0.5f, (xT + xB) / 2f)
        val vColor = if (valveOpen) valveOpenColor else valveClosedColor
        drawCircle(vColor.copy(alpha = 0.25f), vR + 4f, vCenter)
        drawCircle(vColor, vR, vCenter)
        drawCircle(outlineColor, vR, vCenter, style = Stroke(2.5f))

        val vlText = if (valveOpen) "OPEN" else "CLOSED"
        val vlResult = textMeasurer.measure(vlText, labelStyle)
        drawText(vlResult, topLeft = Offset(vCenter.x - vlResult.size.width / 2f, vCenter.y + vR + 6f))

        // Drip drops
        if (showDrip && valveOpen) {
            val dripX = w * 0.5f
            drawCircle(dripColor, 4f, Offset(dripX, xB + h * 0.06f))
            drawCircle(dripColor, 3f, Offset(dripX - 4f, xB + h * 0.13f))
            drawCircle(dripColor, 2.5f, Offset(dripX + 3f, xB + h * 0.19f))
        }

        // Coffee label
        val bedLabel = textMeasurer.measure("coffee", labelStyle)
        drawText(bedLabel, topLeft = Offset(bL + (bW - bedLabel.size.width) / 2f, bedTop + bedH * 0.25f))
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

            InfoRow("☕", "Coffee", "${"%.1f".format(coffeeG)}g")
            InfoRow("💧", "Water", "${"%.0f".format(waterG)}g")
            InfoRow("⚖️", "Ratio", "1:${"%.1f".format(if (coffeeG > 0f) waterG / coffeeG else 0f)}")

            if (refillCount > 0) {
                InfoRow("🔄", "Refills", "$refillCount — drains between pours")
            }

            // Current phase details
            currentPhase?.let { phase ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Current Phase",
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
                text = "Coffee Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("📏", "Coffee depth", "~${"%.0f".format(bedDepthMm)}mm in brewer")

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
                coffeeG <= 20f -> "Medium-fine · like table salt"
                coffeeG <= 25f -> "Medium · like coarse sand"
                coffeeG <= 30f -> "Medium-coarse · adjust by taste"
                else -> "Coarse · longer brew expected"
            }
            InfoRow("⚙️", "Grind", grindNote)

            val doseNote = when {
                coffeeG < 20f -> "Below 20g may taste dry or harsh"
                coffeeG in 20f..25f -> "Sweet spot for clarity & balance"
                coffeeG in 25f..30f -> "Rich, full-bodied results"
                else -> "Go coarser, expect longer brew"
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
                "Dose 20–25g recommended for best bed depth",
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
            PhaseType.BLOOM -> MaterialTheme.colorScheme.tertiaryContainer
            PhaseType.DRAIN_AND_REFILL -> MaterialTheme.colorScheme.errorContainer
            PhaseType.DRAWDOWN -> MaterialTheme.colorScheme.secondaryContainer
            PhaseType.POUR -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardBg",
    )
    val contentColor by animateColorAsState(
        targetValue = when (current.phaseType) {
            PhaseType.BLOOM -> MaterialTheme.colorScheme.onTertiaryContainer
            PhaseType.DRAIN_AND_REFILL -> MaterialTheme.colorScheme.onErrorContainer
            PhaseType.DRAWDOWN -> MaterialTheme.colorScheme.onSecondaryContainer
            PhaseType.POUR -> MaterialTheme.colorScheme.onPrimaryContainer
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
                    text = "${current.emoji} ${current.phaseName}",
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
                        contentDescription = "Brew details",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hero diagram
            PulsarBrewerDiagram(
                waterFillFraction = animatedFill,
                bedFraction = bedFraction,
                valveOpen = current.valveOpen,
                showDrip = current.valveOpen && current.waterInBrewer > 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phase-semantic hero — each phase type shows exactly what the user needs
            when (current.phaseType) {
                PhaseType.BLOOM -> {
                    val bloomRatio = if (coffeeG > 0f) current.waterThisPhase / coffeeG else 0f
                    Text(
                        text = "POUR TO ${"%.0f".format(current.cumulativeWater)}g",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = "${"%.0f".format(bloomRatio)}× your coffee · Open valve → Close → Wait",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                }
                PhaseType.POUR -> {
                    Text(
                        text = "🔓 POUR TO ${"%.0f".format(current.cumulativeWater)}g",
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
