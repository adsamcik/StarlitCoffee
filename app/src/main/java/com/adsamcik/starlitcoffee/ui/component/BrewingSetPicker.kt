package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

@Composable
fun BrewingSetPicker(
    sets: List<BrewingSet>,
    selectedId: String?,
    grinderData: GrinderDataProvider,
    onSelect: (String) -> Unit,
    onManage: (() -> Unit)? = null,
) {
    if (sets.isEmpty()) return
    val selected = sets.find { it.id == selectedId } ?: sets.first()
    var expanded by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(brewingSetName(selected)) },
            leadingIcon = { Icon(Icons.Default.Coffee, contentDescription = null) },
            modifier = Modifier.testTag("brewing_set_picker"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sets.forEach { set ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(brewingSetName(set))
                            Text(brewingSetSummary(set, grinderData), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(set.id); expanded = false },
                    modifier = Modifier.testTag("choose_brewing_set_${set.id}"),
                )
            }
            if (onManage != null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.action_manage_brewing_sets)) },
                    onClick = { expanded = false; onManage() }, modifier = Modifier.testTag("manage_brewing_sets"))
            }
        }
    }
}

@Composable
fun brewingSetName(set: BrewingSet): String = set.name ?: set.method.localizedDisplayName()

@Composable
fun brewingSetSummary(set: BrewingSet, grinderData: GrinderDataProvider): String {
    val filter = set.setup.filterType?.let(FilterType::valueOf)
    val grinder = grinderData.grindersFor(set.method, filter).find { it.id == set.setup.grinderId }
    return listOfNotNull(set.method.localizedDisplayName(), filter?.localizedDisplayName(),
        grinder?.let { "${it.brand} ${it.model}" }).joinToString(" · ")
}
