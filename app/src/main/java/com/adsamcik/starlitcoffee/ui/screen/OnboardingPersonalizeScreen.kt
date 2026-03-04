package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingPersonalizeScreen(
    selectedMethods: Set<BrewMethod>,
    onBack: () -> Unit,
    onFinish: (
        filterType: FilterType?,
        grinderId: String?,
    ) -> Unit,
) {
    val filterType = remember { mutableStateOf<FilterType?>(null) }
    val selectedGrinderId = remember { mutableStateOf<String?>(null) }

    val showFilterSection = selectedMethods.contains(BrewMethod.PULSAR)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Personalise your brew",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Filter section (Pulsar only)
            if (showFilterSection) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pulsar filter type",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Affects cup profile and grind recommendations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterType.entries.forEach { filter ->
                                FilterChip(
                                    selected = filterType.value == filter,
                                    onClick = {
                                        filterType.value = if (filterType.value == filter) {
                                            null
                                        } else {
                                            filter
                                        }
                                    },
                                    label = { Text(filter.displayName) },
                                    leadingIcon = if (filterType.value == filter) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        if (filterType.value != null) {
                            Text(
                                text = filterType.value!!.cupProfile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            // Grinder section
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your grinder",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Optional — enables specific grind setting recommendations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = selectedGrinderId.value == null,
                            onClick = { selectedGrinderId.value = null },
                            label = { Text("No grinder") },
                            leadingIcon = if (selectedGrinderId.value == null) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                        DefaultGrinders.grinders.forEach { grinder ->
                            val isGrinderSelected = selectedGrinderId.value == grinder.id
                            FilterChip(
                                selected = isGrinderSelected,
                                onClick = {
                                    selectedGrinderId.value = if (isGrinderSelected) {
                                        null
                                    } else {
                                        grinder.id
                                    }
                                },
                                label = {
                                    val label = if (grinder.brand == grinder.model) {
                                        grinder.model
                                    } else {
                                        "${grinder.brand} ${grinder.model}"
                                    }
                                    Text(label)
                                },
                                leadingIcon = if (isGrinderSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                onFinish(null, null)
            }) {
                Text("Skip")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onFinish(filterType.value, selectedGrinderId.value)
                },
            ) {
                Text("Finish")
            }
        }
    }
}
