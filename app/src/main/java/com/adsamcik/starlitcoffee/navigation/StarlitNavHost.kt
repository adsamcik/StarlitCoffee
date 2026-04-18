package com.adsamcik.starlitcoffee.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.adsamcik.starlitcoffee.ui.screen.BagInventoryScreen
import com.adsamcik.starlitcoffee.ui.screen.BarcodeScannerScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogDetailScreen
import com.adsamcik.starlitcoffee.ui.screen.CalculatorBrewScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.BloomTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.GrindPrepScreen
import com.adsamcik.starlitcoffee.ui.screen.LiveScanScreen
import com.adsamcik.starlitcoffee.ui.screen.MoreScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingMethodsScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingPersonalizeScreen
import com.adsamcik.starlitcoffee.ui.screen.SavedRecipesScreen
import com.adsamcik.starlitcoffee.ui.screen.SettingsScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModelFactory
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import com.adsamcik.starlitcoffee.viewmodel.LiveScanViewModel
import com.adsamcik.starlitcoffee.viewmodel.LiveScanViewModelFactory
import com.adsamcik.starlitcoffee.ui.component.RescanDeltaDialog
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

private const val TRANSITION_DURATION = 300
private const val FADE_DURATION = 200

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
    val onboardingMethods = remember { mutableStateOf(emptySet<BrewMethod>()) }
    val onboardingDefault = remember { mutableStateOf(BrewMethod.PULSAR) }
    val onboardingFilter = remember { mutableStateOf<FilterType?>(null) }
    val onboardingGrinder = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val showBottomBar = currentDestination?.let { dest ->
        bottomBarRoutes.any { dest.hasRoute(it) }
    } ?: false

    // Handle rating reminder from notification tap
    val activity = LocalContext.current as? com.adsamcik.starlitcoffee.MainActivity
    val pendingRatingId = activity?.pendingRatingBrewId
    if (pendingRatingId != null) {
        com.adsamcik.starlitcoffee.ui.component.BrewRatingSheet(
            onDismiss = { activity.clearPendingRating() },
            onSave = { rating, descriptors, notes ->
                brewViewModel.saveRatingForLog(pendingRatingId, rating, descriptors, notes)
                com.adsamcik.starlitcoffee.service.RatingReminderWorker.cancel(context, pendingRatingId)
                activity.clearPendingRating()
            },
        )
    }

    // Wait for prefs to load before rendering
    val prefs = userPrefs
    if (prefs == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val startDestination: Any = if (prefs.onboardingCompleted) CalculatorBrew else OnboardingMethods
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(TRANSITION_DURATION)) +
                    slideInHorizontally(tween(TRANSITION_DURATION)) { it / 4 }
            },
            exitTransition = {
                fadeOut(tween(FADE_DURATION))
            },
            popEnterTransition = {
                fadeIn(tween(TRANSITION_DURATION)) +
                    slideInHorizontally(tween(TRANSITION_DURATION)) { -it / 4 }
            },
            popExitTransition = {
                fadeOut(tween(FADE_DURATION)) +
                    slideOutHorizontally(tween(FADE_DURATION)) { it / 4 }
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
                        navController.navigate(GrindPrep)
                    },
                )
            }
            composable<GrindPrep> {
                GrindPrepScreen(
                    brewViewModel = brewViewModel,
                    onNavigateToBrew = { navController.navigate(BrewTimer) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<BloomTimer> {
                BloomTimerScreen(
                    brewViewModel = brewViewModel,
                    onNavigateToBrew = {
                        navController.navigate(BrewTimer) {
                            popUpTo(GrindPrep) { inclusive = true }
                        }
                    },
                    onBack = {
                        brewViewModel.pauseTimer()
                        navController.popBackStack()
                    },
                )
            }
            composable<BrewTimer> {
                val brewSavedMsg = stringResource(R.string.snackbar_brew_saved)
                val viewLogAction = stringResource(R.string.snackbar_action_view_log)
                BrewTimerScreen(
                    brewViewModel = brewViewModel,
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
                    onNavigateToCamera = { navController.navigate(LiveScan) },
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
                    onBack = { navController.popBackStack() },
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
            composable<LiveScan> {
                val liveScanViewModel: LiveScanViewModel = viewModel(
                    factory = LiveScanViewModelFactory(context as Application),
                )
                LiveScanScreen(
                    liveScanViewModel = liveScanViewModel,
                    brewViewModel = brewViewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onSaveComplete = {
                        navController.popBackStack()
                    },
                    onNavigateToReview = { resolvedFields ->
                        // Pass resolved fields to BagInventory via savedStateHandle
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scan_fields", HashMap(resolvedFields))
                        navController.popBackStack()
                    },
                )
            }
            composable<RescanBag> { backStackEntry ->
                val route = backStackEntry.toRoute<RescanBag>()
                val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
                val originalBag = remember(bags, route.bagId) {
                    bags.find { it.id == route.bagId }
                }
                val rescanFields by backStackEntry.savedStateHandle
                    .getStateFlow<HashMap<String, String>?>("rescan_fields", null)
                    .collectAsStateWithLifecycle()

                if (originalBag == null) {
                    // Bag was deleted while navigating — go back
                    navController.popBackStack()
                    return@composable
                }

                val pendingRescan = rescanFields
                if (pendingRescan != null) {
                    // Scan complete — show delta dialog
                    RescanDeltaDialog(
                        bag = originalBag,
                        resolvedFields = pendingRescan,
                        onUpdateBag = { updatedBag ->
                            brewViewModel.updateCoffeeBag(updatedBag)
                            navController.popBackStack()
                        },
                        onNewBag = { fields ->
                            // Pass fields to BagInventory for AddBagSheet
                            navController.getBackStackEntry(BagInventory)
                                .savedStateHandle
                                .set("scan_fields", HashMap(fields))
                            navController.popBackStack()
                        },
                        onDismiss = {
                            navController.popBackStack()
                        },
                    )
                } else {
                    // Launch LiveScan for rescan
                    val liveScanViewModel: LiveScanViewModel = viewModel(
                        factory = LiveScanViewModelFactory(context as Application),
                    )
                    LiveScanScreen(
                        liveScanViewModel = liveScanViewModel,
                        brewViewModel = brewViewModel,
                        onBack = {
                            navController.popBackStack()
                        },
                        onSaveComplete = {
                            navController.popBackStack()
                        },
                        onNavigateToReview = { resolvedFields ->
                            backStackEntry.savedStateHandle
                                .set("rescan_fields", HashMap(resolvedFields))
                        },
                    )
                }
            }
            composable<Settings> {
                SettingsScreen(
                    userPreferencesRepository = userPreferencesRepository,
                    cupPresetRepository = cupPresetRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
