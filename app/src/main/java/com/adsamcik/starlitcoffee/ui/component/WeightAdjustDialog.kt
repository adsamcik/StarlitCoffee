package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R

@Composable
fun WeightAdjustDialog(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(currentWeight.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_adjust_weight_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_remaining_weight_grams)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toFloatOrNull()?.let(onConfirm) }) {
                Text(stringResource(R.string.action_save_simple))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
