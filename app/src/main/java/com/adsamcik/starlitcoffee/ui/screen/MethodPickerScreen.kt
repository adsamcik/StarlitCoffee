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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.ui.component.BrewPreviewCard
import com.adsamcik.starlitcoffee.ui.component.ChipEmphasis
import com.adsamcik.starlitcoffee.ui.component.FreshnessRing
import com.adsamcik.starlitcoffee.ui.component.InsightChip
import com.adsamcik.starlitcoffee.ui.component.InsightChipRow
import com.adsamcik.starlitcoffee.ui.component.PostBrewCheckInCard
import com.adsamcik.starlitcoffee.ui.component.HomeContextCardView
import com.adsamcik.starlitcoffee.ui.component.FavoritesRow
import com.adsamcik.starlitcoffee.ui.component.SaveFavoriteDialog
import com.adsamcik.starlitcoffee.ui.component.RatioPresetRow
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.data.model.QuickRating
import com.adsamcik.starlitcoffee.viewmodel.GrindResult
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.RankedBagSuggestion
import com.adsamcik.starlitcoffee.ui.util.shortLabelRes
import com.adsamcik.starlitcoffee.ui.util.descriptionRes
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
    val contextCard by brewViewModel.homeContextCard.collectAsStateWithLifecycle()
    val savedRecipes by brewViewModel.savedRecipes.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )

    var showSaveFavoriteDialog by remember { mutableStateOf(false) }

    val activeBags = bags.filter { it.status != "FINISHED" }
    val rankedBags = remember(activeBags, brewLogs, flavorTags, state.coffeeG) {
        CoffeeBagInsights.rankBagsForBrew(
            bags = activeBags,
            brewLogs = brewLogs,
            flavorTags = flavorTags,
            targetDoseG = state.coffeeG.takeIf { it > 0f } ?: 20f,
        )
    }
    // Stable partition: when brewing decaf, matching bags float up, but score order is
    // preserved within each section. Ranking logic stays honest (no hidden score bumps).
    val rankedBagsForPicker = remember(rankedBags, state.isDecafBrew) {
        if (state.isDecafBrew) {
            val (match, rest) = rankedBags.partition { it.bag.isDecaf }
            match + rest
        } else {
            val (match, rest) = rankedBags.partition { !it.bag.isDecaf }
            match + rest
        }
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
                    text = stringResource(R.string.screen_method_picker_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val noFilterLabel = stringResource(R.string.label_no_filter)
                val decafSuffix = stringResource(R.string.label_decaf_suffix)
                Text(
                    text = buildString {
                        append(state.filterType?.displayName ?: noFilterLabel)
                        if (state.isDecafBrew) append(decafSuffix)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val settingsLabel = stringResource(R.string.label_settings)
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("settings_button")) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = settingsLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Favorites row
        if (savedRecipes.isNotEmpty()) {
            FavoritesRow(
                recipes = savedRecipes,
                onTap = { recipe ->
                    brewViewModel.loadRecipe(recipe)
                },
                preferDecaf = state.isDecafBrew,
                matchLabel = { recipe ->
                    val bag = selectedBag
                    if (bag != null && recipe.isDecaf == bag.isDecaf && recipe.isDecaf == state.isDecafBrew) {
                        "matches bag"
                    } else {
                        null
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
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
            InputMode.COFFEE_TO_WATER -> stringResource(R.string.label_coffee)
            InputMode.WATER_TO_COFFEE -> stringResource(R.string.label_water)
            InputMode.BREW_SIZE_TO_BOTH -> stringResource(R.string.label_brew_size)
            InputMode.CUP_SIZE_TO_BOTH -> stringResource(R.string.label_cup_size)
        }
        val amountUnit = when (inputMode) {
            InputMode.COFFEE_TO_WATER, InputMode.WATER_TO_COFFEE -> stringResource(R.string.unit_grams)
            InputMode.BREW_SIZE_TO_BOTH, InputMode.CUP_SIZE_TO_BOTH -> stringResource(R.string.unit_ml)
        }
        val sliderMax = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> maxSlider
            else -> 1000f
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
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
                    Text(stringResource(mode.shortLabelRes()), maxLines = 1)
                }
            }
        }
        Text(
            text = stringResource(inputMode.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )

        // Amount slider with stepper buttons
        val stepSize = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> 1f
            else -> 10f
        }
        val displayValue = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> if (amountFloat % 1f != 0f) "${"%.1f".format(amountFloat)}$amountUnit" else "${amountFloat.toInt()}$amountUnit"
            else -> "${amountFloat.toInt()}$amountUnit"
        }
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        val amountCd = stringResource(R.string.format_amount_cd, amountLabel, amountFloat.toInt(), amountUnit)
        Slider(
            value = amountFloat.coerceIn(0f, sliderMax),
            onValueChange = {
                val rounded = kotlin.math.round(it / stepSize) * stepSize
                brewViewModel.setAmount(
                    if (stepSize < 1f) "%.1f".format(rounded)
                    else rounded.toInt().toString()
                )
            },
            valueRange = 0f..sliderMax,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("coffee_slider")
                .semantics { contentDescription = amountCd },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val decreaseCd = stringResource(R.string.label_decrease, amountLabel)
            val increaseCd = stringResource(R.string.label_increase, amountLabel)
            FilledTonalIconButton(
                onClick = {
                    val newVal = (amountFloat - stepSize).coerceAtLeast(0f)
                    brewViewModel.setAmount(
                        if (stepSize < 1f) "%.1f".format(newVal)
                        else newVal.toInt().toString()
                    )
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = decreaseCd)
            }
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            FilledTonalIconButton(
                onClick = {
                    val newVal = (amountFloat + stepSize).coerceAtMost(sliderMax)
                    brewViewModel.setAmount(
                        if (stepSize < 1f) "%.1f".format(newVal)
                        else newVal.toInt().toString()
                    )
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = increaseCd)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Strength presets
        if (state.ratioPresets.isNotEmpty()) {
            Text(
                text = stringResource(R.string.label_strength),
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
            bloomG = state.bloomG,
            timeTargetLowS = state.timeTargetLowS,
            timeTargetHighS = state.timeTargetHighS,
            predictedCupVolumeG = state.predictedCupVolumeG,
            retainedWaterG = state.retainedWaterG,
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

        // Decaf mismatch warning: user has explicitly toggled decaf to a state that
        // doesn't match the selected bag. Offer one-tap resolution.
        if (state.decafMismatchWithBag) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("decaf_mismatch_warning"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bagIsDecaf = selectedBag?.isDecaf == true
                    val msg = if (state.isDecafBrew && !bagIsDecaf) {
                        "Brewing as decaf but selected bag is regular"
                    } else {
                        "Brewing as regular but selected bag is decaf"
                    }
                    Text(
                        text = "⚠️ $msg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { brewViewModel.syncDecafToBag() },
                        modifier = Modifier.testTag("sync_decaf_button"),
                    ) {
                        Text(stringResource(R.string.action_sync_to_bag), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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
                    val selectCoffeeBagLabel = stringResource(R.string.label_select_coffee_bag)
                    val bagLabel = selectedRankedBag?.let { option ->
                        val decafLabel = if (option.bag.isDecaf) stringResource(R.string.label_decaf_suffix) else ""
                        val weightHint = option.bag.weightG?.let { w ->
                            stringResource(R.string.format_weight_left, w)
                        } ?: ""
                        stringResource(R.string.format_bag_chip, option.bag.name, decafLabel, option.freshness.phase.displayName, weightHint)
                    } ?: selectCoffeeBagLabel
                    Text(
                        text = bagLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                trailingIcon = if (selectedBag != null) {
                    {
                        val clearBagCd = stringResource(R.string.cd_clear_bag)
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = clearBagCd,
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
                                text = stringResource(R.string.label_freshness),
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
                    text = stringResource(R.string.format_recommended_now, topRecommendation.bag.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
        }

        // Grind preview — plain language for casual users, detail for grinder owners
        val grindText = when (val gr = state.grindResult) {
            is GrindResult.Generic ->
                stringResource(R.string.format_grind_generic, gr.descriptor.displayName, gr.descriptor.visualCue)
            is GrindResult.Specific ->
                stringResource(R.string.format_grind_specific_range, "%.1f".format(gr.recommendation.rangeStart), "%.1f".format(gr.recommendation.rangeEnd))
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
                        text = stringResource(R.string.format_last_time, bagGrindHint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Start Brewing
        Button(
            onClick = onNavigateToTimer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_brewing_button"),
        ) {
            Text(stringResource(R.string.action_start_brewing), style = MaterialTheme.typography.labelLarge)
        }

        // Save as Favorite — separate action to avoid misclicks
        TextButton(
            onClick = { showSaveFavoriteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag("save_favorite_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
            )
            Text(stringResource(R.string.action_save_as_favorite))
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
        } else {
            // Context card — shows when no unrated brews
            val card = contextCard
            if (card != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HomeContextCardView(card = card)
            } else {
                // Brew tip card — fills empty space with useful guidance
                Spacer(modifier = Modifier.height(16.dp))
                BrewTipCard(method = state.method)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Bag picker bottom sheet
    if (showBagPicker) {
        ModalBottomSheet(
            onDismissRequest = { showBagPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_select_coffee_bag_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (rankedBags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_active_bags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else {
                    rankedBagsForPicker.forEachIndexed { index, option ->
                        BagPickerOptionCard(
                            option = option,
                            // "Best now" label reflects the overall top pick, not section order,
                            // so mark it only for whichever bag is actually rank-0 in rankedBags.
                            isRecommended = option.bag.id == rankedBags.firstOrNull()?.bag?.id,
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
                    Text(stringResource(R.string.action_brew_without_bag))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Save Favorite dialog
    if (showSaveFavoriteDialog) {
        val suggestedName = selectedBag?.name ?: ""
        SaveFavoriteDialog(
            suggestedName = suggestedName,
            onSave = { name ->
                brewViewModel.saveRecipe(name)
                showSaveFavoriteDialog = false
            },
            onDismiss = { showSaveFavoriteDialog = false },
        )
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
                            label = stringResource(R.string.label_decaf),
                            emphasis = ChipEmphasis.NEUTRAL,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (isRecommended) {
                        InsightChip(
                            label = stringResource(R.string.label_best_now),
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

@Composable
private fun BrewTipCard(method: BrewMethod) {
    val tip = when (method) {
        BrewMethod.PULSAR -> stringResource(R.string.tip_pulsar)
        else -> stringResource(R.string.tip_generic)
    }
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}
