package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeIdentityEntity
import com.adsamcik.starlitcoffee.viewmodel.PackAddError
import com.adsamcik.starlitcoffee.viewmodel.PackAddState
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Review one physical pack. Only the confirmed callback can create inventory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreparationNewPackSheet(
    identities: List<CoffeeIdentityEntity>,
    addState: PackAddState,
    scannedBarcode: String?,
    onBarcodeHandled: () -> Unit,
    onFindCoffee: suspend (String) -> List<CoffeeIdentityEntity>,
    onScan: (() -> Unit)?,
    onReadLabel: (() -> Unit)?,
    onAdd: (Long?, String, String?, Boolean, Float, Long?, Boolean, Float?, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var coffeeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var roaster by rememberSaveable { mutableStateOf("") }
    var isDecaf by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable { mutableStateOf("") }
    var remaining by rememberSaveable { mutableStateOf("") }
    var roastDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var barcode by rememberSaveable { mutableStateOf<String?>(null) }
    var candidateIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var unknownBarcode by rememberSaveable { mutableStateOf(false) }
    var lookupFailed by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val operationId = rememberSaveable { UUID.randomUUID().toString() }

    fun selectCoffee(coffee: CoffeeIdentityEntity) {
        coffeeId = coffee.id
        name = coffee.name
        roaster = coffee.roaster.orEmpty()
        isDecaf = coffee.isDecaf
        reviewing = true
        unknownBarcode = false
    }

    LaunchedEffect(scannedBarcode) {
        scannedBarcode?.let { code ->
            barcode = code
            loading = true
            lookupFailed = false
            try {
                val candidates = onFindCoffee(code)
                candidateIds = candidates.map { it.id }
                when (candidates.size) {
                    0 -> {
                        coffeeId = null
                        name = ""
                        roaster = ""
                        unknownBarcode = true
                        reviewing = true
                    }
                    1 -> selectCoffee(candidates.single())
                    else -> reviewing = false
                }
                onBarcodeHandled()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                lookupFailed = true
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(addState.addedPackId) {
        if (addState.addedPackId != null) onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = { if (!addState.isSaving) onDismiss() },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("prep_new_pack_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (reviewing) R.string.prep_new_pack else R.string.prep_add_pack),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (reviewing) R.string.prep_new_pack_review else R.string.prep_new_pack_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) CircularProgressIndicator()
            if (lookupFailed) Text(stringResource(R.string.prep_new_pack_lookup_failed), color = MaterialTheme.colorScheme.error)
            if (reviewing) {
                if (coffeeId != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(roaster, stringResource(if (isDecaf) R.string.label_decaf else R.string.label_regular))
                                    .filter(String::isNotBlank).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { coffeeId = null }, enabled = !addState.isSaving) {
                        Text(stringResource(R.string.prep_new_pack_different_coffee))
                    }
                } else {
                    if (unknownBarcode) {
                        Text(stringResource(R.string.prep_barcode_coffee_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        onReadLabel?.let { read -> TextButton(onClick = read) { Text(stringResource(R.string.action_read_bag_photos)) } }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; invalid = false },
                        label = { Text(stringResource(R.string.label_name)) },
                        enabled = !addState.isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("prep_new_pack_name"),
                    )
                    OutlinedTextField(
                        value = roaster,
                        onValueChange = { roaster = it },
                        label = { Text(stringResource(R.string.label_roaster)) },
                        enabled = !addState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PackSwitch(stringResource(R.string.label_decaf), isDecaf, !addState.isSaving) { isDecaf = it }
                }
                Text(
                    stringResource(R.string.prep_new_pack_separate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it; invalid = false },
                    label = { Text(stringResource(R.string.prep_new_pack_size)) },
                    enabled = !addState.isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("prep_new_pack_weight"),
                )
                TextButton(
                    onClick = { showDatePicker = true },
                    enabled = !addState.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("prep_new_pack_roast"),
                ) {
                    Text(stringResource(R.string.prep_new_pack_roast_optional), modifier = Modifier.weight(1f))
                    roastDate?.let { Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
                PackSwitch(stringResource(R.string.prep_new_pack_opened), opened, !addState.isSaving) { opened = it }
                if (opened) {
                    OutlinedTextField(
                        value = remaining,
                        onValueChange = { remaining = it; invalid = false },
                        label = { Text(stringResource(R.string.label_remaining_weight_grams)) },
                        enabled = !addState.isSaving,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("prep_new_pack_remaining"),
                    )
                }
                if (coffeeId != null) {
                    Text(
                        stringResource(R.string.prep_new_pack_setting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (invalid || addState.error != null) {
                    val message = when (addState.error) {
                        PackAddError.UNKNOWN_COFFEE -> R.string.prep_new_pack_unknown_coffee
                        PackAddError.SAVE_FAILED -> R.string.prep_new_pack_failed
                        else -> R.string.prep_new_pack_invalid
                    }
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("prep_new_pack_error"))
                }
                Button(
                    onClick = {
                        val size = weight.replace(',', '.').toFloatOrNull()
                        val left = remaining.replace(',', '.').toFloatOrNull()
                        val validSize = size != null && size.isFinite() && size > 0f
                        val validRemaining = when {
                            !opened -> true
                            left == null || size == null -> false
                            !left.isFinite() -> false
                            else -> left > 0f && left <= size
                        }
                        invalid = name.isBlank() || !validSize || !validRemaining
                        if (!invalid && size != null) {
                            onAdd(coffeeId, name.trim(), roaster.trim().takeIf(String::isNotEmpty), isDecaf, size,
                                roastDate, opened, left.takeIf { opened }, operationId, barcode)
                        }
                    },
                    enabled = !addState.isSaving,
                    colors = primaryActionButtonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("prep_new_pack_commit"),
                ) {
                    Text(stringResource(R.string.prep_new_pack_commit))
                }
            } else {
                onScan?.let { scan ->
                    Button(onClick = scan, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("prep_scan_new_pack")) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.prep_scan_new_pack))
                    }
                }
                TextButton(onClick = { coffeeId = null; reviewing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_add_bag_manual))
                }
                if (candidateIds?.size?.let { it > 1 } == true) {
                    Text(stringResource(R.string.prep_barcode_coffee_ambiguous))
                } else {
                    Text(stringResource(R.string.prep_coffee_rebuy), style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.label_search_coffee_bags)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                identities.filter { coffee ->
                    val matchesQuery = listOfNotNull(coffee.name, coffee.roaster).any { it.contains(query.trim(), ignoreCase = true) }
                    (candidateIds == null || coffee.id in candidateIds.orEmpty()) && matchesQuery
                }.forEach { coffee ->
                    Surface(
                        onClick = { selectCoffee(coffee) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().testTag("prep_rebuy_coffee_${coffee.id}"),
                    ) {
                        Row(
                            Modifier.heightIn(min = 64.dp).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(coffee.name, style = MaterialTheme.typography.titleMedium)
                                Text(coffee.roaster.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, enabled = !addState.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = roastDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { roastDate = picker.selectedDateMillis; showDatePicker = false }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun PackSwitch(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
