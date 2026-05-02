package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

@Composable
fun GrindPrepScreen(
    brewViewModel: BrewViewModel,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()

    // Hands are busy with grinder/scale/kettle here — don't let the screen sleep.
    KeepScreenOn()

    Scaffold(
        bottomBar = {
            // Sticky primary CTA — always visible, regardless of scroll position.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onNavigateToBrew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = stringResource(R.string.action_ready_to_brew_short),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(12.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTopBar(
                title = stringResource(R.string.screen_grind_prep_title),
                onBack = onBack,
            )

            // Surface guardrail warnings so the user notices issues with the
            // current setup (e.g. bloom > water, ratio outside sane range)
            // before committing to a brew.
            state.ratioWarning?.let { warning ->
                WarningCard(message = warning)
            }
            state.bloomWarning?.let { warning ->
                WarningCard(message = warning)
            }

            GrindHeroCard(state.grindResult)

            MetricsRow(
                coffeeG = state.coffeeG,
                waterG = state.waterG,
                tempLow = state.method.tempRangeLow,
                tempHigh = state.method.tempRangeHigh,
            )

            PrepTipCard(
                tipRes = prepTipFor(state.method, state.filterType),
            )
        }
    }
}

@Composable
private fun GrindHeroCard(grindResult: GrindResult) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val headerLabel = when (grindResult) {
                is GrindResult.Generic -> stringResource(R.string.label_grind)
                is GrindResult.Specific -> "${grindResult.grinder.brand} ${grindResult.grinder.model}"
            }
            Text(
                text = headerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            when (grindResult) {
                is GrindResult.Generic -> {
                    Text(
                        text = grindResult.descriptor.displayName,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = grindResult.descriptor.visualCue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                is GrindResult.Specific -> {
                    val formatted = formatGrindSetting(
                        grinder = grindResult.grinder,
                        value = grindResult.recommendation.suggestedStart,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatted.primary,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (formatted.unit != null) {
                            Text(
                                text = formatted.unit,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    if (formatted.breakdown != null) {
                        Text(
                            text = formatted.breakdown,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = rangeLine(
                            grinder = grindResult.grinder,
                            rec = grindResult.recommendation,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    if (grindResult.recommendation.adjustmentNote.isNotBlank()) {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = grindResult.recommendation.adjustmentNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

private data class FormattedGrindSetting(
    val primary: String,
    val unit: String?,
    val breakdown: String?,
)

/**
 * Format a grind setting value for display based on the grinder's scale type.
 *
 * - DIAL_CLICKS (e.g. 1Zpresso): "5.2" shown as "5.2" with breakdown "5 · 2 clicks"
 * - PURE_CLICKS (e.g. Comandante C40): shown as integer clicks count
 * - NUMBERED_DIAL (e.g. Fellow Ode): shown as printed dial setting
 */
private fun formatGrindSetting(grinder: Grinder, value: Float): FormattedGrindSetting {
    return when (grinder.scaleType) {
        GrinderScaleType.DIAL_CLICKS -> {
            val whole = value.toInt()
            val clicks = Math.round((value - whole) * 10f).coerceAtLeast(0)
            val clicksLabel = if (clicks == 1) "click" else "clicks"
            FormattedGrindSetting(
                primary = "%.1f".format(value),
                unit = null,
                breakdown = if (clicks > 0) "$whole + $clicks $clicksLabel" else "$whole (no extra clicks)",
            )
        }
        GrinderScaleType.PURE_CLICKS -> {
            val rounded = Math.round(value)
            FormattedGrindSetting(
                primary = rounded.toString(),
                unit = if (rounded == 1) "click" else "clicks",
                breakdown = "from zero",
            )
        }
        GrinderScaleType.NUMBERED_DIAL -> {
            FormattedGrindSetting(
                primary = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value),
                unit = null,
                breakdown = "on dial",
            )
        }
    }
}

private fun rangeLine(grinder: Grinder, rec: GrindRecommendation): String {
    return when (grinder.scaleType) {
        GrinderScaleType.PURE_CLICKS -> {
            val lo = Math.round(rec.rangeStart)
            val hi = Math.round(rec.rangeEnd)
            val step = Math.round(rec.adjustmentStepSize).coerceAtLeast(1)
            "range $lo–$hi clicks · ±$step to taste"
        }
        GrinderScaleType.DIAL_CLICKS -> {
            val stepClicks = Math.round(rec.adjustmentStepSize * 10f).coerceAtLeast(1)
            val clicksLabel = if (stepClicks == 1) "click" else "clicks"
            "range ${"%.1f".format(rec.rangeStart)}–${"%.1f".format(rec.rangeEnd)} · ±$stepClicks $clicksLabel to taste"
        }
        GrinderScaleType.NUMBERED_DIAL -> {
            val step = rec.adjustmentStepSize
            val stepLabel = if (step % 1f == 0f) step.toInt().toString() else "%.1f".format(step)
            val loLabel = if (rec.rangeStart % 1f == 0f) rec.rangeStart.toInt().toString() else "%.1f".format(rec.rangeStart)
            val hiLabel = if (rec.rangeEnd % 1f == 0f) rec.rangeEnd.toInt().toString() else "%.1f".format(rec.rangeEnd)
            "range $loLabel–$hiLabel · ±$stepLabel to taste"
        }
    }
}

@Composable
private fun MetricsRow(
    coffeeG: Float,
    waterG: Float,
    tempLow: Int,
    tempHigh: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricTile(
            icon = Icons.Filled.Coffee,
            label = stringResource(R.string.label_coffee),
            value = "${"%.0f".format(coffeeG)}g",
            subtitle = null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MetricTile(
            icon = Icons.Filled.LocalFireDepartment,
            label = stringResource(R.string.label_water),
            value = if (waterG > 0f) "${"%.0f".format(waterG)}g" else "–",
            subtitle = if (tempLow > 0 && tempHigh > 0) "$tempLow–$tempHigh°C" else null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrepTipCard(@androidx.annotation.StringRes tipRes: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.prep_tip_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(tipRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/**
 * Resolve a single concise prep tip string resource ID for the given brew method + filter combo.
 */
@androidx.annotation.StringRes
private fun prepTipFor(method: BrewMethod, filter: FilterType?): Int = when (method) {
    BrewMethod.PULSAR -> when (filter) {
        FilterType.METAL_19K -> R.string.prep_tip_pulsar_19k
        FilterType.METAL_40K -> R.string.prep_tip_pulsar_40k
        else -> R.string.prep_tip_pulsar_paper
    }
    BrewMethod.V60 -> R.string.prep_tip_pour_over_paper
    BrewMethod.FRENCH_PRESS -> R.string.prep_tip_french_press
    BrewMethod.AEROPRESS -> R.string.prep_tip_aeropress
    BrewMethod.ESPRESSO -> R.string.prep_tip_espresso
    BrewMethod.MOKA_POT -> R.string.prep_tip_moka
    BrewMethod.COLD_BREW -> R.string.prep_tip_cold_brew
}
