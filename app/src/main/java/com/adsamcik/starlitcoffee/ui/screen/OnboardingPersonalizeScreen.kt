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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

private val NullableFilterTypeSaver: Saver<MutableState<FilterType?>, String> = Saver(
    save = { it.value?.name ?: "" },
    restore = { name ->
        mutableStateOf(
            if (name.isEmpty()) null else runCatching { FilterType.valueOf(name) }.getOrNull(),
        )
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingPersonalizeScreen(
    selectedMethods: Set<BrewMethod>,
    initialFilter: FilterType? = null,
    initialGrinder: String? = null,
    onBack: () -> Unit,
    onSelectionChanged: (FilterType?, String?) -> Unit = { _, _ -> },
    onFinish: (
        filterType: FilterType?,
        grinderId: String?,
    ) -> Unit,
) {
    val filterType = rememberSaveable(saver = NullableFilterTypeSaver) {
        mutableStateOf(initialFilter)
    }
    val selectedGrinderId = rememberSaveable { mutableStateOf(initialGrinder) }
    val context = LocalContext.current

    val showFilterSection = selectedMethods.contains(BrewMethod.PULSAR)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_personalize_title),
            onBack = {
                onSelectionChanged(filterType.value, selectedGrinderId.value)
                onBack()
            },
        )

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
                            text = stringResource(R.string.label_pulsar_filter_type),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.msg_filter_hint),
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
                        text = stringResource(R.string.label_your_grinder),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.msg_grinder_hint),
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
                            label = { Text(stringResource(R.string.label_no_grinder)) },
                            leadingIcon = if (selectedGrinderId.value == null) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                        GrinderDataSource.getInstance(context).grinders.forEach { grinder ->
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
            }, modifier = Modifier.testTag("onboarding_skip_button")) {
                Text(stringResource(R.string.action_skip))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onFinish(filterType.value, selectedGrinderId.value)
                },
                modifier = Modifier.testTag("onboarding_finish_button"),
            ) {
                Text(stringResource(R.string.action_finish))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPersonalizeScreenPreview() {
    StarlitCoffeeTheme {
        OnboardingPersonalizeScreen(
            selectedMethods = setOf(BrewMethod.V60, BrewMethod.PULSAR),
            onBack = {},
            onFinish = { _, _ -> },
        )
    }
}
