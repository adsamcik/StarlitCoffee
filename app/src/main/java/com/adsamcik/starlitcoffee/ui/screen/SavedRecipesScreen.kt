package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.SwipeToDismissCard
import com.adsamcik.starlitcoffee.ui.component.normalizedForCounts
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun normalizeSavedRecipeDecafFilter(
    selected: DecafFilter,
    regularCount: Int,
    decafCount: Int,
): DecafFilter = selected.normalizedForCounts(regularCount, decafCount)

@Composable
fun SavedRecipesScreen(
    brewViewModel: BrewViewModel,
    onNavigateToAmount: () -> Unit,
    onBack: () -> Unit,
){
    val recipes by brewViewModel.savedRecipes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var decafFilter by remember { mutableStateOf(DecafFilter.ALL) }
    val decafCounts = remember(recipes) {
        mapOf(
            DecafFilter.ALL to recipes.size,
            DecafFilter.REGULAR to recipes.count { !it.isDecaf },
            DecafFilter.DECAF to recipes.count { it.isDecaf },
        )
    }
    val effectiveDecafFilter = normalizeSavedRecipeDecafFilter(
        selected = decafFilter,
        regularCount = decafCounts[DecafFilter.REGULAR] ?: 0,
        decafCount = decafCounts[DecafFilter.DECAF] ?: 0,
    )
    val showDecafFilter = (decafCounts[DecafFilter.DECAF] ?: 0) > 0 &&
        (decafCounts[DecafFilter.REGULAR] ?: 0) > 0
    val filteredRecipes = remember(recipes, effectiveDecafFilter) {
        recipes.filter { effectiveDecafFilter.matches(it.isDecaf) }
    }
    var pendingDelete by remember { mutableStateOf<SavedRecipeEntity?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(effectiveDecafFilter) {
        if (decafFilter != effectiveDecafFilter) decafFilter = effectiveDecafFilter
    }

    pendingDelete?.let { recipe ->
        DestructiveActionDialog(
            titleRes = R.string.action_delete,
            confirmLabelRes = R.string.action_delete,
            onConfirm = {
                if (!isDeleting) {
                    isDeleting = true
                    scope.launch {
                        try {
                            check(brewViewModel.deleteRecipeAndWait(recipe)) {
                                "Recipe repository is unavailable"
                            }
                            pendingDelete = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                            Log.e("SavedRecipesScreen", "Failed to delete recipe", error)
                            Toast.makeText(context, R.string.msg_could_not_delete, Toast.LENGTH_LONG).show()
                        } finally {
                            isDeleting = false
                        }
                    }
                }
            },
            onDismiss = { if (!isDeleting) pendingDelete = null },
            enabled = !isDeleting,
        )
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
                                selected = effectiveDecafFilter,
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
                            onDelete = { pendingDelete = recipe },
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
    val method = remember(recipe.method) {
        runCatching { BrewMethod.valueOf(recipe.method) }.getOrNull()
    }
    val methodLabel = method?.localizedDisplayName()
        ?: recipe.method.lowercase().replaceFirstChar { it.uppercase() }

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
                            append(methodLabel)
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
