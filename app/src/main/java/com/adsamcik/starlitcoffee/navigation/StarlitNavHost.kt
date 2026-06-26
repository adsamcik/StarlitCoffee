package com.adsamcik.starlitcoffee.navigation

import android.app.Application
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.notification.DeepLinkBus
import com.adsamcik.starlitcoffee.ui.adaptive.LocalWindowWidthClass
import com.adsamcik.starlitcoffee.ui.screen.BagInventoryScreen
import com.adsamcik.starlitcoffee.ui.screen.BarcodeScannerScreen
import com.adsamcik.starlitcoffee.ui.screen.BloomAnimationSettingsScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogDetailScreen
import com.adsamcik.starlitcoffee.ui.screen.CalculatorBrewScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.BloomTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.CupPresetEditorScreen
import com.adsamcik.starlitcoffee.ui.screen.DisplaySettingsScreen
import com.adsamcik.starlitcoffee.ui.screen.GrindPrepScreen
import com.adsamcik.starlitcoffee.ui.screen.GuidedScanFlow
import com.adsamcik.starlitcoffee.ui.screen.MoreScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingMethodsScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingPersonalizeScreen
import com.adsamcik.starlitcoffee.ui.screen.SavedRecipesScreen
import com.adsamcik.starlitcoffee.ui.screen.ScanAddBagReview
import com.adsamcik.starlitcoffee.ui.screen.ScanRescanReview
import com.adsamcik.starlitcoffee.ui.screen.SettingsScreen
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModelFactory
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import java.util.HashMap
import kotlinx.coroutines.launch

private data class BottomNavItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: Any,
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_brew, Icons.Filled.LocalCafe, CalculatorBrew),
    BottomNavItem(R.string.nav_log, Icons.Filled.History, BrewLogList),
    BottomNavItem(R.string.nav_more, Icons.Filled.MoreHoriz, More),
)

private val bottomBarRoutes = setOf(
    CalculatorBrew::class,
    BrewLogList::class,
    More::class,
)

private val BrewMethodSetStateSaver: Saver<MutableState<Set<BrewMethod>>, ArrayList<String>> = Saver(
    save = { state -> ArrayList(state.value.map { it.name }) },
    restore = { list ->
        mutableStateOf(
            list.mapNotNull { runCatching { BrewMethod.valueOf(it) }.getOrNull() }.toSet(),
        )
    },
)

private val BrewMethodStateSaver: Saver<MutableState<BrewMethod>, String> = Saver(
    save = { it.value.name },
    restore = { name ->
        runCatching { BrewMethod.valueOf(name) }.getOrNull()?.let { mutableStateOf(it) }
    },
)

private val NullableFilterTypeStateSaver: Saver<MutableState<FilterType?>, String> = Saver(
    save = { it.value?.name ?: "" },
    restore = { name ->
        mutableStateOf(
            if (name.isEmpty()) null else runCatching { FilterType.valueOf(name) }.getOrNull(),
        )
    },
)

@Composable
fun StarlitNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val database = remember { AppDatabase.getInstance(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val cupPresetRepository = remember { CupPresetRepository(database.cupPresetDao()) }
    val brewViewModel: BrewViewModel = viewModel(
        factory = BrewViewModelFactory(context as Application),
    )
    val calculatorViewModel: CalculatorViewModel = viewModel {
        CalculatorViewModel(cupPresetRepository, userPreferencesRepository)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val userPrefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = null,
    )

    // Track onboarding state for methods screen → personalize screen
    val onboardingMethods = rememberSaveable(saver = BrewMethodSetStateSaver) {
        mutableStateOf(emptySet<BrewMethod>())
    }
    val onboardingDefault = rememberSaveable(saver = BrewMethodStateSaver) {
        mutableStateOf(BrewMethod.PULSAR)
    }
    val onboardingFilter = rememberSaveable(saver = NullableFilterTypeStateSaver) {
        mutableStateOf<FilterType?>(null)
    }
    val onboardingGrinder = rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val showBottomBar = currentDestination?.let { dest ->
        bottomBarRoutes.any { dest.hasRoute(it) }
    } ?: false

    val widthClass = LocalWindowWidthClass.current
    // On wide windows (Medium/Expanded — tablets, foldables, landscape, desktop
    // mode), the bottom bar is replaced by a side NavigationRail per the adaptive
    // navigation guidance. This matters on Android 17 (SDK 37), where the portrait
    // lock is ignored on sw>=600dp so the app is regularly shown in wide windows.
    val useNavigationRail = showBottomBar && widthClass.isWide

    // Wait for prefs to load before rendering
    val prefs = userPrefs
    if (prefs == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val startDestination: Any = if (prefs.onboardingCompleted) CalculatorBrew else OnboardingMethods
    val snackbarHostState = remember { SnackbarHostState() }

    // Notification deep link → BrewLogDetail. The bus is owned by MainActivity;
    // we pop the pending id here, navigate, and clear it so a recompose doesn't
    // re-fire the navigation.
    val pendingBrewLogId by DeepLinkBus.pendingBrewLogId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingBrewLogId) {
        val id = pendingBrewLogId
        if (id != null && prefs.onboardingCompleted) {
            navController.navigate(BrewLogDetail(logId = id))
            DeepLinkBus.consumeBrewLogDetail()
        }
    }

    // Notification deep link → analyzed coffee-bag form. The BrewViewModel holds
    // the result that finished in the background; navigate to the bag inventory
    // and promote it so the screen's bagPhotoResult observer opens the form.
    val pendingBagAnalysis by DeepLinkBus.pendingBagAnalysis.collectAsStateWithLifecycle()
    LaunchedEffect(pendingBagAnalysis) {
        if (pendingBagAnalysis && prefs.onboardingCompleted) {
            // Recover the completed result from WorkManager's persisted output
            // (durable across the CLEAR_TOP Activity recreation), then show the
            // inventory so its dedicated review channel opens the form.
            brewViewModel.openLastBagExtractionResult()
            navController.navigate(BagInventory) { launchSingleTop = true }
            DeepLinkBus.consumeBagAnalysisReady()
        }
    }

    // Shared top-level navigation action used by both the bottom bar (compact)
    // and the side rail (wide) so the two stay behaviourally identical.
    val onSelectTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar && !useNavigationRail) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute(item.route::class) == true,
                            onClick = { onSelectTopLevel(item.route) },
                            icon = {
                                val label = stringResource(item.labelRes)
                                Icon(item.icon, contentDescription = label)
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The Scaffold reports the system-bar insets (status bar at the
                // top, nav bar at the bottom) as innerPadding, which we apply
                // above. Consume them here so descendants don't re-apply the
                // same insets: a nested Scaffold (e.g. MoreScreen) would
                // otherwise double the status-bar gap, and a bare scrollable
                // screen (e.g. SettingsScreen) would get a thick dead band of
                // doubled nav-bar inset at the bottom that clipped its content.
                .consumeWindowInsets(innerPadding),
        ) {
            if (useNavigationRail) {
                StarlitNavigationRail(
                    items = bottomNavItems,
                    currentDestination = currentDestination,
                    onSelect = onSelectTopLevel,
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                enterTransition = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 }
                },
                exitTransition = {
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                popEnterTransition = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 4 }
                },
                popExitTransition = {
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 }
                },
            ) {
                // Onboarding flow
                composable<OnboardingMethods> {
                    OnboardingMethodsScreen(
                        initialMethods = onboardingMethods.value,
                        initialDefault = onboardingDefault.value.takeIf { onboardingMethods.value.isNotEmpty() },
                        onNext = { methods, default ->
                            onboardingMethods.value = methods
                            onboardingDefault.value = default
                            navController.navigate(OnboardingPersonalize)
                        },
                    )
                }
                composable<OnboardingPersonalize> {
                    OnboardingPersonalizeScreen(
                        selectedMethods = onboardingMethods.value,
                        initialFilter = onboardingFilter.value,
                        initialGrinder = onboardingGrinder.value,
                        onBack = {
                            // Save personalize state before going back
                            navController.popBackStack()
                        },
                        onSelectionChanged = { filter, grinder ->
                            onboardingFilter.value = filter
                            onboardingGrinder.value = grinder
                        },
                        onFinish = { filterType, grinderId ->
                            scope.launch {
                                userPreferencesRepository.completeOnboarding(
                                    enabledMethods = onboardingMethods.value,
                                    defaultMethod = onboardingDefault.value,
                                    defaultFilterType = filterType,
                                    selectedGrinderId = grinderId,
                                )
                            }
                            brewViewModel.setMethod(onboardingDefault.value)
                            if (filterType != null) brewViewModel.setFilterType(filterType)
                            if (grinderId != null) brewViewModel.setGrinder(grinderId)

                            navController.navigate(CalculatorBrew) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }

                // Main app screens
                composable<CalculatorBrew> {
                    CalculatorBrewScreen(
                        calculatorViewModel = calculatorViewModel,
                        brewViewModel = brewViewModel,
                        userPreferencesRepository = userPreferencesRepository,
                        onNavigateToBrew = {
                            brewViewModel.startNewBrewSession()
                            // Quick Brew preference jumps past the grind/prep
                            // checkpoint and lands directly in the timer. Default
                            // flow keeps GrindPrep as the pre-flight checkpoint.
                            val target: Any = if (prefs.skipMethodSelection) BrewTimer else GrindPrep
                            navController.navigate(target)
                        },
                    )
                }
                composable<GrindPrep> {
                    GrindPrepScreen(
                        brewViewModel = brewViewModel,
                        dimModeEnabled = prefs.dimModeEnabled,
                        dimModeTrueBlack = prefs.dimModeTrueBlack,
                        dimModeReduceBrightness = prefs.dimModeReduceBrightness,
                        dimModeFullscreen = prefs.dimModeFullscreen,
                        dimModeForceDarkInLight = prefs.dimModeForceDarkInLight,
                        showBrewingInstructions = prefs.showBrewingInstructions,
                        onNavigateToBrew = { navController.navigate(BrewTimer) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BloomTimer> {
                    BloomTimerScreen(
                        brewViewModel = brewViewModel,
                        bloomSpritesheetWeights = prefs.bloomSpritesheetWeights,
                        dimModeEnabled = prefs.dimModeEnabled,
                        dimModeTrueBlack = prefs.dimModeTrueBlack,
                        dimModeReduceBrightness = prefs.dimModeReduceBrightness,
                        dimModeFullscreen = prefs.dimModeFullscreen,
                        dimModeForceDarkInLight = prefs.dimModeForceDarkInLight,
                        onNavigateToBrew = {
                            navController.navigate(BrewTimer) {
                                popUpTo(GrindPrep) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BrewTimer> {
                    val brewSavedMsg = stringResource(R.string.snackbar_brew_saved)
                    val viewLogAction = stringResource(R.string.snackbar_action_view_log)
                    BrewTimerScreen(
                        brewViewModel = brewViewModel,
                        bloomSpritesheetWeights = prefs.bloomSpritesheetWeights,
                        dimModeEnabled = prefs.dimModeEnabled,
                        dimModeTrueBlack = prefs.dimModeTrueBlack,
                        dimModeReduceBrightness = prefs.dimModeReduceBrightness,
                        dimModeFullscreen = prefs.dimModeFullscreen,
                        dimModeForceDarkInLight = prefs.dimModeForceDarkInLight,
                        showBrewingInstructions = prefs.showBrewingInstructions,
                        onBack = { navController.popBackStack() },
                        onComplete = {
                            // Save the brew log immediately (without feedback — user rates later)
                            brewViewModel.logBrew()
                            // Navigate back to calculator, clearing the brew flow from backstack
                            navController.navigate(CalculatorBrew) {
                                popUpTo(CalculatorBrew) { inclusive = true }
                            }
                            // Show snackbar prompting async feedback
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = brewSavedMsg,
                                    actionLabel = viewLogAction,
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    navController.navigate(BrewLogList)
                                }
                            }
                        },
                    )
                }
                composable<SavedRecipes> {
                    SavedRecipesScreen(
                        brewViewModel = brewViewModel,
                        onNavigateToAmount = { navController.navigate(CalculatorBrew) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BagInventory> { backStackEntry ->
                    val capturedPhotos by backStackEntry.savedStateHandle
                        .getStateFlow<String?>("captured_photos", null)
                        .collectAsStateWithLifecycle()
                    val scanFields by backStackEntry.savedStateHandle
                        .getStateFlow<HashMap<String, String>?>("scan_fields", null)
                        .collectAsStateWithLifecycle()
                    val scannedBarcode by backStackEntry.savedStateHandle
                        .getStateFlow<String?>("scanned_barcode", null)
                        .collectAsStateWithLifecycle()
                    BagInventoryScreen(
                        brewViewModel = brewViewModel,
                        onNavigateToCamera = { navController.navigate(GuidedScan) },
                        onNavigateToBarcode = { navController.navigate(BarcodeScanner) },
                        onBack = { navController.popBackStack() },
                        onNavigateToBrewWithBag = { bagId ->
                            brewViewModel.selectBagForBrewing(bagId)
                            navController.navigate(CalculatorBrew) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToRescan = { bagId ->
                            navController.navigate(RescanBag(bagId))
                        },
                        capturedPhotosResult = capturedPhotos,
                        scanFieldsResult = scanFields,
                        scannedBarcodeResult = scannedBarcode,
                    )
                }
                composable<BrewLogList> {
                    BrewLogScreen(
                        brewViewModel = brewViewModel,
                        onBack = null,
                        onNavigateToDetail = { logId ->
                            navController.navigate(BrewLogDetail(logId = logId))
                        },
                    )
                }
                composable<More> {
                    MoreScreen(
                        onNavigateToRecipes = { navController.navigate(SavedRecipes) },
                        onNavigateToBags = { navController.navigate(BagInventory) },
                        onNavigateToSettings = { navController.navigate(Settings) },
                    )
                }
                composable<Settings> {
                    SettingsScreen(
                        userPreferencesRepository = userPreferencesRepository,
                        cupPresetRepository = cupPresetRepository,
                        onNavigateToBloomAnimationSettings = {
                            navController.navigate(BloomAnimationSettings)
                        },
                        onNavigateToDisplaySettings = {
                            navController.navigate(DisplaySettings)
                        },
                        onNavigateToCupPresetEditor = { presetId ->
                            navController.navigate(CupPresetEditor(presetId = presetId))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BloomAnimationSettings> {
                    BloomAnimationSettingsScreen(
                        userPreferencesRepository = userPreferencesRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<DisplaySettings> {
                    DisplaySettingsScreen(
                        userPreferencesRepository = userPreferencesRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<CupPresetEditor> { backStackEntry ->
                    val route = backStackEntry.toRoute<CupPresetEditor>()
                    CupPresetEditorScreen(
                        cupPresetRepository = cupPresetRepository,
                        presetId = route.presetId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BrewLogDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<BrewLogDetail>()
                    BrewLogDetailScreen(
                        brewViewModel = brewViewModel,
                        logId = route.logId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<BarcodeScanner> {
                    BarcodeScannerScreen(
                        onBack = { navController.popBackStack() },
                        onBarcodeScanned = { barcode ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("scanned_barcode", barcode)
                            navController.popBackStack()
                        },
                    )
                }
                composable<GuidedScan> {
                    val captureViewModel: BagScanCaptureViewModel = viewModel()
                    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
                    GuidedScanFlow(
                        captureViewModel = captureViewModel,
                        brewViewModel = brewViewModel,
                        onExit = { navController.popBackStack() },
                    ) { data, callbacks ->
                        ScanAddBagReview(
                            brewViewModel = brewViewModel,
                            data = data,
                            callbacks = callbacks,
                            existingBags = bags,
                        )
                    }
                }
                composable<RescanBag> { backStackEntry ->
                    val route = backStackEntry.toRoute<RescanBag>()
                    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
                    val originalBag = remember(bags, route.bagId) {
                        bags.find { it.id == route.bagId }
                    }

                    if (originalBag == null) {
                        // Bag was deleted while navigating — go back
                        navController.popBackStack()
                        return@composable
                    }

                    val captureViewModel: BagScanCaptureViewModel = viewModel()
                    GuidedScanFlow(
                        captureViewModel = captureViewModel,
                        brewViewModel = brewViewModel,
                        onExit = { navController.popBackStack() },
                    ) { data, callbacks ->
                        ScanRescanReview(
                            brewViewModel = brewViewModel,
                            bag = originalBag,
                            data = data,
                            callbacks = callbacks,
                            onNewBag = { fields ->
                                // Fork into a new bag — hand fields to BagInventory's AddBagSheet.
                                navController.getBackStackEntry(BagInventory)
                                    .savedStateHandle
                                    .set("scan_fields", HashMap(fields))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Side navigation rail shown on wide windows (Medium / Expanded) in place of the
 * bottom navigation bar. Mirrors the bottom bar's destinations and behaviour so
 * the app's primary navigation is reachable without a bottom bar on tablets,
 * foldables, landscape phones, and desktop windows targeting Android 17.
 */
@Composable
private fun StarlitNavigationRail(
    items: List<BottomNavItem>,
    currentDestination: NavDestination?,
    onSelect: (Any) -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        items.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationRailItem(
                selected = currentDestination?.hasRoute(item.route::class) == true,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
