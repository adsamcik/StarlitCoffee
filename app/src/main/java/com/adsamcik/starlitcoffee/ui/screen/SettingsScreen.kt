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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.SettingsGroup
import com.adsamcik.starlitcoffee.ui.component.SettingsNavigationRow
import com.adsamcik.starlitcoffee.ui.component.SettingsRowDivider
import com.adsamcik.starlitcoffee.ui.component.SettingsSectionHeader
import com.adsamcik.starlitcoffee.ui.component.SettingsSelectorBlock
import com.adsamcik.starlitcoffee.ui.component.SettingsSwitchRow
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
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
import com.adsamcik.starlitcoffee.scan.observability.ScanLlmDiagnosticsStore
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
    onNavigateToDisplaySettings: () -> Unit,
    onNavigateToCupPresetEditor: (presetId: Long?) -> Unit,
    onBack: () -> Unit,
){
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val cupPresets by cupPresetRepository.presets.collectAsStateWithLifecycle(initialValue = emptyList())
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- Brewing ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_brewing))

            // Cup presets — keeps its add/reset actions and tappable list.
            SettingsGroup {
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
                                onClick = { onNavigateToCupPresetEditor(null) },
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
                                .clickable { onNavigateToCupPresetEditor(preset.id) }
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
                                onClick = { onNavigateToCupPresetEditor(preset.id) },
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

            // Brew configuration — methods, default, filter, grinder grouped together.
            SettingsGroup {
                SettingsSelectorBlock(
                    title = stringResource(R.string.label_brew_methods),
                    summary = stringResource(R.string.msg_methods_hint),
                ) {
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
                SettingsRowDivider()
                SettingsSelectorBlock(title = stringResource(R.string.label_default_method)) {
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
                SettingsRowDivider()
                SettingsSelectorBlock(
                    title = stringResource(R.string.label_pulsar_filter_type),
                    summary = stringResource(R.string.msg_pulsar_settings_applied),
                ) {
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
                SettingsRowDivider()
                SettingsSelectorBlock(title = stringResource(R.string.label_your_grinder)) {
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

            // Brew flow toggles.
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.label_quick_brew),
                    summary = stringResource(R.string.msg_quick_brew_hint),
                    checked = prefs.skipMethodSelection,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateSkipMethodSelection(enabled)
                        }
                    },
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_show_brewing_instructions),
                    summary = stringResource(R.string.msg_show_brewing_instructions_hint),
                    checked = prefs.showBrewingInstructions,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateShowBrewingInstructions(enabled)
                        }
                    },
                )
            }

            // ---------- Appearance ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_appearance))
            SettingsGroup {
                SettingsNavigationRow(
                    title = stringResource(R.string.label_display_dim_settings),
                    summary = stringResource(R.string.msg_display_dim_settings_subtitle),
                    onClick = onNavigateToDisplaySettings,
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    title = stringResource(R.string.label_bloom_animation_settings),
                    summary = stringResource(R.string.msg_bloom_animation_settings_subtitle),
                    onClick = onNavigateToBloomAnimationSettings,
                )
            }

            // ---------- Notifications ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_notifications))
            SettingsGroup {
                RatingReminderRow(
                    enabled = prefs.ratingReminderEnabled,
                    onEnabledChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateRatingReminderEnabled(enabled)
                        }
                    },
                )
            }

            // ---------- Developer (debug only) ----------
            if (BuildConfig.DEBUG) {
                SettingsSectionHeader(stringResource(R.string.label_settings_section_developer))
                MindlayerSettingsCard()
                ScanDebugCard()
            }
        }
    }
}

@Composable
private fun ScanDebugCard() {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf(ScanSessionRingBuffer.getAll(context)) }
    var llmPasses by remember { mutableStateOf(ScanLlmDiagnosticsStore.getAll(context)) }
    var showHistory by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_scan_debug),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${sessions.size} sessions · ${llmPasses.size} LLM passes stored",
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
                        ScanLlmDiagnosticsStore.clear(context)
                        sessions = emptyList()
                        llmPasses = emptyList()
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
 * Rating reminder switch row. On Android 13+ enabling it prompts the runtime
 * POST_NOTIFICATIONS permission inline; if the user denies it the toggle
 * reverts to off and a hint surfaces a deep-link into system settings. Below
 * SDK 33 no runtime permission exists, so the toggle is straightforward.
 * Designed to live inside a [SettingsGroup].
 */
@Composable
private fun RatingReminderRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Use a tick state so we re-read the permission whenever the user comes
    // back from the system settings screen (foreground state isn't observed
    // here, so we expose a manual "refresh" via the launcher callback).
    var permissionTick by remember { mutableIntStateOf(0) }
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

    SettingsSwitchRow(
        title = stringResource(R.string.label_rating_reminder),
        summary = stringResource(R.string.msg_rating_reminder_hint),
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
    if (enabled && !hasPostNotifications) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
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
