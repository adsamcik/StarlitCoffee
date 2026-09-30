package com.adsamcik.starlitcoffee.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.selection.selectableGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

/** The same adaptive, illustrated choices are used for the first set and later edits. */
@Composable
fun MethodChoiceGrid(selected: BrewMethod?, enabled: Boolean = true, tagPrefix: String, onSelect: (BrewMethod) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.6f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            BrewMethod.entries.chunked(columns).forEach { methods ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { method ->
                        val chosen = method == selected
                        Surface(onClick = { onSelect(method) }, enabled = enabled,
                            shape = RoundedCornerShape(if (chosen) 16.dp else 28.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).testTag("${tagPrefix}_${method.name}")
                                .semantics { this.selected = chosen; role = Role.RadioButton }) {
                            Column(Modifier.heightIn(min = 112.dp).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    EquipmentIcon(EquipmentVisual.method(method), Modifier.size(44.dp),
                                        tint = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (chosen) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                                }
                                Text(method.localizedDisplayName(), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrewingSetForm(
    draft: BrewingSetDraft,
    grinderData: GrinderDataProvider,
    enabled: Boolean,
    onChange: (BrewingSetDraft) -> Unit,
) {
    var pane by rememberSaveable(draft.set.id) { mutableStateOf<String?>(null) }
    var recipeExpanded by rememberSaveable(draft.set.id) { mutableStateOf(false) }
    val set = draft.set
    val filter = set.setup.filterType?.let(FilterType::valueOf).takeIf { set.method == BrewMethod.PULSAR }
    val grinders = grinderData.grindersFor(set.method, filter)
    val grinder = grinders.find { it.id == set.setup.grinderId }
    PredictiveBackHandler(enabled = pane != null && enabled) { progress ->
        progress.collect { }
        pane = null
    }
    val expression = draft.amount ?: set.setup.tokens.joinToString(" ") { token -> when (token) {
        is CalcToken.Number -> token.value
        is CalcToken.Operator -> token.op.symbol
        is CalcToken.PresetRef -> token.preset.name
    } }
    val quantityLabel = stringResource(when (set.setup.quantity) {
        CalculatorQuantityTarget.WATER_IN.name -> R.string.label_water_in
        CalculatorQuantityTarget.IN_CUP.name -> R.string.label_in_cup
        else -> R.string.label_coffee
    })
    val expressionHasGrams = draft.amount != null || set.setup.tokens.none { it is CalcToken.PresetRef }
    val recipeSummary = listOf(expression.takeIf { it.isNotBlank() }?.let {
        "$it${if (expressionHasGrams) " g" else ""} · $quantityLabel"
    }, "1:${draft.ratio}").filterNotNull().joinToString(" · ")
    val recipeAction = stringResource(if (recipeExpanded) R.string.cd_collapse_advanced else R.string.cd_expand_advanced)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (pane != null) {
            TextButton(onClick = { pane = null }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text(stringResource(R.string.action_back), Modifier.padding(start = 8.dp))
            }
            if (pane == "method") {
                FormHeading(stringResource(R.string.label_brewing_set_method))
                MethodChoiceGrid(set.method, enabled, "brewing_set_method_option") {
                    onChange(draft.withMethod(it)); pane = null
                }
            } else {
                FormHeading(stringResource(R.string.label_your_grinder))
                EquipmentChoiceRow(stringResource(R.string.label_no_grinder), null,
                    set.setup.grinderId == null || grinder == null, enabled, "brewing_set_grinder_option_none") {
                    onChange(draft.copy(set = set.copy(setup = set.setup.copy(grinderId = null)))); pane = null
                }
                grinders.forEach { option ->
                    EquipmentChoiceRow("${option.brand} ${option.model}", EquipmentVisual.grinder(option.id),
                        option.id == grinder?.id, enabled, "brewing_set_grinder_option_${option.id}") {
                        onChange(draft.copy(set = set.copy(setup = set.setup.copy(grinderId = option.id)))); pane = null
                    }
                }
            }
        } else {
            EquipmentDisclosureRow(stringResource(R.string.label_brewing_set_method), set.method.localizedDisplayName(),
                EquipmentVisual.method(set.method), enabled, "brewing_set_method") { pane = "method" }
            if (set.method == BrewMethod.PULSAR) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("brewing_set_filter")) {
                    FormHeading(stringResource(R.string.label_filter))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (listOf<FilterType?>(null) + FilterType.entries).forEach { option ->
                            FilterChip(selected = option == filter, enabled = enabled, onClick = {
                                onChange(draft.copy(set = set.copy(setup = set.setup.copy(filterType = option?.name))))
                            }, label = { Text(option?.localizedDisplayName() ?: stringResource(R.string.label_none)) },
                                leadingIcon = { EquipmentIcon(EquipmentVisual.filter(option), Modifier.size(24.dp)) },
                                modifier = Modifier.testTag("brewing_set_filter_option_${option?.name ?: "none"}"))
                        }
                    }
                }
            }
            if (grinders.isNotEmpty()) {
                EquipmentDisclosureRow(stringResource(R.string.label_your_grinder),
                    grinder?.let { "${it.brand} ${it.model}" } ?: stringResource(R.string.label_no_grinder),
                    EquipmentVisual.grinder(grinder?.id), enabled, "brewing_set_grinder") { pane = "grinder" }
            }
            OutlinedTextField(value = draft.name, onValueChange = { onChange(draft.copy(name = it.take(80))) },
                label = { Text(stringResource(R.string.label_brewing_set_name_optional)) },
                placeholder = { Text(stringResource(R.string.hint_brewing_set_name)) },
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("brewing_set_name"))
            Surface(onClick = { recipeExpanded = !recipeExpanded }, enabled = enabled,
                color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("brewing_set_recipe").semantics { stateDescription = recipeAction }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_starting_recipe), style = MaterialTheme.typography.titleSmall)
                        Text(recipeSummary, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ExpandMore, null)
                }
            }
            if (recipeExpanded) {
                OutlinedTextField(value = draft.ratio, onValueChange = { onChange(draft.copy(ratio = it.take(16))) },
                    label = { Text(stringResource(R.string.label_ratio)) }, prefix = { Text("1:") }, enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("brewing_set_ratio"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalculatorQuantityTarget.entries.filter { it.isAvailableFor(set.method) }.forEach { target ->
                        FilterChip(selected = target.name == set.setup.quantity, enabled = enabled, onClick = {
                            onChange(draft.copy(set = set.copy(setup = set.setup.copy(quantity = target.name))))
                        }, label = { Text(stringResource(when (target) {
                            CalculatorQuantityTarget.COFFEE -> R.string.label_coffee
                            CalculatorQuantityTarget.WATER_IN -> R.string.label_water_in
                            CalculatorQuantityTarget.IN_CUP -> R.string.label_in_cup
                        })) }, modifier = Modifier.testTag("brewing_set_quantity_${target.name}"))
                    }
                }
                if (draft.amount == null && set.setup.tokens.size > 1) {
                    Text(stringResource(R.string.msg_saved_brewing_calculation, expression),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = draft.amount ?: (set.setup.tokens.singleOrNull() as? CalcToken.Number)?.value.orEmpty(),
                    onValueChange = { onChange(draft.copy(amount = it.take(24))) },
                    label = { Text(stringResource(R.string.label_starting_amount)) }, suffix = { Text("g") }, enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("brewing_set_amount"))
            }
            if (draft.build(grinderData) == null) {
                Text(stringResource(R.string.msg_brewing_set_recipe_invalid), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FormHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun EquipmentDisclosureRow(label: String, value: String, visual: EquipmentVisual?, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EquipmentVisualBadge(visual)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun EquipmentChoiceRow(value: String, visual: EquipmentVisual?, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(if (selected) 16.dp else 8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { this.selected = selected; role = Role.RadioButton }) {
        Row(Modifier.heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EquipmentVisualBadge(visual, selected)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, null)
        }
    }
}
