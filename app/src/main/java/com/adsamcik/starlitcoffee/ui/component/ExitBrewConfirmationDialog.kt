package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R

/**
 * Confirmation prompt shown when the user tries to leave an active brew
 * (back button, close icon, or any other exit affordance). Confirmation
 * loses brew progress, so it's clearly destructive — the confirm button
 * uses the error color and matches the dialog's question shape elsewhere
 * in the app (see `dialog_delete_brew_*`).
 */
@Composable
fun ExitBrewConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_exit_brew_title)) },
        text = { Text(stringResource(R.string.dialog_exit_brew_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_exit_brew),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
