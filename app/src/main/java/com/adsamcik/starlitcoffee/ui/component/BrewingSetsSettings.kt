package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider

@Composable
fun BrewingSetsSettings(
    sets: List<BrewingSet>, selectedId: String?, grinderData: GrinderDataProvider, enabled: Boolean,
    onSelect: (String) -> Unit, onEdit: (BrewingSet) -> Unit, onDelete: (BrewingSet) -> Unit, onAdd: () -> Unit,
) {
    SettingsSelectorBlock(title = stringResource(R.string.label_brewing_sets),
        summary = stringResource(R.string.msg_brewing_sets_hint)) {
        BrewingSetsList(sets, selectedId, grinderData, enabled, onSelect, onEdit, onDelete)
        FilledTonalButton(onClick = onAdd, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("add_brewing_set")) {
            Icon(Icons.Default.Add, null)
            Text(stringResource(R.string.action_add_brewing_set))
        }
    }
}

@Composable
fun BrewingSetsList(
    sets: List<BrewingSet>, selectedId: String?, grinderData: GrinderDataProvider, enabled: Boolean,
    onSelect: (String) -> Unit, onEdit: (BrewingSet) -> Unit, onDelete: (BrewingSet) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.selectableGroup()) {
        sets.forEachIndexed { index, set ->
            var menu by remember(set.id) { mutableStateOf(false) }
            BrewingSetRow(set, set.id == selectedId, grinderData, enabled,
                modifier = Modifier.testTag("brewing_set_${set.id}"), first = index == 0, last = index == sets.lastIndex,
                trailingContent = {
                    Column {
                        IconButton(onClick = { menu = true }, enabled = enabled,
                            modifier = Modifier.testTag("brewing_set_options_${set.id}")) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.cd_brewing_set_options, brewingSetName(set)))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.label_edit_brewing_set)) },
                                onClick = { menu = false; onEdit(set) }, modifier = Modifier.testTag("edit_brewing_set_${set.id}"))
                            if (sets.size > 1) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = { menu = false; onDelete(set) }, modifier = Modifier.testTag("delete_brewing_set_${set.id}"))
                            }
                        }
                    }
                }, onSelect = { onSelect(set.id) })
        }
    }
}
