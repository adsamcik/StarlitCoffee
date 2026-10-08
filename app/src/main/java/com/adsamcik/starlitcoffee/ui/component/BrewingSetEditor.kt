package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewingSetEditor(
    set: BrewingSet,
    isNew: Boolean,
    grinderData: GrinderDataProvider,
    isSaving: Boolean,
    saveFailed: Boolean = false,
    onSave: (BrewingSet) -> Unit,
    onDismiss: () -> Unit,
    onSaveRecipe: ((BrewingSet) -> Unit)? = null,
) {
    var encodedDraft by rememberSaveable(set.id) { mutableStateOf(BrewingSetDraft(set).encode()) }
    val draft = BrewingSetDraft.decode(encodedDraft)
    val validSet = draft.build(grinderData)
    val dismiss = { if (!isSaving) onDismiss() }
    ModalBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().imePadding().testTag("brewing_set_editor")) {
            Text(stringResource(if (isNew) R.string.label_new_brewing_set else R.string.label_edit_brewing_set),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() })
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp)) {
                BrewingSetForm(draft, grinderData, !isSaving) { encodedDraft = it.encode() }
                if (saveFailed) {
                    Text(stringResource(R.string.msg_settings_save_failed), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = dismiss, enabled = !isSaving) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { validSet?.let { saved ->
                    val recipeChanged = saved.method == set.method && (
                        saved.setup.ratio != set.setup.ratio || saved.setup.quantity != set.setup.quantity ||
                            saved.setup.tokens != set.setup.tokens)
                    if (recipeChanged && onSaveRecipe != null) onSaveRecipe(saved) else onSave(saved)
                } }, enabled = validSet != null && !isSaving,
                    colors = primaryActionButtonColors(), modifier = Modifier.weight(1f).testTag("save_brewing_set")) {
                    Text(stringResource(if (isNew) R.string.action_create_brewing_set else R.string.action_save_simple))
                }
            }
        }
    }
}
