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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.ui.util.shortLabelRes
import com.adsamcik.starlitcoffee.ui.component.BrewPreviewCard
import com.adsamcik.starlitcoffee.ui.component.RatioPresetRow
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountStrengthScreen(
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
    onNavigateToTimer: () -> Unit,
){
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val coffeeBags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val activeBags = remember(coffeeBags) { coffeeBags.filter { it.status != "FINISHED" } }
    val selectedBag = remember(coffeeBags, selectedBagId) {
        coffeeBags.find { it.id == selectedBagId }
    }
    var showBagPicker by remember { mutableStateOf(false) }

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
        InputMode.COFFEE_TO_WATER -> stringResource(R.string.label_coffee_input)
        InputMode.WATER_TO_COFFEE -> stringResource(R.string.label_water_input)
        InputMode.BREW_SIZE_TO_BOTH -> stringResource(R.string.label_brew_size_input)
        InputMode.CUP_SIZE_TO_BOTH -> stringResource(R.string.label_cup_size_input)
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
        stringResource(R.string.format_capacity_note, method.displayName, it)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Back button + method context
        ScreenTopBar(
            title = stringResource(R.string.screen_brew_setup_title),
            onBack = onBack,
            modifier = Modifier.padding(top = 8.dp),
            actions = {
                val ratioLabel = ratioPresets.getOrNull(selectedPresetIndex)
                    ?.let { stringResource(it.labelResId, it.labelArg) }
                    ?: "1:${method.defaultRatio.toInt()}"
                Text(
                    text = "${method.displayName} · $ratioLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Coffee bag picker chip
        FilterChip(
            selected = selectedBag != null,
            onClick = {
                if (selectedBag != null) {
                    brewViewModel.selectBag(null)
                } else {
                    showBagPicker = true
                }
            },
            label = {
                val bagLabel = selectedBag?.let { bag ->
                    val decafLabel = if (bag.isDecaf) stringResource(R.string.label_decaf_suffix) else ""
                    val weightHint = bag.weightG?.let { w ->
                        stringResource(R.string.format_weight_left, w)
                    } ?: ""
                    "☕ ${bag.name}$decafLabel$weightHint"
                } ?: stringResource(R.string.label_select_coffee_bag)
                Text(text = bagLabel, maxLines = 1)
            },
            trailingIcon = if (selectedBag != null) {
                {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear_bag),
                    )
                }
            } else null,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp)
                .testTag("bag_picker_chip"),
        )

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
                    Text(stringResource(mode.shortLabelRes()), maxLines = 1)
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
            suffix = {
                val isMlMode = inputMode == InputMode.BREW_SIZE_TO_BOTH ||
                    inputMode == InputMode.CUP_SIZE_TO_BOTH
                Text(stringResource(if (isMlMode) R.string.unit_ml else R.string.unit_grams))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("amount_input"),
        )

        val doseCd = stringResource(R.string.format_coffee_dose_cd, amountFloat.toInt())
        Slider(
            value = amountFloat.coerceIn(0f, maxSlider),
            onValueChange = {
                val rounded = kotlin.math.round(it)
                brewViewModel.setAmount(rounded.toInt().toString())
            },
            valueRange = 0f..maxSlider,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("amount_slider")
                .semantics { contentDescription = doseCd },
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

        // Strength section
        Text(
            text = stringResource(R.string.label_strength),
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
                text = stringResource(R.string.label_filter),
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
        val advancedCd = if (advancedExpanded) stringResource(R.string.cd_collapse_advanced) else stringResource(R.string.cd_expand_advanced)
        TextButton(
            onClick = { advancedExpanded = !advancedExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.label_advanced_options), modifier = Modifier.semantics { heading() })
            Icon(
                imageVector = if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = advancedCd,
            )
        }

        AnimatedVisibility(visible = advancedExpanded) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedTextField(
                    value = customRatio,
                    onValueChange = { brewViewModel.setCustomRatio(it) },
                    label = { Text(stringResource(R.string.label_custom_ratio)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("custom_ratio_input"),
                )

                OutlinedTextField(
                    value = tempC,
                    onValueChange = { brewViewModel.setTempC(it) },
                    label = { Text(stringResource(R.string.label_temperature)) },
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
                        label = { Text(stringResource(R.string.label_bloom_multiplier)) },
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
                        label = { Text(stringResource(R.string.label_pulse_count)) },
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
                    text = stringResource(R.string.label_grind),
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
                        val rec = gr.recommendation
                        Text(
                            text = "Setting: ${fmt(rec.suggestedStart)} " +
                                "(range ${fmt(rec.rangeStart)}–${fmt(rec.rangeEnd)})",
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
                onNavigateToTimer()
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_timer_button"),
        ) {
            Text(stringResource(R.string.action_start_brewing), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Bag picker bottom sheet
    if (showBagPicker) {
        ModalBottomSheet(
            onDismissRequest = { showBagPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_select_coffee_bag_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (activeBags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_active_bags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(activeBags, key = { it.id }) { bag ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        brewViewModel.selectBagForBrewing(bag.id)
                                        showBagPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                            ) {
                                Column {
                                    val decafLabel = if (bag.isDecaf) stringResource(R.string.label_decaf_suffix) else ""
                                    Text(
                                        text = "${bag.name}$decafLabel",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (bag.id == selectedBagId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    val subtitleParts = buildList {
                                        bag.roaster?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        bag.weightG?.let { w -> add("${w.toInt()}g left") }
                                    }
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        brewViewModel.selectBag(null)
                        showBagPicker = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_brew_without_bag))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

