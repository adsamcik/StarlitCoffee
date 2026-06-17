package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity

/**
 * Optional coffee-bag selector chip used during the brew flow (e.g. the grind
 * step). Renders a single [FilterChip] that opens a bottom-sheet picker so the
 * user can attach the coffee they're brewing — or clear it again. Selection is
 * entirely optional; the chip simply reflects the current choice.
 *
 * State (selected bag, available bags) lives in `BrewViewModel`; this component
 * is purely presentational and forwards intent through [onSelectBag] /
 * [onClearBag].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeBagSelector(
    bags: List<CoffeeBagEntity>,
    selectedBag: CoffeeBagEntity?,
    onSelectBag: (Long) -> Unit,
    onClearBag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    val chipLabel = selectedBag?.let { bag ->
        val decaf = if (bag.isDecaf) stringResource(R.string.label_decaf_suffix) else ""
        stringResource(R.string.format_brewing_with, "${bag.name}$decaf")
    } ?: stringResource(R.string.label_select_coffee_bag)

    FilterChip(
        selected = selectedBag != null,
        onClick = { showPicker = true },
        label = { Text(text = chipLabel, maxLines = 1) },
        trailingIcon = if (selectedBag != null) {
            {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_clear_bag),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClearBag() },
                )
            }
        } else {
            null
        },
        modifier = modifier,
    )

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_select_coffee_bag_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (bags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_active_bags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(bags, key = { it.id }) { bag ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectBag(bag.id)
                                        showPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                            ) {
                                Column {
                                    val decaf = if (bag.isDecaf) {
                                        stringResource(R.string.label_decaf_suffix)
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "${bag.name}$decaf",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (bag.id == selectedBag?.id) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    val subtitleParts = buildList {
                                        bag.roaster?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        bag.weightG?.let { w ->
                                            add(stringResource(R.string.format_weight_left, w).trim())
                                        }
                                    }
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (selectedBag != null) {
                    TextButton(
                        onClick = {
                            onClearBag()
                            showPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_brew_without_bag))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
