package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
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
) {
    var hasFocus by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
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
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                menuExpanded = true
                onValueChange(updated.text)
            },
            label = { Text(label) },
            shape = MaterialTheme.shapes.small,
            singleLine = singleLine,
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSuggestions)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .onFocusChanged {
                    hasFocus = it.isFocused
                    if (it.isFocused) menuExpanded = true
                    onFocusChanged(it.isFocused)
                },
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
                        fieldValue = TextFieldValue(
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
