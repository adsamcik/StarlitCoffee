package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrindPrepScreen(
    brewViewModel: BrewViewModel,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_grind_prep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Grind Recommendation Card
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.label_grind),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (val gr = state.grindResult) {
                        is GrindResult.Generic -> {
                            Text(
                                text = "${gr.descriptor.displayName} – ${gr.descriptor.visualCue}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        is GrindResult.Specific -> {
                            val rec = gr.recommendation
                            Text(
                                text = "Setting: ${rec.suggestedStart} (range ${rec.rangeStart}–${rec.rangeEnd})",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rec.adjustmentNote} · Adjust by ±${rec.adjustmentStepSize} to taste",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dose + water targets together — tells the user what to prepare
            Text(
                text = stringResource(R.string.format_grind_coffee, state.coffeeG),
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.waterG > 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                val method = state.method
                val tempSuffix = if (method.tempRangeLow > 0 && method.tempRangeHigh > 0) {
                    " · ${method.tempRangeLow}–${method.tempRangeHigh}°C"
                } else ""
                Text(
                    text = "Heat ${"%.0f".format(state.waterG)}g water$tempSuffix",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Brew Prep Checklist
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val steps = when {
                    state.method == BrewMethod.PULSAR -> listOf(
                        stringResource(R.string.checklist_pulsar_filter),
                        stringResource(R.string.checklist_pulsar_barrel),
                        stringResource(R.string.checklist_pulsar_coffee),
                        stringResource(R.string.checklist_pulsar_cap),
                        stringResource(R.string.checklist_tare_scale),
                    )

                    state.method.hasBloom -> listOf(
                        stringResource(R.string.checklist_v60_filter),
                        stringResource(R.string.checklist_pulsar_coffee),
                        stringResource(R.string.checklist_tare_scale),
                    )

                    else -> listOf(
                        stringResource(R.string.checklist_prepare_brewer),
                        stringResource(R.string.checklist_add_coffee),
                        stringResource(R.string.checklist_tare_scale),
                    )
                }

                steps.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Water temperature is now shown inline with the dose/water target above

            Spacer(modifier = Modifier.height(24.dp))

            // Ready to Brew Button
            Button(
                onClick = { onNavigateToBrew() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.action_ready_to_brew),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
