package com.adsamcik.starlitcoffee.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.SettingsGroup
import com.adsamcik.starlitcoffee.ui.component.SettingsRowDivider
import com.adsamcik.starlitcoffee.ui.component.SettingsSectionHeader
import com.adsamcik.starlitcoffee.ui.component.SettingsSwitchRow
import com.adsamcik.starlitcoffee.viewmodel.SettingsFailure
import com.adsamcik.starlitcoffee.viewmodel.SettingsOperation
import com.adsamcik.starlitcoffee.viewmodel.SettingsViewModel

private val ScreenHorizontalPadding = 16.dp
private val ScreenTopPadding = 24.dp
private val ListTopPadding = 8.dp
private val ListBottomPadding = 24.dp
private val SectionGap = 8.dp

/**
 * Dedicated "Display & dim mode" sub-screen. Hosts the master automatic-dim
 * toggle plus the four dependent options that only matter when dim mode is on,
 * so the main Settings screen can show a single compact entry instead of a wall
 * of toggles. Dependent rows dim and disable while the master is off.
 */
@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )
    val operationState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isBusy = operationState.operation != SettingsOperation.IDLE
    val dimEnabled = prefs.dimModeEnabled
    val requestBack = { if (!isBusy) onBack() }

    BackHandler(onBack = requestBack)
    LaunchedEffect(operationState.failure) {
        if (operationState.failure == SettingsFailure.SAVE) {
            Toast.makeText(context, R.string.msg_settings_save_failed, Toast.LENGTH_LONG).show()
            viewModel.consumeFailure()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = ScreenTopPadding),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_display_settings_title),
            onBack = requestBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            Spacer(modifier = Modifier.height(ListTopPadding))

            // Master toggle.
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode),
                    summary = stringResource(R.string.msg_dim_mode_hint),
                    checked = dimEnabled,
                    enabled = !isBusy,
                    onCheckedChange = viewModel::updateDimModeEnabled,
                )
            }

            // Dependent options — only take effect while dim mode is on.
            SettingsSectionHeader(stringResource(R.string.label_settings_section_when_dimmed))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_true_black),
                    summary = stringResource(R.string.msg_dim_mode_true_black_hint),
                    checked = prefs.dimModeTrueBlack,
                    enabled = dimEnabled && !isBusy,
                    onCheckedChange = viewModel::updateDimModeTrueBlack,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_reduce_brightness),
                    summary = stringResource(R.string.msg_dim_mode_reduce_brightness_hint),
                    checked = prefs.dimModeReduceBrightness,
                    enabled = dimEnabled && !isBusy,
                    onCheckedChange = viewModel::updateDimModeReduceBrightness,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_fullscreen),
                    summary = stringResource(R.string.msg_dim_mode_fullscreen_hint),
                    checked = prefs.dimModeFullscreen,
                    enabled = dimEnabled && !isBusy,
                    onCheckedChange = viewModel::updateDimModeFullscreen,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_force_dark_in_light),
                    summary = stringResource(R.string.msg_dim_mode_force_dark_in_light_hint),
                    checked = prefs.dimModeForceDarkInLight,
                    enabled = dimEnabled && !isBusy,
                    onCheckedChange = viewModel::updateDimModeForceDarkInLight,
                )
            }

            Spacer(modifier = Modifier.height(ListBottomPadding))
        }
    }
}
