package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.ui.component.PreparationCoffeeSelector
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

/** Confirm exact source equipment before preparing; physical time starts later, at its stated origin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewedGuideStartSheet(
    guide: ReviewedMethodGuide,
    brewViewModel: BrewViewModel,
    starting: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onStart: (Double?, Double?) -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    var confirmed by rememberSaveable(guide.id) { mutableStateOf(false) }
    var dose by rememberSaveable(guide.id) { mutableStateOf("") }
    var water by rememberSaveable(guide.id) { mutableStateOf("") }
    val measuredDose = dose.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val measuredWater = water.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    ModalBottomSheet(onDismissRequest = { if (!starting) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(guide.methodName, style = MaterialTheme.typography.headlineMedium)
            ReviewedGuideOverview(guide)
            PreparationCoffeeSelector(bags, bags.firstOrNull { it.id == selectedId }, state.isDecafBrew,
                onSelectBag = brewViewModel::selectBagForBrewing, onClearBag = { brewViewModel.selectBag(null) })
            if (guide.doseG == null) {
                OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.label_measured_coffee_g)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            if (guide.waterG == null && guide.waterMl == null && guide.yieldG == null) {
                OutlinedTextField(water, { water = it }, label = { Text(stringResource(R.string.label_optional_measured_water_g)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Row(Modifier.fillMaxWidth().toggleable(confirmed, enabled = !starting, role = Role.Checkbox,
                onValueChange = { confirmed = it })) {
                Checkbox(confirmed, onCheckedChange = null, enabled = !starting)
                Text(stringResource(R.string.msg_reviewed_equipment_confirm), modifier = Modifier.padding(top = 10.dp))
            }
            if (error) Text(stringResource(R.string.msg_brew_session_unavailable), color = MaterialTheme.colorScheme.error)
            Button(onClick = { onStart(measuredDose, measuredWater) },
                enabled = confirmed && !starting && (guide.doseG != null || measuredDose != null) &&
                    (water.isBlank() || measuredWater != null),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_prepare_this_guide))
            }
        }
    }
}
