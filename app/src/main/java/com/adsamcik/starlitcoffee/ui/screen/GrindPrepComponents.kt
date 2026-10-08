package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.GrindResult
import com.adsamcik.starlitcoffee.viewmodel.PreparedGrind
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource
import com.adsamcik.starlitcoffee.ui.component.preparationGrindSource
import com.adsamcik.starlitcoffee.ui.component.preparationSavedGrindExplanation
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
internal fun PreparationIdentity(state: BrewUiState) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            state.method.displayName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (state.timeTargetLowS > 0 && state.timeTargetHighS > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.label_brew_time) + " · " +
                        preparationDurationRange(state.timeTargetLowS, state.timeTargetHighS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PreparationBoard(
    state: BrewUiState,
    onTypeSelected: (Boolean) -> Unit,
    coffeeSelection: @Composable () -> Unit,
    onEditGrind: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreparationMetric(
                    label = stringResource(R.string.label_coffee),
                    value = preparationNumber(state.coffeeG),
                    unit = "g",
                    testTag = "grind_prep_dose",
                )
                coffeeSelection()
                CoffeeTypeSelector(
                    isDecaf = state.isDecafBrew,
                    onTypeSelected = onTypeSelected,
                    showAdjustmentHint = state.preparedGrind.source == GrindSettingSource.RECOMMENDATION,
                )
            }
            PreparationRowDivider()
            Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                Column {
                    when (val result = state.grindResult) {
                        is GrindResult.Specific -> PreparationGrinderDetails(
                            result,
                            state.preparedGrind.takeIf { it.displayValue.isNotBlank() },
                            state.isDecafBrew,
                            onEditGrind,
                        )
                        is GrindResult.Generic -> {
                            PreparationGrindMetric(result)
                            Text(
                                text = result.descriptor.visualCue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparationRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

/** Supporting details use two columns only while labels and values fit comfortably. */
@Composable
private fun PreparationPair(
    minimumSideBySideWidth: Dp = 280.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < minimumSideBySideWidth || LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { first() }
                Column(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun PreparationMetric(
    label: String,
    value: String,
    unit: String? = null,
    detail: String? = null,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val unitStyle = MaterialTheme.typography.titleLarge
            Text(
                text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontWeight = FontWeight.Normal)) {
                            append(" $unit")
                        }
                    }
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PreparationGrinderIllustration(icon: Int, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon == 0) {
            Icon(Icons.Filled.Settings, null, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Box
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun PreparationGrindMetric(result: GrindResult, prepared: PreparedGrind? = null) {
    val value = if (prepared != null && result is GrindResult.Specific) {
        if (result.grinder.scaleType == GrinderScaleType.PURE_CLICKS) {
            preparationClicks(prepared.clicks ?: result.recommendation.suggestedStart.roundToInt())
        } else {
            prepared.displayValue
        }
    } else when (result) {
        is GrindResult.Generic -> result.descriptor.displayName
        is GrindResult.Specific -> {
            val setting = result.recommendation.suggestedStart
            when (result.grinder.scaleType) {
                GrinderScaleType.DIAL_CLICKS -> preparationNumber(setting, minimumDecimals = 1)
                GrinderScaleType.PURE_CLICKS -> preparationClicks(setting.roundToInt())
                GrinderScaleType.NUMBERED_DIAL -> preparationNumber(setting)
            }
        }
    }
    PreparationMetric(
        label = stringResource(R.string.label_grind),
        value = value,
        testTag = "grind_prep_grind",
    )
}

@Composable
private fun preparationGrindExplanation(result: GrindResult.Specific): String = when (result.grinder.scaleType) {
    GrinderScaleType.DIAL_CLICKS -> {
        val setting = result.recommendation.suggestedStart
        val whole = setting.toInt()
        val clicks = ((setting - whole) * 10f).roundToInt().coerceAtLeast(0)
        if (clicks > 0) {
            stringResource(R.string.prep_grind_dial_breakdown, whole, preparationClicks(clicks))
        } else {
            stringResource(R.string.prep_grind_no_extra_clicks, whole)
        }
    }
    GrinderScaleType.PURE_CLICKS -> stringResource(R.string.prep_grind_from_zero)
    GrinderScaleType.NUMBERED_DIAL -> stringResource(R.string.prep_grind_on_dial)
}

/** Model silhouettes are decorative; custom grinders retain a truthful form-factor fallback. */
@DrawableRes
internal fun preparationGrinderIcon(grinder: Grinder): Int = when (grinder.id) {
    "1zpresso-zp6-special" -> R.drawable.equipment_grinder_zp6
    "comandante-c40" -> R.drawable.equipment_grinder_c40
    "fellow-ode-gen2" -> R.drawable.equipment_grinder_ode
    "baratza-encore-esp" -> R.drawable.equipment_grinder_encore
    "niche-zero" -> R.drawable.equipment_grinder_niche
    else -> 0
}

@Composable
private fun PreparationGrinderDetails(
    result: GrindResult.Specific,
    prepared: PreparedGrind?,
    isDecaf: Boolean,
    onEditGrind: (() -> Unit)?,
) {
    var expanded by rememberSaveable(result.grinder.id) { mutableStateOf(false) }
    val grinderName = if (result.grinder.brand.equals(result.grinder.model, ignoreCase = true)) {
        result.grinder.model
    } else {
        "${result.grinder.brand} ${result.grinder.model}"
    }
    Column(Modifier.fillMaxWidth()) {
        if (onEditGrind != null && prepared?.availableScopes?.isNotEmpty() == true) {
            Surface(
                onClick = onEditGrind,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.fillMaxWidth().testTag("prep_edit_grind"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { PreparationGrindMetric(result, prepared) }
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.prep_grind_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            PreparationGrindMetric(result, prepared)
        }
        prepared?.let {
            Text(
                preparationGrindSource(it.source, isDecaf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag("prep_grind_source"),
            )
        }
        PreparationDisclosure(
            title = grinderName,
            expanded = expanded,
            onClick = { expanded = !expanded },
            testTag = "grind_prep_grinder_details",
            secondary = true,
        )
        AnimatedVisibility(expanded) {
            val rec = result.recommendation
            val range: String
            val adjustment: String
            when (result.grinder.scaleType) {
                GrinderScaleType.PURE_CLICKS -> {
                    range = "${preparationNumber(rec.rangeStart)}–${preparationClicks(rec.rangeEnd.roundToInt())}"
                    adjustment = preparationClicks(rec.adjustmentStepSize.roundToInt().coerceAtLeast(1))
                }
                GrinderScaleType.DIAL_CLICKS -> {
                    range = "${preparationNumber(rec.rangeStart, 1)}–${preparationNumber(rec.rangeEnd, 1)}"
                    adjustment = preparationClicks((rec.adjustmentStepSize * 10).roundToInt().coerceAtLeast(1))
                }
                GrinderScaleType.NUMBERED_DIAL -> {
                    range = "${preparationNumber(rec.rangeStart)}–${preparationNumber(rec.rangeEnd)}"
                    adjustment = preparationNumber(rec.adjustmentStepSize)
                }
            }
            Row(
                Modifier.fillMaxWidth().testTag("grind_prep_grinder_guidance").padding(top = 6.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreparationGrinderIllustration(preparationGrinderIcon(result.grinder), 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        prepared?.let { preparationSavedGrindExplanation(it) } ?: preparationGrindExplanation(result),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(stringResource(R.string.prep_grinder_range), style = MaterialTheme.typography.labelMedium)
                    Text(range, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = stringResource(R.string.prep_grinder_adjustment, adjustment),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreparationEquipmentCard(state: BrewUiState) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PreparationHeading(stringResource(R.string.prep_equipment_station))
        PreparationSteps(state.method, state.filterType)
    }
}

/** Recipe targets are visible before grinding, so hot water can heat during preparation. */
@Composable
internal fun PreparationTargets(state: BrewUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("grind_prep_targets")
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        PreparationWaterSummary(state)
    }
}

@Composable
private fun PreparationWaterSummary(state: BrewUiState) {
    val beverageYield = state.method.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD
    val customTemp = state.tempC.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    val temperature = when {
        customTemp != null -> preparationNumber(customTemp) + " °C"
        state.method == BrewMethod.COLD_BREW -> null
        state.method.tempRangeLow > 0 && state.method.tempRangeHigh > 0 ->
            "${state.method.tempRangeLow}–${state.method.tempRangeHigh} °C"
        else -> null
    }
    PreparationPair(
        minimumSideBySideWidth = 240.dp,
        first = {
            if (state.waterG > 0f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (beverageYield) R.string.prep_beverage_yield else R.string.label_water),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        preparationNumber(state.waterG) + " g",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        second = {
            temperature?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.label_temp_short),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}

@Composable
private fun PreparationSteps(method: BrewMethod, filter: FilterType?) {
    val tip = stringResource(
        if (method == BrewMethod.PULSAR) {
            when (filter) {
                FilterType.METAL_19K -> R.string.prep_tip_pulsar_19k
                FilterType.METAL_40K -> R.string.prep_tip_pulsar_40k
                else -> R.string.prep_tip_pulsar_paper
            }
        } else {
            method.stageGuidance.prepTipRes
        },
    )
    val steps = tip.split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim().trimEnd('.', '!', '?') }
        .filter { it.isNotBlank() }
    Column(modifier = Modifier.testTag("grind_prep_steps"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.TopStart) {
                    Text(
                        text = NumberFormat.getIntegerInstance().format(index + 1),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PreparationRecipeDetails(state: BrewUiState) {
    var expanded by rememberSaveable(state.method) { mutableStateOf(false) }
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        PreparationDisclosure(
            stringResource(R.string.prep_recipe_details),
            expanded,
            onClick = { expanded = !expanded },
            testTag = "grind_prep_recipe_details",
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.effectiveRatio > 0f) {
                    PreparationDetail(stringResource(R.string.label_ratio), "1:${preparationNumber(state.effectiveRatio)}")
                }
                state.filterType?.let { PreparationDetail(stringResource(R.string.label_filter), it.displayName) }
                if (state.method.hasBloom && state.bloomG > 0f) {
                    PreparationDetail(
                        stringResource(R.string.label_bloom_short),
                        "${preparationNumber(state.bloomG)} g · ${preparationDuration(state.effectiveBloomDurationSeconds)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparationDisclosure(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    testTag: String,
    secondary: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = if (secondary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
                color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.cd_collapse_advanced else R.string.cd_expand_advanced),
                tint = LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun PreparationHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PreparationDetail(label: String, value: String) {
    PreparationPair(
        first = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
        second = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
    )
}

@Composable
private fun preparationClicks(count: Int): String = pluralStringResource(R.plurals.prep_clicks, count, count)

internal fun preparationNumber(value: Float, minimumDecimals: Int = 0): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = minimumDecimals
    maximumFractionDigits = 1
    isGroupingUsed = false
}.format(value)

private fun preparationDurationRange(low: Int, high: Int): String =
    if (low == high) preparationDuration(low) else "${preparationDuration(low)}–${preparationDuration(high)}"

private fun preparationDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds % 60)
        else -> "%d:%02d".format(minutes, seconds % 60)
    }
}
