package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.SaveFavoriteDialog
import com.adsamcik.starlitcoffee.ui.util.presetIcon
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import kotlinx.coroutines.delay

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorBrewScreen(
    calculatorViewModel: CalculatorViewModel,
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateToBrew: () -> Unit,
    onNavigateToSettings: () -> Unit,
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

    // Hoisted so we can drive the scroll position when the brew config card
    // expands — otherwise the newly-revealed chips push past the visible
    // viewport (the calc keyboard takes ~320dp at the bottom) and feel
    // "hidden behind the keyboard".
    val scrollState = rememberScrollState()
    var configExpanded by rememberSaveable { mutableStateOf(false) }
    // Tracks the previous expand state in the current composition scope (NOT
    // saveable). When the screen is restored from back-navigation with
    // configExpanded=true, this initializes to true on the same recomposition,
    // so `justExpanded` is false and we skip the auto-scroll. Only a real
    // user-driven false→true transition triggers the animation.
    var lastConfigExpanded by remember { mutableStateOf(configExpanded) }

    LaunchedEffect(configExpanded) {
        val justExpanded = configExpanded && !lastConfigExpanded
        lastConfigExpanded = configExpanded
        if (justExpanded) {
            // AnimatedVisibility's bouncy spring expand grows the content
            // over ~400ms; if we read scrollState.maxValue too early it
            // reflects only the partially-expanded height and we under-
            // scroll. Wait for maxValue to stabilize across two ticks
            // before animating to the final position. Bounded so we don't
            // hang if the layout never settles (e.g. ambient animations).
            var previousMax = -1
            var attempts = 0
            while (previousMax != scrollState.maxValue && attempts < 10) {
                previousMax = scrollState.maxValue
                delay(80)
                attempts++
            }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
        // Top section: direction toggle + save favorite + ratio chip
        TopControlBar(
            direction = state.inputDirection,
            ratio = state.ratio,
            canSaveFavorite = state.hasValidExpression && state.previewDoseG > 0f,
            onToggleDirection = { calculatorViewModel.toggleDirection() },
            onRatioChange = { calculatorViewModel.setRatio(it) },
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

            Spacer(modifier = Modifier.height(12.dp))

            // Brew config — compact summary, expandable to full config.
            // configExpanded + scrollState are hoisted to the function scope
            // so the screen-level LaunchedEffect can scroll the expand into
            // view when it opens.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Summary row — always visible, tappable to toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { configExpanded = !configExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedMethod.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (selectedFilter != null) {
                            Text(
                                text = "·",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = selectedFilter!!.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val selectedGrinder = grinders.find { it.id == selectedGrinderId }
                        if (selectedGrinder != null) {
                            Text(
                                text = "·",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = selectedGrinder.model,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (configExpanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (configExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Filter row — surfaced as the daily-relevant Pulsar control
                // so the user doesn't need to expand the card to switch
                // between paper / 19K / 40K. Hidden when method is not Pulsar
                // (FilterType is a Pulsar-specific concept).
                if (selectedMethod == BrewMethod.PULSAR) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = stringResource(R.string.label_filter),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterType.entries.forEach { filter ->
                                val isSelected = selectedFilter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { brewViewModel.setFilterType(if (isSelected) null else filter) },
                                    label = { Text(filter.displayName) },
                                    leadingIcon = if (isSelected) checkIcon else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Expanded config sections — method (if multiple enabled) and
                // grinder. Filter is surfaced above; method/grinder are
                // set-once equipment settings hidden behind the chevron.
                AnimatedVisibility(
                    visible = configExpanded,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                        shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Method
                        if (prefs.enabledMethods.size > 1) {
                            Text(
                                text = stringResource(R.string.label_method),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                prefs.enabledMethods.forEach { method ->
                                    val isSelected = selectedMethod == method
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { brewViewModel.setMethod(method) },
                                        label = { Text(method.displayName) },
                                        leadingIcon = if (isSelected) checkIcon else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    )
                                }
                            }
                        }

                        // Filter — surfaced above this expand block; see the
                        // always-visible filter row attached to the summary.

                        // Grinder
                        if (grinders.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.label_grinder),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(
                                    selected = selectedGrinderId == null,
                                    onClick = { brewViewModel.setGrinder(null) },
                                    label = { Text(stringResource(R.string.label_none)) },
                                    leadingIcon = if (selectedGrinderId == null) checkIcon else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                )
                                grinders.forEach { grinder ->
                                    val isSelected = selectedGrinderId == grinder.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { brewViewModel.setGrinder(grinder.id) },
                                        label = {
                                            Text(
                                                if (grinder.brand == grinder.model) grinder.model
                                                else "${grinder.brand} ${grinder.model}",
                                            )
                                        },
                                        leadingIcon = if (isSelected) checkIcon else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    )
                                }
                            }
                            // Grinders are equipment-level config that mostly
                            // belongs in Settings; this link keeps the brew
                            // screen tidy while still letting the user reach
                            // the full management UI in one tap.
                            TextButton(
                                onClick = onNavigateToSettings,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.action_manage_grinders_in_settings))
                            }
                        }
                    }
                }
            }

        }

        // Calculator keyboard — pinned to bottom, outside scroll
        Spacer(modifier = Modifier.height(12.dp))

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
    ratio: Float,
    canSaveFavorite: Boolean,
    onToggleDirection: () -> Unit,
    onRatioChange: (Float) -> Unit,
    onSaveFavorite: () -> Unit,
) {
    var showRatioDialog by remember { mutableStateOf(false) }

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

        // Right cluster: save-as-favorite + ratio chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

            // Ratio chip
            AssistChip(
                onClick = { showRatioDialog = true },
                label = {
                    Text(
                        text = "1:${ratio.toInt()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
    }

    if (showRatioDialog) {
        RatioPickerDialog(
            currentRatio = ratio,
            onRatioSelected = {
                onRatioChange(it)
                showRatioDialog = false
            },
            onDismiss = { showRatioDialog = false },
        )
    }
}

@Composable
private fun RatioPickerDialog(
    currentRatio: Float,
    onRatioSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val ratioOptions = listOf(2f, 8f, 10f, 15f, 16f, 17f, 18f)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_brew_ratio_title)) },
        text = {
            Column {
                ratioOptions.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        val isSelected = r == currentRatio
                        if (isSelected) {
                            Button(
                                onClick = { onRatioSelected(r) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("1:${r.toInt()}")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onRatioSelected(r) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("1:${r.toInt()}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
        shape = RoundedCornerShape(24.dp),
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
        shape = RoundedCornerShape(16.dp),
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
        shape = RoundedCornerShape(16.dp),
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
        shape = RoundedCornerShape(16.dp),
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
        shape = RoundedCornerShape(16.dp),
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
