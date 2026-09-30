package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BarcodeBrewEntry(
    bags: List<CoffeeBagEntity>,
    inventoryLoaded: Boolean,
    scannedBarcode: String?,
    onBarcodeConsumed: () -> Unit,
    onScan: () -> Unit,
    onViewBeans: () -> Unit,
    onSelectBag: (Long) -> Unit,
) {
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(scannedBarcode) {
        scannedBarcode?.let {
            pendingBarcode = it
            onBarcodeConsumed()
        }
    }
    val matches = remember(pendingBarcode, bags) {
        pendingBarcode?.let { inStockBagsForBarcode(it, bags) }.orEmpty()
    }
    LaunchedEffect(pendingBarcode, matches, inventoryLoaded) {
        if (pendingBarcode != null && inventoryLoaded && matches.size == 1) {
            pendingBarcode = null
            onSelectBag(matches.single().id)
        }
    }

    if (hasInStockBarcodeBags(bags)) {
        AssistChip(
            onClick = onScan,
            label = { Text(stringResource(R.string.action_scan_to_brew)) },
            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            modifier = Modifier.testTag("scan_to_brew"),
        )
    }

    if (pendingBarcode != null && inventoryLoaded && matches.isEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingBarcode = null },
            title = { Text(stringResource(R.string.action_scan_barcode)) },
            text = { Text(stringResource(R.string.msg_barcode_brew_no_match)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingBarcode = null
                    onViewBeans()
                }) { Text(stringResource(R.string.label_your_beans)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBarcode = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    } else if (pendingBarcode != null && inventoryLoaded && matches.size > 1) {
        BarcodeBrewBagPicker(
            bags = matches,
            onDismiss = { pendingBarcode = null },
            onSelect = { id ->
                pendingBarcode = null
                onSelectBag(id)
            },
        )
    }
}

@Composable
private fun BarcodeBrewBagPicker(
    bags: List<CoffeeBagEntity>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_select_coffee_bag_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.msg_barcode_brew_choose_bag))
                bags.forEach { bag ->
                    TextButton(onClick = { onSelect(bag.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(bag.name, style = MaterialTheme.typography.titleSmall)
                            bag.roaster?.let { Text(it) }
                            val status = stringResource(
                                if (bag.status == "OPEN") R.string.bag_status_open else R.string.bag_status_sealed,
                            )
                            val weight = bag.weightG?.let {
                                stringResource(R.string.format_weight_left, it)
                            }.orEmpty()
                            Text("$status$weight")
                            bag.roastDate?.let {
                                Text("${stringResource(R.string.label_roast_date)}: ${dateFormat.format(Date(it))}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
