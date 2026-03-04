package com.adsamcik.starlitcoffee.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adsamcik.starlitcoffee.ui.screen.AmountStrengthScreen
import com.adsamcik.starlitcoffee.ui.screen.BagInventoryScreen
import com.adsamcik.starlitcoffee.ui.screen.BarcodeScannerScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewLogScreen
import com.adsamcik.starlitcoffee.ui.screen.BrewTimerScreen
import com.adsamcik.starlitcoffee.ui.screen.MethodPickerScreen
import com.adsamcik.starlitcoffee.ui.screen.ResultScreen
import com.adsamcik.starlitcoffee.ui.screen.SavedRecipesScreen
import com.adsamcik.starlitcoffee.ui.screen.TasteFeedbackScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModelFactory

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

private const val TRANSITION_DURATION = 300
private const val FADE_DURATION = 200

@Composable
fun StarlitNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val brewViewModel: BrewViewModel = viewModel(
        factory = BrewViewModelFactory(context as Application),
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.let { dest ->
        bottomNavItems.any { dest.hasRoute(it.route::class) }
    } ?: true

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
            startDestination = MethodPicker,
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
            composable<MethodPicker> {
                MethodPickerScreen(navController = navController, brewViewModel = brewViewModel)
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
        }
    }
}
