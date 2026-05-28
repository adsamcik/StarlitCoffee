package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity

/**
 * Dialog shown when the user starts a brew without a bag selected while at
 * least one tracked bag exists. Lets them pick one of the tracked bags or
 * dismiss to continue brewing without one.
 *
 * The dialog only renders a compact list — the full bag picker remains
 * available in the existing bag inventory and method picker surfaces.
 */
@Composable
fun BagSelectionPromptDialog(
    trackedBags: List<CoffeeBagEntity>,
    onSelectBag: (Long) -> Unit,
    onBrewWithoutBag: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (trackedBags.isEmpty()) {
        // Defensive: caller is supposed to gate on this, but never show an
        // empty list. Just fall through to brew-without-bag so the user
        // doesn't get stuck.
        onBrewWithoutBag()
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.dialog_pick_bag_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.msg_pick_bag_body, trackedBags.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(trackedBags, key = { it.id }) { bag ->
                            ListItem(
                                headlineContent = { Text(bag.name) },
                                supportingContent = {
                                    val supporting = buildString {
                                        bag.roaster?.let { append(it) }
                                        bag.weightG?.let { weight ->
                                            if (isNotEmpty()) append(" · ")
                                            append(stringResource(R.string.format_weight_left, weight).trim())
                                        }
                                    }
                                    if (supporting.isNotBlank()) {
                                        Text(supporting)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                tonalElevation = 0.dp,
                                trailingContent = {
                                    TextButton(onClick = { onSelectBag(bag.id) }) {
                                        Text(stringResource(R.string.action_pick_bag))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBrewWithoutBag) {
                Text(stringResource(R.string.action_brew_without_bag_short))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
