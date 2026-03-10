package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Text field with history-based autocomplete suggestions.
 * Shows matching recent values when focused; filters as the user types.
 * When no text is entered, shows all [suggestions] (capped at [maxSuggestions]).
 */
@Composable
fun SuggestingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    maxSuggestions: Int = 5,
    singleLine: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var hasFocus by remember { mutableStateOf(false) }
    val filtered by remember(value, suggestions) {
        derivedStateOf {
            if (value.isBlank()) {
                suggestions.take(maxSuggestions)
            } else {
                suggestions.filter {
                    it.contains(value, ignoreCase = true) &&
                        !it.equals(value, ignoreCase = true)
                }.take(maxSuggestions)
            }
        }
    }
    val showSuggestions = hasFocus && filtered.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            shape = MaterialTheme.shapes.small,
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    hasFocus = it.isFocused
                    onFocusChanged(it.isFocused)
                },
        )

        AnimatedVisibility(visible = showSuggestions) {
            Surface(
                shape = MaterialTheme.shapes.small,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Column {
                    for (suggestion in filtered) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        if (suggestion != filtered.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
