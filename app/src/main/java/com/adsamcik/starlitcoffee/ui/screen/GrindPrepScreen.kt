package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.PreparationCoffeeSelector
import com.adsamcik.starlitcoffee.ui.component.PreparationGrindEditor
import com.adsamcik.starlitcoffee.ui.component.PreparationNewPackSheet
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.ui.util.keepScreenOnTimeoutMillis
import com.adsamcik.starlitcoffee.ui.util.rememberDimModeController
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.GrindResult

// A bounded keep-awake window for hands-busy weighing, grinding and setup.
private const val GRIND_PREP_ESTIMATE_SECONDS = 240

@Composable
fun GrindPrepScreen(
    brewViewModel: BrewViewModel,
    dimModeEnabled: Boolean = true,
    dimModeTrueBlack: Boolean = false,
    dimModeReduceBrightness: Boolean = false,
    dimModeFullscreen: Boolean = false,
    dimModeForceDarkInLight: Boolean = false,
    showBrewingInstructions: Boolean = true,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
    isStartingBrew: Boolean = false,
    onScanToChoose: (() -> Unit)? = null,
    onScanNewPack: (() -> Unit)? = null,
    onReadNewPackLabel: (() -> Unit)? = null,
    scannedNewPackBarcode: String? = null,
    onScannedNewPackBarcodeHandled: () -> Unit = {},
    scannedChooseBarcode: String? = null,
    onScannedChooseBarcodeHandled: () -> Unit = {},
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val coffeeBags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val selectedBagId by brewViewModel.selectedBagId.collectAsStateWithLifecycle()
    val identities by brewViewModel.coffeeIdentities.collectAsStateWithLifecycle()
    val packAddState by brewViewModel.newPackAddState.collectAsStateWithLifecycle()
    val inventoryLoaded by brewViewModel.isCoffeeBagInventoryLoaded.collectAsStateWithLifecycle()
    val selectedBag = remember(coffeeBags, selectedBagId) { coffeeBags.find { it.id == selectedBagId } }
    var showGrindEditor by rememberSaveable { mutableStateOf(false) }
    var showNewPack by rememberSaveable { mutableStateOf(false) }
    var openCoffeePickerRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scannedNewPackBarcode) {
        if (scannedNewPackBarcode != null) showNewPack = true
    }

    KeepScreenOn(timeoutMillis = keepScreenOnTimeoutMillis(GRIND_PREP_ESTIMATE_SECONDS))
    val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)
    DimModeScaffold(
        controller = dimController,
        modifier = Modifier.fillMaxSize(),
        trueBlackBackground = dimModeTrueBlack,
        reduceBrightness = dimModeReduceBrightness,
        hideSystemBars = dimModeFullscreen,
        forceDarkInLight = dimModeForceDarkInLight,
    ) {
        GrindPrepContent(
            state = state,
            showBrewingInstructions = showBrewingInstructions,
            onTypeSelected = brewViewModel::setDecafBrew,
            onNavigateToBrew = onNavigateToBrew,
            onBack = onBack,
            onEditGrind = { showGrindEditor = true },
            isStartingBrew = isStartingBrew,
            coffeeSelection = {
                PreparationCoffeeSelector(
                    bags = coffeeBags,
                    selectedBag = selectedBag,
                    isDecaf = state.isDecafBrew,
                    onSelectBag = { brewViewModel.selectBagForBrewing(it) },
                    onClearBag = { brewViewModel.selectBag(null) },
                    onScanToChoose = onScanToChoose,
                    onAddPack = { brewViewModel.clearPackAddState(); showNewPack = true },
                    openPickerRequest = openCoffeePickerRequest,
                )
            },
        )
        BarcodeBrewEntry(
            bags = coffeeBags,
            inventoryLoaded = inventoryLoaded,
            scannedBarcode = scannedChooseBarcode,
            onBarcodeConsumed = onScannedChooseBarcodeHandled,
            onScan = { onScanToChoose?.invoke() },
            onViewBeans = { openCoffeePickerRequest++ },
            onSelectBag = { brewViewModel.selectBagForBrewing(it) },
            showScanAction = false,
            requireExplicitSelection = true,
        )
        val prepared = state.preparedGrind
        val specific = state.grindResult as? GrindResult.Specific
        if (showGrindEditor && specific != null && prepared.availableScopes.isNotEmpty()) {
            val grinder = specific.grinder
            PreparationGrindEditor(
                prepared = prepared,
                grinderName = if (grinder.brand.equals(grinder.model, ignoreCase = true)) grinder.model else "${grinder.brand} ${grinder.model}",
                methodName = listOfNotNull(state.method.displayName, state.filterType?.displayName).joinToString(" · "),
                selectedBag = selectedBag,
                isDecaf = state.isDecafBrew,
                onSave = brewViewModel::saveGrindSetting,
                onReset = brewViewModel::removeEffectiveGrindSetting,
                onClearError = brewViewModel::clearGrindError,
                onDismiss = { showGrindEditor = false },
            )
        }
        if (showNewPack) {
            PreparationNewPackSheet(
                identities = identities,
                addState = packAddState,
                scannedBarcode = scannedNewPackBarcode,
                onBarcodeHandled = onScannedNewPackBarcodeHandled,
                onFindCoffee = brewViewModel::findCoffeeIdentitiesByBarcode,
                onScan = onScanNewPack,
                onReadLabel = onReadNewPackLabel,
                onAdd = brewViewModel::addPackAndUse,
                onDismiss = { showNewPack = false; brewViewModel.clearPackAddState() },
            )
        }
    }
}

/** Stateless preparation surface, also used by previews and UI verification. */
@Composable
internal fun GrindPrepContent(
    state: BrewUiState,
    showBrewingInstructions: Boolean,
    onTypeSelected: (Boolean) -> Unit,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
    coffeeSelection: @Composable () -> Unit = {},
    onEditGrind: (() -> Unit)? = null,
    isStartingBrew: Boolean = false,
) {
    Scaffold(
        modifier = Modifier.testTag("grind_prep_content"),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onNavigateToBrew,
                    enabled = !isStartingBrew,
                    modifier = Modifier
                        .testTag("grind_prep_ready")
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = primaryActionButtonColors(),
                ) {
                    Text(
                        text = stringResource(R.string.action_ready_to_brew_short),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(12.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.prep_title_short),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PreparationIdentity(state)
            state.ratioWarning?.let { WarningCard(message = it) }
            state.bloomWarning?.let { WarningCard(message = it) }
            PreparationTargets(state)
            PreparationBoard(state = state, onTypeSelected = onTypeSelected, coffeeSelection = coffeeSelection, onEditGrind = onEditGrind)
            if (showBrewingInstructions) {
                PreparationEquipmentCard(state)
            }
            PreparationRecipeDetails(state)
        }
    }
}

/** The existing choice remains explicit and is recalculated by the view model. */
@Composable
internal fun CoffeeTypeSelector(
    isDecaf: Boolean,
    onTypeSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showAdjustmentHint: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            if (maxWidth < 220.dp || LocalDensity.current.fontScale >= 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(false, true).forEach { decaf ->
                        CoffeeTypeChoice(decaf, isDecaf == decaf, onTypeSelected)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(false, true).forEach { decaf ->
                        CoffeeTypeChoice(decaf, isDecaf == decaf, onTypeSelected, Modifier.weight(1f))
                    }
                }
            }
        }
        if (isDecaf && showAdjustmentHint) {
            Text(
                text = stringResource(R.string.msg_decaf_grind_adjustment),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoffeeTypeChoice(decaf: Boolean, selected: Boolean, onTypeSelected: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onTypeSelected(decaf) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(if (decaf) R.string.label_decaf else R.string.label_regular), style = MaterialTheme.typography.labelLarge)
        }
    }
}
