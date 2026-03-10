package com.adsamcik.starlitcoffee.ui.screen

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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import kotlinx.coroutines.launch

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit,
){
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        ScreenTopBar(
            title = "Settings",
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Enabled methods
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Brew methods",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Selected methods appear first. Tap to toggle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BrewMethod.entries.forEach { method ->
                            val enabled = prefs.enabledMethods.contains(method)
                            FilterChip(
                                selected = enabled,
                                onClick = {
                                    val newSet = if (enabled) {
                                        // Don't allow deselecting last method
                                        if (prefs.enabledMethods.size > 1) {
                                            prefs.enabledMethods - method
                                        } else {
                                            return@FilterChip
                                        }
                                    } else {
                                        prefs.enabledMethods + method
                                    }
                                    scope.launch {
                                        userPreferencesRepository.updateEnabledMethods(newSet)
                                        if (!newSet.contains(prefs.defaultMethod)) {
                                            userPreferencesRepository.updateDefaultMethod(newSet.first())
                                        }
                                    }
                                },
                                label = { Text(method.displayName) },
                                leadingIcon = if (enabled) checkIcon else null,
                            )
                        }
                    }
                }
            }

            // Default method
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Default method",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        prefs.enabledMethods.forEach { method ->
                            val isDefault = prefs.defaultMethod == method
                            FilterChip(
                                selected = isDefault,
                                onClick = {
                                    scope.launch {
                                        userPreferencesRepository.updateDefaultMethod(method)
                                    }
                                },
                                label = { Text(method.displayName) },
                                leadingIcon = if (isDefault) checkIcon else null,
                            )
                        }
                    }
                }
            }

            // Filter type (always visible in settings)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pulsar filter type",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Applied when brewing with Pulsar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = prefs.defaultFilterType == null,
                            onClick = {
                                scope.launch {
                                    userPreferencesRepository.updateDefaultFilterType(null)
                                }
                            },
                            label = { Text("None") },
                            leadingIcon = if (prefs.defaultFilterType == null) checkIcon else null,
                        )
                        FilterType.entries.forEach { filter ->
                            val isFilterSelected = prefs.defaultFilterType == filter
                            FilterChip(
                                selected = isFilterSelected,
                                onClick = {
                                    scope.launch {
                                        userPreferencesRepository.updateDefaultFilterType(filter)
                                    }
                                },
                                label = { Text(filter.displayName) },
                                leadingIcon = if (isFilterSelected) checkIcon else null,
                            )
                        }
                    }
                }
            }

            // Grinder
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your grinder",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = prefs.selectedGrinderId == null,
                            onClick = {
                                scope.launch {
                                    userPreferencesRepository.updateSelectedGrinder(null)
                                }
                            },
                            label = { Text("No grinder") },
                            leadingIcon = if (prefs.selectedGrinderId == null) checkIcon else null,
                        )
                        GrinderDataSource.getInstance(context).grinders.forEach { grinder ->
                            val isGrinderSelected = prefs.selectedGrinderId == grinder.id
                            FilterChip(
                                selected = isGrinderSelected,
                                onClick = {
                                    scope.launch {
                                        userPreferencesRepository.updateSelectedGrinder(grinder.id)
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
                                leadingIcon = if (isGrinderSelected) checkIcon else null,
                            )
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = "QR link explorer",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "When a bag photo includes a public HTTPS QR website, " +
                                    "fetch it safely and offer reviewable coffee hints. " +
                                    "Local/private addresses stay blocked, and nothing is " +
                                    "applied silently.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.qrLinkExplorerEnabled,
                            modifier = Modifier.testTag("qr_link_explorer_toggle"),
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateQrLinkExplorerEnabled(enabled)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
