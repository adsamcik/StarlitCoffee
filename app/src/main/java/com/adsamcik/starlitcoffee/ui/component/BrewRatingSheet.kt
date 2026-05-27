package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import com.adsamcik.starlitcoffee.ui.util.displayNameRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewRatingSheet(
    onDismiss: () -> Unit,
    onSave: (rating: Float, selectedDescriptors: List<String>, notes: String) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    var rating by remember { mutableFloatStateOf(0f) }
    var selectedDescriptors by remember { mutableStateOf(emptySet<FlavorDescriptor>()) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = stringResource(R.string.label_how_was_it),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_rate_brew_fresh),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Star rating
            HalfStarRatingRow(
                rating = rating,
                onRatingChange = { rating = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Flavor tags
            Text(
                text = stringResource(R.string.label_flavor_notes_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlavorTagPicker(
                selectedTags = selectedDescriptors,
                onTagToggle = { descriptor ->
                    selectedDescriptors = if (descriptor in selectedDescriptors) {
                        selectedDescriptors - descriptor
                    } else {
                        selectedDescriptors + descriptor
                    }
                },
            )
            if (selectedDescriptors.isNotEmpty()) {
                InsightChipRow(
                    chips = selectedDescriptors.map { stringResource(it.displayNameRes()) },
                    modifier = Modifier.padding(top = 12.dp),
                    maxVisible = 4,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = {
                    Text(stringResource(R.string.hint_what_stood_out))
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            FilledTonalButton(
                onClick = {
                    onSave(
                        rating,
                        selectedDescriptors.map { it.displayName },
                        notes,
                    )
                },
                enabled = rating > 0f,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.action_save_rating))
            }
        }
    }
}
