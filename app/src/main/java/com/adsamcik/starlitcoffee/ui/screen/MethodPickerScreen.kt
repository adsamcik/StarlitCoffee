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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.ui.component.BrewPreviewCard
import com.adsamcik.starlitcoffee.ui.component.ChipEmphasis
import com.adsamcik.starlitcoffee.ui.component.FreshnessRing
import com.adsamcik.starlitcoffee.ui.component.InsightChip
import com.adsamcik.starlitcoffee.ui.component.InsightChipRow
import com.adsamcik.starlitcoffee.ui.component.PostBrewCheckInCard
import com.adsamcik.starlitcoffee.ui.component.RatioPresetRow
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.data.model.QuickRating
import com.adsamcik.starlitcoffee.viewmodel.GrindResult
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.RankedBagSuggestion
import com.adsamcik.starlitcoffee.ui.util.shortLabel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodPickerScreen(
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateToSettings: () -> Unit,
    onNavigateToTimer: () -> Unit,
){
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val brewLogs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val flavorTags by brewViewModel.flavorTags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val lastUnratedBrew by brewViewModel.lastUnratedBrew.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )

    val activeBags = bags.filter { it.status != "FINISHED" }
    val rankedBags = remember(activeBags, brewLogs, flavorTags, state.coffeeG) {
        CoffeeBagInsights.rankBagsForBrew(
            bags = activeBags,
            brewLogs = brewLogs,
            flavorTags = flavorTags,
            targetDoseG = state.coffeeG.takeIf { it > 0f } ?: 20f,
        )
    }
    val selectedRankedBag = remember(rankedBags, selectedBagId) {
        rankedBags.find { it.bag.id == selectedBagId }
    }
    val selectedBag = selectedRankedBag?.bag
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.method.displayName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = state.filterType?.displayName ?: "No filter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("settings_button")) {
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
                        modifier = Modifier.testTag("method_chip_${method.name}"),
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

        // Input mode selector
        val inputMode = state.inputMode
        val amountLabel = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> "Coffee"
            InputMode.WATER_TO_COFFEE -> "Water"
            InputMode.BREW_SIZE_TO_BOTH -> "Brew size"
            InputMode.CUP_SIZE_TO_BOTH -> "Cup size"
        }
        val amountUnit = when (inputMode) {
            InputMode.COFFEE_TO_WATER, InputMode.WATER_TO_COFFEE -> "g"
            InputMode.BREW_SIZE_TO_BOTH, InputMode.CUP_SIZE_TO_BOTH -> "ml"
        }
        val sliderMax = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> maxSlider
            else -> 1000f
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            InputMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = inputMode == mode,
                    onClick = { brewViewModel.setInputMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = InputMode.entries.size,
                    ),
                ) {
                    Text(mode.shortLabel(), maxLines = 1)
                }
            }
        }

        // Amount slider
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = amountFloat.coerceIn(0f, sliderMax),
                onValueChange = {
                    val rounded = kotlin.math.round(it)
                    brewViewModel.setAmount(rounded.toInt().toString())
                },
                valueRange = 0f..sliderMax,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coffee_slider")
                    .semantics { contentDescription = "$amountLabel: ${amountFloat.toInt()} $amountUnit" },
            )
            Text(
                text = "${amountFloat.toInt()}$amountUnit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Strength presets
        if (state.ratioPresets.isNotEmpty()) {
            Text(
                text = "Strength",
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
                    val bagLabel = selectedRankedBag?.let { option ->
                        val decafLabel = if (option.bag.isDecaf) " · Decaf" else ""
                        val weightHint = option.bag.weightG?.let { w ->
                            " · ${"%.0f".format(w)}g left"
                        } ?: ""
                        "☕ ${option.bag.name}$decafLabel · ${option.freshness.phase.displayName}$weightHint"
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

            val topRecommendation = rankedBags.firstOrNull()
            if (selectedRankedBag != null) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FreshnessRing(
                            insight = selectedRankedBag.freshness,
                            size = 68.dp,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = "Freshness",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = selectedRankedBag.freshness.coachText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            selectedRankedBag.grindInsight.adjustmentHint?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            if (selectedRankedBag.sensorySnapshot.topChips.isNotEmpty()) {
                                InsightChipRow(
                                    chips = selectedRankedBag.sensorySnapshot.topChips,
                                    modifier = Modifier.padding(top = 12.dp),
                                    maxVisible = 4,
                                )
                            }
                        }
                    }
                }
            } else if (topRecommendation != null) {
                Text(
                    text = "Recommended now: ${topRecommendation.bag.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
        }

        // Grind preview — plain language for casual users, detail for grinder owners
        val grindText = when (val gr = state.grindResult) {
            is GrindResult.Generic ->
                "Grind: ${gr.descriptor.displayName} – ${gr.descriptor.visualCue}"
            is GrindResult.Specific ->
                "Grind setting: ${"%.1f".format(gr.recommendation.suggestedStart)} · Adjust by taste"
        }
        val bagGrindHint = selectedRankedBag?.grindInsight?.bestGrindSetting ?: selectedBag?.grindSetting
        ElevatedCard(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = grindText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (bagGrindHint != null) {
                    Text(
                        text = "Last time: $bagGrindHint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Start Brewing button
        Button(
            onClick = onNavigateToTimer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_brewing_button"),
        ) {
            Text("Start Brewing →", style = MaterialTheme.typography.labelLarge)
        }

        // Post-brew check-in card
        val unratedBrew = lastUnratedBrew
        if (unratedBrew != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PostBrewCheckInCard(
                brew = unratedBrew,
                onQuickRate = { rating ->
                    brewViewModel.quickRateBrewLog(
                        logId = unratedBrew.id,
                        rating = rating.starRating,
                        tasteFeedback = rating.tasteFeedback,
                    )
                },
                onIssueRate = { issue ->
                    brewViewModel.quickRateBrewLog(
                        logId = unratedBrew.id,
                        rating = QuickRating.NOT_GREAT.starRating,
                        tasteFeedback = issue.tasteFeedback,
                    )
                },
            )
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Select Coffee Bag",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (rankedBags.isEmpty()) {
                    Text(
                        text = "No active bags. Add one in the Bags tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else {
                    rankedBags.forEachIndexed { index, option ->
                        BagPickerOptionCard(
                            option = option,
                            isRecommended = index == 0,
                            onSelect = {
                                brewViewModel.selectBag(option.bag.id)
                                showBagPicker = false
                            },
                        )
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

@Composable
private fun BagPickerOptionCard(
    option: RankedBagSuggestion,
    isRecommended: Boolean,
    onSelect: () -> Unit,
) {
    ElevatedCard(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FreshnessRing(
                insight = option.freshness,
                size = 58.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.bag.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (option.bag.isDecaf) {
                        InsightChip(
                            label = "Decaf",
                            emphasis = ChipEmphasis.NEUTRAL,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (isRecommended) {
                        InsightChip(
                            label = "Best now",
                            emphasis = ChipEmphasis.POSITIVE,
                        )
                    }
                }
                option.bag.roaster?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = option.freshness.coachText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                InsightChipRow(
                    chips = option.reasons,
                    modifier = Modifier.padding(top = 12.dp),
                    maxVisible = 3,
                )
                if (option.sensorySnapshot.topChips.isNotEmpty()) {
                    InsightChipRow(
                        chips = option.sensorySnapshot.topChips,
                        modifier = Modifier.padding(top = 8.dp),
                        maxVisible = 3,
                    )
                }
            }
        }
    }
}
