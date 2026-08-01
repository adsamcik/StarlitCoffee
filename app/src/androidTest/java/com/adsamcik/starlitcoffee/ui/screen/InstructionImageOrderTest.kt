package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePolicySource
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetReview
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetReviewStatus
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedGuidancePolicy
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstructionImageOrderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun learnPlacesApprovedIllustrationBeforeItsInstructionAndWarning() {
        val asset = approvedAsset()
        val instruction = "Learn image-first instruction"
        val warning = "Learn warning after instruction"

        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                LearnBrewerScreen(
                    resolution = LearnGuidanceCatalogResolution(
                        policy = null,
                        availability = LearnGuidanceCatalogAvailability.Available,
                        content = listOf(
                            ResolvedLearnGuidanceContent(
                                id = asset.contentId,
                                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                                instruction = instruction,
                                target = null,
                                completionCue = null,
                                explanation = null,
                                tip = null,
                                nextAction = null,
                                controlRequirements = emptyList(),
                                warning = warning,
                                utilities = emptyList(),
                                altText = "Approved instructional illustration",
                                safetyCritical = true,
                            ),
                        ),
                    ),
                    hasPendingVisualAssets = false,
                    onBack = {},
                    instructionAssets = InstructionAssetCatalog(listOf(asset)),
                )
            }
        }

        assertTopToBottom(asset, instruction, warning)
    }

    @Test
    fun liveBrewPlacesApprovedIllustrationAndInstructionBeforeGuidanceControl() {
        val asset = approvedAsset()
        val instruction = "Live image-first instruction"
        val target = "Live target after instruction"
        val guidanceLevel = guidanceLevelLabel()

        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                BrewSessionGuidancePanel(
                    resolution = DurableBrewSessionGuidanceResolution(
                        policy = ResolvedGuidancePolicy(
                            methodFamilyId = asset.familyId,
                            brewerProfileId = asset.profileId,
                            level = GuidancePresentationLevel.CONCISE,
                            source = GuidancePolicySource.SAFE_DEFAULT,
                        ),
                        availability = DurableBrewGuidanceAvailability.Available,
                        routineContent = listOf(
                            ResolvedBrewGuidanceContent(
                                id = asset.contentId,
                                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                                instruction = instruction,
                                target = target,
                                completionCue = null,
                                explanation = null,
                                tip = null,
                                nextAction = null,
                                controlRequirements = emptyList(),
                                warning = null,
                                utilities = emptyList(),
                                altText = "Approved instructional illustration",
                                safetyCritical = false,
                            ),
                        ),
                        criticalContent = emptyList(),
                        stageSafetyMessages = emptyList(),
                        visualStatus = DurableBrewGuidanceVisualStatus.Approved(asset),
                    ),
                    sessionOverride = null,
                    onSessionOverride = {},
                    onRememberForBrewer = {},
                )
            }
        }

        assertTopToBottom(asset, instruction, target, guidanceLevel)
    }

    private fun approvedAsset(): InstructionAssetRecord = BuiltInInstructionAssetCatalog.catalog.assets
        .first { asset -> asset.altTextRes == R.string.action_brew_add_coffee }
        .copy(
            review = InstructionAssetReview(
                status = InstructionAssetReviewStatus.APPROVED,
                reviewer = "test",
                reviewedOn = LocalDate.of(2026, 8, 1),
            ),
        )

    private fun guidanceLevelLabel(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(
            R.string.format_brew_guidance_level,
            context.getString(R.string.guidance_level_concise),
        )
    }

    private fun assertTopToBottom(asset: InstructionAssetRecord, vararg labels: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageTop = composeRule
            .onNodeWithContentDescription(context.getString(asset.altTextRes))
            .getBoundsInRoot()
            .top
        var previousTop = imageTop
        labels.forEach { label ->
            val currentTop = composeRule
                .onNodeWithText(label)
                .getBoundsInRoot()
                .top
            assertTrue("Expected '$label' below the preceding instruction content", currentTop > previousTop)
            previousTop = currentTop
        }
    }
}
