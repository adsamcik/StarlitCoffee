package com.adsamcik.starlitcoffee.ui.screen

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import androidx.compose.material3.TextButton
import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.calculator.calculatorRatioOptions
import com.adsamcik.starlitcoffee.calculator.formatCalculatorRatio
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.ui.component.BrewingSetEditor
import com.adsamcik.starlitcoffee.ui.component.BrewingSetPicker
import java.util.UUID
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.ui.adaptive.LocalWindowWidthClass
import com.adsamcik.starlitcoffee.ui.component.CalculationQuantityIcon
import com.adsamcik.starlitcoffee.ui.component.CalculationQuantityIconType
import com.adsamcik.starlitcoffee.ui.component.CalculatorQuantityCardItem
import com.adsamcik.starlitcoffee.ui.component.CalculatorQuantitySelector
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import com.adsamcik.starlitcoffee.viewmodel.WaterAmountMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorBrewScreen(
    calculatorViewModel: CalculatorViewModel,
    brewViewModel: BrewViewModel,
    onNavigateToBrew: () -> Unit,
    recoverableSessionId: String?,
    onResumeSession: (String) -> Unit,
    onNavigateToBarcode: () -> Unit,
    onNavigateToBags: () -> Unit,
    scannedBarcodeResult: String? = null,
    onScannedBarcodeResultConsumed: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    isStartingBrew: Boolean = false,
) {
    val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val brewState by brewViewModel.uiState.collectAsStateWithLifecycle()

    // The selected set owns calculator input and equipment; preparation receives the same values.
    val selectedMethod = state.brewMethod
    val selectedFilter = state.filterType
    val context = LocalContext.current
    val grinderData = remember { GrinderDataSource.getInstance(context) }
    val grinders = grinderData.grindersFor(state.brewMethod, selectedFilter)
    val selectedGrinderId = state.grinderId?.takeIf { id -> grinders.any { it.id == id } }
    LaunchedEffect(state.brewMethod, brewState.beverageOutputCalibration, state.preferencesLoaded) {
        if (state.preferencesLoaded) {
            calculatorViewModel.setBrewContext(
                method = selectedMethod,
                calibration = brewState.beverageOutputCalibration,
            )
        }
    }

    LaunchedEffect(state.brewMethod, selectedFilter, state.grinderId, state.preferencesLoaded) {
        if (state.preferencesLoaded) {
            if (brewViewModel.uiState.value.method != state.brewMethod) brewViewModel.setMethod(state.brewMethod)
            if (state.grinderId != selectedGrinderId) {
                calculatorViewModel.setEquipment(selectedFilter, selectedGrinderId)
            }
            brewViewModel.setFilterType(selectedFilter)
            brewViewModel.setGrinder(selectedGrinderId)
        }
    }

    // Compact-height adaptation. Reference values (dp):
    //   - Small phones (5" / Pixel 4a): ~683 dp
    //   - Standard phones (6.1" / Pixel 7): ~810 dp
    //   - Large phones (6.7" / Pixel 7 Pro): ~890 dp
    // We treat anything below ~700 dp as compact and tighten the layout so the
    // keyboard, preview, and pills all stay reachable without scrolling.
    // Uses LocalWindowInfo.containerSize (the actual window) instead of
    // Configuration.screenHeightDp, which has inconsistent inset handling
    // across target SDKs (lint: ConfigurationScreenWidthHeight).
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val screenHeightDp = with(density) { containerSize.height.toDp().value }
    val isCompactHeight = screenHeightDp < 700f
    // Reverse adaptation — tall screens (large phones, foldables open) waste
    // vertical space on the keyboard. Detect them and grow the keys for
    // better thumb reach. Tablet bracket (>=900dp) is excluded; that's the
    // domain of WindowSizeClass-based layouts.
    val isTallHeight = screenHeightDp >= 860f && screenHeightDp < 900f
    val sectionSpacer = if (isCompactHeight) 6.dp else 12.dp
    val barSpacer = if (isCompactHeight) 4.dp else 8.dp

    // Wide-window adaptation: in landscape on large-enough windows (tablets,
    // foldables, landscape phones, desktop) lay the calculator out as a classic
    // two-pane split — display + settings on the left, keypad on the right —
    // instead of one tall column. Width class gates "is this wide enough"; the
    // landscape check gates "is a side-by-side split actually better than a
    // centered column" so portrait tablets keep the single column.
    val screenWidthDp = with(density) { containerSize.width.toDp().value }
    val isLandscape = screenWidthDp > screenHeightDp
    val twoPaneCalculator = LocalWindowWidthClass.current.isWide && isLandscape

    val coffeeBags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val inventoryLoaded by brewViewModel.isCoffeeBagInventoryLoaded.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val selectedBag = remember(coffeeBags, selectedBagId) {
        coffeeBags.find { it.id == selectedBagId }
    }

    var newSetId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.savedSetId) {
        if (state.savedSetId == newSetId && newSetId != null) {
            newSetId = null
            calculatorViewModel.consumeSetSaveOutcome()
        }
    }

    val scrollState = rememberScrollState()

    // Snapshot the whole selected set before starting, including a switch whose
    // presentation effect has not reached the preparation ViewModel yet.
    val syncCalcDerivedState: () -> Unit = {
        if (brewViewModel.uiState.value.method != state.brewMethod) brewViewModel.setMethod(state.brewMethod)
        brewViewModel.setFilterType(state.filterType)
        brewViewModel.setGrinder(selectedGrinderId)
        brewViewModel.setCustomRatio(state.ratio.toString())
        brewViewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        brewViewModel.setAmount(state.previewDoseG.toString())
    }

    // Content blocks shared by the single-column (compact/portrait) and the
    // two-pane (wide landscape) layouts so the two never drift apart.
    val header: @Composable () -> Unit = {
        // Combined header: direction toggle + expression (with optional inline
        // result on compact heights) + save-favorite. Replaces the older
        // dedicated TopControlBar row, recovering ~48dp of vertical space.
        ExpressionHeader(
            tokens = state.tokens,
            direction = state.inputDirection,
            waterAmountMode = state.waterAmountMode,
            canSaveSet = state.preferencesLoaded,
            showInlineResult = isCompactHeight && state.hasValidExpression,
            previewDoseG = state.previewDoseG,
            previewWaterMl = state.previewWaterMl,
            isBeverageYield = state.brewMethod.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD,
            isCompactHeight = isCompactHeight,
            onSaveSet = { newSetId = UUID.randomUUID().toString() },
        )
    }

    val previewAndConfig: @Composable () -> Unit = {
        val isYield = state.brewMethod.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD
        val coffeeValue = formatQuantityCardAmount(state.previewDoseG)
        val waterValue = if (isYield) "—" else formatQuantityCardAmount(state.previewWaterMl)
        val cupValue = state.previewBeverageG?.let(::formatQuantityCardAmount) ?: "—"
        val gramsUnit = stringResource(R.string.unit_grams)
        CalculatorQuantitySelector(
            items = listOf(
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.COFFEE,
                    label = stringResource(R.string.label_coffee),
                    value = coffeeValue,
                    spokenValue = quantityCardSpokenValue(coffeeValue, gramsUnit),
                    icon = CalculationQuantityIconType.COFFEE_DOSE,
                ),
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.WATER_IN,
                    label = stringResource(R.string.label_water_in),
                    value = waterValue,
                    spokenValue = quantityCardSpokenValue(waterValue, gramsUnit),
                    icon = CalculationQuantityIconType.WATER_IN,
                    enabled = CalculatorQuantityTarget.WATER_IN.isAvailableFor(state.brewMethod),
                ),
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.IN_CUP,
                    label = stringResource(R.string.label_in_cup),
                    value = cupValue,
                    spokenValue = if (
                        state.previewBeverageG != null &&
                        !isYield &&
                        state.quantityTarget != CalculatorQuantityTarget.IN_CUP
                    ) {
                        stringResource(
                            R.string.format_estimated_coffee_out,
                            quantityCardSpokenValue(cupValue, gramsUnit),
                        )
                    } else {
                        quantityCardSpokenValue(cupValue, gramsUnit)
                    },
                    icon = CalculationQuantityIconType.CUP_OUTPUT,
                    approximate = state.previewBeverageG != null &&
                        !isYield &&
                        state.quantityTarget != CalculatorQuantityTarget.IN_CUP,
                    enabled = CalculatorQuantityTarget.IN_CUP.isAvailableFor(state.brewMethod),
                ),
            ),
            selected = state.quantityTarget,
            onSelect = calculatorViewModel::selectQuantity,
            compact = isCompactHeight,
        )
        if (hasInStockBarcodeBags(coffeeBags) || selectedBag != null) {
            Spacer(modifier = Modifier.height(sectionSpacer))
        }
        BarcodeBrewEntry(
            bags = coffeeBags,
            inventoryLoaded = inventoryLoaded,
            scannedBarcode = scannedBarcodeResult,
            onBarcodeConsumed = onScannedBarcodeResultConsumed,
            onScan = onNavigateToBarcode,
            onViewBeans = onNavigateToBags,
            onSelectBag = { bagId ->
                val requestedQuantity = calculatorViewModel.uiState.value.quantityTarget
                brewViewModel.selectBag(bagId)
                // Bag selection can restore its last method. Recompute an in-cup
                // amount for that method before taking the preparation snapshot.
                val selectedBrewState = brewViewModel.uiState.value
                calculatorViewModel.setBrewContext(
                    method = selectedBrewState.method,
                    calibration = selectedBrewState.beverageOutputCalibration,
                )
                val scanCalcState = calculatorViewModel.uiState.value
                if (
                    scanCalcState.hasValidExpression && scanCalcState.previewDoseG > 0f &&
                    scanCalcState.quantityTarget == requestedQuantity
                ) {
                    brewViewModel.selectBagForBrewing(bagId)
                    brewViewModel.setFilterType(scanCalcState.filterType)
                    brewViewModel.setGrinder(
                        scanCalcState.grinderId?.takeIf {
                            grinderData.grindersFor(scanCalcState.brewMethod, scanCalcState.filterType)
                                .any { grinder -> grinder.id == it }
                        },
                    )
                    brewViewModel.setCustomRatio(scanCalcState.ratio.toString())
                    brewViewModel.setInputMode(InputMode.COFFEE_TO_WATER)
                    brewViewModel.setAmount(scanCalcState.previewDoseG.toString())
                    onNavigateToBrew()
                } else {
                    val bagName = coffeeBags.firstOrNull { it.id == bagId }?.name.orEmpty()
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_barcode_brew_enter_amount, bagName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
        // Selected bag indicator — visible reminder that a bag is in play,
        // with one-tap clear so the user can switch to brewing without one.
        selectedBag?.let { bag ->
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

    val settingsToolbar: @Composable () -> Unit = {
        BrewSettingsToolbar(
            sets = state.brewingSets,
            selectedSetId = state.activeBrewingSetId,
            selectedMethod = selectedMethod,
            grinderData = grinderData,
            ratio = state.ratio,
            onSetChange = calculatorViewModel::selectBrewingSet,
            onRatioChange = calculatorViewModel::setRatio,
            recoverableSessionId = recoverableSessionId,
            onResumeSession = onResumeSession,
            onManage = onNavigateToSettings,
            onBackspace = calculatorViewModel::backspace,
        )
        if (state.setSaveFailed && newSetId == null) {
            TextButton(onClick = calculatorViewModel::retrySetupSave) {
                Text(stringResource(R.string.msg_settings_save_failed) + " " + stringResource(R.string.action_retry_brewing_set))
            }
        }
    }

    val keyboard: @Composable () -> Unit = {
        CalculatorKeyboard(
            presets = state.availablePresets,
            showCupPresets = state.preferencesLoaded && state.showCupPresets,
            hasValidExpression = state.hasValidExpression && !isStartingBrew,
            isCompactHeight = isCompactHeight,
            isTallHeight = isTallHeight,
            onDigit = { calculatorViewModel.appendDigit(it) },
            onDecimal = { calculatorViewModel.appendDecimal() },
            onOperator = { calculatorViewModel.appendOperator(it) },
            onPreset = { calculatorViewModel.appendPreset(it) },
            onClear = { calculatorViewModel.clear() },
            onBrew = {
                selectedBagId?.let(brewViewModel::selectBagForBrewing)
                syncCalcDerivedState()
                onNavigateToBrew()
            },
        )
    }

    if (twoPaneCalculator) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // Left pane: display + settings, top-aligned and scrollable so they
            // stay grouped at the top instead of being spread down a tall window.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            ) {
                header()
                Spacer(modifier = Modifier.height(sectionSpacer))
                previewAndConfig()
                Spacer(modifier = Modifier.height(sectionSpacer))
                settingsToolbar()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                keyboard()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            header()
            Spacer(modifier = Modifier.height(sectionSpacer))
            // Scrollable content: preview + config
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                previewAndConfig()
            }
            Spacer(modifier = Modifier.height(sectionSpacer))
            settingsToolbar()
            // Calculator keyboard — pinned to bottom, outside scroll
            Spacer(modifier = Modifier.height(barSpacer))
            keyboard()
            Spacer(modifier = Modifier.height(barSpacer))
        }
    }

    newSetId?.let { id ->
        val draft = remember(id) { BrewingSet(id = id, method = state.brewMethod, setup = calculatorViewModel.currentSetup()) }
        BrewingSetEditor(draft, isNew = true, grinderData = grinderData,
            isSaving = state.setSaveInProgress, saveFailed = state.setSaveFailed,
            onSave = calculatorViewModel::saveBrewingSet,
            onDismiss = { newSetId = null; calculatorViewModel.consumeSetSaveOutcome() })
    }
}

@Composable
private fun ExpressionHeader(
    tokens: List<CalcToken>,
    direction: InputDirection,
    waterAmountMode: WaterAmountMode,
    canSaveSet: Boolean,
    showInlineResult: Boolean,
    previewDoseG: Float,
    previewWaterMl: Float,
    isBeverageYield: Boolean,
    isCompactHeight: Boolean,
    onSaveSet: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isCompactHeight) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExpressionDisplay(
            tokens = tokens,
            isCompactHeight = isCompactHeight,
            inlineResult = if (showInlineResult) {
                buildInlineResult(
                    direction = direction,
                    waterAmountMode = waterAmountMode,
                    doseG = previewDoseG,
                    waterMl = previewWaterMl,
                    isBeverageYield = isBeverageYield,
                )
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onSaveSet,
            enabled = canSaveSet,
            modifier = Modifier
                .size(40.dp)
                .testTag("save_set_button"),
        ) {
            Icon(
                    imageVector = Icons.Filled.BookmarkAdd,
                contentDescription = stringResource(R.string.action_save_brewing_set),
                tint = if (canSaveSet) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Captures the data shown in the compact-height inline result suffix
 * ("= 💧 340g") so the icon's tint matches the result side without the
 * caller needing to thread Material colors through.
 */
internal data class InlineResult(
    val icon: CalculationQuantityIconType,
    val value: String,
    val side: ResultSide,
)

internal enum class ResultSide { COFFEE, WATER, CUP }

/**
 * Picks the inline result side: the user's input direction names the side
 * they're typing, so the result is always the *other* side.
 */
internal fun buildInlineResult(
    direction: InputDirection,
    waterAmountMode: WaterAmountMode,
    doseG: Float,
    waterMl: Float,
    isBeverageYield: Boolean = false,
): InlineResult = when {
    isBeverageYield && direction == InputDirection.DOSE -> InlineResult(
        icon = CalculationQuantityIconType.CUP_OUTPUT,
        value = formatAmount(waterMl),
        side = ResultSide.CUP,
    )
    isBeverageYield -> InlineResult(
        icon = CalculationQuantityIconType.COFFEE_DOSE,
        value = formatAmount(doseG),
        side = ResultSide.COFFEE,
    )
    direction == InputDirection.WATER && waterAmountMode == WaterAmountMode.BEVERAGE_OUTPUT -> InlineResult(
        icon = CalculationQuantityIconType.WATER_IN,
        value = formatAmount(waterMl),
        side = ResultSide.WATER,
    )
    direction == InputDirection.DOSE -> InlineResult(
        icon = CalculationQuantityIconType.WATER_IN,
        value = formatAmount(waterMl),
        side = ResultSide.WATER,
    )
    else -> InlineResult(
        icon = CalculationQuantityIconType.COFFEE_DOSE,
        value = formatAmount(doseG),
        side = ResultSide.COFFEE,
    )
}

@Composable
private fun ExpressionDisplay(
    tokens: List<CalcToken>,
    isCompactHeight: Boolean,
    modifier: Modifier = Modifier,
    inlineResult: InlineResult? = null,
) {
    val scrollState = rememberScrollState()
    val numberStyle = if (isCompactHeight) {
        MaterialTheme.typography.headlineLarge
    } else {
        MaterialTheme.typography.displayMedium
    }
    val operatorStyle = if (isCompactHeight) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.displaySmall
    }

    Box(
        modifier = modifier
            .height(if (isCompactHeight) 56.dp else 72.dp)
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (tokens.isEmpty()) {
            Text(
                text = "0",
                style = numberStyle,
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
                            style = numberStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        is CalcToken.Operator -> Text(
                            text = token.op.symbol,
                            style = operatorStyle,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        is CalcToken.PresetRef -> {
                            PresetIcon(
                                iconName = token.preset.iconName,
                                contentDescription = token.preset.name,
                                modifier = Modifier.size(if (isCompactHeight) 28.dp else 36.dp),
                            )
                        }
                    }
                }

                inlineResult?.let { result ->
                    Text(
                        text = "=",
                        style = operatorStyle,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                    )
                    CalculationQuantityIcon(
                        icon = result.icon,
                        contentDescription = null,
                        tint = when (result.side) {
                            ResultSide.COFFEE -> MaterialTheme.colorScheme.primary
                            ResultSide.WATER -> MaterialTheme.colorScheme.secondary
                            ResultSide.CUP -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(if (isCompactHeight) 22.dp else 26.dp),
                    )
                    Text(
                        text = result.value,
                        style = numberStyle,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BrewSettingsToolbar(
    sets: List<BrewingSet>,
    selectedSetId: String?,
    selectedMethod: BrewMethod,
    grinderData: GrinderDataProvider,
    ratio: Float,
    onSetChange: (String) -> Unit,
    onRatioChange: (Float) -> Unit,
    recoverableSessionId: String?,
    onResumeSession: (String) -> Unit,
    onManage: (() -> Unit)?,
    onBackspace: () -> Unit,
) {
    val ratioOptions = selectedMethod.calculatorRatioOptions(ratio)
    val ratioPicker: @Composable (Modifier) -> Unit = { modifier ->
        RatioDropdown(
            label = "1:${formatCalculatorRatio(ratio)}",
            options = ratioOptions.map { value ->
                RatioOption("1:${formatCalculatorRatio(value)}", value == ratio) { onRatioChange(value) }
            },
            modifier = modifier,
            contentDescription = stringResource(R.string.cd_ratio, formatCalculatorRatio(ratio)),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackControls = maxWidth < 360.dp && LocalDensity.current.fontScale > 1.2f
            if (stackControls) {
                // Give the brewer name room when larger text leaves too little
                // width beside the ratio and delete controls.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewingSetPicker(sets, selectedSetId, grinderData, onSetChange, onManage,
                        modifier = Modifier.fillMaxWidth(), showSummary = true)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ratioPicker(Modifier.weight(1f).fillMaxHeight())
                        CalculatorBackspaceButton(onBackspace, Modifier.fillMaxHeight())
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewingSetPicker(sets, selectedSetId, grinderData, onSetChange, onManage,
                        modifier = Modifier.weight(1f).fillMaxHeight(), showSummary = true)
                    ratioPicker(Modifier.fillMaxHeight())
                    CalculatorBackspaceButton(onBackspace, Modifier.fillMaxHeight())
                }
            }
        }
        recoverableSessionId?.let { id ->
            AssistChip(onClick = { onResumeSession(id) }, label = { Text(stringResource(R.string.action_resume)) })
        }
    }
}

private data class RatioOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun RatioDropdown(
    label: String,
    options: List<RatioOption>,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "RatioDropdownArrow",
    )
    Box(modifier = modifier, propagateMinConstraints = true) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxHeight().heightIn(min = 56.dp).widthIn(min = 88.dp)
                .testTag("calculator_ratio_picker")
                .semantics { this.contentDescription = contentDescription; role = Role.Button },
            shape = RoundedCornerShape(16.dp),
            color = if (expanded) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
internal fun CalculatorKeyboard(
    presets: List<CupPreset>,
    showCupPresets: Boolean,
    hasValidExpression: Boolean,
    isCompactHeight: Boolean,
    isTallHeight: Boolean,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (CalcOp) -> Unit,
    onPreset: (CupPreset) -> Unit,
    onClear: () -> Unit,
    onBrew: () -> Unit,
) {
    val rowSpacing = if (isCompactHeight) 6.dp else 8.dp
    // Tall phones (e.g. Pixel 7 Pro, Galaxy S24 Ultra in portrait) leave too
    // much empty space above the keyboard with the standard 56dp keys, so
    // grow them for easier thumb reach. Compact wins over tall when both
    // somehow apply.
    val keyHeight = when {
        isCompactHeight -> 48.dp
        isTallHeight -> 64.dp
        else -> 56.dp
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        if (showCupPresets && presets.isNotEmpty()) {
            CalculatorPresetBar(presets, onPreset)
        }

        // Rows 2-5: Number pad + operators + brew button.
        // Layout: 3 number columns + 1 action column.

        // Row 2: 7 8 9 ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            CalcKey("7", Modifier.weight(1f).height(keyHeight)) { onDigit('7') }
            CalcKey("8", Modifier.weight(1f).height(keyHeight)) { onDigit('8') }
            CalcKey("9", Modifier.weight(1f).height(keyHeight)) { onDigit('9') }
            OperatorKey("×", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.MULTIPLY) }
        }

        // Row 3: 4 5 6 +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            CalcKey("4", Modifier.weight(1f).height(keyHeight)) { onDigit('4') }
            CalcKey("5", Modifier.weight(1f).height(keyHeight)) { onDigit('5') }
            CalcKey("6", Modifier.weight(1f).height(keyHeight)) { onDigit('6') }
            OperatorKey("+", Modifier.weight(1f).height(keyHeight)) { onOperator(CalcOp.ADD) }
        }

        // Row 4: 1 2 3 [Brew top half]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
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
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            CalcKey("0", Modifier.weight(2f).height(keyHeight)) { onDigit('0') }
            CalcKey(".", Modifier.weight(1f).height(keyHeight)) { onDecimal() }
            ClearKey(Modifier.weight(1f).height(keyHeight)) { onClear() }
        }
    }
}

@Composable
internal fun CalculatorPresetBar(
    presets: List<CupPreset>,
    onPreset: (CupPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (presets.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    val unit = stringResource(R.string.unit_ml)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("calculator_preset_bar")) {
        // Share the available width on phones; scroll every saved cup on narrow
        // windows or with larger text instead of squeezing or hiding shortcuts.
        val visibleCount = minOf(presets.size, 5)
        val itemWidth = maxOf(64.dp * fontScale, (maxWidth - 8.dp * (visibleCount - 1)) / visibleCount)
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("calculator_cup_presets"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                Surface(
                    onClick = { onPreset(preset) },
                    modifier = Modifier.width(itemWidth).heightIn(min = 64.dp)
                        .testTag("calculator_preset_${preset.id}"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PresetIcon(preset.iconName, preset.name, Modifier.size(28.dp))
                        Text(
                            text = quantityCardSpokenValue(formatQuantityCardAmount(preset.waterMl), unit),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorBackspaceButton(onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onBackspace,
        modifier = modifier.width(48.dp).heightIn(min = 56.dp).testTag("calculator_backspace"),
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.cd_backspace), Modifier.size(22.dp))
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
        colors = primaryActionButtonColors(),
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

internal fun formatQuantityCardAmount(value: Float): String {
    return if (value == 0f) {
        "—"
    } else if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}

internal fun quantityCardSpokenValue(value: String, unit: String): String =
    if (value == "—") value else "$value $unit"

private fun formatAmount(value: Float): String {
    val amount = formatQuantityCardAmount(value)
    return if (amount == "—") amount else "${amount}g"
}
