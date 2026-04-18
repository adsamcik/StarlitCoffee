package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.SwipeToDismissCard
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedRecipesScreen(
    brewViewModel: BrewViewModel,
    onNavigateToAmount: () -> Unit,
    onBack: () -> Unit,
){
    val recipes by brewViewModel.savedRecipes.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var decafFilter by remember { mutableStateOf(com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL) }
    val decafCounts = remember(recipes) {
        mapOf(
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL to recipes.size,
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR to recipes.count { !it.isDecaf },
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF to recipes.count { it.isDecaf },
        )
    }
    val showDecafFilter = (decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF] ?: 0) > 0 &&
        (decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR] ?: 0) > 0
    val filteredRecipes = remember(recipes, decafFilter) {
        recipes.filter { decafFilter.matches(it.isDecaf) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenTopBar(title = stringResource(R.string.screen_favorites_title), onBack = onBack)

            if (recipes.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Filled.Bookmark,
                    message = stringResource(R.string.msg_no_favorites_title),
                    subtitle = stringResource(R.string.msg_no_favorites_subtitle),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 16.dp,
                        bottom = 88.dp,
                    ),
                ) {
                    if (showDecafFilter) {
                        item {
                            com.adsamcik.starlitcoffee.ui.component.DecafFilterChipRow(
                                selected = decafFilter,
                                counts = decafCounts,
                                onSelected = { decafFilter = it },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(filteredRecipes, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            dateFormat = dateFormat,
                            onTap = {
                                brewViewModel.loadRecipe(recipe)
                                onNavigateToAmount()
                            },
                            onDelete = { brewViewModel.deleteRecipe(recipe) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: SavedRecipeEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val decafSuffix = stringResource(R.string.label_decaf_suffix)

    SwipeToDismissCard(onDismiss = onDelete) {
        ElevatedCard(
            onClick = onTap,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().testTag("recipe_card_${recipe.id}"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.coffeeName ?: stringResource(R.string.label_untitled),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append(recipe.method)
                            if (recipe.isDecaf) append(decafSuffix)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.format_recipe_summary, "%.0f".format(recipe.doseG), "%.0f".format(recipe.waterG)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = dateFormat.format(Date(recipe.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
