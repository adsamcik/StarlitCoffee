package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.calculator.formatCalculatorRatio
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

/** The existing favorites are also reusable home/work setups, picked without leaving Brew. */
@Composable
fun SavedSetupPicker(
    recipes: List<SavedRecipeEntity>,
    enabledMethods: Set<BrewMethod>,
    onSelect: (SavedRecipeEntity) -> Unit,
) {
    val available = recipes.filter { recipe -> enabledMethods.any { it.name == recipe.method } }
    if (available.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(R.string.label_your_favorites)) },
            leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
            modifier = Modifier.testTag("saved_setup_picker"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { recipe ->
                val method = BrewMethod.valueOf(recipe.method)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(recipe.coffeeName ?: stringResource(R.string.label_untitled))
                            Text(
                                stringResource(
                                    R.string.format_saved_setup_summary,
                                    method.localizedDisplayName(),
                                    formatCalculatorRatio(recipe.ratio),
                                    formatCalculatorRatio(recipe.doseG),
                                    stringResource(R.string.label_coffee),
                                    stringResource(R.string.unit_grams),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(recipe)
                        expanded = false
                    },
                    modifier = Modifier.testTag("saved_setup_${recipe.id}"),
                )
            }
        }
    }
}
