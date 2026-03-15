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
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.screen.AmountStrengthScreen
import com.adsamcik.starlitcoffee.ui.screen.BagInventoryScreen
import com.adsamcik.starlitcoffee.ui.screen.BarcodeScannerScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogDetailScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.LiveScanScreen
import com.adsamcik.starlitcoffee.ui.screen.MethodPickerScreen
import com.adsamcik.starlitcoffee.ui.screen.MoreScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingMethodsScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingPersonalizeScreen
import com.adsamcik.starlitcoffee.ui.screen.SavedRecipesScreen
import com.adsamcik.starlitcoffee.ui.screen.SettingsScreen
import com.adsamcik.starlitcoffee.ui.screen.TasteFeedbackScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModelFactory
import com.adsamcik.starlitcoffee.viewmodel.LiveScanViewModel
import java.util.HashMap
import kotlinx.coroutines.launch

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any,
)

private val bottomNavItems = listOf(
    BottomNavItem("Brew", Icons.Filled.LocalCafe, MethodPicker),
    BottomNavItem("Log", Icons.Filled.History, BrewLogList),
    BottomNavItem("More", Icons.Filled.MoreHoriz, More),
)

private val onboardingRoutes = setOf(
    OnboardingMethods::class,
    OnboardingPersonalize::class,
)

private const val TRANSITION_DURATION = 300
private const val FADE_DURATION = 200

@Composable
fun StarlitNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val brewViewModel: BrewViewModel = viewModel(
        factory = BrewViewModelFactory(context as Application),
    )
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
        bottomNavItems.any { dest.hasRoute(it.route::class) }
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

    val startDestination: Any = if (prefs.onboardingCompleted) MethodPicker else OnboardingMethods
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
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

                        navController.navigate(MethodPicker) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            // Main app screens
            composable<MethodPicker> {
                MethodPickerScreen(
                    brewViewModel = brewViewModel,
                    userPreferencesRepository = userPreferencesRepository,
                    onNavigateToSettings = { navController.navigate(Settings) },
                    onNavigateToTimer = { navController.navigate(BrewTimer) },
                )
            }
            composable<AmountStrength> {
                AmountStrengthScreen(
                    brewViewModel = brewViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToTimer = { navController.navigate(BrewTimer) },
                )
            }
            composable<BrewTimer> {
                BrewTimerScreen(
                    brewViewModel = brewViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<TasteFeedback> {
                TasteFeedbackScreen(
                    brewViewModel = brewViewModel,
                    onSaveAndFinish = {
                        navController.navigate(MethodPicker) {
                            popUpTo(MethodPicker) { inclusive = true }
                        }
                    },
                    onNavigateToResult = {
                        navController.popBackStack(Result, inclusive = false)
                    },
                )
            }
            composable<SavedRecipes> {
                SavedRecipesScreen(
                    brewViewModel = brewViewModel,
                    onNavigateToAmount = { navController.navigate(AmountStrength) },
                )
            }
            composable<BagInventory> { backStackEntry ->
                val capturedPhotos by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("captured_photos", null)
                    .collectAsStateWithLifecycle()
                val scanFields by backStackEntry.savedStateHandle
                    .getStateFlow<HashMap<String, String>?>("scan_fields", null)
                    .collectAsStateWithLifecycle()
                BagInventoryScreen(
                    brewViewModel = brewViewModel,
                    onNavigateToCamera = { navController.navigate(LiveScan) },
                    capturedPhotosResult = capturedPhotos,
                    scanFieldsResult = scanFields,
                )
            }
            composable<BrewLogList> {
                BrewLogScreen(
                    brewViewModel = brewViewModel,
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
                val liveScanViewModel: LiveScanViewModel = viewModel()
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
            composable<Settings> {
                SettingsScreen(
                    userPreferencesRepository = userPreferencesRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
