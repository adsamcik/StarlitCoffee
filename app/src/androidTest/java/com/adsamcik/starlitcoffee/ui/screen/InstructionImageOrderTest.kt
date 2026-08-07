package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePolicySource
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetProvenance
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
        val asset = approvedTestInstructionAsset()
        val instruction = "Learn image-first instruction"
        val warning = "Learn warning after instruction"

        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                LearnBrewerScreen(
                    title = "Test brewer",
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
                                altText = TEST_ALT_TEXT,
                                safetyCritical = true,
                            ),
                        ),
                    ),
                    onBack = {},
                    instructionAssets = InstructionAssetCatalog(listOf(asset)),
                )
            }
        }

        assertTopToBottom(TEST_ALT_TEXT, instruction, warning)
    }

    @Test
    fun liveBrewPlacesApprovedIllustrationBeforePrimaryCriticalAndSupportingContent() {
        val asset = approvedTestInstructionAsset()
        val primaryInstruction = "Live image-first instruction"
        val criticalWarning = "Live critical warning after illustrated instruction"
        val supportingInstruction = "Live supporting instruction after warning"
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
                            brewGuidanceContent(
                                id = asset.contentId,
                                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                                instruction = primaryInstruction,
                            ),
                            brewGuidanceContent(
                                id = StageContentId("test_supporting_content"),
                                placement = BuiltInGuidancePlacement.UTILITY,
                                instruction = supportingInstruction,
                            ),
                        ),
                        criticalContent = listOf(
                            brewGuidanceContent(
                                id = StageContentId("test_critical_content"),
                                placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
                                instruction = "",
                                warning = criticalWarning,
                                safetyCritical = true,
                            ),
                        ),
                        stageSafetyMessages = emptyList(),
                        visualStatus = DurableBrewGuidanceVisualStatus.Approved(asset),
                    ),
                    sessionOverride = null,
                    onSessionOverride = {},
                    onRememberForBrewer = {},
                )
            }
        }

        assertTopToBottom(
            TEST_ALT_TEXT,
            primaryInstruction,
            criticalWarning,
            supportingInstruction,
            guidanceLevel,
        )
    }

    private fun brewGuidanceContent(
        id: StageContentId,
        placement: BuiltInGuidancePlacement,
        instruction: String,
        warning: String? = null,
        safetyCritical: Boolean = false,
    ): ResolvedBrewGuidanceContent = ResolvedBrewGuidanceContent(
        id = id,
        placement = placement,
        instruction = instruction,
        target = null,
        completionCue = null,
        explanation = null,
        tip = null,
        nextAction = null,
        controlRequirements = emptyList(),
        warning = warning,
        utilities = emptyList(),
        altText = TEST_ALT_TEXT,
        safetyCritical = safetyCritical,
    )

    private fun approvedTestInstructionAsset(): InstructionAssetRecord = InstructionAssetRecord(
        id = InstructionAssetId(
            "instruction_${TEST_FAMILY.value}_${TEST_PROFILE.value}_${TEST_CONTENT.value}_default",
        ),
        familyId = TEST_FAMILY,
        profileId = TEST_PROFILE,
        stageId = TEST_STAGE,
        contentId = TEST_CONTENT,
        drawableRes = R.drawable.vessel_icon_mug,
        altTextRes = R.string.app_name,
        companionInstructionRes = R.string.app_name,
        mandatoryForFullGuidance = false,
        safetySensitive = false,
        provenance = InstructionAssetProvenance(
            promptDocument = "android-test-fixture",
            promptRevision = "instruction-image-order-v1",
        ),
        review = InstructionAssetReview(
            status = InstructionAssetReviewStatus.APPROVED,
            reviewer = "test",
            reviewedOn = LocalDate.of(2026, 8, 1),
        )
    )

    private companion object {
        val TEST_FAMILY = MethodFamilyId("manual_gravity")
        val TEST_PROFILE = BrewerProfileId("v60_02")
        val TEST_STAGE = StageId("test_live_stage")
        val TEST_CONTENT = StageContentId("test_live_stage_content")
        const val TEST_ALT_TEXT = "Approved instructional illustration"
    }

    private fun guidanceLevelLabel(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(
            R.string.format_brew_guidance_level,
            context.getString(R.string.guidance_level_concise),
        )
    }

    private fun assertTopToBottom(contentDescription: String, vararg labels: String) {
        val imageTop = composeRule
            .onNodeWithContentDescription(contentDescription)
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
