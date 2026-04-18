package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity

@Composable
fun FavoritesRow(
    recipes: List<SavedRecipeEntity>,
    onTap: (SavedRecipeEntity) -> Unit,
    modifier: Modifier = Modifier,
    preferDecaf: Boolean = false,
    matchLabel: ((SavedRecipeEntity) -> String?)? = null,
) {
    if (recipes.isEmpty()) return

    // Stable partition: when brewing decaf, float decaf recipes to the front. Never hide
    // non-matching recipes — users can still tap them; this is deprioritization only.
    val ordered = if (preferDecaf) {
        val (decaf, regular) = recipes.partition { it.isDecaf }
        decaf + regular
    } else {
        val (regular, decaf) = recipes.partition { !it.isDecaf }
        regular + decaf
    }

    val decafLabel = stringResource(R.string.label_decaf)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.label_your_favorites_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEach { recipe ->
                val suffix = matchLabel?.invoke(recipe)
                FilterChip(
                    selected = false,
                    onClick = { onTap(recipe) },
                    label = {
                        Text(
                            text = buildString {
                                append(recipe.coffeeName ?: "Untitled")
                                if (recipe.isDecaf) append(" · $decafLabel")
                                if (suffix != null) append(" · $suffix")
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}
