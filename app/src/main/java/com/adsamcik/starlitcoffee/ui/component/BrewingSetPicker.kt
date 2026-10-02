package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewingSetPicker(
    sets: List<BrewingSet>, selectedId: String?, grinderData: GrinderDataProvider,
    onSelect: (String) -> Unit, onManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSummary: Boolean = false,
) {
    if (sets.isEmpty()) return
    val active = sets.find { it.id == selectedId } ?: sets.first()
    var expanded by remember { mutableStateOf(false) }
    Surface(onClick = { expanded = true }, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.heightIn(min = if (showSummary) 56.dp else 48.dp)
            .then(if (showSummary) Modifier else Modifier.widthIn(max = 220.dp))
            .testTag("brewing_set_picker").semantics { role = Role.Button }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipmentIcon(EquipmentVisual.method(active.method), Modifier.size(28.dp))
            Column(Modifier.weight(1f, fill = showSummary), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(brewingSetName(active), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showSummary) {
                    val summary = brewingSetSummary(active, grinderData)
                    if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
        }
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.label_brewing_sets), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 20.dp).semantics { heading() })
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sets.forEachIndexed { index, set ->
                        BrewingSetRow(set, set.id == active.id, grinderData,
                            modifier = Modifier.testTag("choose_brewing_set_${set.id}"),
                            first = index == 0, last = index == sets.lastIndex,
                            onSelect = { onSelect(set.id); expanded = false })
                    }
                }
                if (onManage != null) {
                    FilledTonalButton(onClick = { expanded = false; onManage() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("manage_brewing_sets")) {
                        Text(stringResource(R.string.action_manage_brewing_sets))
                    }
                }
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
    return listOfNotNull(set.method.localizedDisplayName().takeIf { set.name != null }, filter?.localizedDisplayName(),
        grinder?.let { "${it.brand} ${it.model}" }).joinToString(" · ")
}
