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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ai.ModelManager
import kotlinx.coroutines.launch

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    userPreferencesRepository: UserPreferencesRepository,
) {
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        ScreenTopBar(
            title = "Settings",
            onBack = { navController.popBackStack() },
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
                        DefaultGrinders.grinders.forEach { grinder ->
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

            // --- AI Features ---
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "AI Features",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))

            val context = LocalContext.current
            val modelState by ModelManager.state.collectAsStateWithLifecycle()
            val downloadProgress by ModelManager.downloadProgress.collectAsStateWithLifecycle()
            val aiEnabled = ModelManager.isAiEnabled(context)

            LaunchedEffect(Unit) { ModelManager.refreshState(context) }

            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI-powered extraction", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Uses on-device Gemma 3n to extract bag info from photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { enabled ->
                                ModelManager.setAiEnabled(context, enabled)
                                if (enabled) ModelManager.refreshState(context)
                            },
                        )
                    }

                    if (aiEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        when (modelState) {
                            ModelManager.ModelState.NOT_DOWNLOADED -> {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        val job = scope.launch { ModelManager.downloadModel(context) }
                                        ModelManager.setDownloadJob(job)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Download AI Model (~1.5 GB)")
                                }
                            }
                            ModelManager.ModelState.DOWNLOADING -> {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${(downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = { ModelManager.cancelDownload() },
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                            ModelManager.ModelState.DOWNLOADED, ModelManager.ModelState.LOADING, ModelManager.ModelState.READY -> {
                                val sizeBytes = ModelManager.getModelSizeBytes(context)
                                val sizeMb = sizeBytes / (1024 * 1024)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AI Model Ready (${sizeMb}MB)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    androidx.compose.material3.TextButton(
                                        onClick = { scope.launch { ModelManager.deleteModel(context) } },
                                    ) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            ModelManager.ModelState.ERROR -> {
                                val errorMsg by ModelManager.errorMessage.collectAsStateWithLifecycle()
                                Text(
                                    errorMsg ?: "Download failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        val job = scope.launch { ModelManager.downloadModel(context) }
                                        ModelManager.setDownloadJob(job)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Retry Download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
