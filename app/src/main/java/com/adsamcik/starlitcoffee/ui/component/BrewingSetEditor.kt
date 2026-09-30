package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

@Composable
fun BrewingSetEditor(
    set: BrewingSet,
    isNew: Boolean,
    grinderData: GrinderDataProvider,
    isSaving: Boolean,
    saveFailed: Boolean = false,
    onSave: (BrewingSet) -> Unit,
    onDismiss: () -> Unit,
) {
    val originalMethodName = set.method.localizedDisplayName()
    var name by rememberSaveable(set.id) { mutableStateOf(set.name ?: if (isNew) "" else originalMethodName) }
    var methodName by rememberSaveable(set.id) { mutableStateOf(set.method.name) }
    var filterName by rememberSaveable(set.id) { mutableStateOf(set.setup.filterType) }
    var grinderId by rememberSaveable(set.id) { mutableStateOf(set.setup.grinderId) }
    val method = BrewMethod.valueOf(methodName)
    val filter = filterName?.let(FilterType::valueOf).takeIf { method == BrewMethod.PULSAR }
    val grinders = grinderData.grindersFor(method, filter)
    val effectiveGrinder = grinderId?.takeIf { id -> grinders.any { it.id == id } }
    val dismiss = { if (!isSaving) onDismiss() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(if (isNew) R.string.label_new_brewing_set else R.string.label_edit_brewing_set)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true, enabled = !isSaving,
                    label = { Text(stringResource(R.string.label_name)) },
                    placeholder = { Text(stringResource(R.string.hint_brewing_set_name)) },
                    modifier = Modifier.fillMaxWidth().testTag("brewing_set_name"),
                )
                SetChoiceDropdown(
                    label = stringResource(R.string.label_brewing_set_method),
                    value = method.localizedDisplayName(), enabled = !isSaving, tag = "brewing_set_method",
                    options = BrewMethod.entries.map { it.name to it.localizedDisplayName() },
                    onSelect = { selected ->
                        if (selected != methodName) {
                            methodName = requireNotNull(selected)
                            filterName = FilterType.PAPER.name.takeIf { selected == BrewMethod.PULSAR.name }
                        }
                    },
                )
                if (method == BrewMethod.PULSAR) {
                    SetChoiceDropdown(
                        label = stringResource(R.string.label_filter),
                        value = filter?.localizedDisplayName() ?: stringResource(R.string.label_none),
                        enabled = !isSaving, tag = "brewing_set_filter",
                        options = listOf(null to stringResource(R.string.label_none)) +
                            FilterType.entries.map { it.name to it.localizedDisplayName() },
                        onSelect = { filterName = it },
                    )
                }
                if (grinders.isNotEmpty()) {
                    val none = stringResource(R.string.label_no_grinder)
                    SetChoiceDropdown(
                        label = stringResource(R.string.label_your_grinder),
                        value = grinders.find { it.id == effectiveGrinder }?.let { "${it.brand} ${it.model}" } ?: none,
                        enabled = !isSaving, tag = "brewing_set_grinder",
                        options = listOf(null to none) + grinders.map { it.id to "${it.brand} ${it.model}" },
                        onSelect = { grinderId = it },
                    )
                }
                if (saveFailed) {
                    Text(stringResource(R.string.msg_settings_save_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !isSaving, onClick = {
                val setup = if (method == set.method) set.setup else CalculatorSetup(ratio = method.defaultRatio)
                onSave(set.copy(name = name.trim(), method = method,
                    setup = setup.copy(filterType = filter?.name, grinderId = effectiveGrinder)))
            }, modifier = Modifier.testTag("save_brewing_set")) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SetChoiceDropdown(
    label: String,
    value: String,
    enabled: Boolean,
    tag: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false },
                    modifier = Modifier.testTag("${tag}_option_${id ?: "none"}"))
            }
        }
    }
}
