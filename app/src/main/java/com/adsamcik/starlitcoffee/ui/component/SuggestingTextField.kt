package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestion
import com.adsamcik.starlitcoffee.util.CoffeeInputSuggestionEngine

/**
 * Free-form text field with ranked history and library suggestions.
 * Suggestions never constrain the entered value; selecting one replaces the active
 * comma-separated token when [multiValue] is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<CoffeeInputSuggestion>,
    modifier: Modifier = Modifier,
    maxSuggestions: Int = 6,
    singleLine: Boolean = true,
    multiValue: Boolean = false,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    isError: Boolean = false,
    filled: Boolean = false,
    showLabel: Boolean = true,
    textStyle: TextStyle? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var fieldValueState by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    // The parent owns text; the local value retains cursor and IME composition.
    val fieldValue = if (fieldValueState.text == value) fieldValueState
        else TextFieldValue(value, selection = TextRange(value.length))
    SideEffect {
        if (fieldValue.selection != fieldValueState.selection || fieldValue.composition != fieldValueState.composition) {
            fieldValueState = fieldValue
        }
    }
    val rankedSuggestions by remember(
        fieldValue.text,
        fieldValue.selection,
        suggestions,
        maxSuggestions,
        multiValue,
    ) {
        derivedStateOf {
            CoffeeInputSuggestionEngine.rank(
                input = fieldValue.text,
                suggestions = suggestions,
                maxResults = maxSuggestions,
                multiValue = multiValue,
                cursorIndex = fieldValue.selection.end,
            ).filter {
                CoffeeInputSuggestionEngine.accept(
                    currentValue = fieldValue.text,
                    suggestion = it,
                    multiValue = multiValue,
                    cursorIndex = fieldValue.selection.end,
                ) != fieldValue.text
            }
        }
    }
    val showSuggestions = enabled && hasFocus && menuExpanded && rankedSuggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = showSuggestions,
        onExpandedChange = { expanded ->
            if (enabled && hasFocus) menuExpanded = expanded
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled && rankedSuggestions.isNotEmpty())
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) menuExpanded = true
                onFocusChanged(it.isFocused)
            }
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label })
        val fieldLabel: (@Composable () -> Unit)? = if (showLabel) ({ Text(label) }) else null
        val fieldTrailingIcon: (@Composable () -> Unit)? = when {
            isError -> ({ Icon(Icons.Outlined.WarningAmber, contentDescription = null) })
            rankedSuggestions.isNotEmpty() -> ({ ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSuggestions) })
            else -> null
        }
        if (filled) TextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValueState = updated
                menuExpanded = true
                onValueChange(updated.text)
            },
            label = fieldLabel,
            textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            trailingIcon = fieldTrailingIcon,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
            ),
            modifier = fieldModifier,
        ) else OutlinedTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValueState = updated
                menuExpanded = true
                onValueChange(updated.text)
            },
            label = fieldLabel,
            textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            trailingIcon = fieldTrailingIcon,
            modifier = fieldModifier,
        )

        ExposedDropdownMenu(
            expanded = showSuggestions,
            onDismissRequest = { menuExpanded = false },
        ) {
            for (suggestion in rankedSuggestions) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    enabled = enabled,
                    onClick = {
                        val accepted = CoffeeInputSuggestionEngine.acceptWithSelection(
                            currentValue = fieldValue.text,
                            suggestion = suggestion,
                            multiValue = multiValue,
                            cursorIndex = fieldValue.selection.end,
                        )
                        fieldValueState = TextFieldValue(
                            text = accepted.value,
                            selection = TextRange(accepted.cursorIndex),
                        )
                        menuExpanded = false
                        onValueChange(accepted.value)
                    },
                )
            }
        }
    }
}
