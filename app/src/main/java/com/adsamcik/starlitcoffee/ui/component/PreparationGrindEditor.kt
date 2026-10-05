package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource
import com.adsamcik.starlitcoffee.data.model.GrindInputError
import com.adsamcik.starlitcoffee.viewmodel.PreparedGrind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreparationGrindEditor(
    prepared: PreparedGrind,
    grinderName: String,
    methodName: String,
    selectedBag: CoffeeBagEntity?,
    isDecaf: Boolean,
    onSave: (String, GrindSaveScope) -> Unit,
    onReset: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(grinderName, methodName, selectedBag?.id, isDecaf) { mutableStateOf(prepared.inputText) }
    var scope by rememberSaveable(grinderName, methodName, selectedBag?.id, isDecaf) { mutableStateOf(GrindSaveScope.BREW) }
    var expanded by rememberSaveable(grinderName, methodName, selectedBag?.id, isDecaf) { mutableStateOf(false) }
    val openingRevision = rememberSaveable(grinderName, methodName, selectedBag?.id, isDecaf) { prepared.saveRevision }
    LaunchedEffect(prepared.saveRevision) {
        if (prepared.saveRevision > openingRevision && prepared.error == null) onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = { if (!prepared.isSaving) onDismiss() },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp).testTag("prep_grind_editor"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.prep_grind_editor_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "$grinderName · $methodName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onClearError() },
                label = { Text(stringResource(R.string.prep_grind_setting)) },
                singleLine = true,
                enabled = !prepared.isSaving,
                isError = prepared.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().testTag("prep_grind_input"),
            )
            if (text == prepared.inputText) {
                Text(
                    preparationGrindSource(prepared.source, isDecaf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preparationSavedGrindExplanation(prepared)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                HorizontalDivider()
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("prep_grind_scope_disclosure"),
                ) {
                    Text(stringResource(R.string.prep_grind_use_for), modifier = Modifier.weight(1f))
                    Text(preparationGrindScope(scope, isDecaf), modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.selectableGroup()) {
                        prepared.availableScopes.forEach { option ->
                            PreparationGrindScopeOption(
                                option = option,
                                isSelected = scope == option,
                                isDecaf = isDecaf,
                                selectedBag = selectedBag,
                                enabled = !prepared.isSaving,
                                onSelect = { scope = option },
                            )
                        }
                        Text(
                            stringResource(R.string.prep_grind_scope_context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
            val retainsNarrower = when (scope) {
                GrindSaveScope.TYPE -> prepared.source in setOf(GrindSettingSource.PACK, GrindSettingSource.COFFEE)
                GrindSaveScope.COFFEE -> prepared.source == GrindSettingSource.PACK
                else -> false
            }
            if (retainsNarrower) {
                Text(
                    stringResource(R.string.prep_grind_scope_preserve),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (prepared.canReset) {
                TextButton(onClick = onReset, enabled = !prepared.isSaving, modifier = Modifier.fillMaxWidth().testTag("prep_grind_reset")) {
                    Text(preparationGrindReset(prepared.source, isDecaf))
                }
            }
            prepared.error?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            GrindInputError.INVALID_FORMAT -> R.string.prep_grind_invalid_format
                            GrindInputError.OUT_OF_RANGE -> R.string.prep_grind_out_of_range
                            GrindInputError.SAVE_FAILED -> R.string.prep_grind_save_failed
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("prep_grind_error"),
                )
            }
            Button(
                onClick = { onSave(text, scope) },
                enabled = !prepared.isSaving && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("prep_grind_save"),
                colors = primaryActionButtonColors(),
            ) {
                Text(stringResource(R.string.prep_grind_use_setting))
            }
        }
    }
}

@Composable
private fun PreparationGrindScopeOption(
    option: GrindSaveScope,
    isSelected: Boolean,
    isDecaf: Boolean,
    selectedBag: CoffeeBagEntity?,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("prep_grind_scope_${option.name.lowercase()}")
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = isSelected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preparationGrindScope(option, isDecaf), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val hint = when (option) {
                GrindSaveScope.BREW -> stringResource(R.string.prep_grind_scope_brew_hint)
                GrindSaveScope.COFFEE -> stringResource(R.string.prep_grind_scope_coffee_hint)
                GrindSaveScope.TYPE -> stringResource(R.string.prep_grind_scope_type_hint)
                GrindSaveScope.PACK -> selectedBag?.let { "${it.name} · ${stringResource(R.string.prep_pack_number, it.packNumber)}" }.orEmpty()
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun preparationGrindSource(source: GrindSettingSource, isDecaf: Boolean): String = stringResource(
    when (source) {
        GrindSettingSource.BREW -> R.string.prep_grind_source_brew
        GrindSettingSource.PACK -> R.string.prep_grind_source_pack
        GrindSettingSource.COFFEE -> R.string.prep_grind_source_coffee
        GrindSettingSource.TYPE -> if (isDecaf) R.string.prep_grind_source_decaf else R.string.prep_grind_source_regular
        GrindSettingSource.RECOMMENDATION, GrindSettingSource.GENERIC -> R.string.prep_grind_source_recommendation
    },
)

@Composable
internal fun preparationSavedGrindExplanation(prepared: PreparedGrind): String? = prepared.rotations?.let { rotations ->
    val clicks = prepared.clicks ?: 0
    if (clicks == 0) {
        stringResource(R.string.prep_grind_no_extra_clicks, rotations)
    } else {
        stringResource(R.string.prep_grind_dial_breakdown, rotations, pluralStringResource(R.plurals.prep_clicks, clicks, clicks))
    }
}

@Composable
private fun preparationGrindScope(scope: GrindSaveScope, isDecaf: Boolean): String = stringResource(
    when (scope) {
        GrindSaveScope.BREW -> R.string.prep_grind_source_brew
        GrindSaveScope.PACK -> R.string.prep_grind_scope_pack
        GrindSaveScope.COFFEE -> R.string.prep_grind_scope_coffee
        GrindSaveScope.TYPE -> if (isDecaf) R.string.prep_grind_scope_decaf else R.string.prep_grind_scope_regular
    },
)

@Composable
private fun preparationGrindReset(source: GrindSettingSource, isDecaf: Boolean): String = stringResource(
    when (source) {
        GrindSettingSource.BREW -> R.string.prep_grind_reset_brew
        GrindSettingSource.PACK -> R.string.prep_grind_reset_pack
        GrindSettingSource.COFFEE -> R.string.prep_grind_reset_coffee
        GrindSettingSource.TYPE -> if (isDecaf) R.string.prep_grind_reset_decaf else R.string.prep_grind_reset_regular
        else -> R.string.prep_grind_reset_brew
    },
)
