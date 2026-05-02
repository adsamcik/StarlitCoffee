package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.util.FreshnessInsight
import com.adsamcik.starlitcoffee.util.FreshnessPhase
import com.adsamcik.starlitcoffee.util.GrindOutcomeEntry
import com.adsamcik.starlitcoffee.util.GrindOutcomeTag

@Composable
fun FreshnessRing(
    insight: FreshnessInsight,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
) {
    val accent = freshnessColor(insight.phase)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { insight.ringProgress.coerceIn(0.08f, 1f) },
            modifier = Modifier.size(size),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 5.dp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            insight.daysSinceRoast?.let { days ->
                Text(
                    text = days.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = insight.phase.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightChipRow(
    chips: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
) {
    if (chips.isEmpty()) return
    val visible = chips.take(maxVisible)
    val remaining = chips.size - visible.size

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { chip ->
            InsightChip(label = chip)
        }
        if (remaining > 0) {
            InsightChip(label = "+$remaining more")
        }
    }
}

@Composable
fun GrindOutcomeChip(
    entry: GrindOutcomeEntry,
    modifier: Modifier = Modifier,
) {
    InsightChip(
        label = "${entry.outcome.label}: ${entry.grindSetting}",
        modifier = modifier,
        emphasis = when (entry.outcome) {
            GrindOutcomeTag.WORKED -> ChipEmphasis.POSITIVE
            GrindOutcomeTag.TOO_FINE,
            GrindOutcomeTag.TOO_COARSE,
            -> ChipEmphasis.WARNING
            GrindOutcomeTag.UNKNOWN -> ChipEmphasis.NEUTRAL
        },
    )
}

@Composable
fun InsightChip(
    label: String,
    modifier: Modifier = Modifier,
    emphasis: ChipEmphasis = ChipEmphasis.NEUTRAL,
) {
    val containerColor = when (emphasis) {
        ChipEmphasis.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        ChipEmphasis.POSITIVE -> MaterialTheme.colorScheme.secondaryContainer
        ChipEmphasis.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        ChipEmphasis.CRITICAL -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (emphasis) {
        ChipEmphasis.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        ChipEmphasis.POSITIVE -> MaterialTheme.colorScheme.onSecondaryContainer
        ChipEmphasis.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        ChipEmphasis.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

enum class ChipEmphasis {
    NEUTRAL,
    POSITIVE,
    WARNING,
    CRITICAL,
}

@Composable
private fun freshnessColor(phase: FreshnessPhase) = when (phase) {
    FreshnessPhase.DEGASSING -> MaterialTheme.colorScheme.secondary
    FreshnessPhase.PEAK -> MaterialTheme.colorScheme.primary
    FreshnessPhase.MELLOWING -> MaterialTheme.colorScheme.tertiary
    FreshnessPhase.VINTAGE -> MaterialTheme.colorScheme.error
    FreshnessPhase.UNKNOWN -> MaterialTheme.colorScheme.outline
}
