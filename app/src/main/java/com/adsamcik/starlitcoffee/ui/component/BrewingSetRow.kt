package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider

@Composable
fun BrewingSetRow(
    set: BrewingSet,
    selected: Boolean,
    grinderData: GrinderDataProvider,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    first: Boolean = true,
    last: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onSelect: () -> Unit,
) {
    val detail = brewingSetSummary(set, grinderData)
    Surface(onClick = onSelect, enabled = enabled,
        shape = RoundedCornerShape(topStart = if (first) 24.dp else 4.dp, topEnd = if (first) 24.dp else 4.dp,
            bottomStart = if (last) 24.dp else 4.dp, bottomEnd = if (last) 24.dp else 4.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.RadioButton }) {
        Row(Modifier.heightIn(min = 80.dp).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EquipmentVisualBadge(EquipmentVisual.method(set.method), selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(brewingSetName(set), style = MaterialTheme.typography.titleMedium)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
            trailingContent?.invoke()
        }
    }
}
