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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.navigation.AmountStrength
import com.adsamcik.starlitcoffee.navigation.Result
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
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Starlit Coffee",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 16.dp)
                .semantics { heading() },
        )

        if (state.coffeeG > 0f && state.waterG > 0f) {
            QuickBrewCard(state = state, navController = navController)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(BrewMethod.entries.toList()) { method ->
                ElevatedCard(
                    onClick = {
                        brewViewModel.setMethod(method)
                        navController.navigate(AmountStrength)
                    },
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
        }
    }
}

@Composable
private fun QuickBrewCard(
    state: BrewUiState,
    navController: NavController,
) {
    ElevatedCard(
        onClick = { navController.navigate(Result) },
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
                    text = "${state.method.displayName} · ${"%.0f".format(state.coffeeG)}g → ${"%.0f".format(state.waterG)}g · ${state.strengthPreset.displayName}",
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
