package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.viewmodel.BrewPhase
import kotlin.math.sin

private data class PhaseVisualState(
    val phaseName: String,
    val waterInBrewer: Float,
    val fillFraction: Float,
    val valveOpen: Boolean,
    val instruction: String,
    val waterThisPhase: Float,
    val cumulativeWater: Float,
    val emoji: String,
)

private fun phaseEmoji(name: String): String = when {
    name == "Bloom" -> "🌱"
    name.startsWith("Pour") -> "💧"
    name.startsWith("Drain") -> "🔄"
    name == "Drawdown" -> "⏬"
    else -> "☕"
}

private fun phaseShortLabel(name: String): String = when {
    name == "Bloom" -> "Bloom"
    name.startsWith("Pour") -> name.substringAfter("Pour ").trim()
    name.startsWith("Drain") -> "Drain"
    name == "Drawdown" -> "Done"
    else -> name.take(5)
}

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
                emoji = phaseEmoji(phase.name),
            ),
        )
    }

    return states
}

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

/**
 * Large, expressive Canvas cross-section of the Pulsar brewer.
 * M3 Expressive: bold strokes, gradient water, wavy surface, chunky valve.
 */
@Composable
private fun PulsarBrewerDiagram(
    waterFillFraction: Float,
    bedFraction: Float,
    valveOpen: Boolean,
    showDrip: Boolean,
    modifier: Modifier = Modifier,
) {
    val waterTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val waterBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val bedColor = Color(0xFF8D6E63)
    val bedHighlight = Color(0xFFA1887F)
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val filterColor = MaterialTheme.colorScheme.outlineVariant
    val capColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val valveOpenColor = Color(0xFF66BB6A)
    val valveClosedColor = Color(0xFFEF5350)
    val dripColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 4f
        val thick = 6f

        // Barrel geometry — big and centered
        val bL = w * 0.15f
        val bR = w * 0.85f
        val bT = h * 0.04f
        val bB = h * 0.62f
        val bW = bR - bL
        val bH = bB - bT

        // Base geometry
        val xL = bL - w * 0.08f
        val xR = bR + w * 0.08f
        val xT = bB + 4f
        val xB = h * 0.78f

        // Coffee bed with gradient
        val bedH = bH * bedFraction.coerceIn(0.1f, 0.4f)
        val bedTop = bB - bedH
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(bedHighlight, bedColor), bedTop, bB),
            topLeft = Offset(bL + 4f, bedTop),
            size = Size(bW - 8f, bedH - 2f),
            cornerRadius = CornerRadius(4f, 4f),
        )

        // Water above the bed — gradient fill with wavy top
        val waterSpace = bedTop - bT - bH * 0.08f
        val waterH = waterSpace * waterFillFraction.coerceIn(0f, 1f)
        if (waterH > 2f) {
            val wSurfaceY = bedTop - waterH
            drawWavyWaterSurface(
                left = bL + 4f,
                right = bR - 4f,
                surfaceY = wSurfaceY,
                amplitude = 3f.coerceAtMost(waterH * 0.2f),
                waterBottom = bedTop,
                brush = Brush.verticalGradient(listOf(waterTop, waterBottom), wSurfaceY, bedTop),
            )
        }

        // Filter line
        drawLine(filterColor, Offset(bL + 8f, bedTop), Offset(bR - 8f, bedTop), 2.5f)

        // Dispersion cap (chunky dashed line)
        val capY = bT + bH * 0.06f
        drawLine(
            capColor, Offset(bL + 14f, capY), Offset(bR - 14f, capY), 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )

        // Barrel walls — bold
        drawLine(outlineColor, Offset(bL, bT), Offset(bL, bB), thick)
        drawLine(outlineColor, Offset(bR, bT), Offset(bR, bB), thick)
        drawLine(outlineColor, Offset(bL, bB), Offset(bR, bB), thick)
        // Rim
        drawRoundRect(
            outlineColor, Offset(bL - 6f, bT - 4f), Size(bW + 12f, 10f),
            cornerRadius = CornerRadius(5f, 5f),
        )

        // Base walls — bold
        drawLine(outlineColor, Offset(xL, xT), Offset(xL, xB), thick)
        drawLine(outlineColor, Offset(xR, xT), Offset(xR, xB), thick)
        drawLine(outlineColor, Offset(xL, xT), Offset(bL, xT), stroke)
        drawLine(outlineColor, Offset(xR, xT), Offset(bR, xT), stroke)
        drawRoundRect(
            outlineColor, Offset(xL - 2f, xB - 2f), Size(xR - xL + 4f, 6f),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Valve — chunky circle with label
        val vR = w * 0.055f
        val vCenter = Offset(w * 0.5f, (xT + xB) / 2f)
        val vColor = if (valveOpen) valveOpenColor else valveClosedColor
        drawCircle(vColor.copy(alpha = 0.25f), vR + 4f, vCenter)
        drawCircle(vColor, vR, vCenter)
        drawCircle(outlineColor, vR, vCenter, style = Stroke(2.5f))

        // Valve label
        val vlText = if (valveOpen) "OPEN" else "SHUT"
        val vlResult = textMeasurer.measure(vlText, labelStyle)
        drawText(
            vlResult,
            topLeft = Offset(vCenter.x - vlResult.size.width / 2f, vCenter.y + vR + 6f),
        )

        // Drip drops
        if (showDrip && valveOpen) {
            val dripX = w * 0.5f
            val dropY1 = xB + h * 0.06f
            val dropY2 = xB + h * 0.13f
            val dropY3 = xB + h * 0.19f
            drawCircle(dripColor, 4f, Offset(dripX, dropY1))
            drawCircle(dripColor, 3f, Offset(dripX - 4f, dropY2))
            drawCircle(dripColor, 2.5f, Offset(dripX + 3f, dropY3))
        }

        // Labels on diagram
        val bedLabel = textMeasurer.measure("coffee", labelStyle)
        drawText(
            bedLabel,
            topLeft = Offset(bL + (bW - bedLabel.size.width) / 2f, bedTop + bedH * 0.25f),
        )
    }
}

/**
 * Interactive brew plan visualization: M3 Expressive design with large
 * animated Pulsar diagram, emoji phase pills, and bold typography.
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "waterFill",
    )
    val bedFraction = (coffeeG * 0.7f / 82f).coerceIn(0.12f, 0.35f)

    // Card background animates with phase type
    val cardColor by animateColorAsState(
        targetValue = when {
            current.phaseName == "Bloom" -> MaterialTheme.colorScheme.tertiaryContainer
            current.phaseName.startsWith("Drain") -> MaterialTheme.colorScheme.errorContainer
            current.phaseName == "Drawdown" -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardBg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            current.phaseName == "Bloom" -> MaterialTheme.colorScheme.onTertiaryContainer
            current.phaseName.startsWith("Drain") -> MaterialTheme.colorScheme.onErrorContainer
            current.phaseName == "Drawdown" -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Phase emoji + name — big and bold
            Text(
                text = "${current.emoji} ${current.phaseName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Diagram — large and centered
            PulsarBrewerDiagram(
                waterFillFraction = animatedFill,
                bedFraction = bedFraction,
                valveOpen = current.valveOpen,
                showDrip = current.valveOpen && current.waterInBrewer > 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Water progress — bold numbers
            if (current.waterThisPhase > 0f) {
                Text(
                    text = "+${"%.0f".format(current.waterThisPhase)}g",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            Text(
                text = "${"%.0f".format(current.cumulativeWater)}g of ${"%.0f".format(waterG)}g total",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.7f),
            )

            // Water progress bar
            Spacer(modifier = Modifier.height(10.dp))
            val progressFraction = if (waterG > 0f) (current.cumulativeWater / waterG).coerceIn(0f, 1f) else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(contentColor.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(contentColor.copy(alpha = 0.5f)),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instruction text
            Text(
                text = current.instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phase pills — scrollable row of tappable chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                states.forEachIndexed { index, state ->
                    val isSelected = index == selectedPhase
                    val pillBg = when {
                        isSelected -> contentColor
                        state.phaseName.startsWith("Drain") -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else -> contentColor.copy(alpha = 0.12f)
                    }
                    val pillText = when {
                        isSelected -> cardColor
                        else -> contentColor.copy(alpha = 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBg)
                            .clickable { selectedPhase = index }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isSelected) "${state.emoji} ${phaseShortLabel(state.phaseName)}" else phaseShortLabel(state.phaseName),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = pillText,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dose insights with visual bed depth bar and grind recommendation.
 * M3 Expressive: vibrant container, visual meter, bold type.
 */
@Composable
fun PulsarDoseInsights(
    coffeeG: Float,
    refillCount: Int,
    modifier: Modifier = Modifier,
) {
    val bedDepthMm = coffeeG * 0.7f
    // Ideal range is 14-25mm (20-35g). Fraction for visual bar:
    val bedBarFraction = (bedDepthMm / 25f).coerceIn(0f, 1f)
    val bedBarColor = when {
        bedDepthMm < 14f -> Color(0xFFEF5350) // too shallow
        bedDepthMm <= 20f -> Color(0xFF66BB6A) // great
        else -> Color(0xFFFFA726) // deep (fine, but different)
    }

    val grindNote = when {
        coffeeG <= 20f -> "Similar to V60"
        coffeeG <= 25f -> "Coarser than V60 · ~800μm"
        coffeeG <= 30f -> "Approaching batch grind"
        else -> "Batch territory — adjust by taste"
    }

    val doseEmoji = when {
        coffeeG < 20f -> "⚠️"
        coffeeG <= 25f -> "✨"
        coffeeG <= 30f -> "💪"
        else -> "🔥"
    }

    val doseNote = when {
        coffeeG < 20f -> "Below 20g risks astringency"
        coffeeG in 20f..25f -> "Sweet spot for clarity & balance"
        coffeeG in 25f..30f -> "Rich, full-bodied results"
        else -> "Go coarser, expect longer brew"
    }

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "$doseEmoji Dose Insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bed depth visual bar
            Text(
                text = "Bed depth: ~${"%.0f".format(bedDepthMm)}mm",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bedBarFraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(bedBarColor),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Shallow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f))
                Text("Deep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grind recommendation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚙️", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Grind",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    )
                    Text(
                        text = grindNote,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            if (refillCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔄", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$refillCount refill${if (refillCount > 1) "s" else ""} — timer guides you",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = doseNote,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
