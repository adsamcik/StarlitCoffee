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
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupStateFactory
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileStartSelection
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartInputFactory
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartInputResult
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1ExactRecipeStartMetadata
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogRequest
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolver
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.screen.LearnBrewerScreen
import com.adsamcik.starlitcoffee.ui.screen.P1BrewerProfileSetupScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartFactory
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/** Exact P1 setup and Learn routes. Neither permits profile, recipe, plan, or equipment fallback. */
internal fun NavGraphBuilder.p1BrewingRoutes(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    durableSessionRuntime: BrewSessionRuntime,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
    snackbarHostState: SnackbarHostState,
    configuration: P1BrewingRouteConfiguration,
) {
    val exactRecipeReleaseGate = configuration.exactRecipeReleaseGate
    p1BrewerSetupRoute(
        navController = navController,
        brewViewModel = brewViewModel,
        durableSessionRuntime = durableSessionRuntime,
        snackbarHostState = snackbarHostState,
        unavailableMessage = configuration.unavailableMessage,
        exactRecipeReleaseGate = exactRecipeReleaseGate,
        onTurnOffPreview = configuration.onTurnOffPreview,
    )
    p1LearnBrewerRoute(
        navController = navController,
        guidancePreferences = guidancePreferences,
        exactRecipeReleaseGate = exactRecipeReleaseGate,
    )
}

private fun NavGraphBuilder.p1BrewerSetupRoute(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    durableSessionRuntime: BrewSessionRuntime,
    snackbarHostState: SnackbarHostState,
    unavailableMessage: String,
    exactRecipeReleaseGate: P1ExactRecipeReleaseGate,
    onTurnOffPreview: () -> Unit,
) {
    composable<BrewerProfileSetup> setupRoute@{
        val eligibleRecipeIds = exactRecipeReleaseGate.eligibleRecipeIds
        if (eligibleRecipeIds.isEmpty()) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@setupRoute
        }
        val destinationScope = rememberCoroutineScope()
        val sessionStartFactory = remember { BuiltinBrewerSessionStartFactory() }
        var setupState by remember(eligibleRecipeIds) {
            mutableStateOf(
                P1BrewerProfileSetupStateFactory.create(
                    executableRecipeIds = eligibleRecipeIds,
                ),
            )
        }

        P1BrewerProfileSetupScreen(
            state = setupState,
            onProfileSelected = { setupState = setupState.selectProfile(it) },
            onRecipeSelected = { setupState = setupState.selectRecipe(it) },
            onEquipmentOptionSelected = { setupState = setupState.selectEquipmentOption(it) },
            onEquipmentCapacityChanged = { setupState = setupState.updateEquipmentCapacity(it) },
            onMeasuredReservoirInputChanged = {
                setupState = setupState.updateMeasuredReservoirInput(it)
            },
            onCezveSugarSelected = { setupState = setupState.selectCezveSugar(it) },
            onCezveHeatSourceSelected = { setupState = setupState.selectCezveHeatSource(it) },
            onStart = { selection ->
                if (!setupState.isStarting && exactRecipeReleaseGate.isEligible(selection.builtInRecipeId)) {
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
            isGuidancePreview = eligibleRecipeIds.any(exactRecipeReleaseGate::isPreview),
            onTurnOffPreview = onTurnOffPreview,
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
)

private fun NavGraphBuilder.p1LearnBrewerRoute(
    navController: NavHostController,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
    exactRecipeReleaseGate: P1ExactRecipeReleaseGate,
) {
    composable<LearnBrewer> learnRoute@{ backStackEntry ->
        val route = backStackEntry.toRoute<LearnBrewer>()
        val recipeId = remember(route.builtInRecipeId) {
            route.builtInRecipeId?.let { rawId -> runCatching { BuiltInRecipeId(rawId) }.getOrNull() }
        }
        val recipe = remember(recipeId) { recipeId?.let(BuiltInP1RecipeCatalog::find) }
        val exactGuidance = remember(recipeId, exactRecipeReleaseGate) {
            recipeId?.let(exactRecipeReleaseGate::guidanceFor)
        }
        val recipeGuidanceCatalog = remember(recipeId, exactRecipeReleaseGate) {
            recipeId?.let(exactRecipeReleaseGate::catalogFor)
        }
        val routeIsReleaseEligible = recipe != null &&
            exactGuidance != null &&
            recipeGuidanceCatalog != null &&
            recipe.brewerProfileId.value == route.brewerProfileId
        if (!routeIsReleaseEligible) {
            LaunchedEffect(route) { navController.popBackStack() }
            return@learnRoute
        }
        val exactRecipe = requireNotNull(recipe)
        val exactRecipeGuidance = requireNotNull(exactGuidance)
        val resolver = remember(recipeGuidanceCatalog) {
            LearnGuidanceCatalogResolver(
                guidanceCatalogs = listOf(requireNotNull(recipeGuidanceCatalog)),
            )
        }
        val exactStageOrder = remember(exactRecipeGuidance) {
            exactRecipeGuidance.stages.map { stage -> stage.stageId }
        }
        val resolution = remember(route, guidancePreferences, resolver, exactStageOrder) {
            resolver.resolve(
                LearnGuidanceCatalogRequest(
                    methodFamilyId = exactRecipe.methodFamilyId.value,
                    brewerProfileId = route.brewerProfileId,
                    preferences = guidancePreferences,
                    exactStageOrder = exactStageOrder,
                ),
            )
        }
        LearnBrewerScreen(
            resolution = resolution,
            onBack = { navController.popBackStack() },
            instructionAssets = BuiltInInstructionAssetCatalog.catalog,
            isGuidancePreview = exactRecipeReleaseGate.isPreview(exactRecipe.id),
        )
    }
}
