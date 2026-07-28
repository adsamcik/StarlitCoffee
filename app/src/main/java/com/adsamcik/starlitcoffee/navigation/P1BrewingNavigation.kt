package com.adsamcik.starlitcoffee.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewerEquipmentDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupStateFactory
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogRequest
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolver
import com.adsamcik.starlitcoffee.ui.guidance.P1BuiltInGuidanceCatalog
import com.adsamcik.starlitcoffee.ui.screen.LearnBrewerScreen
import com.adsamcik.starlitcoffee.ui.screen.P1BrewerProfileSetupScreen
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartFactory
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartInput
import com.adsamcik.starlitcoffee.viewmodel.BuiltinBrewerSessionStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Keeps the P1 profile setup and Learn routes separate from the legacy
 * calculator route. Both surfaces use stable profile IDs and the durable
 * coordinator; neither may fall back to a nearby brewer or generic plan.
 */
fun NavGraphBuilder.p1BrewingRoutes(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    durableSessionRuntime: BrewSessionRuntime,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
    snackbarHostState: SnackbarHostState,
    unavailableMessage: String,
) {
    composable<BrewerProfileSetup> {
        val destinationScope = rememberCoroutineScope()
        val sessionStartFactory = remember { BuiltinBrewerSessionStartFactory() }
        val equipmentDefaults = remember { BuiltinBrewerEquipmentDefaults() }
        var setupState by remember {
            mutableStateOf(P1BrewerProfileSetupStateFactory.create())
        }

        P1BrewerProfileSetupScreen(
            state = setupState,
            onProfileSelected = { profileId ->
                setupState = setupState.selectProfile(profileId)
            },
            onHarioSwitchWorkflowSelected = { workflow ->
                setupState = setupState.selectHarioSwitchWorkflow(workflow)
            },
            onEquipmentCapacityChanged = { rawCapacity ->
                setupState = setupState.updateEquipmentCapacity(rawCapacity)
            },
            onCezveSugarSelected = { includeSugar ->
                setupState = setupState.selectCezveSugar(includeSugar)
            },
            onCezveFoamRiseCyclesSelected = { cycles ->
                setupState = setupState.selectCezveFoamRiseCycles(cycles)
            },
            onCezveHeatSourceSelected = { heatSource ->
                setupState = setupState.selectCezveHeatSource(heatSource)
            },
            onStart = { selection ->
                if (!setupState.isStarting) {
                    setupState = setupState.withStarting(true)
                    destinationScope.launch {
                        try {
                            val legacyState = brewViewModel.uiState.value
                            val doseG = legacyState.coffeeG.toDouble()
                            val equipment = equipmentDefaults.create(selection.brewerProfileId)?.copy(
                                capacityOverrideG = selection.equipmentCapacityG,
                                heatSource = selection.heatSource,
                            )
                            if (!doseG.isFinite() || doseG <= 0.0 || equipment == null) {
                                snackbarHostState.showSnackbar(unavailableMessage)
                            } else {
                                val result = sessionStartFactory.create(
                                    BuiltinBrewerSessionStartInput(
                                        brewerProfileId = selection.brewerProfileId,
                                        dryCoffeeDoseG = doseG,
                                        equipment = equipment,
                                        harioSwitchWorkflow = selection.harioSwitchWorkflow,
                                        cezveSetup = selection.cezveSetup,
                                        temperatureC = legacyState.tempC.toIntOrNull(),
                                        grinderId = legacyState.selectedGrinderId,
                                        isDecaf = legacyState.isDecafBrew,
                                        notes = legacyState.feedbackNotes,
                                        coffeeBagId = brewViewModel.selectedBagId.value,
                                    ),
                                )
                                val request = (result as? BuiltinBrewerSessionStartResult.Ready)?.request
                                if (request == null) {
                                    snackbarHostState.showSnackbar(unavailableMessage)
                                } else {
                                    when (durableSessionRuntime.coordinator.createOrResume(request)) {
                                        is com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult.Active,
                                        is com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult.PendingEffect,
                                        -> navController.navigate(BrewSession(request.sessionId.value)) {
                                            launchSingleTop = true
                                        }

                                        else -> snackbarHostState.showSnackbar(unavailableMessage)
                                    }
                                }
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
            onLearn = { selection ->
                navController.navigate(
                    LearnBrewer(
                        brewerProfileId = selection.brewerProfileId.value,
                        harioSwitchWorkflow = selection.harioSwitchWorkflow?.name,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable<LearnBrewer> { backStackEntry ->
        val route = backStackEntry.toRoute<LearnBrewer>()
        val profile = remember(route.brewerProfileId) {
            runCatching { BrewerProfileId(route.brewerProfileId) }
                .getOrNull()
                ?.let(BuiltinBrewingCatalog.instance::findBrewerProfile)
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
        )
    }
}
