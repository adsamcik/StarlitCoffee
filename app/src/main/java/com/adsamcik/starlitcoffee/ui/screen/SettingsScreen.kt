package com.adsamcik.starlitcoffee.ui.screen

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
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
import com.adsamcik.starlitcoffee.ui.component.SettingsGroup
import com.adsamcik.starlitcoffee.ui.component.SettingsNavigationRow
import com.adsamcik.starlitcoffee.ui.component.SettingsRowDivider
import com.adsamcik.starlitcoffee.ui.component.SettingsSectionHeader
import com.adsamcik.starlitcoffee.ui.component.SettingsSelectorBlock
import com.adsamcik.starlitcoffee.ui.component.SettingsSwitchRow
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName
import com.adsamcik.starlitcoffee.util.VibrationHelper
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
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.MindlayerDiagnosticsCard
import com.adsamcik.starlitcoffee.ui.component.MindlayerSettingsCard
import com.adsamcik.starlitcoffee.ui.component.ScanHistoryDialog
import com.adsamcik.starlitcoffee.ui.component.formatSessionForShare
import com.adsamcik.starlitcoffee.viewmodel.SettingsCompletion
import com.adsamcik.starlitcoffee.viewmodel.SettingsFailure
import com.adsamcik.starlitcoffee.viewmodel.SettingsOperation
import com.adsamcik.starlitcoffee.viewmodel.SettingsUiState
import com.adsamcik.starlitcoffee.viewmodel.SettingsViewModel

private const val PRIVACY_POLICY_URL = "https://adsamcik.github.io/StarlitCoffee/privacy/"

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    cupPresetRepository: CupPresetRepository,
    onNavigateToBloomAnimationSettings: () -> Unit,
    onNavigateToDisplaySettings: () -> Unit,
    onNavigateToCupPresetEditor: (presetId: Long?) -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onBack: () -> Unit,
){
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle(
        initialValue = com.adsamcik.starlitcoffee.data.repository.UserPreferences(),
    )
    val cupPresets by cupPresetRepository.presets.collectAsStateWithLifecycle(initialValue = emptyList())
    val operationState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showResetPresetsDialog by rememberSaveable { mutableStateOf(false) }
    val isBusy = operationState.operation != SettingsOperation.IDLE
    val isResettingPresets = operationState.operation == SettingsOperation.RESETTING_CUP_PRESETS
    val requestBack = { if (!isBusy) onBack() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(onBack = requestBack)

    LaunchedEffect(operationState.completion) {
        if (operationState.completion == SettingsCompletion.CUP_PRESETS_RESET) {
            showResetPresetsDialog = false
            viewModel.consumeCompletion()
        }
    }
    LaunchedEffect(operationState.failure) {
        if (operationState.failure == SettingsFailure.SAVE) {
            Toast.makeText(context, R.string.msg_settings_save_failed, Toast.LENGTH_LONG).show()
            viewModel.consumeFailure()
        }
    }

    if (showResetPresetsDialog) {
        DestructiveActionDialog(
            titleRes = R.string.action_reset_defaults,
            confirmLabelRes = R.string.action_reset_defaults,
            messageRes = if (operationState.failure == SettingsFailure.RESET_CUP_PRESETS) {
                R.string.msg_cup_preset_reset_failed
            } else {
                R.string.msg_reset_cup_presets
            },
            enabled = !isResettingPresets,
            onConfirm = viewModel::resetCupPresets,
            onDismiss = {
                showResetPresetsDialog = false
                if (operationState.failure == SettingsFailure.RESET_CUP_PRESETS) {
                    viewModel.consumeFailure()
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- Brewing ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_brewing))

            // Cup presets — keeps its add/reset actions and tappable list.
            SettingsGroup {
                Column(modifier = Modifier.padding(20.dp)) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (!isBusy) onNavigateToCupPresetEditor(null)
                                },
                                enabled = !isBusy,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.action_add_preset),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            IconButton(
                                onClick = { showResetPresetsDialog = true },
                                enabled = !isBusy,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = stringResource(R.string.action_reset_defaults),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .clickable(enabled = !isBusy) {
                                    onNavigateToCupPresetEditor(preset.id)
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val dotColor = preset.colorHex?.let {
                                try { Color(it.toColorInt()) } catch (_: IllegalArgumentException) { null }
                            } ?: MaterialTheme.colorScheme.secondaryContainer
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        CircleShape,
                                    ),
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
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.surfaceContainerLow,
                                            CircleShape,
                                        ),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${preset.waterMl.toInt()} ${stringResource(R.string.unit_ml)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                enabled = !isBusy,
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
                                    viewModel.updateMethodSelection(newSet, prefs.defaultMethod)
                                },
                                label = { Text(method.localizedDisplayName()) },
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
                                enabled = !isBusy,
                                onClick = {
                                    viewModel.updateDefaultMethod(prefs.enabledMethods, method)
                                },
                                label = { Text(method.localizedDisplayName()) },
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
                            enabled = !isBusy,
                            onClick = { viewModel.updateDefaultFilterType(null) },
                            label = { Text(stringResource(R.string.label_none)) },
                            leadingIcon = if (prefs.defaultFilterType == null) checkIcon else null,
                        )
                        FilterType.entries.forEach { filter ->
                            val isFilterSelected = prefs.defaultFilterType == filter
                            FilterChip(
                                selected = isFilterSelected,
                                enabled = !isBusy,
                                onClick = { viewModel.updateDefaultFilterType(filter) },
                                label = { Text(filter.localizedDisplayName()) },
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
                            enabled = !isBusy,
                            onClick = { viewModel.updateSelectedGrinder(null) },
                            label = { Text(stringResource(R.string.label_no_grinder)) },
                            leadingIcon = if (prefs.selectedGrinderId == null) checkIcon else null,
                        )
                        GrinderDataSource.getInstance(context).grinders.forEach { grinder ->
                            val isGrinderSelected = prefs.selectedGrinderId == grinder.id
                            FilterChip(
                                selected = isGrinderSelected,
                                enabled = !isBusy,
                                onClick = { viewModel.updateSelectedGrinder(grinder.id) },
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
                    enabled = !isBusy,
                    onCheckedChange = viewModel::updateSkipMethodSelection,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_show_brewing_instructions),
                    summary = stringResource(R.string.msg_show_brewing_instructions_hint),
                    checked = prefs.showBrewingInstructions,
                    enabled = !isBusy,
                    onCheckedChange = viewModel::updateShowBrewingInstructions,
                )
            }

            // ---------- Appearance ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_appearance))
            SettingsGroup {
                SettingsNavigationRow(
                    title = stringResource(R.string.label_display_dim_settings),
                    summary = stringResource(R.string.msg_display_dim_settings_subtitle),
                    onClick = { if (!isBusy) onNavigateToDisplaySettings() },
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    title = stringResource(R.string.label_bloom_animation_settings),
                    summary = stringResource(R.string.msg_bloom_animation_settings_subtitle),
                    onClick = { if (!isBusy) onNavigateToBloomAnimationSettings() },
                )
            }

            // ---------- Notifications ----------
            SettingsSectionHeader(stringResource(R.string.label_settings_section_notifications))
            SettingsGroup {
                RatingReminderRow(
                    enabled = prefs.ratingReminderEnabled,
                    operationEnabled = !isBusy,
                    onEnabledChange = viewModel::updateRatingReminderEnabled,
                )
                SettingsRowDivider()
                BrewVibrationThemeSelector(
                    selectedTheme = prefs.brewVibrationTheme,
                    enabled = !isBusy,
                    onThemeSelected = viewModel::updateBrewVibrationTheme,
                )
            }

            // AI setup is a normal product setting. Debug-only connection
            // details live below with the other developer tools.
            SettingsSectionHeader(stringResource(R.string.label_ai_service))
            MindlayerSettingsCard()

            // Diagnostics are a user-controlled support feature in every build.
            SettingsSectionHeader(stringResource(R.string.label_settings_section_support))
            SettingsGroup {
                SettingsNavigationRow(
                    title = stringResource(R.string.label_diagnostics),
                    summary = stringResource(R.string.msg_diagnostics_summary),
                    onClick = { if (!isBusy) onNavigateToDiagnostics() },
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    title = stringResource(R.string.label_privacy_policy),
                    summary = stringResource(R.string.msg_privacy_policy_summary),
                    onClick = {
                        if (!isBusy) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)),
                                )
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    R.string.msg_privacy_policy_open_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
            }

            // ---------- Developer (debug only) ----------
            if (BuildConfig.DEBUG) {
                SettingsSectionHeader(stringResource(R.string.label_settings_section_developer))
                MindlayerDiagnosticsCard()
                ScanDebugCard(viewModel = viewModel, operationState = operationState)
                // Phase 3 — opt-in, on-device capture of model-vs-user field
                // corrections, used to measure extraction quality on real bags.
                // Debug-only + default off: the flag never activates in release,
                // so there is no release-time data-collection surface.
                SettingsGroup {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_log_scan_corrections),
                        summary = stringResource(R.string.msg_log_scan_corrections),
                        checked = prefs.scanCorrectionLoggingEnabled,
                        enabled = !isBusy,
                        onCheckedChange = viewModel::updateScanCorrectionLoggingEnabled,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanDebugCard(
    viewModel: SettingsViewModel,
    operationState: SettingsUiState,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var sessions by remember { mutableStateOf(ScanSessionRingBuffer.getAll(context)) }
    var llmPasses by remember { mutableStateOf(ScanLlmDiagnosticsStore.getAll(context)) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val isClearing = operationState.operation == SettingsOperation.CLEARING_DIAGNOSTICS
    val operationsEnabled = operationState.operation == SettingsOperation.IDLE

    LaunchedEffect(operationState.completion) {
        if (operationState.completion == SettingsCompletion.DIAGNOSTICS_CLEARED) {
            sessions = emptyList()
            llmPasses = emptyList()
            showClearDialog = false
            viewModel.consumeCompletion()
        }
    }

    if (showClearDialog) {
        DestructiveActionDialog(
            titleRes = R.string.action_clear,
            confirmLabelRes = R.string.action_clear,
            messageRes = if (operationState.failure == SettingsFailure.CLEAR_DIAGNOSTICS) {
                R.string.msg_diagnostic_clear_failed
            } else {
                R.string.msg_clear_diagnostics
            },
            enabled = !isClearing,
            onConfirm = viewModel::clearDiagnostics,
            onDismiss = {
                showClearDialog = false
                if (operationState.failure == SettingsFailure.CLEAR_DIAGNOSTICS) {
                    viewModel.consumeFailure()
                }
            },
        )
    }

    SettingsGroup {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.label_scan_debug),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.format_scan_debug_counts,
                    sessions.size,
                    llmPasses.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { showHistory = true },
                    enabled = operationsEnabled,
                ) {
                    Text(stringResource(R.string.action_view_history))
                }
                OutlinedButton(
                    onClick = { ScanBugReporter.shareReport(context) },
                    enabled = operationsEnabled,
                ) {
                    Text(stringResource(R.string.action_share_report))
                }
                TextButton(
                    onClick = { showClearDialog = true },
                    enabled = operationsEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
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
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        resources.getString(R.string.format_scan_session_subject, session.sessionId),
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        resources.getString(R.string.action_share_scan_session),
                    )
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
    operationEnabled: Boolean,
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
        enabled = operationEnabled,
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


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrewVibrationThemeSelector(
    selectedTheme: BrewVibrationTheme,
    enabled: Boolean,
    onThemeSelected: (BrewVibrationTheme) -> Unit,
) {
    val context = LocalContext.current
    SettingsSelectorBlock(
        title = stringResource(R.string.label_brew_vibration_theme),
        summary = stringResource(R.string.msg_brew_vibration_theme_hint),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BrewVibrationTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = selectedTheme == theme,
                    onClick = {
                        onThemeSelected(theme)
                        VibrationHelper.previewBrewTheme(context, theme)
                    },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = BrewVibrationTheme.entries.size,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(vibrationThemeLabel(theme))
                }
            }
        }
    }
}

@Composable
private fun vibrationThemeLabel(theme: BrewVibrationTheme): String = when (theme) {
    BrewVibrationTheme.SOFT -> stringResource(R.string.label_brew_vibration_soft)
    BrewVibrationTheme.CLASSIC -> stringResource(R.string.label_brew_vibration_classic)
    BrewVibrationTheme.BOLD -> stringResource(R.string.label_brew_vibration_bold)
}
