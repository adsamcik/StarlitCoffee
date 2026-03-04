package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.viewmodel.BrewPhase

private data class PhaseVisualState(
    val phaseName: String,
    val waterInBrewer: Float,
    val fillFraction: Float,
    val valveOpen: Boolean,
    val instruction: String,
    val waterThisPhase: Float,
    val cumulativeWater: Float,
)

private fun computePhaseVisualStates(
    phases: List<BrewPhase>,
    capacity: Float,
): List<PhaseVisualState> {
    val states = mutableListOf<PhaseVisualState>()
    var waterInBrewer = 0f

    for (phase in phases) {
        when {
            phase.name.startsWith("Drain") -> waterInBrewer = 0f
            phase.name == "Drawdown" -> waterInBrewer *= 0.1f
            else -> waterInBrewer += phase.waterG
        }
        states.add(
            PhaseVisualState(
                phaseName = phase.name,
                waterInBrewer = waterInBrewer,
                fillFraction = if (capacity > 0f) (waterInBrewer / capacity).coerceIn(0f, 1f) else 0f,
                valveOpen = !phase.name.startsWith("Bloom"),
                instruction = phase.instruction,
                waterThisPhase = phase.waterG,
                cumulativeWater = phase.cumulativeWaterG,
            ),
        )
    }

    return states
}

/**
 * Canvas cross-section of the Pulsar brewer showing coffee bed,
 * water level, valve state, and drip indicator.
 */
@Composable
private fun PulsarBrewerDiagram(
    waterFillFraction: Float,
    bedFraction: Float,
    valveOpen: Boolean,
    showDrip: Boolean,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val waterColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val bedColor = Color(0xFF8D6E63)
    val filterColor = MaterialTheme.colorScheme.outlineVariant
    val capColor = outlineColor.copy(alpha = 0.4f)
    val valveOpenColor = Color(0xFF66BB6A)
    val valveClosedColor = Color(0xFFEF5350)
    val dripColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 3f

        // Barrel geometry
        val bL = w * 0.18f
        val bR = w * 0.82f
        val bT = h * 0.06f
        val bB = h * 0.68f
        val bW = bR - bL
        val bH = bB - bT
        val pad = 3f

        // Base geometry
        val xL = bL - w * 0.06f
        val xR = bR + w * 0.06f
        val xT = bB + 3f
        val xB = h * 0.82f

        // Coffee bed
        val bedH = bH * bedFraction.coerceIn(0.08f, 0.4f)
        val bedTop = bB - bedH
        drawRect(bedColor, Offset(bL + pad, bedTop), Size(bW - pad * 2, bedH - pad))

        // Water above the bed
        val waterSpace = bedTop - bT - bH * 0.08f
        val waterH = waterSpace * waterFillFraction.coerceIn(0f, 1f)
        if (waterH > 1f) {
            drawRect(waterColor, Offset(bL + pad, bedTop - waterH), Size(bW - pad * 2, waterH))
        }

        // Filter (thin line at top of bed)
        drawLine(filterColor, Offset(bL + 6f, bedTop), Offset(bR - 6f, bedTop), 2f)

        // Dispersion cap (dashed line near barrel top)
        val capY = bT + bH * 0.055f
        drawLine(
            capColor, Offset(bL + 10f, capY), Offset(bR - 10f, capY), 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
        )

        // Barrel walls
        drawLine(outlineColor, Offset(bL, bT), Offset(bL, bB), stroke)
        drawLine(outlineColor, Offset(bR, bT), Offset(bR, bB), stroke)
        drawLine(outlineColor, Offset(bL, bB), Offset(bR, bB), stroke)
        drawLine(outlineColor, Offset(bL - 4f, bT), Offset(bR + 4f, bT), stroke + 1f)

        // Base
        drawLine(outlineColor, Offset(xL, xT), Offset(xL, xB), stroke)
        drawLine(outlineColor, Offset(xR, xT), Offset(xR, xB), stroke)
        drawLine(outlineColor, Offset(xL, xT), Offset(bL, xT), stroke)
        drawLine(outlineColor, Offset(xR, xT), Offset(bR, xT), stroke)
        drawLine(outlineColor, Offset(xL, xB), Offset(xR, xB), stroke)

        // Valve indicator
        val vR = w * 0.04f
        val vCenter = Offset(w * 0.5f, (xT + xB) / 2f)
        drawCircle(if (valveOpen) valveOpenColor else valveClosedColor, vR, vCenter)
        drawCircle(outlineColor, vR, vCenter, style = Stroke(1.5f))

        // Drip line
        if (showDrip && valveOpen) {
            drawLine(
                dripColor, Offset(w * 0.5f, xB + 4f), Offset(w * 0.5f, h * 0.96f), 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)),
            )
        }
    }
}

/**
 * Interactive brew plan visualization: animated Pulsar cross-section
 * with a phase stepper to explore each brew step.
 */
@Composable
fun BrewPlanVisualization(
    phases: List<BrewPhase>,
    coffeeG: Float,
    waterG: Float,
    capacityMaxG: Float?,
    modifier: Modifier = Modifier,
) {
    var selectedPhase by remember { mutableIntStateOf(0) }
    val capacity = capacityMaxG ?: waterG
    val states = remember(phases, capacity) { computePhaseVisualStates(phases, capacity) }
    if (states.isEmpty()) return

    val current = states[selectedPhase.coerceIn(0, states.lastIndex)]
    val animatedFill by animateFloatAsState(
        targetValue = current.fillFraction,
        animationSpec = tween(400),
        label = "waterFill",
    )
    // Approximate bed depth fraction: ~0.7mm per gram, barrel ~82mm
    val bedFraction = (coffeeG * 0.7f / 82f).coerceIn(0.12f, 0.35f)

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Brew Plan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PulsarBrewerDiagram(
                    waterFillFraction = animatedFill,
                    bedFraction = bedFraction,
                    valveOpen = current.valveOpen,
                    showDrip = current.valveOpen && current.waterInBrewer > 0f,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                )

                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = current.phaseName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (current.waterThisPhase > 0f) {
                        Text(
                            text = "Pour ${"%.0f".format(current.waterThisPhase)}g",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        text = "Total: ${"%.0f".format(current.cumulativeWater)}g / ${"%.0f".format(waterG)}g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (current.valveOpen) Color(0xFF66BB6A) else Color(0xFFEF5350),
                                ),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (current.valveOpen) "Valve open" else "Valve closed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = current.instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phase stepper dots with navigation arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { if (selectedPhase > 0) selectedPhase-- },
                    enabled = selectedPhase > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Previous phase",
                        Modifier.size(18.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    states.forEachIndexed { index, state ->
                        val isSelected = index == selectedPhase
                        val isDrain = state.phaseName.startsWith("Drain")
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isDrain -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                )
                                .clickable { selectedPhase = index },
                        )
                    }
                }

                IconButton(
                    onClick = { if (selectedPhase < states.lastIndex) selectedPhase++ },
                    enabled = selectedPhase < states.lastIndex,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, "Next phase",
                        Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Research-backed dose insights for Pulsar: bed depth, grind recommendation,
 * and dose-specific notes derived from Scott Rao and Coffee ad Astra data.
 */
@Composable
fun PulsarDoseInsights(
    coffeeG: Float,
    refillCount: Int,
    modifier: Modifier = Modifier,
) {
    val bedDepthMm = coffeeG * 0.7f

    val grindNote = when {
        coffeeG <= 20f -> "Medium-coarse · similar to V60"
        coffeeG <= 25f -> "Coarser than V60, finer than batch · ~800μm"
        coffeeG <= 30f -> "Medium-coarse · approaching batch grind"
        else -> "Coarse · batch brew territory — adjust by taste"
    }

    val doseNote = when {
        coffeeG < 20f -> "Below 20g risks astringency — shallow bed depth"
        coffeeG in 20f..25f -> "Optimal bed depth for clarity and balance"
        coffeeG in 25f..30f -> "Great depth — expect rich, full-bodied results"
        else -> "Large dose — grind coarser, expect longer brew time"
    }

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Dose Insights",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "📏 Bed depth: ~${"%.0f".format(bedDepthMm)}mm",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "⚙️ Grind: $grindNote",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (refillCount > 0) {
                Text(
                    text = "🔄 $refillCount refill${if (refillCount > 1) "s" else ""} needed — timer handles this",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = doseNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
