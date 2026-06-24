package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.adsamcik.starlitcoffee.ui.component.CoffeeBagSelector
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.ui.util.keepScreenOnTimeoutMillis
import com.adsamcik.starlitcoffee.ui.util.rememberDimModeController
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult
import kotlin.math.abs

// Generous estimate of how long hands-busy grind prep (grind + weigh + heat
// water) takes. There is no live timer on this screen, so it seeds the
// keep-awake safety timeout instead of a measured target time.
private const val GRIND_PREP_ESTIMATE_SECONDS = 240

@Composable
fun GrindPrepScreen(
    brewViewModel: BrewViewModel,
    dimModeEnabled: Boolean = true,
    dimModeTrueBlack: Boolean = false,
    dimModeReduceBrightness: Boolean = false,
    dimModeFullscreen: Boolean = false,
    dimModeForceDarkInLight: Boolean = false,
    showBrewingInstructions: Boolean = true,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()

    // Optional coffee selection happens right here on the grind step: the user
    // is committing to a brew, so let them attach which coffee they're making.
    val coffeeBags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val trackedBags = remember(coffeeBags) {
        coffeeBags.filter { it.status != "FINISHED" }
    }
    val selectedBag = remember(coffeeBags, selectedBagId) {
        coffeeBags.find { it.id == selectedBagId }
    }

    // Hands are busy with grinder/scale/kettle here — don't let the screen sleep.
    // No live timer drives this step, so cap the hold at a generous multiple of
    // an estimated prep window (grind + weigh + heat water) so a phone left on
    // the counter eventually sleeps instead of being pinned on indefinitely.
    KeepScreenOn(timeoutMillis = keepScreenOnTimeoutMillis(GRIND_PREP_ESTIMATE_SECONDS))

    val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)
    DimModeScaffold(
        controller = dimController,
        modifier = Modifier.fillMaxSize(),
        trueBlackBackground = dimModeTrueBlack,
        reduceBrightness = dimModeReduceBrightness,
        hideSystemBars = dimModeFullscreen,
        forceDarkInLight = dimModeForceDarkInLight,
    ) {
        Scaffold(
            bottomBar = {
                // Sticky primary CTA — anchored to the bottom regardless of
                // scroll position. The Surface keeps the action area on the
                // app canvas so the button doesn't appear to float over a
                // disconnected void on long screens.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = onNavigateToBrew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = primaryActionButtonColors(),
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ScreenTopBar(
                    title = stringResource(R.string.screen_grind_prep_title),
                    onBack = onBack,
                )

                MethodBreadcrumb(
                    method = state.method,
                    filter = state.filterType,
                    ratio = state.effectiveRatio,
                )

                // Optional: attach the coffee being brewed. Only surfaced when
                // there's something to pick (a tracked bag exists or one is
                // already selected) so the grind hero stays the focus otherwise.
                if (trackedBags.isNotEmpty() || selectedBag != null) {
                    CoffeeBagSelector(
                        bags = trackedBags,
                        selectedBag = selectedBag,
                        onSelectBag = { brewViewModel.selectBagForBrewing(it) },
                        onClearBag = { brewViewModel.selectBag(null) },
                    )
                }

                // Surface guardrail warnings so the user notices issues with the
                // current setup (e.g. bloom > water, ratio outside sane range)
                // before committing to a brew. Errors deliberately keep their
                // colored container — they're the one signal that should not
                // blend into the calm canvas.
                state.ratioWarning?.let { warning ->
                    WarningCard(message = warning)
                }
                state.bloomWarning?.let { warning ->
                    WarningCard(message = warning)
                }

                DualHeroSection(state = state)

                SectionDivider()

                RecipeSection(state = state)

                // Prep section — the pre-brew setup checklist. Hidden when
                // the user has turned off in-brew instructions in Settings
                // (they know the method by heart).
                if (showBrewingInstructions) {
                    SectionDivider()

                    PrepSection(
                        method = state.method,
                        filter = state.filterType,
                        grindResult = state.grindResult,
                        coffeeG = state.coffeeG,
                    )
                }
            }
        }
    }
}

/**
 * Subtle single-line context strip — "Pulsar · Paper filter · 1:17" — pinned
 * under the title so the brew identity is always visible without competing
 * with the grind hero below.
 */
@Composable
private fun MethodBreadcrumb(
    method: BrewMethod,
    filter: FilterType?,
    ratio: Float,
) {
    val parts = buildList {
        add(method.displayName)
        if (filter != null) add("${filter.displayName} filter")
        if (ratio > 0f) add("1:${formatRatio(ratio)}")
    }
    Text(
        text = parts.joinToString(separator = " · "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Two co-equal hero stats side by side: the coffee dose (how much to weigh
 * and grind — the number most people reach for first) and the grinder
 * setting (often already known, but kept just as prominent). Detail that
 * doesn't belong in the columns — grinder name, suggested range, or the
 * generic texture cue — drops to a full-width caption beneath both.
 */
@Composable
private fun DualHeroSection(state: BrewUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            DoseHero(
                modifier = Modifier.weight(1f),
                coffeeG = state.coffeeG,
            )
            VerticalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            GrinderHero(
                modifier = Modifier.weight(1f),
                grindResult = state.grindResult,
            )
        }
        GrinderHeroCaption(grindResult = state.grindResult)
    }
}

/**
 * Coffee dose hero — the "how much do I grind?" answer in grams.
 */
@Composable
private fun DoseHero(modifier: Modifier = Modifier, coffeeG: Float) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeroLabel(text = stringResource(R.string.label_coffee))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "%.0f".format(coffeeG),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "g",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

/**
 * Grinder setting hero — the dial value (or generic descriptor) to dial in.
 * Co-equal in size with the dose; the breakdown line stays compact so the
 * two columns balance, with the rest pushed to [GrinderHeroCaption].
 */
@Composable
private fun GrinderHero(modifier: Modifier = Modifier, grindResult: GrindResult) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeroLabel(text = stringResource(R.string.label_grind))
        when (grindResult) {
            is GrindResult.Generic -> {
                Text(
                    text = grindResult.descriptor.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            is GrindResult.Specific -> {
                val formatted = formatGrindSetting(
                    grinder = grindResult.grinder,
                    value = grindResult.recommendation.suggestedStart,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatted.primary,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (formatted.unit != null) {
                        Text(
                            text = formatted.unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
                formatted.breakdown?.let { breakdown ->
                    Text(
                        text = breakdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Full-width supporting caption under the two heroes: the grinder name and
 * suggested range for a specific grinder, or the texture cue for a generic
 * descriptor.
 */
@Composable
private fun GrinderHeroCaption(grindResult: GrindResult) {
    val caption = when (grindResult) {
        is GrindResult.Generic -> grindResult.descriptor.visualCue
        is GrindResult.Specific -> buildList {
            add("${grindResult.grinder.brand} ${grindResult.grinder.model}")
            add(rangeLine(grinder = grindResult.grinder, rec = grindResult.recommendation))
        }.joinToString(separator = " · ")
    }
    if (caption.isNotBlank()) {
        Text(
            text = caption,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Flat label/value recipe rows. Surfaces every parameter the user is about
 * to act on — including ratio, bloom amount and target time which were
 * previously invisible despite already being in [BrewUiState].
 */
@Composable
private fun RecipeSection(state: BrewUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(text = stringResource(R.string.label_recipe_heading))
        if (state.waterG > 0f) {
            RecipeRow(
                label = stringResource(R.string.label_water),
                value = "${"%.0f".format(state.waterG)} g",
            )
        }
        val tempLow = state.method.tempRangeLow
        val tempHigh = state.method.tempRangeHigh
        if (tempLow > 0 && tempHigh > 0) {
            RecipeRow(
                label = stringResource(R.string.label_temp_short),
                value = "$tempLow–$tempHigh °C",
            )
        }
        if (state.effectiveRatio > 0f) {
            RecipeRow(
                label = stringResource(R.string.label_ratio),
                value = "1:${formatRatio(state.effectiveRatio)}",
            )
        }
        if (state.method.hasBloom && state.bloomG > 0f) {
            RecipeRow(
                label = stringResource(R.string.label_bloom_short),
                value = "${"%.0f".format(state.bloomG)} g · ${state.effectiveBloomDurationSeconds} s",
            )
        }
        if (state.timeTargetLowS > 0 && state.timeTargetHighS > 0) {
            RecipeRow(
                label = stringResource(R.string.label_target),
                value = formatTargetDurationRange(state.timeTargetLowS, state.timeTargetHighS),
            )
        }
    }
}

@Composable
private fun RecipeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Splits the localised prep paragraph into one bullet per sentence so the
 * checklist can be scanned at a glance with busy hands.
 */
@Composable
private fun PrepSection(
    method: BrewMethod,
    filter: FilterType?,
    grindResult: GrindResult,
    coffeeG: Float,
) {
    val tipString = stringResource(prepTipFor(method, filter))
    val steps = remember(tipString) { splitIntoSteps(tipString) }
    // Lead the checklist with a grind step tailored to the current setup: the
    // exact dose plus the grinder's dial setting for a known grinder, or a
    // texture cue when no grinder is configured. Keeps the prep actionable for
    // each supported grinder instead of a generic "add grounds".
    val grindStep = grindPrepStep(grindResult = grindResult, coffeeG = coffeeG)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(text = stringResource(R.string.prep_tip_label))
        grindStep?.let { PrepStep(text = it) }
        steps.forEach { step ->
            PrepStep(text = step)
        }
    }
}

/**
 * Builds the setup-aware grind step shown at the top of the prep checklist.
 * Returns null when there is no dose to act on yet.
 */
@Composable
private fun grindPrepStep(grindResult: GrindResult, coffeeG: Float): String? {
    if (coffeeG <= 0f) return null
    val dose = "${"%.0f".format(coffeeG)} g"
    return when (grindResult) {
        is GrindResult.Generic -> stringResource(
            R.string.format_prep_grind_generic,
            dose,
            grindResult.descriptor.displayName,
            grindResult.descriptor.visualCue,
        )
        is GrindResult.Specific -> stringResource(
            R.string.format_prep_grind_specific,
            dose,
            "${grindResult.grinder.brand} ${grindResult.grinder.model}",
            grindSettingInline(
                grinder = grindResult.grinder,
                value = grindResult.recommendation.suggestedStart,
            ),
        )
    }
}

/**
 * Compact one-line grinder setting for the prep step — the dial value plus a
 * clicks breakdown only where it clarifies (manual click grinders). Mirrors
 * [formatGrindSetting] but drops the parenthetical noise ("on dial", "from
 * zero") that doesn't help inside a sentence.
 */
private fun grindSettingInline(grinder: Grinder, value: Float): String {
    return when (grinder.scaleType) {
        GrinderScaleType.DIAL_CLICKS -> {
            val whole = value.toInt()
            val clicks = Math.round((value - whole) * 10f).coerceAtLeast(0)
            if (clicks > 0) {
                val label = if (clicks == 1) "click" else "clicks"
                "${"%.1f".format(value)} ($whole + $clicks $label)"
            } else {
                "%.1f".format(value)
            }
        }
        GrinderScaleType.PURE_CLICKS -> {
            val rounded = Math.round(value)
            val label = if (rounded == 1) "click" else "clicks"
            "$rounded $label"
        }
        GrinderScaleType.NUMBERED_DIAL -> {
            if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
        }
    }
}

@Composable
private fun PrepStep(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private data class FormattedGrindSetting(
    val primary: String,
    val unit: String?,
    val breakdown: String?,
)

/**
 * Format a grind setting value for display based on the grinder's scale type.
 *
 * - DIAL_CLICKS (e.g. 1Zpresso): "5.2" shown as "5.2" with breakdown "5 + 2 clicks"
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

private fun formatRatio(ratio: Float): String {
    return if (ratio % 1f == 0f) {
        ratio.toInt().toString()
    } else {
        "%.1f".format(ratio)
    }
}

/**
 * Splits a prep paragraph into one entry per sentence so the section can
 * render as a checklist. Sentences are detected by punctuation+whitespace,
 * which matches the natural breaks in both EN and CS source strings.
 * Trailing punctuation is trimmed so bullets read uniformly.
 */
private fun splitIntoSteps(text: String): List<String> {
    return text.split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim().trimEnd('.', '!', '?') }
        .filter { it.isNotBlank() }
}

private fun formatTargetDurationRange(lowSeconds: Int, highSeconds: Int): String {
    if (lowSeconds <= 0 || highSeconds <= 0) return "–"
    return "${formatLongDuration(lowSeconds)}–${formatLongDuration(highSeconds)}"
}

private fun formatLongDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> formatBrewTime(seconds)
    }
}

private fun formatBrewTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
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
    else -> method.stageGuidance.prepTipRes
}
