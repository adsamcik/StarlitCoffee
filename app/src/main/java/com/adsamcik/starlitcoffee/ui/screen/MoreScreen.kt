package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onNavigateToRecipes: () -> Unit,
    onNavigateToBags: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "More",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                    .semantics { heading() },
            )

            MoreItem(
                icon = Icons.Filled.Bookmark,
                title = "Your Favorites",
                subtitle = "Replay your favourite brews",
                onClick = onNavigateToRecipes,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MoreItem(
                icon = Icons.Filled.ShoppingBag,
                title = "Your Beans",
                subtitle = "Freshness, stock, and what to brew next",
                onClick = onNavigateToBags,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MoreItem(
                icon = Icons.Filled.Settings,
                title = "Settings",
                subtitle = "Methods, grinder and preferences",
                onClick = onNavigateToSettings,
            )
        }
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
