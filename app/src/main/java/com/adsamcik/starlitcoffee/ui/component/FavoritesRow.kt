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
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity

@Composable
fun FavoritesRow(
    recipes: List<SavedRecipeEntity>,
    onTap: (SavedRecipeEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recipes.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Your favorites",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recipes.forEach { recipe ->
                FilterChip(
                    selected = false,
                    onClick = { onTap(recipe) },
                    label = {
                        Text(
                            text = recipe.coffeeName ?: "Untitled",
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
