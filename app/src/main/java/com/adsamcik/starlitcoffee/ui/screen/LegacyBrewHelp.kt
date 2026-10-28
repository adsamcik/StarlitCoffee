package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState

/** Secondary legacy recipe details and controls stay off the live brewing surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacyBrewHelpSheet(
    state: BrewUiState,
    bloomActive: Boolean,
    showInstructions: Boolean = true,
    onToggleMinuteAlert: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.action_brew_recipe),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.method.displayName, style = MaterialTheme.typography.titleMedium)
            if (state.coffeeG > 0f) {
                LegacyRecipeDetail(stringResource(R.string.label_coffee), "%.0f g".format(state.coffeeG))
            }
            if (state.waterG > 0f) {
                LegacyRecipeDetail(stringResource(R.string.label_water), "%.0f g".format(state.waterG))
            }
            if (state.method.hasBloom && state.bloomG > 0f) {
                LegacyRecipeDetail(stringResource(R.string.label_bloom_short), "%.0f g".format(state.bloomG))
            }
            if (state.timeTargetLowS > 0 && state.timeTargetHighS > 0) {
                LegacyRecipeDetail(
                    stringResource(R.string.label_brew_time),
                    formatLegacyTargetDurationRange(state.timeTargetLowS, state.timeTargetHighS),
                )
            }
            if (state.method.tempRangeLow > 0 && state.method.tempRangeHigh > 0) {
                LegacyRecipeDetail(
                    stringResource(R.string.label_temperature),
                    "${state.method.tempRangeLow}–${state.method.tempRangeHigh} °C",
                )
            }
            if (showInstructions) LegacyBrewInstruction(state, bloomActive)
            if (state.method.timingMode == BrewTimingMode.ACTIVE_TIMER) {
                TextButton(onClick = onToggleMinuteAlert, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(
                        if (state.minuteAlertEnabled) R.string.cd_disable_minute_alerts
                        else R.string.cd_enable_minute_alerts,
                    ))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_return_to_live_brew))
            }
        }
    }
}

@Composable
private fun LegacyRecipeDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LegacyBrewInstruction(state: BrewUiState, bloomActive: Boolean) {
    if (state.waterG <= 0f && state.bloomG <= 0f) return
    val methodGuidance = state.method.stageGuidance
    val methodInstruction = when {
        state.timeTargetLowS > 0 && state.elapsedSeconds >= state.timeTargetLowS ->
            methodGuidance.timerReadyRes ?: methodGuidance.timerActiveRes ?: methodGuidance.timerStartRes
        state.timerRunning || state.elapsedSeconds > 0 ->
            methodGuidance.timerActiveRes ?: methodGuidance.timerStartRes
        else -> methodGuidance.timerStartRes ?: methodGuidance.timerActiveRes
    }
    val primary = when {
        bloomActive -> {
            val duration = state.effectiveBloomDurationSeconds
            val elapsed = duration - (state.bloomCountdownSeconds ?: duration)
            val pourWindow = minOf(10, duration / 2).coerceAtLeast(3)
            if (elapsed < pourWindow) stringResource(R.string.instruction_bloom_pour, state.bloomG)
            else stringResource(R.string.instruction_bloom_wait)
        }
        state.bloomFinished && state.bloomMarkedAtSeconds != null -> {
            if (state.method == BrewMethod.CHEMEX && methodInstruction != null) stringResource(methodInstruction)
            else stringResource(R.string.instruction_pour_total, state.waterG)
        }
        state.method.hasBloom && state.bloomG > 0f -> stringResource(R.string.format_pour_bloom_water, state.bloomG)
        methodInstruction != null -> stringResource(methodInstruction)
        else -> stringResource(R.string.instruction_pour_total, state.waterG)
    }
    Text(primary, style = MaterialTheme.typography.bodyLarge)
    val valveInstruction = when {
        state.method != BrewMethod.PULSAR -> null
        state.bloomFinished && state.bloomMarkedAtSeconds != null -> R.string.instruction_open_valve_short
        bloomActive || state.bloomG > 0f -> R.string.instruction_close_valve_short
        else -> null
    }
    if (valveInstruction != null) {
        Text(
            stringResource(valveInstruction),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun formatLegacyTargetDurationRange(lowSeconds: Int, highSeconds: Int): String {
    if (lowSeconds <= 0 || highSeconds <= 0) return "–"
    return "${formatLegacyDuration(lowSeconds)}–${formatLegacyDuration(highSeconds)}"
}

private fun formatLegacyDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "$minutes:%02d".format(seconds % 60)
    }
}
