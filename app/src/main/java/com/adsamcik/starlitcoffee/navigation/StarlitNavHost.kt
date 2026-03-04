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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.screen.AmountStrengthScreen
import com.adsamcik.starlitcoffee.ui.screen.BagInventoryScreen
import com.adsamcik.starlitcoffee.ui.screen.BarcodeScannerScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.MethodPickerScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingMethodsScreen
import com.adsamcik.starlitcoffee.ui.screen.OnboardingPersonalizeScreen
import com.adsamcik.starlitcoffee.ui.screen.ResultScreen
import com.adsamcik.starlitcoffee.ui.screen.SavedRecipesScreen
import com.adsamcik.starlitcoffee.ui.screen.SettingsScreen
import com.adsamcik.starlitcoffee.ui.screen.TasteFeedbackScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModelFactory
import kotlinx.coroutines.launch

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any,
)

private val bottomNavItems = listOf(
    BottomNavItem("Brew", Icons.Filled.LocalCafe, MethodPicker),
    BottomNavItem("Recipes", Icons.Filled.Bookmark, SavedRecipes),
    BottomNavItem("Bags", Icons.Filled.ShoppingBag, BagInventory),
    BottomNavItem("Log", Icons.Filled.History, BrewLogList),
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
    val scope = rememberCoroutineScope()

    val showBottomBar = currentDestination?.let { dest ->
        bottomNavItems.any { dest.hasRoute(it.route::class) }
    } ?: false

    // Wait for prefs to load before rendering
    val prefs = userPrefs
    if (prefs == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val startDestination: Any = if (prefs.onboardingCompleted) MethodPicker else OnboardingMethods

    Scaffold(
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
                    onBack = { navController.popBackStack() },
                    onFinish = { filterType, grinderId ->
                        scope.launch {
                            userPreferencesRepository.completeOnboarding(
                                enabledMethods = onboardingMethods.value,
                                defaultMethod = onboardingDefault.value,
                                defaultFilterType = filterType,
                                selectedGrinderId = grinderId,
                            )
                        }
                        // Apply defaults to ViewModel immediately
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
                    navController = navController,
                    brewViewModel = brewViewModel,
                    userPreferencesRepository = userPreferencesRepository,
                )
            }
            composable<AmountStrength> {
                AmountStrengthScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<Result> {
                ResultScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<BrewTimer> {
                BrewTimerScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<TasteFeedback> {
                TasteFeedbackScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<SavedRecipes> {
                SavedRecipesScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<BagInventory> {
                BagInventoryScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<BrewLogList> {
                BrewLogScreen(navController = navController, brewViewModel = brewViewModel)
            }
            composable<BarcodeScanner> {
                BarcodeScannerScreen(
                    navController = navController,
                    onBarcodeScanned = { barcode ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_barcode", barcode)
                    },
                )
            }
            composable<Settings> {
                SettingsScreen(
                    navController = navController,
                    userPreferencesRepository = userPreferencesRepository,
                )
            }
        }
    }
}
