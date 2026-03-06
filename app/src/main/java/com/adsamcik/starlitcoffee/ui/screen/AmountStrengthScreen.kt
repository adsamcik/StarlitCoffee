package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.ui.util.shortLabel
import com.adsamcik.starlitcoffee.navigation.BrewTimer
import com.adsamcik.starlitcoffee.ui.component.BrewPreviewCard
import com.adsamcik.starlitcoffee.ui.component.RatioPresetRow
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

@Composable
fun AmountStrengthScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()

    val method = uiState.method
    val inputMode = uiState.inputMode
    val amount = uiState.amount
    val ratioPresets = uiState.ratioPresets
    val selectedPresetIndex = uiState.selectedPresetIndex
    val customRatio = uiState.customRatio
    val tempC = uiState.tempC
    val filterType = uiState.filterType
    val bloomMultiplier = uiState.bloomMultiplier
    val pulseCount = uiState.pulseCount

    val focusManager = LocalFocusManager.current
    var advancedExpanded by remember { mutableStateOf(false) }

    val amountLabel = when (inputMode) {
        InputMode.COFFEE_TO_WATER -> "Coffee (g)"
        InputMode.WATER_TO_COFFEE -> "Water (g)"
        InputMode.BREW_SIZE_TO_BOTH -> "Brew size (ml)"
        InputMode.CUP_SIZE_TO_BOTH -> "Cup size (ml)"
    }

    val amountFloat = amount.toFloatOrNull() ?: 0f

    // Smart slider range: allow large doses; refills handle capacity limits
    val maxSlider = when (inputMode) {
        InputMode.COFFEE_TO_WATER -> {
            val singleFillDose = method.capacityMaxG?.let { it.toFloat() / method.defaultRatio }
            if (singleFillDose != null) {
                // Allow up to ~3 fills worth of coffee via slider
                (singleFillDose * 3f).coerceIn(30f, 100f)
            } else {
                50f
            }
        }
        else -> {
            // Water/brew-size modes: allow up to ~3 fills, refills handle the rest
            val singleFill = method.capacityMaxG?.toFloat()
            if (singleFill != null) (singleFill * 3f).coerceAtLeast(500f) else 1000f
        }
    }

    val capacityHint = method.capacityMaxG?.let {
        "${method.displayName} holds ${it}g water — refills are automatic for larger brews"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Back button + method context
        ScreenTopBar(
            title = "",
            onBack = { navController.popBackStack() },
            modifier = Modifier.padding(top = 8.dp),
            actions = {
                Text(
                    text = "${method.displayName} · ${ratioPresets.getOrNull(selectedPresetIndex)?.label ?: "1:${method.defaultRatio.toInt()}"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input mode selector (merged from InputModeScreen)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            InputMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = inputMode == mode,
                    onClick = { brewViewModel.setInputMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = InputMode.entries.size,
                    ),
                ) {
                    Text(mode.shortLabel(), maxLines = 1)
                }
            }
        }

        // Amount section
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp)
                .semantics { heading() },
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { brewViewModel.setAmount(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
            suffix = { Text(if (inputMode == InputMode.BREW_SIZE_TO_BOTH || inputMode == InputMode.CUP_SIZE_TO_BOTH) "ml" else "g") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Slider(
            value = amountFloat.coerceIn(0f, maxSlider),
            onValueChange = {
                val rounded = kotlin.math.round(it)
                brewViewModel.setAmount(rounded.toInt().toString())
            },
            valueRange = 0f..maxSlider,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )

        if (capacityHint != null) {
            Text(
                text = capacityHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Ratio section
        Text(
            text = "Ratio",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp)
                .semantics { heading() },
        )

        RatioPresetRow(
            presets = ratioPresets,
            selectedIndex = selectedPresetIndex,
            onSelectPreset = { brewViewModel.selectRatioPreset(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter type (Pulsar only, requires grinder for meaningful effect)
        if (method == BrewMethod.PULSAR && uiState.selectedGrinderId != null) {
            Text(
                text = "Filter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                FilterType.entries.forEachIndexed { index, ft ->
                    SegmentedButton(
                        selected = filterType == ft,
                        onClick = { brewViewModel.setFilterType(ft) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FilterType.entries.size,
                        ),
                    ) {
                        Text(ft.displayName)
                    }
                }
            }
        }

        // Advanced options
        TextButton(
            onClick = { advancedExpanded = !advancedExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Advanced options")
            Icon(
                imageVector = if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }

        AnimatedVisibility(visible = advancedExpanded) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedTextField(
                    value = customRatio,
                    onValueChange = { brewViewModel.setCustomRatio(it) },
                    label = { Text("Custom ratio (e.g. 16)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = tempC,
                    onValueChange = { brewViewModel.setTempC(it) },
                    label = { Text("Temperature (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                if (method.hasBloom) {
                    OutlinedTextField(
                        value = bloomMultiplier,
                        onValueChange = { brewViewModel.setBloomMultiplier(it) },
                        label = { Text("Bloom multiplier (e.g. 3.0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }

                if (method.hasPulses) {
                    OutlinedTextField(
                        value = pulseCount,
                        onValueChange = { brewViewModel.setPulseCount(it) },
                        label = { Text("Pulse count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live result preview
        BrewPreviewCard(
            coffeeG = uiState.coffeeG,
            waterG = uiState.waterG,
            ratio = uiState.effectiveRatio,
            coffeeFormat = "%.1f",
            ratioFormat = "%.1f",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Grind recommendation
        ElevatedCard(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Grind",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                when (val gr = uiState.grindResult) {
                    is GrindResult.Generic -> {
                        Text(
                            text = "${gr.descriptor.displayName} – ${gr.descriptor.visualCue}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is GrindResult.Specific -> {
                        val fmt = { v: Float -> if (v % 1f == 0f) "%.0f".format(v) else "%.1f".format(v) }
                        Text(
                            text = "Setting: ${fmt(gr.recommendation.suggestedStart)} (range ${fmt(gr.recommendation.rangeStart)}–${fmt(gr.recommendation.rangeEnd)})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${gr.recommendation.adjustmentNote} · Adjust by ±${fmt(gr.recommendation.adjustmentStepSize)} to taste",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        // Guardrail warnings
        uiState.ratioWarning?.let { warning ->
            Text(
                text = "⚠️ $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Navigate to full details + timer
        FilledTonalButton(
            onClick = {
                focusManager.clearFocus()
                navController.navigate(BrewTimer)
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start Brewing →", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}