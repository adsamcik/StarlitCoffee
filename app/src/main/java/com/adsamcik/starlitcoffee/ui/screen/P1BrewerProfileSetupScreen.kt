package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewTimeRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.CapacityRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.PrimaryOutputQuantity
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.TemperatureRecommendation
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupOption
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupUiState
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileStartSelection
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
    onStart: (P1BrewerProfileStartSelection) -> Unit,
    onBack: () -> Unit,
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
                    IconButton(onClick = onBack) {
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
                text = "Choose your brewer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Start with the brewer in front of you. Its defaults stay visible before a session begins.",
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
                if (selected.requiresHarioSwitchWorkflow) {
                    HarioSwitchWorkflowPicker(
                        selectedWorkflow = state.harioSwitchWorkflow,
                        onSelected = onHarioSwitchWorkflowSelected,
                    )
                }
            }

            state.startSelection?.let { selection ->
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onStart(selection) },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (state.isStarting) "Starting…" else {
                            stringResource(R.string.action_start_brewing)
                        },
                    )
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
            text = "No supported brewer profiles are available yet.",
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
                text = option.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = option.methodFamilyName,
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
                text = "Starting defaults",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            DefaultsRow(
                label = stringResource(R.string.label_ratio),
                value = "1:${formatNumber(option.defaults.ratio.waterPerCoffee)}",
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
                label = "Expected result",
                value = outputLabel(option.defaults.quantitySemantics.primaryOutput),
            )
            if (option.defaults.quantitySemantics.servingIsSeparateFromExtraction) {
                Text(
                    text = "Serving is a separate choice after extraction.",
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
                text = "Equipment check",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (val capacity = option.defaults.capacity) {
                CapacityRecommendation.RequiresEquipmentConfiguration -> Text(
                    text = "Capacity is not preset. Check your brewer’s marked capacity before using these amounts.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                is CapacityRecommendation.ConfirmedRange -> Text(
                    text = capacityDescription(capacity),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = when {
                    option.hasCompatibleFilters -> "Use a filter compatible with this brewer."
                    option.allowsIntentionallyUnfiltered -> "This profile is intentionally unfiltered."
                    else -> "Confirm the required filter before starting."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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
                text = "Hario Switch brew style",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Choose the valve behaviour for this brew. Steep and release is the default.",
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

private fun inputLabel(option: P1BrewerProfileSetupOption): String = when (
    option.defaults.quantitySemantics.inputRole
) {
    QuantityRole.BREW_WATER_INPUT -> "Water input"
    QuantityRole.RESERVOIR_INPUT -> "Reservoir water"
    else -> "Water input"
}

private fun inputMeaning(option: P1BrewerProfileSetupOption): String = when (
    option.defaults.quantitySemantics.inputRole
) {
    QuantityRole.BREW_WATER_INPUT -> "Water added to the brewer"
    QuantityRole.RESERVOIR_INPUT -> "Water added to the machine reservoir"
    else -> "Water added to the brewer"
}

private fun temperatureLabel(recommendation: TemperatureRecommendation): String = when (recommendation) {
    is TemperatureRecommendation.CelsiusRange -> {
        "${recommendation.minimumC}–${recommendation.maximumC} °C"
    }

    TemperatureRecommendation.Unavailable -> "Managed by the heat source or machine"
}

private fun brewTimeLabel(recommendation: BrewTimeRecommendation): String = when (recommendation) {
    is BrewTimeRecommendation.SecondsRange -> formatDurationRange(
        minimumSeconds = recommendation.minimumSeconds,
        maximumSeconds = recommendation.maximumSeconds,
    )

    BrewTimeRecommendation.ObservedCompletion -> "Finish by observation"
    BrewTimeRecommendation.MachineControlled -> "Machine-controlled cycle"
}

private fun outputLabel(output: PrimaryOutputQuantity): String = when (output) {
    PrimaryOutputQuantity.ESTIMATED_BEVERAGE_YIELD -> "Estimated beverage yield"
    PrimaryOutputQuantity.COLLECTED_CONCENTRATE -> "Collected concentrate"
    PrimaryOutputQuantity.PREPARED_UNFILTERED_VOLUME -> "Prepared unfiltered coffee"
}

private fun capacityDescription(capacity: CapacityRecommendation.ConfirmedRange): String {
    val range = capacity.range
    val minimum = range.minimumG?.let { "${formatNumber(it)} g" }
    val maximum = range.maximumG?.let { "${formatNumber(it)} g" }
    return when {
        minimum != null && maximum != null -> "Capacity: $minimum to $maximum"
        maximum != null -> "Capacity: up to $maximum"
        minimum != null -> "Capacity: from $minimum"
        else -> "Capacity is configured for this brewer."
    }
}

private fun workflowLabel(workflow: HarioSwitchWorkflow): String = when (workflow) {
    HarioSwitchWorkflow.STEEP_AND_RELEASE -> "Steep & release"
    HarioSwitchWorkflow.MANUAL_GRAVITY -> "Manual gravity"
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
