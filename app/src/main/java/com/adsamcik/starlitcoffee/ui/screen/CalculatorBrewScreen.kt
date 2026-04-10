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
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.util.presetIcon
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel

private val checkIcon: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalculatorBrewScreen(
    calculatorViewModel: CalculatorViewModel,
    brewViewModel: BrewViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateToBrew: () -> Unit,
) {
    val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()

    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )
    var selectedMethod by remember { mutableStateOf(prefs.defaultMethod) }
    var selectedFilter by remember { mutableStateOf(prefs.defaultFilterType) }
    var selectedGrinderId by remember { mutableStateOf(prefs.selectedGrinderId) }

    LaunchedEffect(prefs) {
        selectedMethod = prefs.defaultMethod
        selectedFilter = prefs.defaultFilterType
        selectedGrinderId = prefs.selectedGrinderId
    }

    val context = LocalContext.current
    val grinders = remember { GrinderDataSource.getInstance(context).grinders }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Top section: direction toggle + ratio chip
        TopControlBar(
            direction = state.inputDirection,
            ratio = state.ratio,
            onToggleDirection = { calculatorViewModel.toggleDirection() },
            onRatioChange = { calculatorViewModel.setRatio(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Expression display
        ExpressionDisplay(
            tokens = state.tokens,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable content: preview + config + keyboard
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Live preview card
            LivePreviewCard(
                doseG = state.previewDoseG,
                waterMl = state.previewWaterMl,
                direction = state.inputDirection,
                method = selectedMethod,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Brew config — compact summary, expandable to full config
            var configExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.largeIncreased)
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
                        contentDescription = if (configExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Expanded config sections
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
                                text = "Method",
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
                                        onClick = { selectedMethod = method },
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

                        // Filter — Pulsar only
                        if (selectedMethod == BrewMethod.PULSAR) {
                            Text(
                                text = "Filter",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterType.entries.forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFilter = if (isSelected) null else filter },
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

                        // Grinder
                        if (grinders.isNotEmpty()) {
                            Text(
                                text = "Grinder",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(
                                    selected = selectedGrinderId == null,
                                    onClick = { selectedGrinderId = null },
                                    label = { Text("None") },
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
                                        onClick = { selectedGrinderId = grinder.id },
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
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calculator keyboard
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
                    brewViewModel.setMethod(selectedMethod)
                    brewViewModel.setFilterType(selectedFilter)
                    brewViewModel.setGrinder(selectedGrinderId)
                    brewViewModel.setAmount(state.previewDoseG.toString())
                    onNavigateToBrew()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TopControlBar(
    direction: InputDirection,
    ratio: Float,
    onToggleDirection: () -> Unit,
    onRatioChange: (Float) -> Unit,
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
                contentDescription = "Switch direction",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (direction) {
                    InputDirection.WATER -> "Water → Dose"
                    InputDirection.DOSE -> "Dose → Water"
                },
                style = MaterialTheme.typography.labelLarge,
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
        title = { Text("Brew Ratio") },
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
                Text("Cancel")
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
                    text = "coffee",
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
                    text = "total water",
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
                        text = "bloom",
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
                        text = "remaining",
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Row 1: Preset buttons + backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.take(5).forEach { preset ->
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
                        IconButtonDefaults.filledTonalIconButtonColors()
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
                    contentDescription = "Backspace",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Rows 2-5: Number pad + operators + brew button
        // Layout: 3 number columns + 1 action column
        val keyHeight = 56.dp

        // Row 2: 7 8 9 ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CalcKey("7", Modifier.weight(1f).height(keyHeight)) { onDigit('7') }
            CalcKey("8", Modifier.weight(1f).height(keyHeight)) { onDigit('8') }
            CalcKey("9", Modifier.weight(1f).height(keyHeight)) { onDigit('9') }
            OperatorKey("×", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.MULTIPLY) }
        }

        // Row 3: 4 5 6 +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CalcKey("4", Modifier.weight(1f).height(keyHeight)) { onDigit('4') }
            CalcKey("5", Modifier.weight(1f).height(keyHeight)) { onDigit('5') }
            CalcKey("6", Modifier.weight(1f).height(keyHeight)) { onDigit('6') }
            OperatorKey("+", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.ADD) }
        }

        // Row 4: 1 2 3 [Brew top half]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            text = "Brew",
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.largeIncreased)
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
