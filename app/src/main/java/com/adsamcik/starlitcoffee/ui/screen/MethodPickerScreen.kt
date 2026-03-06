package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.component.BrewPreviewCard
import com.adsamcik.starlitcoffee.ui.component.RatioPresetRow
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.viewmodel.GrindResult
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodPickerScreen(
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateToAmount: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTimer: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
){
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )

    val activeBags = bags.filter { it.status != "FINISHED" }
    val selectedBag = activeBags.find { it.id == selectedBagId }
    var showBagPicker by remember { mutableStateOf(false) }

    val enabledMethods = BrewMethod.entries.filter { prefs.enabledMethods.contains(it) }
    val multipleMethodsEnabled = enabledMethods.size > 1

    val amountFloat = state.amount.toFloatOrNull() ?: 0f
    val maxSlider = run {
        val singleFillDose = state.method.capacityMaxG?.let { it.toFloat() / state.method.defaultRatio }
        if (singleFillDose != null) (singleFillDose * 3f).coerceIn(30f, 100f) else 50f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 8.dp),
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
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Method selector — only when multiple methods enabled
        if (multipleMethodsEnabled) {
            val otherMethods = BrewMethod.entries.filter { !prefs.enabledMethods.contains(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                enabledMethods.forEach { method ->
                    FilterChip(
                        selected = state.method == method,
                        onClick = { brewViewModel.setMethod(method) },
                        label = { Text(method.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = iconForMethod(method),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
                otherMethods.forEach { method ->
                    FilterChip(
                        selected = state.method == method,
                        onClick = { brewViewModel.setMethod(method) },
                        label = {
                            Text(
                                text = method.displayName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        // Method context line
        Row(
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconForMethod(state.method),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.method.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.filterType != null) {
                Text(
                    text = " · ${state.filterType?.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Amount slider
        Text(
            text = "Coffee",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = amountFloat.coerceIn(0f, maxSlider),
                onValueChange = {
                    val rounded = kotlin.math.round(it)
                    brewViewModel.setAmount(rounded.toInt().toString())
                },
                valueRange = 0f..maxSlider,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${amountFloat.toInt()}g",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ratio presets
        if (state.ratioPresets.isNotEmpty()) {
            Text(
                text = "Ratio",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            RatioPresetRow(
                presets = state.ratioPresets,
                selectedIndex = state.selectedPresetIndex,
                onSelectPreset = { brewViewModel.selectRatioPreset(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }

        // Live preview
        BrewPreviewCard(
            coffeeG = state.coffeeG,
            waterG = state.waterG,
            ratio = state.effectiveRatio,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Guardrail warning
        state.ratioWarning?.let { warning ->
            Text(
                text = "⚠️ $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Bag picker chip
        if (activeBags.isNotEmpty()) {
            FilterChip(
                selected = selectedBag != null,
                onClick = {
                    if (selectedBag != null) {
                        brewViewModel.selectBag(null)
                    } else {
                        showBagPicker = true
                    }
                },
                label = {
                    val bagLabel = selectedBag?.let { bag ->
                        val weightHint = bag.weightG?.let { w ->
                            " · ${"%.0f".format(w)}g left"
                        } ?: ""
                        "☕ ${bag.name}$weightHint"
                    } ?: "Select coffee bag"
                    Text(
                        text = bagLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                trailingIcon = if (selectedBag != null) {
                    {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear bag",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }

        // Grind preview
        val grindText = when (val gr = state.grindResult) {
            is GrindResult.Generic ->
                "${gr.descriptor.displayName} – ${gr.descriptor.visualCue}"
            is GrindResult.Specific ->
                "Setting: ${"%.1f".format(gr.recommendation.suggestedStart)} (${gr.recommendation.adjustmentNote})"
        }
        val bagGrindHint = selectedBag?.grindSetting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚙️ $grindText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (bagGrindHint != null) {
                Text(
                    text = "☕ Dialed in: $bagGrindHint",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // Start Brewing button
        FilledTonalButton(
            onClick = onNavigateToTimer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start Brewing →", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Customize link → full AmountStrength screen
        TextButton(
            onClick = onNavigateToAmount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Customize brew parameters")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Bag picker bottom sheet
    if (showBagPicker) {
        ModalBottomSheet(
            onDismissRequest = { showBagPicker = false },
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Select Coffee Bag",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (activeBags.isEmpty()) {
                    Text(
                        text = "No active bags. Add one in the Bags tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else {
                    activeBags.forEach { bag ->
                        ElevatedCard(
                            onClick = {
                                brewViewModel.selectBag(bag.id)
                                showBagPicker = false
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bag.name, style = MaterialTheme.typography.titleMedium)
                                    bag.roaster?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                bag.weightG?.let { w ->
                                    Text(
                                        "${"%.0f".format(w)}g",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                // None option
                TextButton(
                    onClick = {
                        brewViewModel.selectBag(null)
                        showBagPicker = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Brew without selecting a bag")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}