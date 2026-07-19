package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Segmented chip row for filtering by decaf status. Each screen shows counts next to the
 * label so users can see at a glance how many items match each segment.
 *
 * @param counts map from filter to item count; counts for missing filters default to 0.
 */
@Composable
fun DecafFilterChipRow(
    selected: DecafFilter,
    counts: Map<DecafFilter, Int>,
    onSelected: (DecafFilter) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DecafFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            val label = stringResource(filter.labelRes)
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                enabled = enabled,
                modifier = Modifier.testTag("decaf_filter_${filter.name}"),
                label = {
                    Text(
                        text = "$label ($count)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
