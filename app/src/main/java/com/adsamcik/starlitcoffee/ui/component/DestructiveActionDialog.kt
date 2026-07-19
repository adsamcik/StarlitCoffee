package com.adsamcik.starlitcoffee.ui.component

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R

@Composable
fun DestructiveActionDialog(
    @StringRes titleRes: Int,
    @StringRes confirmLabelRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes messageRes: Int = R.string.dialog_delete_brew_message,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.testTag("confirm_destructive_action"),
            ) {
                Text(
                    text = stringResource(confirmLabelRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.testTag("cancel_destructive_action"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag("destructive_action_dialog"),
    )
}
