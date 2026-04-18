package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R

/**
 * Debug-only dialog for configuring brew experiment metadata.
 * Sets phone placement and environment context for recording sessions.
 *
 * Shown when the user taps the Record button in AudioDebugOverlay.
 * Values are passed to RecordingMetadata via BrewAudioManager.setBrewContext().
 */
@Composable
fun BrewLabSetupDialog(
    onConfirm: (placement: String, environment: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val placements = listOf(
        "on_counter_30cm" to "Counter (~30cm)",
        "on_counter_15cm" to "Counter (~15cm)",
        "in_hand" to "In hand",
        "tripod_15cm" to "Tripod (~15cm)",
        "propped_against" to "Propped against brewer",
    )

    val environments = listOf(
        "quiet_kitchen" to "Quiet kitchen",
        "noisy_kitchen" to "Noisy kitchen (fan/music)",
        "office" to "Office",
        "cafe" to "Café",
        "outdoors" to "Outdoors",
    )

    var selectedPlacement by remember { mutableStateOf(placements[0].first) }
    var selectedEnvironment by remember { mutableStateOf(environments[0].first) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\uD83D\uDD2C Brew Lab Setup") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Placement section
                Text(
                    "Phone placement:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    for ((value, label) in placements) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPlacement == value,
                                    onClick = { selectedPlacement = value },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = selectedPlacement == value,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                // Environment section
                Text(
                    "Environment:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    for ((value, label) in environments) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedEnvironment == value,
                                    onClick = { selectedEnvironment = value },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = selectedEnvironment == value,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes_optional)) },
                    placeholder = { Text(stringResource(R.string.hint_notes_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPlacement, selectedEnvironment, notes) }) {
                Text(stringResource(R.string.action_start_recording))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_skip))
            }
        },
    )
}
