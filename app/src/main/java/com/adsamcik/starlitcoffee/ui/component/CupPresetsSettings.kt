package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.ui.util.PresetIcon

/** One list manages the same cups whether their calculator shortcuts are shown or hidden. */
@Composable
fun CupPresetsSettings(
    presets: List<CupPreset>,
    showOnCalculator: Boolean,
    enabled: Boolean,
    onShowOnCalculatorChange: (Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    SettingsGroup(modifier = modifier.testTag("cup_presets_settings")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_cup_presets),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.size(48.dp).testTag("cup_presets_options"),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_cup_preset_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded && enabled,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reset_defaults)) },
                        onClick = { menuExpanded = false; onReset() },
                        modifier = Modifier.testTag("cup_presets_reset"),
                    )
                }
            }
        }
        SettingsSwitchRow(
            title = stringResource(R.string.label_show_cup_presets),
            checked = showOnCalculator,
            onCheckedChange = onShowOnCalculatorChange,
            enabled = enabled,
            modifier = Modifier.testTag("settings_show_cup_presets"),
        )
        SettingsRowDivider()
        presets.forEachIndexed { index, preset ->
            CupPresetSettingsRow(preset, enabled, onClick = { onEdit(preset.id) })
            if (index < presets.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
        TextButton(
            onClick = onAdd,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("cup_presets_add"),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.action_add_preset))
            }
        }
    }
}

@Composable
private fun CupPresetSettingsRow(preset: CupPreset, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .testTag("cup_presets_edit_${preset.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            PresetIcon(preset.iconName, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${formatBrewQuantity(preset.waterMl)} ${stringResource(R.string.unit_ml)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
