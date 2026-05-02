package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.SaveFavoriteDialog
import com.adsamcik.starlitcoffee.ui.util.presetIcon
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorBrewScreen(
    calculatorViewModel: CalculatorViewModel,
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateToBrew: () -> Unit,
) {
    val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val brewState by brewViewModel.uiState.collectAsStateWithLifecycle()

    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )

    // Brew config (method/filter/grinder) is owned by BrewViewModel — its
    // `applyUserDefaults()` already seeds these from prefs at VM init. Keep
    // local read-only aliases for ergonomics; mutations go directly to the VM.
    val selectedMethod = brewState.method
    val selectedFilter = brewState.filterType
    val selectedGrinderId = brewState.selectedGrinderId

    val context = LocalContext.current
    val grinders = remember { GrinderDataSource.getInstance(context).grinders }

    val coffeeBags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val selectedBag = remember(coffeeBags, selectedBagId) {
        coffeeBags.find { it.id == selectedBagId }
    }

    var showSaveFavoriteDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Calc-side derived values (ratio, dose) need to land on BrewViewModel
    // before downstream actions that snapshot the brew state (save recipe,
    // start brew). Method/filter/grinder are already in the VM via direct
    // chip handlers, so only the calc-derived fields need explicit syncing.
    val syncCalcDerivedState: () -> Unit = {
        brewViewModel.setCustomRatio(state.ratio.toString())
        brewViewModel.setAmount(state.previewDoseG.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Top section: direction toggle + save favorite
        TopControlBar(
            direction = state.inputDirection,
            canSaveFavorite = state.hasValidExpression && state.previewDoseG > 0f,
            onToggleDirection = { calculatorViewModel.toggleDirection() },
            onSaveFavorite = {
                syncCalcDerivedState()
                showSaveFavoriteDialog = true
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Expression display
        ExpressionDisplay(
            tokens = state.tokens,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable content: preview + config
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            // Live preview card
            LivePreviewCard(
                doseG = state.previewDoseG,
                waterMl = state.previewWaterMl,
                direction = state.inputDirection,
                method = selectedMethod,
            )

            // Selected bag indicator — visible reminder that a bag is in play,
            // with one-tap clear so the user can switch to brewing without one.
            selectedBag?.let { bag ->
                Spacer(modifier = Modifier.height(12.dp))
                InputChip(
                    selected = true,
                    onClick = { brewViewModel.selectBag(null) },
                    label = {
                        Text(
                            text = stringResource(R.string.format_brewing_with, bag.name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LocalCafe,
                            contentDescription = null,
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_clear_bag),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }

        }

        // Pills bar — quick-access brew settings above the keyboard, within
        // thumb reach while the user is entering numbers. Replaces the older
        // expandable config card.
        Spacer(modifier = Modifier.height(12.dp))

        BrewSettingsPillBar(
            enabledMethods = prefs.enabledMethods.toList(),
            selectedMethod = selectedMethod,
            selectedFilter = selectedFilter,
            selectedGrinderId = selectedGrinderId,
            grinders = grinders,
            ratio = state.ratio,
            onMethodChange = { brewViewModel.setMethod(it) },
            onFilterChange = { brewViewModel.setFilterType(it) },
            onGrinderChange = { brewViewModel.setGrinder(it) },
            onRatioChange = { calculatorViewModel.setRatio(it) },
        )

        // Calculator keyboard — pinned to bottom, outside scroll
        Spacer(modifier = Modifier.height(8.dp))

        CalculatorKeyboard(
            presets = state.availablePresets,
            hasValidExpression = state.hasValidExpression,
            onDigit = { calculatorViewModel.appendDigit(it) },
            onDecimal = { calculatorViewModel.appendDecimal() },
            onOperator = { calculatorViewModel.appendOperator(it) },
            onPreset = { calculatorViewModel.appendPreset(it) },
            onBackspace = { calculatorViewModel.backspace() },
            onClear = { calculatorViewModel.clear() },
            onBrew = {
                syncCalcDerivedState()
                onNavigateToBrew()
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showSaveFavoriteDialog) {
        SaveFavoriteDialog(
            suggestedName = "",
            onSave = { name ->
                brewViewModel.saveRecipe(name)
                showSaveFavoriteDialog = false
            },
            onDismiss = { showSaveFavoriteDialog = false },
        )
    }
}

@Composable
private fun TopControlBar(
    direction: InputDirection,
    canSaveFavorite: Boolean,
    onToggleDirection: () -> Unit,
    onSaveFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Direction toggle
        FilledTonalButton(
            onClick = onToggleDirection,
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.cd_switch_direction),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (direction) {
                    InputDirection.WATER -> stringResource(R.string.label_water_to_dose)
                    InputDirection.DOSE -> stringResource(R.string.label_dose_to_water)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Save-as-favorite — ratio moved to the pill bar above the keyboard
        IconButton(
            onClick = onSaveFavorite,
            enabled = canSaveFavorite,
            modifier = Modifier.testTag("save_favorite_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.action_save_as_favorite),
                tint = if (canSaveFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ExpressionDisplay(
    tokens: List<CalcToken>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .height(72.dp)
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (tokens.isEmpty()) {
            Text(
                text = "0",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tokens.forEach { token ->
                    when (token) {
                        is CalcToken.Number -> Text(
                            text = token.value,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        is CalcToken.Operator -> Text(
                            text = token.op.symbol,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        is CalcToken.PresetRef -> {
                            val icon = presetIcon(token.preset.iconName)
                            val tint = token.preset.colorHex?.let {
                                try { Color(android.graphics.Color.parseColor(it)) } catch (_: IllegalArgumentException) { null }
                            } ?: MaterialTheme.colorScheme.tertiary
                            Icon(
                                imageVector = icon,
                                contentDescription = token.preset.name,
                                modifier = Modifier.size(36.dp),
                                tint = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePreviewCard(
    doseG: Float,
    waterMl: Float,
    direction: InputDirection,
    method: BrewMethod = BrewMethod.PULSAR,
) {
    val bloomG = if (method.hasBloom && doseG > 0f) doseG * method.bloomMultiplier else 0f
    val remainingWaterG = if (bloomG > 0f) (waterMl - bloomG).coerceAtLeast(0f) else 0f

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Coffee dose
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.LocalCafe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = formatAmount(doseG),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "dose",
                ) { dose ->
                    Text(
                        text = dose,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (direction == InputDirection.DOSE) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.label_coffee),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp),
            )

            // Water
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = formatAmount(waterMl),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "water",
                ) { water ->
                    Text(
                        text = water,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (direction == InputDirection.WATER) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.label_total_water),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Bloom + remaining water breakdown (only for methods with bloom)
        AnimatedVisibility(visible = bloomG > 0f && waterMl > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatAmount(bloomG),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = stringResource(R.string.label_bloom_lowercase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatAmount(remainingWaterG),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = stringResource(R.string.label_remaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrewSettingsPillBar(
    enabledMethods: List<BrewMethod>,
    selectedMethod: BrewMethod,
    selectedFilter: FilterType?,
    selectedGrinderId: String?,
    grinders: List<Grinder>,
    ratio: Float,
    onMethodChange: (BrewMethod) -> Unit,
    onFilterChange: (FilterType?) -> Unit,
    onGrinderChange: (String?) -> Unit,
    onRatioChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Method pill — only when more than one method is enabled in Settings.
        if (enabledMethods.size > 1) {
            PillDropdown(
                label = selectedMethod.displayName,
                options = enabledMethods.map { method ->
                    PillOption(
                        label = method.displayName,
                        selected = method == selectedMethod,
                        onClick = { onMethodChange(method) },
                    )
                },
            )
        }

        // Ratio pill — always visible. Options match the legacy ratio dialog.
        val ratioOptions = listOf(2f, 8f, 10f, 15f, 16f, 17f, 18f)
        PillDropdown(
            label = "1:${ratio.toInt()}",
            options = ratioOptions.map { value ->
                PillOption(
                    label = "1:${value.toInt()}",
                    selected = value == ratio,
                    onClick = { onRatioChange(value) },
                )
            },
        )

        // Filter pill — Pulsar only (FilterType is a Pulsar-specific concept).
        if (selectedMethod == BrewMethod.PULSAR) {
            val noFilterLabel = stringResource(R.string.label_no_filter)
            val pillLabel = selectedFilter?.displayName ?: noFilterLabel
            PillDropdown(
                label = pillLabel,
                options = buildList {
                    add(
                        PillOption(
                            label = noFilterLabel,
                            selected = selectedFilter == null,
                            onClick = { onFilterChange(null) },
                        ),
                    )
                    FilterType.entries.forEach { filter ->
                        add(
                            PillOption(
                                label = filter.displayName,
                                selected = filter == selectedFilter,
                                onClick = { onFilterChange(filter) },
                            ),
                        )
                    }
                },
            )
        }

        // Grinder pill — only when grinder data is available.
        if (grinders.isNotEmpty()) {
            val noGrinderLabel = stringResource(R.string.label_none)
            val selectedGrinder = grinders.find { it.id == selectedGrinderId }
            val pillLabel = selectedGrinder?.let { g ->
                if (g.brand == g.model) g.model else "${g.brand} ${g.model}"
            } ?: noGrinderLabel
            PillDropdown(
                label = pillLabel,
                options = buildList {
                    add(
                        PillOption(
                            label = noGrinderLabel,
                            selected = selectedGrinderId == null,
                            onClick = { onGrinderChange(null) },
                        ),
                    )
                    grinders.forEach { g ->
                        val gLabel = if (g.brand == g.model) g.model else "${g.brand} ${g.model}"
                        add(
                            PillOption(
                                label = gLabel,
                                selected = g.id == selectedGrinderId,
                                onClick = { onGrinderChange(g.id) },
                            ),
                        )
                    }
                },
            )
        }
    }
}

private data class PillOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PillDropdown(
    label: String,
    options: List<PillOption>,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PillDropdownArrow",
    )
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(AssistChipDefaults.IconSize)
                        .rotate(arrowRotation),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (expanded) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                labelColor = if (expanded) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                trailingIconContentColor = if (expanded) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
            border = if (expanded) {
                null
            } else {
                AssistChipDefaults.assistChipBorder(enabled = true)
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .widthIn(min = 160.dp),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt.label,
                            fontWeight = if (opt.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (opt.selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        opt.onClick()
                        expanded = false
                    },
                    leadingIcon = if (opt.selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (opt.selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun CalculatorKeyboard(
    presets: List<CupPreset>,
    hasValidExpression: Boolean,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (CalcOp) -> Unit,
    onPreset: (CupPreset) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onBrew: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: Preset buttons + backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val schemeContainers = listOf(
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
            )
            presets.take(5).forEachIndexed { index, preset ->
                val customColor = preset.colorHex?.let {
                    try { Color(android.graphics.Color.parseColor(it)) } catch (_: IllegalArgumentException) { null }
                }
                FilledTonalIconButton(
                    onClick = { onPreset(preset) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = if (customColor != null) {
                        val contentColor = if (customColor.luminance() > 0.5f) Color.Black else Color.White
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = customColor,
                            contentColor = contentColor,
                        )
                    } else {
                        val (container, content) = schemeContainers[index % schemeContainers.size]
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = container,
                            contentColor = content,
                        )
                    },
                ) {
                    Icon(
                        imageVector = presetIcon(preset.iconName),
                        contentDescription = preset.name,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // Fill remaining space if fewer than 5 presets
            repeat((5 - presets.take(5).size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.weight(1f))
            }
            // Backspace button
            FilledTonalIconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Visual breath between the preset/utility row and the calculation rows.
        Spacer(modifier = Modifier.height(2.dp))

        // Rows 2-5: Number pad + operators + brew button
        // Layout: 3 number columns + 1 action column
        val keyHeight = 56.dp

        // Row 2: 7 8 9 ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("7", Modifier.weight(1f).height(keyHeight)) { onDigit('7') }
            CalcKey("8", Modifier.weight(1f).height(keyHeight)) { onDigit('8') }
            CalcKey("9", Modifier.weight(1f).height(keyHeight)) { onDigit('9') }
            OperatorKey("×", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.MULTIPLY) }
        }

        // Row 3: 4 5 6 +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("4", Modifier.weight(1f).height(keyHeight)) { onDigit('4') }
            CalcKey("5", Modifier.weight(1f).height(keyHeight)) { onDigit('5') }
            CalcKey("6", Modifier.weight(1f).height(keyHeight)) { onDigit('6') }
            OperatorKey("+", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.ADD) }
        }

        // Row 4: 1 2 3 [Brew top half]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("1", Modifier.weight(1f).height(keyHeight)) { onDigit('1') }
            CalcKey("2", Modifier.weight(1f).height(keyHeight)) { onDigit('2') }
            CalcKey("3", Modifier.weight(1f).height(keyHeight)) { onDigit('3') }
            BrewKey(
                enabled = hasValidExpression,
                modifier = Modifier.weight(1f).height(keyHeight),
                onClick = onBrew,
            )
        }

        // Row 5: 0 (wide) . C
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("0", Modifier.weight(2f).height(keyHeight)) { onDigit('0') }
            CalcKey(".", Modifier.weight(1f).height(keyHeight)) { onDecimal() }
            ClearKey(Modifier.weight(1f).height(keyHeight)) { onClear() }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OperatorKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BrewKey(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = stringResource(R.string.action_brew),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ClearKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = "C",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun formatAmount(value: Float): String {
    return if (value == 0f) {
        "—"
    } else if (value == value.toInt().toFloat()) {
        "${value.toInt()}g"
    } else {
        "%.1fg".format(value)
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
