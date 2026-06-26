package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.SettingsGroup
import com.adsamcik.starlitcoffee.ui.component.SettingsRowDivider
import com.adsamcik.starlitcoffee.ui.component.SettingsSectionHeader
import com.adsamcik.starlitcoffee.ui.component.SettingsSwitchRow
import kotlinx.coroutines.launch

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
    userPreferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit,
) {
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )
    val scope = rememberCoroutineScope()
    val dimEnabled = prefs.dimModeEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = ScreenTopPadding),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_display_settings_title),
            onBack = onBack,
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
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateDimModeEnabled(enabled)
                        }
                    },
                )
            }

            // Dependent options — only take effect while dim mode is on.
            SettingsSectionHeader(stringResource(R.string.label_settings_section_when_dimmed))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_true_black),
                    summary = stringResource(R.string.msg_dim_mode_true_black_hint),
                    checked = prefs.dimModeTrueBlack,
                    enabled = dimEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateDimModeTrueBlack(enabled)
                        }
                    },
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_reduce_brightness),
                    summary = stringResource(R.string.msg_dim_mode_reduce_brightness_hint),
                    checked = prefs.dimModeReduceBrightness,
                    enabled = dimEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateDimModeReduceBrightness(enabled)
                        }
                    },
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_fullscreen),
                    summary = stringResource(R.string.msg_dim_mode_fullscreen_hint),
                    checked = prefs.dimModeFullscreen,
                    enabled = dimEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateDimModeFullscreen(enabled)
                        }
                    },
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.label_dim_mode_force_dark_in_light),
                    summary = stringResource(R.string.msg_dim_mode_force_dark_in_light_hint),
                    checked = prefs.dimModeForceDarkInLight,
                    enabled = dimEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.updateDimModeForceDarkInLight(enabled)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(ListBottomPadding))
        }
    }
}
