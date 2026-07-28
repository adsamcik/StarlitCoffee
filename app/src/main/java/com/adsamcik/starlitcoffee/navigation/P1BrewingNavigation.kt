package com.adsamcik.starlitcoffee.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupStateFactory
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileStartSelection
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartInputFactory
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartInputResult
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartMetadata
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogRequest
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolver
import com.adsamcik.starlitcoffee.ui.guidance.P1BuiltInGuidanceCatalog
import com.adsamcik.starlitcoffee.ui.screen.LearnBrewerScreen
import com.adsamcik.starlitcoffee.ui.screen.P1BrewerProfileSetupScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartFactory
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Exact P1 setup and Learn routes. Neither permits profile, recipe, plan, or equipment fallback. */
fun NavGraphBuilder.p1BrewingRoutes(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    durableSessionRuntime: BrewSessionRuntime,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
    snackbarHostState: SnackbarHostState,
    unavailableMessage: String,
) {
    p1BrewerSetupRoute(
        navController = navController,
        brewViewModel = brewViewModel,
        durableSessionRuntime = durableSessionRuntime,
        snackbarHostState = snackbarHostState,
        unavailableMessage = unavailableMessage,
    )
    p1LearnBrewerRoute(
        navController = navController,
        guidancePreferences = guidancePreferences,
    )
}

private fun NavGraphBuilder.p1BrewerSetupRoute(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    durableSessionRuntime: BrewSessionRuntime,
    snackbarHostState: SnackbarHostState,
    unavailableMessage: String,
) {
    composable<BrewerProfileSetup> setupRoute@{
        val releaseEligibleProfileIds = P1BuiltInGuidanceCatalog.releaseEligibleProfileIds
        if (releaseEligibleProfileIds.isEmpty()) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@setupRoute
        }
        val destinationScope = rememberCoroutineScope()
        val sessionStartFactory = remember { BuiltinBrewerSessionStartFactory() }
        var setupState by remember {
            mutableStateOf(
                P1BrewerProfileSetupStateFactory.create(
                    visibleProfileIds = releaseEligibleProfileIds,
                ),
            )
        }

        P1BrewerProfileSetupScreen(
            state = setupState,
            onProfileSelected = { setupState = setupState.selectProfile(it) },
            onRecipeSelected = { setupState = setupState.selectRecipe(it) },
            onEquipmentOptionSelected = { setupState = setupState.selectEquipmentOption(it) },
            onEquipmentCapacityChanged = { setupState = setupState.updateEquipmentCapacity(it) },
            onCezveSugarSelected = { setupState = setupState.selectCezveSugar(it) },
            onCezveHeatSourceSelected = { setupState = setupState.selectCezveHeatSource(it) },
            onStart = { selection ->
                if (!setupState.isStarting) {
                    setupState = setupState.withStarting(true)
                    destinationScope.launch {
                        try {
                            val legacyState = brewViewModel.uiState.value
                            val sessionId = createOrResumeExactSession(
                                selection = selection,
                                metadata = P1ExactRecipeStartMetadata(
                                    grinderId = legacyState.selectedGrinderId,
                                    isDecaf = legacyState.isDecafBrew,
                                    notes = legacyState.feedbackNotes,
                                    coffeeBagId = brewViewModel.selectedBagId.value,
                                ),
                                sessionStartFactory = sessionStartFactory,
                                durableSessionRuntime = durableSessionRuntime,
                            )
                            if (sessionId == null) {
                                snackbarHostState.showSnackbar(unavailableMessage)
                            } else {
                                navController.navigate(BrewSession(sessionId)) { launchSingleTop = true }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(unavailableMessage)
                        } finally {
                            setupState = setupState.withStarting(false)
                        }
                    }
                }
            },
            onLearn = { selection -> navController.navigate(selection.learnRoute()) },
            onBack = { navController.popBackStack() },
        )
    }
}

private suspend fun createOrResumeExactSession(
    selection: P1BrewerProfileStartSelection,
    metadata: P1ExactRecipeStartMetadata,
    sessionStartFactory: BuiltinBrewerSessionStartFactory,
    durableSessionRuntime: BrewSessionRuntime,
): String? {
    val input = when (val result = P1ExactRecipeStartInputFactory.create(selection, metadata)) {
        is P1ExactRecipeStartInputResult.Ready -> result.input
        P1ExactRecipeStartInputResult.Unavailable -> return null
    }
    val request = (sessionStartFactory.create(input) as? BuiltinBrewerSessionStartResult.Ready)
        ?.request
        ?: return null
    return when (durableSessionRuntime.coordinator.createOrResume(request)) {
        is BrewSessionOperationResult.Active,
        is BrewSessionOperationResult.PendingEffect,
        -> request.sessionId.value

        else -> null
    }
}

private fun P1BrewerProfileStartSelection.learnRoute(): LearnBrewer = LearnBrewer(
    brewerProfileId = brewerProfileId.value,
    builtInRecipeId = builtInRecipeId.value,
    harioSwitchWorkflow = harioSwitchWorkflow?.name,
)

private fun NavGraphBuilder.p1LearnBrewerRoute(
    navController: NavHostController,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
) {
    composable<LearnBrewer> learnRoute@{ backStackEntry ->
        val route = backStackEntry.toRoute<LearnBrewer>()
        val recipeId = remember(route.builtInRecipeId) {
            route.builtInRecipeId?.let { rawId -> runCatching { BuiltInRecipeId(rawId) }.getOrNull() }
        }
        val recipe = remember(recipeId) { recipeId?.let(BuiltInP1RecipeCatalog::find) }
        val routeIsReleaseEligible = recipe != null &&
            recipe.brewerProfileId.value == route.brewerProfileId &&
            recipe.brewerProfileId in P1BuiltInGuidanceCatalog.releaseEligibleProfileIds &&
            BuiltInP1ExactStagePlanCatalog.find(recipe.id) != null
        if (!routeIsReleaseEligible) {
            LaunchedEffect(route) { navController.popBackStack() }
            return@learnRoute
        }
        val profile = remember(recipe) {
            BuiltinBrewingCatalog.instance.findBrewerProfile(requireNotNull(recipe).brewerProfileId)
        }
        val resolver = remember { LearnGuidanceCatalogResolver() }
        val harioSwitchWorkflow = remember(route.harioSwitchWorkflow) {
            route.harioSwitchWorkflow?.let { rawWorkflow ->
                HarioSwitchWorkflow.entries.firstOrNull { workflow -> workflow.name == rawWorkflow }
            }
        }
        val resolution = remember(route, guidancePreferences, profile) {
            resolver.resolve(
                LearnGuidanceCatalogRequest(
                    methodFamilyId = profile?.familyId?.value.orEmpty(),
                    brewerProfileId = route.brewerProfileId,
                    harioSwitchWorkflow = harioSwitchWorkflow,
                    preferences = guidancePreferences,
                ),
            )
        }
        LearnBrewerScreen(
            resolution = resolution,
            hasPendingVisualAssets = P1BuiltInGuidanceCatalog.plannedVisualAssets.any { asset ->
                asset.profileId.value == route.brewerProfileId
            },
            onBack = { navController.popBackStack() },
            instructionAssets = BuiltInInstructionAssetCatalog.catalog,
        )
    }
}
