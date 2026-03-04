package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.navigation.AmountStrength
import com.adsamcik.starlitcoffee.navigation.BrewTimer
import com.adsamcik.starlitcoffee.navigation.Settings
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

private fun iconForMethod(method: BrewMethod): ImageVector = when (method) {
    BrewMethod.PULSAR -> Icons.Filled.FilterDrama
    BrewMethod.V60 -> Icons.Filled.FilterList
    BrewMethod.FRENCH_PRESS -> Icons.Filled.Coffee
    BrewMethod.AEROPRESS -> Icons.Filled.Air
    BrewMethod.ESPRESSO -> Icons.Filled.LocalCafe
    BrewMethod.MOKA_POT -> Icons.Filled.OutdoorGrill
    BrewMethod.COLD_BREW -> Icons.Filled.AcUnit
}

@Composable
fun MethodPickerScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )

    val enabledMethods = BrewMethod.entries.filter { prefs.enabledMethods.contains(it) }
    val otherMethods = BrewMethod.entries.filter { !prefs.enabledMethods.contains(it) }
    var showOther by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Starlit Coffee",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = { navController.navigate(Settings) }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.coffeeG > 0f && state.waterG > 0f) {
            QuickBrewCard(state = state, navController = navController)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(enabledMethods) { method ->
                MethodCard(method = method) {
                    brewViewModel.setMethod(method)
                    navController.navigate(AmountStrength)
                }
            }

            if (otherMethods.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    TextButton(
                        onClick = { showOther = !showOther },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Other brew methods")
                        Icon(
                            imageVector = if (showOther) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (showOther) {
                    items(otherMethods) { method ->
                        MethodCard(method = method) {
                            brewViewModel.setMethod(method)
                            navController.navigate(AmountStrength)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodCard(
    method: BrewMethod,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = iconForMethod(method),
                contentDescription = method.displayName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = method.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "1:${method.defaultRatio.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickBrewCard(
    state: BrewUiState,
    navController: NavController,
) {
    ElevatedCard(
        onClick = { navController.navigate(BrewTimer) },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconForMethod(state.method),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "Quick Brew",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${state.method.displayName} · ${"%.0f".format(state.coffeeG)}g → ${"%.0f".format(state.waterG)}g · 1:${state.effectiveRatio.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go to brew result",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}