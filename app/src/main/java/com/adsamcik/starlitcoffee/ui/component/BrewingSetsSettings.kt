package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider

@Composable
fun BrewingSetsSettings(
    sets: List<BrewingSet>,
    selectedId: String?,
    grinderData: GrinderDataProvider,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (BrewingSet) -> Unit,
    onDelete: (BrewingSet) -> Unit,
    onAdd: () -> Unit,
) {
    SettingsSelectorBlock(title = stringResource(R.string.label_brewing_sets),
        summary = stringResource(R.string.msg_brewing_sets_hint)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sets.forEach { set ->
                val selected = set.id == selectedId
                val name = brewingSetName(set)
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("brewing_set_${set.id}").selectable(
                        selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(set.id) })) {
                    Row(modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(brewingSetSummary(set, grinderData), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(set) }, enabled = enabled,
                            modifier = Modifier.testTag("edit_brewing_set_${set.id}")) {
                            Icon(Icons.Default.Edit, contentDescription = "${stringResource(R.string.label_edit_brewing_set)}: $name")
                        }
                        if (sets.size > 1) {
                            IconButton(onClick = { onDelete(set) }, enabled = enabled,
                                modifier = Modifier.testTag("delete_brewing_set_${set.id}")) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "${stringResource(R.string.action_delete)}: $name")
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onAdd, enabled = enabled, modifier = Modifier.testTag("add_brewing_set")) {
                Text(stringResource(R.string.action_add_brewing_set))
            }
        }
    }
}
