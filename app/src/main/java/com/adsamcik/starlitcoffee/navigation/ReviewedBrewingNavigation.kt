package com.adsamcik.starlitcoffee.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.guides.familyId
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.ui.guidance.learnResolution
import com.adsamcik.starlitcoffee.ui.screen.LearnBrewerScreen
import com.adsamcik.starlitcoffee.ui.screen.LearningLibraryGuideOption
import com.adsamcik.starlitcoffee.ui.screen.LearningLibraryProfileOption
import com.adsamcik.starlitcoffee.ui.screen.ReviewedGuideStartSheet
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun rememberReviewedMethodGuides(): List<ReviewedMethodGuide> {
    val context = LocalContext.current
    return remember(context) {
        ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() })
    }
}

/** One profile row owns its source recipes and existing eligible exact recipes. */
internal fun reviewedLearningProfiles(
    guides: List<ReviewedMethodGuide>,
    existing: List<LearningLibraryProfileOption>,
): List<LearningLibraryProfileOption> {
    val reviewed = guides.map { guide -> LearningLibraryProfileOption(BrewerProfileId(guide.profileId),
        guide.methodName, requireNotNull(BuiltinBrewingCatalog.instance.findMethodFamily(guide.familyId)).displayName,
        listOf(LearningLibraryGuideOption.Reviewed(guide))) }
    return (reviewed + existing).groupBy { it.profileId }.values.map { profiles ->
        profiles.first().copy(guides = profiles.flatMap { it.guides }.distinctBy { it.stableId })
    }
}

@Composable
internal fun ReviewedLearnDestination(
    guide: ReviewedMethodGuide,
    brewViewModel: BrewViewModel,
    runtime: BrewSessionRuntime,
    onBack: () -> Unit,
    onSession: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var preparing by remember(guide.id) { mutableStateOf(false) }
    var starting by remember(guide.id) { mutableStateOf(false) }
    var failed by remember(guide.id) { mutableStateOf(false) }
    LearnBrewerScreen(title = guide.methodName, resolution = remember(guide) { guide.learnResolution() },
        reviewedGuide = guide, onBack = onBack, onBrewGuide = { preparing = true; failed = false })
    if (preparing) {
        ReviewedGuideStartSheet(guide, brewViewModel, starting, failed, onDismiss = { preparing = false },
            onStart = { dose, water ->
                if (!starting) {
                    starting = true
                    scope.launch {
                        try {
                            val request = ReviewedGuideStartFactory.create(guide, brewViewModel.uiState.value,
                                brewViewModel.selectedBagId.value, dose, water)
                            when (runtime.coordinator.createOrResume(request)) {
                                is BrewSessionOperationResult.Active, is BrewSessionOperationResult.PendingEffect -> {
                                    preparing = false
                                    onSession(request.sessionId.value)
                                }
                                else -> failed = true
                            }
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { failed = true }
                        finally { starting = false }
                    }
                }
            })
    }
}
