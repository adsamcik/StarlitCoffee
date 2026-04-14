package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetColorPalette
import com.adsamcik.starlitcoffee.ui.util.presetIcon
import com.adsamcik.starlitcoffee.BuildConfig
import android.content.Intent
import com.adsamcik.starlitcoffee.scan.observability.ScanBugReporter
import com.adsamcik.starlitcoffee.scan.observability.ScanSessionRingBuffer
import com.adsamcik.starlitcoffee.ui.component.MindlayerSettingsCard
import com.adsamcik.starlitcoffee.ui.component.ScanHistoryDialog
import com.adsamcik.starlitcoffee.ui.component.formatSessionForShare
import kotlinx.coroutines.launch

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    cupPresetRepository: CupPresetRepository,
    onBack: () -> Unit,
){
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val cupPresets by cupPresetRepository.presets.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<CupPreset?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_settings_title),
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Cup Presets
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.label_cup_presets),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Row {
                            IconButton(onClick = { showAddPresetDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_preset))
                            }
                            IconButton(onClick = {
                                scope.launch { cupPresetRepository.resetToDefaults() }
                            }) {
                                Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.action_reset_defaults))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.msg_presets_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    cupPresets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingPreset = preset }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val dotColor = preset.colorHex?.let {
                                try { Color(android.graphics.Color.parseColor(it)) } catch (_: IllegalArgumentException) { null }
                            } ?: MaterialTheme.colorScheme.secondaryContainer
                            Icon(
                                imageVector = presetIcon(preset.iconName),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = dotColor,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${preset.waterMl.toInt()} ml",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.format_edit_preset, preset.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Enabled methods
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.label_brew_methods),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.msg_methods_hint),
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
                        text = stringResource(R.string.label_default_method),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
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
                        text = stringResource(R.string.label_pulsar_filter_type),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
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
                            label = { Text(stringResource(R.string.label_none)) },
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
                        text = stringResource(R.string.label_your_grinder),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
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
                            label = { Text(stringResource(R.string.label_no_grinder)) },
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
                    Text(
                        text = "QR link explorer",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "QR links in bag photos require approval before exploration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Skip method selection
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
                                text = "Quick brew",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "Skip method selection and go straight to timer using your default method",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.skipMethodSelection,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateSkipMethodSelection(enabled)
                                }
                            },
                        )
                    }
                }
            }

            // Debug-only cards
            if (BuildConfig.DEBUG) {
                MindlayerSettingsCard()
                ScanDebugCard()
            }
        }
    }

    if (showAddPresetDialog) {
        AddPresetDialog(
            onDismiss = { showAddPresetDialog = false },
            onAdd = { name, waterMl, iconName, colorHex ->
                scope.launch {
                    cupPresetRepository.addPreset(
                        CupPreset(
                            name = name,
                            iconName = iconName,
                            doseG = 0f,
                            waterMl = waterMl,
                            sortOrder = cupPresets.size,
                            colorHex = colorHex,
                        )
                    )
                    showAddPresetDialog = false
                }
            },
        )
    }

    editingPreset?.let { preset ->
        EditPresetDialog(
            preset = preset,
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                scope.launch {
                    cupPresetRepository.updatePreset(updated)
                    editingPreset = null
                }
            },
            onDelete = {
                scope.launch {
                    cupPresetRepository.deletePreset(preset)
                    editingPreset = null
                }
            },
        )
    }
}

@Composable
private fun AddPresetDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, waterMl: Float, iconName: String, colorHex: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var waterMl by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("custom") }
    var selectedColor by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add cup preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waterMl,
                    onValueChange = { waterMl = it },
                    label = { Text("Volume (ml)") },
                    supportingText = { Text("Coffee dose is calculated from your ratio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                IconPickerRow(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
                Text("Color", style = MaterialTheme.typography.labelMedium)
                ColorPickerRow(selectedColor = selectedColor, onSelect = { selectedColor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val water = waterMl.toFloatOrNull() ?: return@TextButton
                    if (name.isNotBlank() && water > 0f) {
                        onAdd(name.trim(), water, selectedIcon, selectedColor)
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditPresetDialog(
    preset: CupPreset,
    onDismiss: () -> Unit,
    onSave: (CupPreset) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(preset.name) }
    var waterMl by remember { mutableStateOf(preset.waterMl.toInt().toString()) }
    var selectedIcon by remember { mutableStateOf(preset.iconName) }
    var selectedColor by remember { mutableStateOf(preset.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit cup preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waterMl,
                    onValueChange = { waterMl = it },
                    label = { Text("Volume (ml)") },
                    supportingText = { Text("Coffee dose is calculated from your ratio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                IconPickerRow(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
                Text("Color", style = MaterialTheme.typography.labelMedium)
                ColorPickerRow(selectedColor = selectedColor, onSelect = { selectedColor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val water = waterMl.toFloatOrNull() ?: return@TextButton
                    if (name.isNotBlank() && water > 0f) {
                        onSave(
                            preset.copy(
                                name = name.trim(),
                                waterMl = water,
                                iconName = selectedIcon,
                                colorHex = selectedColor,
                            ),
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun IconPickerRow(
    selectedIcon: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        availablePresetIcons.forEach { (iconName, label) ->
            val isSelected = iconName == selectedIcon
            IconButton(
                onClick = { onSelect(iconName) },
                colors = if (isSelected) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = presetIcon(iconName),
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    selectedColor: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        presetColorPalette.forEach { (hex, label) ->
            val isSelected = hex == selectedColor
            val circleColor = if (hex != null) {
                try { Color(android.graphics.Color.parseColor(hex)) } catch (_: IllegalArgumentException) { MaterialTheme.colorScheme.secondaryContainer }
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(circleColor)
                    .border(2.dp, borderColor, CircleShape)
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    val checkColor = if (circleColor.luminance() > 0.5f) Color.Black else Color.White
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected: $label",
                        tint = checkColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanDebugCard() {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf(ScanSessionRingBuffer.getAll(context)) }
    var showHistory by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scan Debug",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${sessions.size} sessions stored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showHistory = true }) {
                    Text("View History")
                }
                FilledTonalButton(onClick = { ScanBugReporter.shareReport(context) }) {
                    Text("Share Report")
                }
                FilledTonalButton(onClick = {
                    ScanSessionRingBuffer.clear(context)
                    sessions = emptyList()
                }) {
                    Text("Clear")
                }
            }
        }
    }

    if (showHistory) {
        ScanHistoryDialog(
            sessions = sessions,
            onDismiss = { showHistory = false },
            onShareSession = { session ->
                val text = formatSessionForShare(session)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Scan Session: ${session.sessionId}")
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(intent, "Share Scan Session")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}
