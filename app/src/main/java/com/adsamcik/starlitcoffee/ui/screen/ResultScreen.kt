package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.navigation.BrewTimer
import com.adsamcik.starlitcoffee.navigation.MethodPicker
import com.adsamcik.starlitcoffee.ui.component.BrewGuide
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

@Composable
fun ResultScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val method = uiState.method

    var showSaveDialog by remember { mutableStateOf(false) }
    var recipeName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Core result card
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "☕ Coffee: ${"%.1f".format(uiState.coffeeG)}g",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "💧 Water: ${"%.0f".format(uiState.waterG)}g",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ratio 1:${"%.1f".format(uiState.effectiveRatio)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Refill info (timer handles refills automatically)
        if (uiState.refillCount > 0) {
            WarningCard(
                message = "💧 This brew requires ${uiState.refillCount} refill${if (uiState.refillCount > 1) "s" else ""} — the timer will guide you through ${if (uiState.refillCount > 1) "each" else "it"}",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                showIcon = false,
            )
        }

        // Ratio guardrail warning
        uiState.ratioWarning?.let { warning ->
            WarningCard(message = warning)
        }

        // Bloom guardrail warning
        uiState.bloomWarning?.let { warning ->
            WarningCard(message = warning)
        }

        // Interactive brew guide (Pulsar only)
        if (method == BrewMethod.PULSAR && uiState.timerPhases.isNotEmpty()) {
            BrewGuide(
                phases = uiState.timerPhases,
                coffeeG = uiState.coffeeG,
                waterG = uiState.waterG,
                capacityMaxG = method.capacityMaxG?.toFloat(),
                refillCount = uiState.refillCount,
            )
        }

        // Brew breakdown card
        if (method.hasBloom || method.hasPulses) {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Brew Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (method.hasBloom) {
                        val effectiveBloomMult =
                            uiState.bloomMultiplier.toFloatOrNull() ?: method.bloomMultiplier
                        Text(
                            text = "Bloom: ${"%.0f".format(uiState.bloomG)}g (${effectiveBloomMult}× dose)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (method == BrewMethod.PULSAR) {
                            Text(
                                text = "↳ Valve open → pour → close valve → steep 45–60s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                            )
                        }
                        Text(
                            text = "Remaining: ${"%.0f".format(uiState.remainingWaterG)}g",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (method.hasPulses && uiState.effectivePulseCount > 0) {
                        Text(
                            text = "Pulses: ${uiState.effectivePulseCount} × ${"%.0f".format(uiState.pulseSizeG)}g",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (method == BrewMethod.PULSAR) {
                            Text(
                                text = "↳ Keep slurry ~1cm above bed · open valve",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    val lowMin = uiState.timeTargetLowS / 60
                    val lowSec = uiState.timeTargetLowS % 60
                    val highMin = uiState.timeTargetHighS / 60
                    val highSec = uiState.timeTargetHighS % 60
                    Text(
                        text = "Target time: %d:%02d – %d:%02d".format(lowMin, lowSec, highMin, highSec),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Pulsar-specific tips (collapsible, default collapsed)
            if (method == BrewMethod.PULSAR) {
                var tipsExpanded by remember { mutableStateOf(false) }
                ElevatedCard(
                    onClick = { tipsExpanded = !tipsExpanded },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "☕ Pulsar Tips",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(
                                imageVector = if (tipsExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = if (tipsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        AnimatedVisibility(visible = tipsExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                val tips = listOf(
                                    "No gooseneck needed — dispersion cap distributes water evenly",
                                    "Level the bed before brewing (shake or WDT tool)",
                                    "Short bloom → clarity & acidity · Long bloom → body & sweetness",
                                    "Gentle swirl after last pour (hold by the base!)",
                                    "Very fresh coffee? Try a double bloom to release extra CO₂",
                                    "Dose 20–25g recommended for best bed depth",
                                )
                                tips.forEach { tip ->
                                    Text(
                                        text = "• $tip",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grind section
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Grind",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val grindText = when (val gr = uiState.grindResult) {
                    is GrindResult.Generic ->
                        "${gr.descriptor.displayName} – ${gr.descriptor.visualCue}"
                    is GrindResult.Specific ->
                        "Setting: ${"%.1f".format(gr.recommendation.suggestedStart)} " +
                            "(${gr.recommendation.adjustmentNote})"
                }
                Text(
                    text = grindText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { navController.navigate(BrewTimer) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text("Start Timer", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { showSaveDialog = true },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text("Save Recipe", style = MaterialTheme.typography.labelLarge)
            }
        }

        TextButton(
            onClick = {
                brewViewModel.resetBrew()
                navController.navigate(MethodPicker) {
                    popUpTo(MethodPicker) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start Over", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Recipe") },
            text = {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    label = { Text("Recipe name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    brewViewModel.saveRecipe(recipeName.takeIf { it.isNotBlank() })
                    showSaveDialog = false
                    recipeName = ""
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
