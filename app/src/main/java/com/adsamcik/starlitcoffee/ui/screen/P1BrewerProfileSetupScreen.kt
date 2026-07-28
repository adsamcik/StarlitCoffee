package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewTimeRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.CapacityRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.PrimaryOutputQuantity
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.TemperatureRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupOption
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupUiState
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileStartSelection
import com.adsamcik.starlitcoffee.viewmodel.CezveSessionSetup
import java.util.Locale

/**
 * Selection-first setup for the P1 durable brewer profiles.
 *
 * Callers retain the state and construct a recipe snapshot after [onStart]
 * fires. The screen never infers capacity, equipment configuration, or a
 * stage plan for an unknown profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P1BrewerProfileSetupScreen(
    state: P1BrewerProfileSetupUiState,
    onProfileSelected: (com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId) -> Unit,
    onHarioSwitchWorkflowSelected: (HarioSwitchWorkflow) -> Unit,
    onEquipmentCapacityChanged: (String) -> Unit,
    onCezveSugarSelected: (Boolean) -> Unit,
    onCezveFoamRiseCyclesSelected: (Int) -> Unit,
    onCezveHeatSourceSelected: (HeatSourceClass) -> Unit,
    onStart: (P1BrewerProfileStartSelection) -> Unit,
    onBack: () -> Unit,
    onLearn: ((P1BrewerProfileStartSelection) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_brew_setup_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isStarting) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_choose),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_choose_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.profiles.isEmpty()) {
                ProfileSetupEmptyState()
            } else {
                state.profiles.forEach { option ->
                    BrewerProfileOptionCard(
                        option = option,
                        selected = option.profileId == state.selectedProfileId,
                        onClick = { onProfileSelected(option.profileId) },
                    )
                }
            }

            state.selectedProfile?.let { selected ->
                Spacer(modifier = Modifier.height(4.dp))
                ProfileDefaultsCard(selected)
                EquipmentCompatibilityCard(selected)
                EquipmentCapacityCard(
                    capacityInput = state.selectedEquipmentCapacityInput,
                    capacityIsValid = state.selectedEquipmentCapacityG != null,
                    onCapacityChanged = onEquipmentCapacityChanged,
                )
                if (selected.requiresHarioSwitchWorkflow) {
                    HarioSwitchWorkflowPicker(
                        selectedWorkflow = state.harioSwitchWorkflow,
                        onSelected = onHarioSwitchWorkflowSelected,
                    )
                }
                if (state.requiresCezveSetup) {
                    CezveSetupCard(
                        setup = state.cezveSetup,
                        selectedHeatSource = state.cezveHeatSource,
                        onSugarSelected = onCezveSugarSelected,
                        onFoamRiseCyclesSelected = onCezveFoamRiseCyclesSelected,
                        onHeatSourceSelected = onCezveHeatSourceSelected,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { state.startSelection?.let(onStart) },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (state.isStarting) {
                            stringResource(R.string.label_brewer_profile_starting)
                        } else {
                            stringResource(R.string.action_start_brewing)
                        },
                    )
                }
                state.startSelection?.let { selection ->
                    onLearn?.let { learn ->
                        OutlinedButton(
                            onClick = { learn(selection) },
                            enabled = !state.isStarting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.action_learn_this_brewer))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupEmptyState() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.msg_brewer_profile_empty),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BrewerProfileOptionCard(
    option: P1BrewerProfileSetupOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = localizedProfileName(option.profileId, option.displayName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = localizedMethodFamilyName(option.profileId, option.methodFamilyName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileDefaultsCard(option: P1BrewerProfileSetupOption) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_starting_defaults),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            DefaultsRow(
                label = stringResource(R.string.label_ratio),
                value = stringResource(
                    R.string.format_ratio,
                    formatNumber(option.defaults.ratio.waterPerCoffee),
                ),
            )
            DefaultsRow(
                label = inputLabel(option),
                value = inputMeaning(option),
            )
            DefaultsRow(
                label = stringResource(R.string.label_temperature),
                value = temperatureLabel(option.defaults.temperature),
            )
            DefaultsRow(
                label = stringResource(R.string.label_brew_time),
                value = brewTimeLabel(option.defaults.brewTime),
            )
            DefaultsRow(
                label = stringResource(R.string.label_brewer_profile_expected_result),
                value = outputLabel(option.defaults.quantitySemantics.primaryOutput),
            )
            if (option.defaults.quantitySemantics.servingIsSeparateFromExtraction) {
                Text(
                    text = stringResource(R.string.msg_brewer_profile_serving_separate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefaultsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EquipmentCompatibilityCard(option: P1BrewerProfileSetupOption) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_equipment_check),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (val capacity = option.defaults.capacity) {
                CapacityRecommendation.RequiresEquipmentConfiguration -> Text(
                    text = stringResource(R.string.msg_brewer_profile_capacity_needs_confirmation),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is CapacityRecommendation.ConfirmedRange -> Text(
                    text = capacityDescription(capacity),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = when {
                    option.hasCompatibleFilters -> stringResource(
                        R.string.msg_brewer_profile_filter_compatible,
                    )
                    option.allowsIntentionallyUnfiltered -> stringResource(
                        R.string.msg_brewer_profile_filter_unfiltered,
                    )
                    else -> stringResource(R.string.msg_brewer_profile_filter_confirm)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EquipmentCapacityCard(
    capacityInput: String,
    capacityIsValid: Boolean,
    onCapacityChanged: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_capacity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_capacity_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = capacityInput,
                onValueChange = onCapacityChanged,
                label = { Text(stringResource(R.string.label_brewer_profile_capacity_grams)) },
                isError = capacityInput.isNotBlank() && !capacityIsValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!capacityIsValid) {
                Text(
                    text = stringResource(R.string.msg_brewer_profile_capacity_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CezveSetupCard(
    setup: CezveSessionSetup,
    selectedHeatSource: HeatSourceClass,
    onSugarSelected: (Boolean) -> Unit,
    onFoamRiseCyclesSelected: (Int) -> Unit,
    onHeatSourceSelected: (HeatSourceClass) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_cezve_choices),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_cezve_choices_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.label_brewer_profile_cezve_sugar),
                style = MaterialTheme.typography.labelLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, includeSugar ->
                    SegmentedButton(
                        selected = setup.includeSugar == includeSugar,
                        onClick = { onSugarSelected(includeSugar) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(cezveSugarLabel(includeSugar), maxLines = 2) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.label_brewer_profile_cezve_foam_rises),
                style = MaterialTheme.typography.labelLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2).forEachIndexed { index, cycles ->
                    SegmentedButton(
                        selected = setup.foamRiseCycles == cycles,
                        onClick = { onFoamRiseCyclesSelected(cycles) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(cezveFoamRiseLabel(cycles)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.heading_brewer_profile_cezve_heat_source),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_cezve_heat_source_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CEZVE_HEAT_SOURCES.forEach { heatSource ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeatSourceSelected(heatSource) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedHeatSource == heatSource,
                        onClick = null,
                    )
                    Text(
                        text = cezveHeatSourceLabel(heatSource),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (selectedHeatSource == HeatSourceClass.NONE) {
                Text(
                    text = stringResource(R.string.msg_brewer_profile_cezve_heat_source_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun cezveSugarLabel(includeSugar: Boolean): String = if (includeSugar) {
    stringResource(R.string.label_brewer_profile_cezve_with_sugar)
} else {
    stringResource(R.string.label_brewer_profile_cezve_without_sugar)
}

@Composable
private fun cezveFoamRiseLabel(cycles: Int): String = when (cycles) {
    1 -> stringResource(R.string.label_brewer_profile_cezve_one_foam_rise)
    2 -> stringResource(R.string.label_brewer_profile_cezve_two_foam_rises)
    else -> cycles.toString()
}

@Composable
private fun cezveHeatSourceLabel(heatSource: HeatSourceClass): String = when (heatSource) {
    HeatSourceClass.HOB -> stringResource(R.string.label_brewer_profile_cezve_heat_hob)
    HeatSourceClass.OPEN_FLAME -> stringResource(R.string.label_brewer_profile_cezve_heat_open_flame)
    HeatSourceClass.PORTABLE_HEATER -> stringResource(
        R.string.label_brewer_profile_cezve_heat_portable_heater,
    )
    HeatSourceClass.NONE, HeatSourceClass.ELECTRIC_MACHINE -> ""
}

private val CEZVE_HEAT_SOURCES = listOf(
    HeatSourceClass.HOB,
    HeatSourceClass.OPEN_FLAME,
    HeatSourceClass.PORTABLE_HEATER,
)

@Composable
private fun HarioSwitchWorkflowPicker(
    selectedWorkflow: HarioSwitchWorkflow,
    onSelected: (HarioSwitchWorkflow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_hario_switch_style),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_hario_switch_style_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HarioSwitchWorkflow.entries.forEachIndexed { index, workflow ->
                    SegmentedButton(
                        selected = workflow == selectedWorkflow,
                        onClick = { onSelected(workflow) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = HarioSwitchWorkflow.entries.size,
                        ),
                        label = { Text(workflowLabel(workflow), maxLines = 2) },
                    )
                }
            }
        }
    }
}

@Composable
private fun inputLabel(option: P1BrewerProfileSetupOption): String = when (
    option.defaults.quantitySemantics.inputRole
) {
    QuantityRole.BREW_WATER_INPUT -> stringResource(R.string.label_brewer_profile_input_water)
    QuantityRole.RESERVOIR_INPUT -> stringResource(R.string.label_brewer_profile_input_reservoir_water)
    else -> stringResource(R.string.label_brewer_profile_input_water)
}

@Composable
private fun inputMeaning(option: P1BrewerProfileSetupOption): String = when (
    option.defaults.quantitySemantics.inputRole
) {
    QuantityRole.BREW_WATER_INPUT -> stringResource(R.string.msg_brewer_profile_input_water)
    QuantityRole.RESERVOIR_INPUT -> stringResource(R.string.msg_brewer_profile_input_reservoir_water)
    else -> stringResource(R.string.msg_brewer_profile_input_water)
}

@Composable
private fun temperatureLabel(recommendation: TemperatureRecommendation): String = when (recommendation) {
    is TemperatureRecommendation.CelsiusRange -> {
        "${recommendation.minimumC}–${recommendation.maximumC} °C"
    }

    TemperatureRecommendation.Unavailable -> stringResource(
        R.string.msg_brewer_profile_temperature_machine_controlled,
    )
}

@Composable
private fun brewTimeLabel(recommendation: BrewTimeRecommendation): String = when (recommendation) {
    is BrewTimeRecommendation.SecondsRange -> formatDurationRange(
        minimumSeconds = recommendation.minimumSeconds,
        maximumSeconds = recommendation.maximumSeconds,
    )

    BrewTimeRecommendation.ObservedCompletion -> stringResource(
        R.string.msg_brewer_profile_brew_time_observed,
    )
    BrewTimeRecommendation.MachineControlled -> stringResource(
        R.string.msg_brewer_profile_brew_time_machine_controlled,
    )
}

@Composable
private fun outputLabel(output: PrimaryOutputQuantity): String = when (output) {
    PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD -> stringResource(
        R.string.label_brewer_profile_output_estimated_beverage_yield,
    )
    PrimaryOutputQuantity.COLLECTED_CONCENTRATE -> stringResource(
        R.string.label_brewer_profile_output_collected_concentrate,
    )
    PrimaryOutputQuantity.PREPARED_UNFILTERED_VOLUME -> stringResource(
        R.string.label_brewer_profile_output_prepared_unfiltered_volume,
    )
}

@Composable
private fun capacityDescription(capacity: CapacityRecommendation.ConfirmedRange): String {
    val range = capacity.range
    val minimum = range.minimumG?.let { formatMass(it) }
    val maximum = range.maximumG?.let { formatMass(it) }
    return when {
        minimum != null && maximum != null -> stringResource(
            R.string.format_brewer_profile_capacity_range,
            minimum,
            maximum,
        )
        maximum != null -> stringResource(R.string.format_brewer_profile_capacity_up_to, maximum)
        minimum != null -> stringResource(R.string.format_brewer_profile_capacity_from, minimum)
        else -> stringResource(R.string.msg_brewer_profile_capacity_configured)
    }
}

@Composable
private fun formatMass(value: Double): String = stringResource(
    R.string.format_brewer_profile_mass,
    formatNumber(value),
    stringResource(R.string.unit_grams),
)

@Composable
private fun workflowLabel(workflow: HarioSwitchWorkflow): String = when (workflow) {
    HarioSwitchWorkflow.STEEP_AND_RELEASE -> stringResource(
        R.string.label_brewer_profile_hario_switch_steep_release,
    )
    HarioSwitchWorkflow.MANUAL_GRAVITY -> stringResource(
        R.string.label_brewer_profile_hario_switch_manual_gravity,
    )
}

@Composable
private fun localizedProfileName(profileId: BrewerProfileId, fallback: String): String = when (profileId.value) {
    "clever_style" -> stringResource(R.string.label_brewer_profile_clever_style)
    "hario_switch" -> stringResource(R.string.label_brewer_profile_hario_switch)
    "valve_release_generic" -> stringResource(R.string.label_brewer_profile_valve_release_generic)
    "cezve_generic" -> stringResource(R.string.label_brewer_profile_cezve_generic)
    "automatic_batch_generic" -> stringResource(R.string.label_brewer_profile_automatic_batch_generic)
    "automatic_single_cup_generic" -> stringResource(
        R.string.label_brewer_profile_automatic_single_cup_generic,
    )
    "vietnamese_phin" -> stringResource(R.string.label_brewer_profile_vietnamese_phin)
    else -> fallback
}

@Composable
private fun localizedMethodFamilyName(profileId: BrewerProfileId, fallback: String): String = when (
    profileId.value
) {
    "clever_style",
    "hario_switch",
    "valve_release_generic",
    -> stringResource(R.string.label_brewer_profile_family_steep_and_release)
    "cezve_generic" -> stringResource(R.string.label_brewer_profile_family_heated_unfiltered)
    "automatic_batch_generic",
    "automatic_single_cup_generic",
    -> stringResource(R.string.label_brewer_profile_family_automatic_batch)
    "vietnamese_phin" -> stringResource(
        R.string.label_brewer_profile_family_restricted_flow_gravity_concentrate,
    )
    else -> fallback
}

private fun formatDurationRange(minimumSeconds: Int, maximumSeconds: Int): String =
    "${formatDuration(minimumSeconds)}–${formatDuration(maximumSeconds)}"

private fun formatDuration(seconds: Int): String = "%d:%02d".format(
    Locale.getDefault(),
    seconds / 60,
    seconds % 60,
)

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    "%.1f".format(Locale.getDefault(), value)
}
