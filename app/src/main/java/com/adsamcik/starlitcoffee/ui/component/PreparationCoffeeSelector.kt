package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.viewmodel.isBrewable
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** The named coffee belongs beside the dose; only the picker exposes physical pack detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationCoffeeSelector(
    bags: List<CoffeeBagEntity>,
    selectedBag: CoffeeBagEntity?,
    isDecaf: Boolean,
    onSelectBag: (Long) -> Unit,
    onClearBag: () -> Unit,
    modifier: Modifier = Modifier,
    onScanToChoose: (() -> Unit)? = null,
    onAddPack: (() -> Unit)? = null,
    openPickerRequest: Int = 0,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var handledPickerRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(openPickerRequest) {
        if (openPickerRequest > handledPickerRequest) {
            handledPickerRequest = openPickerRequest
            showPicker = true
        }
    }
    Surface(
        onClick = { showPicker = true },
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.testTag("prep_coffee_selector"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    selectedBag?.name ?: stringResource(
                        if (isDecaf) R.string.prep_choose_decaf_coffee else R.string.prep_choose_coffee,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    selectedBag?.let { preparationPackSummary(it) } ?: stringResource(R.string.prep_coffee_choice_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            PreparationPackPicker(
                bags = bags,
                selectedBagId = selectedBag?.id,
                isDecaf = isDecaf,
                onSelectBag = { showPicker = false; onSelectBag(it) },
                onClearBag = { showPicker = false; onClearBag() },
                onScanToChoose = onScanToChoose?.let { action -> { showPicker = false; action() } },
                onAddPack = onAddPack?.let { action -> { showPicker = false; action() } },
            )
        }
    }
}

/** Coffee identity is a persisted link, never an inferred match on a name or barcode. */
@Composable
internal fun PreparationPackPicker(
    bags: List<CoffeeBagEntity>,
    selectedBagId: Long?,
    isDecaf: Boolean,
    onSelectBag: (Long) -> Unit,
    onClearBag: () -> Unit,
    onScanToChoose: (() -> Unit)?,
    onAddPack: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(bags, query, isDecaf) {
        val term = query.trim()
        bags.asSequence()
            .filter { it.isBrewable() }
            .filter { bag ->
                term.isBlank() || bag.name.contains(term, ignoreCase = true) ||
                    bag.roaster?.contains(term, ignoreCase = true) == true
            }
            .groupBy { it.coffeeId?.let { coffeeId -> "coffee:$coffeeId" } ?: "pack:${it.id}" }
            .values
            .map { group -> group.sortedWith(compareBy<CoffeeBagEntity> { it.status != "OPEN" }.thenBy { it.packNumber }) }
            .sortedWith(compareBy<List<CoffeeBagEntity>> { it.first().isDecaf != isDecaf }.thenBy { it.first().name.lowercase() })
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("prep_pack_picker"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.prep_your_coffee),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.prep_choose_physical_pack),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onScanToChoose?.let { scan ->
            item {
                FilledTonalButton(onClick = scan, modifier = Modifier.fillMaxWidth().testTag("prep_scan_choose")) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.prep_scan_to_choose))
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.label_search_coffee_bags)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prep_pack_search"),
            )
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    stringResource(if (query.isBlank()) R.string.prep_empty_inventory else R.string.msg_no_matching_coffee_bags),
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { group ->
            val coffee = group.first()
            item(key = "heading:${coffee.coffeeId}:${coffee.id}") {
                Column(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        coffee.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    val type = stringResource(if (coffee.isDecaf) R.string.label_decaf else R.string.label_regular)
                    Text(
                        listOfNotNull(coffee.roaster?.takeIf(String::isNotBlank), type).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(group, key = { it.id }) { bag ->
                PreparationPackRow(bag, selectedBagId == bag.id && bag.isDecaf == isDecaf, onSelectBag)
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                onAddPack?.let { add ->
                    FilledTonalButton(onClick = add, modifier = Modifier.fillMaxWidth().testTag("prep_add_pack")) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.prep_add_pack))
                    }
                }
                TextButton(onClick = onClearBag, modifier = Modifier.fillMaxWidth().testTag("prep_no_bag")) {
                    Text(stringResource(R.string.action_brew_without_bag))
                }
            }
        }
    }
}

@Composable
private fun PreparationPackRow(bag: CoffeeBagEntity, isSelected: Boolean, onSelectBag: (Long) -> Unit) {
    Surface(
        onClick = { onSelectBag(bag.id) },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("prep_pack_${bag.id}").semantics {
            selected = isSelected
            role = Role.RadioButton
        },
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preparationPackSummary(bag), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    bag.roastDate?.let {
                        stringResource(R.string.prep_pack_roasted, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
                    } ?: stringResource(R.string.prep_pack_roast_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (isSelected) Icons.Filled.Check else Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun preparationPackSummary(bag: CoffeeBagEntity): String = listOfNotNull(
    stringResource(
        when (bag.status) {
            "OPEN" -> R.string.bag_status_open
            "FROZEN" -> R.string.bag_status_frozen
            "FINISHED" -> R.string.bag_status_finished
            else -> R.string.bag_status_sealed
        },
    ),
    bag.weightG?.let { weight ->
        stringResource(R.string.prep_pack_remaining, NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(weight))
    },
    stringResource(R.string.prep_pack_number, bag.packNumber),
).joinToString(" · ")
