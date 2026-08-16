package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.adsamcik.starlitcoffee.R
import java.util.Locale

internal enum class CoffeeUseInputError {
    INVALID_AMOUNT,
    EXCEEDS_REMAINING,
}

internal data class CoffeeUseInputValidation(
    val amountG: Float? = null,
    val error: CoffeeUseInputError? = null,
) {
    val isValid: Boolean
        get() = amountG != null && error == null
}

internal fun validateCoffeeUseInput(
    text: String,
    remainingG: Float?,
): CoffeeUseInputValidation {
    val amount = text.trim().replace(',', '.').toFloatOrNull()
    if (amount == null || !amount.isFinite() || amount <= 0f) {
        return CoffeeUseInputValidation(error = CoffeeUseInputError.INVALID_AMOUNT)
    }
    if (remainingG != null && amount > remainingG + COFFEE_WEIGHT_EPSILON_G) {
        return CoffeeUseInputValidation(error = CoffeeUseInputError.EXCEEDS_REMAINING)
    }
    return CoffeeUseInputValidation(amountG = amount)
}

@Composable
fun LogCoffeeUseDialog(
    suggestedAmountG: Float,
    remainingG: Float?,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember(suggestedAmountG) {
        mutableStateOf(formatCoffeeAmountForInput(suggestedAmountG))
    }
    val validation = validateCoffeeUseInput(text, remainingG)
    val submit: () -> Unit = {
        val amountG = validation.amountG
        if (validation.isValid && amountG != null) {
            onConfirm(amountG)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_log_coffee_used)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_coffee_used_amount)) },
                singleLine = true,
                isError = validation.error != null,
                supportingText = {
                    Text(
                        when (validation.error) {
                            CoffeeUseInputError.INVALID_AMOUNT ->
                                stringResource(R.string.msg_enter_valid_coffee_amount)
                            CoffeeUseInputError.EXCEEDS_REMAINING ->
                                stringResource(
                                    R.string.msg_coffee_use_exceeds_remaining,
                                    remainingG ?: 0f,
                                )
                            null -> remainingG?.let {
                                stringResource(R.string.format_coffee_remaining, it)
                            }.orEmpty()
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = androidx.compose.ui.Modifier.testTag("coffee_use_amount"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = validation.isValid,
                modifier = androidx.compose.ui.Modifier.testTag("coffee_use_confirm"),
            ) {
                Text(stringResource(R.string.action_log_coffee_used))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun formatCoffeeAmountForInput(amountG: Float): String {
    val whole = amountG.toInt()
    return if (amountG == whole.toFloat()) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.1f", amountG)
    }
}

private const val COFFEE_WEIGHT_EPSILON_G = 0.001f
