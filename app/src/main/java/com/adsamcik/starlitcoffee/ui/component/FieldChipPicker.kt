package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R

/**
 * Reusable chip picker for sealed-interface fields (Known enum + Other).
 * Shows a FlowRow of selectable chips for [knownValues] plus an "Other" chip.
 * When "Other" is selected, a text field appears for free-form input.
 * If the text field is active, matching [recentValues] are shown as suggestion chips.
 *
 * @param label Field label shown above the chips
 * @param knownValues List of known enum values to show as chips
 * @param selectedValue Current value as string (display name or custom text)
 * @param onValueChange Callback with the new display name string
 * @param displayName Extracts the display name from a known value
 * @param isKnownValue Returns true if [selectedValue] matches this known value
 * @param recentValues Recently-used values from bag history (may include Known and Other)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FieldChipPicker(
    label: String,
    knownValues: List<T>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
    displayName: (T) -> String,
    modifier: Modifier = Modifier,
    isKnownValue: (T, String) -> Boolean = { item, value ->
        displayName(item).equals(value, ignoreCase = true)
    },
    recentValues: List<String> = emptyList(),
    multiSelect: Boolean = false,
    onInteraction: () -> Unit = {},
) {
    val knownDisplayNames = knownValues.map { displayName(it).lowercase() }.toSet()
    // Recent values that aren't in the Known enum — show as extra chips
    val recentOtherValues = recentValues.filter { it.lowercase() !in knownDisplayNames }.distinct()
    // Known values sorted: recently-used first, then the rest
    val recentLower = recentValues.map { it.lowercase() }.toSet()
    val sortedKnownValues = knownValues.sortedByDescending { displayName(it).lowercase() in recentLower }

    // Multi-select helpers
    fun isItemInSelection(name: String): Boolean {
        if (!multiSelect) return false
        return selectedValue.split(",").any { it.trim().equals(name, ignoreCase = true) }
    }
    fun toggleInSelection(name: String) {
        val current = selectedValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (current.any { it.equals(name, ignoreCase = true) }) {
            current.removeAll { it.equals(name, ignoreCase = true) }
        } else {
            current.add(name)
        }
        onValueChange(current.joinToString(", "))
    }

    val isOtherSelected = !multiSelect && selectedValue.isNotBlank() &&
        knownValues.none { isKnownValue(it, selectedValue) } &&
        recentOtherValues.none { it.equals(selectedValue, ignoreCase = true) }
    val isRecentOtherSelected = !multiSelect && selectedValue.isNotBlank() &&
        recentOtherValues.any { it.equals(selectedValue, ignoreCase = true) }
    var otherText by androidx.compose.runtime.remember(selectedValue) {
        mutableStateOf(if (isOtherSelected) selectedValue else "")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Known enum chips (recently used ones first)
            for (item in sortedKnownValues) {
                val name = displayName(item)
                val isSelected = if (multiSelect) isItemInSelection(name) else isKnownValue(item, selectedValue)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onInteraction()
                        if (multiSelect) {
                            toggleInSelection(name)
                        } else {
                            otherText = ""
                            onValueChange(if (isSelected) "" else name)
                        }
                    },
                    label = { Text(name) },
                )
            }
            // Recent non-Known values as extra chips
            for (recent in recentOtherValues) {
                val isSelected = if (multiSelect) isItemInSelection(recent) else {
                    isRecentOtherSelected && recent.equals(selectedValue, ignoreCase = true)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onInteraction()
                        if (multiSelect) {
                            toggleInSelection(recent)
                        } else {
                            otherText = ""
                            onValueChange(if (isSelected) "" else recent)
                        }
                    },
                    label = { Text(recent) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    ),
                )
            }
            // "Other" chip
            FilterChip(
                selected = isOtherSelected,
                onClick = {
                    onInteraction()
                    if (!isOtherSelected) {
                        onValueChange(otherText)
                    }
                },
                label = { Text(stringResource(R.string.label_other)) },
            )
        }

        AnimatedVisibility(visible = isOtherSelected) {
            OutlinedTextField(
                value = otherText,
                onValueChange = {
                    onInteraction()
                    otherText = it
                    onValueChange(it)
                },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
