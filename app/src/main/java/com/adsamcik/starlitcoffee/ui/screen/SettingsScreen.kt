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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
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
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetColorPalette
import com.adsamcik.starlitcoffee.BuildConfig
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    onNavigateToBloomAnimationSettings: () -> Unit,
    onBack: () -> Unit,
){
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val cupPresets by cupPresetRepository.presets.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddPresetDialog by rememberSaveable { mutableStateOf(false) }
    // Save only the preset id across config changes; re-derive the full preset from the
    // current `cupPresets` list. Storing the data class directly would require Parcelable.
    var editingPresetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingPreset = editingPresetId?.let { id -> cupPresets.find { it.id == id } }
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
                            IconButton(
                                onClick = { showAddPresetDialog = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.action_add_preset),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            IconButton(
                                onClick = { scope.launch { cupPresetRepository.resetToDefaults() } },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = stringResource(R.string.action_reset_defaults),
                                    modifier = Modifier.size(24.dp),
                                )
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
                                .clickable { editingPresetId = preset.id }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val dotColor = preset.colorHex?.let {
                                try { Color(it.toColorInt()) } catch (_: IllegalArgumentException) { null }
                            } ?: MaterialTheme.colorScheme.secondaryContainer
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PresetIcon(
                                    iconName = preset.iconName,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                )
                            }
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
                            IconButton(
                                onClick = { editingPresetId = preset.id },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.format_edit_preset, preset.name),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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
                        text = stringResource(R.string.msg_pulsar_settings_applied),
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

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToBloomAnimationSettings),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.label_bloom_animation_settings),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.msg_bloom_animation_settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_open_bloom_animation_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                text = stringResource(R.string.label_quick_brew),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.msg_quick_brew_hint),
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

            // Rating reminder + bag prompt — both are opt-in courtesy nudges
            // grouped together so users find them next to "Quick brew".
            RatingReminderSettingCard(
                enabled = prefs.ratingReminderEnabled,
                onEnabledChange = { enabled ->
                    scope.launch {
                        userPreferencesRepository.updateRatingReminderEnabled(enabled)
                    }
                },
            )

            BagSelectionPromptSettingCard(
                enabled = prefs.bagSelectionPromptEnabled,
                onEnabledChange = { enabled ->
                    scope.launch {
                        userPreferencesRepository.updateBagSelectionPromptEnabled(enabled)
                    }
                },
            )

            // Show brewing instructions — sits alongside the other in-brew
            // courtesy toggles. Hides the BrewGuidanceCard on BrewTimer and
            // the Prep section on GrindPrep for users who know their method
            // by heart and don't want the screen real estate / cognitive load.
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
                                text = stringResource(R.string.label_show_brewing_instructions),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.msg_show_brewing_instructions_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.showBrewingInstructions,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateShowBrewingInstructions(enabled)
                                }
                            },
                        )
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
                                text = stringResource(R.string.label_dim_mode),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.msg_dim_mode_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.dimModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateDimModeEnabled(enabled)
                                }
                            },
                        )
                    }

                    // Sub-toggles: child preferences that only take effect when
                    // the parent dim toggle is on. Both render disabled and at
                    // reduced alpha when the parent is off, so they're
                    // discoverable but clearly inactive.
                    val subToggleAlpha = if (prefs.dimModeEnabled) 1f else 0.5f
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_dim_mode_true_black),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = subToggleAlpha),
                            )
                            Text(
                                text = stringResource(R.string.msg_dim_mode_true_black_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = subToggleAlpha),
                            )
                        }
                        Switch(
                            checked = prefs.dimModeTrueBlack,
                            enabled = prefs.dimModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateDimModeTrueBlack(enabled)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_dim_mode_reduce_brightness),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = subToggleAlpha),
                            )
                            Text(
                                text = stringResource(R.string.msg_dim_mode_reduce_brightness_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = subToggleAlpha),
                            )
                        }
                        Switch(
                            checked = prefs.dimModeReduceBrightness,
                            enabled = prefs.dimModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateDimModeReduceBrightness(enabled)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_dim_mode_fullscreen),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = subToggleAlpha),
                            )
                            Text(
                                text = stringResource(R.string.msg_dim_mode_fullscreen_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = subToggleAlpha),
                            )
                        }
                        Switch(
                            checked = prefs.dimModeFullscreen,
                            enabled = prefs.dimModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateDimModeFullscreen(enabled)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_dim_mode_force_dark_in_light),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = subToggleAlpha),
                            )
                            Text(
                                text = stringResource(R.string.msg_dim_mode_force_dark_in_light_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = subToggleAlpha),
                            )
                        }
                        Switch(
                            checked = prefs.dimModeForceDarkInLight,
                            enabled = prefs.dimModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    userPreferencesRepository.updateDimModeForceDarkInLight(enabled)
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
            onDismiss = { editingPresetId = null },
            onSave = { updated ->
                scope.launch {
                    cupPresetRepository.updatePreset(updated)
                    editingPresetId = null
                }
            },
            onDelete = {
                scope.launch {
                    cupPresetRepository.deletePreset(preset)
                    editingPresetId = null
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
    var name by rememberSaveable { mutableStateOf("") }
    var waterMl by rememberSaveable { mutableStateOf("") }
    var selectedIcon by rememberSaveable { mutableStateOf("mug") }
    var selectedColor by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_cup_preset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waterMl,
                    onValueChange = { waterMl = it },
                    label = { Text(stringResource(R.string.label_volume_ml)) },
                    supportingText = { Text(stringResource(R.string.msg_dose_from_ratio)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelMedium)
                IconPickerRow(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
    var name by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
    var waterMl by rememberSaveable(preset.id) { mutableStateOf(preset.waterMl.toInt().toString()) }
    var selectedIcon by rememberSaveable(preset.id) { mutableStateOf(preset.iconName) }
    var selectedColor by rememberSaveable(preset.id) { mutableStateOf(preset.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_cup_preset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waterMl,
                    onValueChange = { waterMl = it },
                    label = { Text(stringResource(R.string.label_volume_ml)) },
                    supportingText = { Text(stringResource(R.string.msg_dose_from_ratio)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelMedium)
                IconPickerRow(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.labelMedium)
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
            ) { Text(stringResource(R.string.action_save_simple)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerRow(
    selectedIcon: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
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
                PresetIcon(
                    iconName = iconName,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerRow(
    selectedColor: String?,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        presetColorPalette.forEach { (hex, label) ->
            val isSelected = hex == selectedColor
            val circleColor = if (hex != null) {
                try { Color(hex.toColorInt()) } catch (_: IllegalArgumentException) { MaterialTheme.colorScheme.secondaryContainer }
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
                text = stringResource(R.string.label_scan_debug),
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
                FilledTonalButton(onClick = { showHistory = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_view_history))
                }
                FilledTonalButton(onClick = { ScanBugReporter.shareReport(context) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_share_report))
                }
                FilledTonalButton(
                    onClick = {
                        ScanSessionRingBuffer.clear(context)
                        sessions = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_clear))
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

/**
 * Rating reminder toggle card. On Android 13+ enabling the toggle prompts the
 * runtime POST_NOTIFICATIONS permission inline; if the user denies it the
 * toggle reverts to off and a hint surfaces a deep-link into system settings.
 * Below SDK 33 no runtime permission exists, so the toggle is straightforward.
 */
@Composable
private fun RatingReminderSettingCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Use a tick state so we re-read the permission whenever the user comes
    // back from the system settings screen (foreground state isn't observed
    // here, so we expose a manual "refresh" via the launcher callback).
    var permissionTick by remember { mutableStateOf(0) }
    val hasPostNotifications by remember(permissionTick) {
        derivedStateOf {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                true
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionTick++
        if (granted) {
            onEnabledChange(true)
        } else {
            // Roll back; user can re-enable after granting in system settings.
            onEnabledChange(false)
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
                        text = stringResource(R.string.label_rating_reminder),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.msg_rating_reminder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { wantEnabled ->
                        if (wantEnabled && !hasPostNotifications) {
                            // Defer the persisted toggle to the permission result.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onEnabledChange(true)
                            }
                        } else {
                            onEnabledChange(wantEnabled)
                        }
                    },
                )
            }
            if (enabled && !hasPostNotifications) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.msg_rating_reminder_perm_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(
                                AndroidSettings.EXTRA_APP_PACKAGE,
                                context.packageName,
                            )
                            // Fallback for older runtime quirks where the action
                            // intent fails to resolve.
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                permissionTick++
                            }
                        permissionTick++
                    },
                ) {
                    Text(stringResource(R.string.action_open_app_settings))
                }
            }
        }
    }
}

@Composable
private fun BagSelectionPromptSettingCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
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
                        text = stringResource(R.string.label_bag_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.msg_bag_prompt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}
