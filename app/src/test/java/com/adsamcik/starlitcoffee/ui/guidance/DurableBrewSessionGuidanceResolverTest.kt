package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableBrewSessionGuidanceResolverTest {

    @Test
    fun `full guidance keeps authored supporting copy and critical safety`() {
        val criticalStageSafety = StageSafetyMessage(
            code = "pulsar_keep_brewer_stable",
            severity = StageSafetySeverity.CRITICAL,
        )

        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            request(
                stage = stage(safetyMessages = listOf(criticalStageSafety)),
                level = GuidancePresentationLevel.FULL,
            ),
        )

        val bloom = resolution.routineContent.single { content ->
            content.id == StageContentId("pulsar_bloom")
        }
        assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
        assertEquals(GuidancePresentationLevel.FULL, resolution.policy?.level)
        assertEquals("Close the valve, pour the bloom water through the dispersion cap, then let the coffee bloom for your recipe's time.", bloom.instruction)
        assertTrue(bloom.explanation?.isNotBlank() == true)
        assertTrue(resolution.criticalContent.any { content ->
            content.id == StageContentId("pulsar_hot_liquid_safety")
        })
        assertEquals(listOf(criticalStageSafety), resolution.criticalStageSafetyMessages)
    }

    @Test
    fun `concise and focused guidance use the concise action without supporting copy`() {
        val resolver = DurableBrewSessionGuidanceResolver()

        listOf(
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.FOCUSED,
        ).forEach { level ->
            val resolution = resolver.resolve(request(level = level))
            val bloom = resolution.routineContent.single { content ->
                content.id == StageContentId("pulsar_bloom")
            }

            assertEquals("Close valve. Pour bloom water. Let it bloom.", bloom.instruction)
            assertNull(bloom.explanation)
            assertNull(bloom.tip)
        }
    }

    @Test
    fun `utilities only renders only utility records that opt into that level`() {
        val resolver = DurableBrewSessionGuidanceResolver(
            guidanceCatalogs = listOf(
                BuiltInGuidanceCatalog(
                    listOf(
                        stageContent(
                            visibility = GuidanceVisibilityPolicy(
                                visibleIn = setOf(GuidancePresentationLevel.FULL),
                            ),
                        ),
                        utilityContent(),
                        globalSafetyContent(),
                    ),
                ),
            ),
        )

        val resolution = resolver.resolve(
            request(level = GuidancePresentationLevel.UTILITIES_ONLY),
        )

        assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
        assertEquals(listOf(StageContentId("pulsar_live_utility")), resolution.routineContent.map { it.id })
        assertEquals(
            listOf(StageContentId("pulsar_global_safety")),
            resolution.criticalContent.map { it.id },
        )
    }

    @Test
    fun `missing profile curriculum does not fall back to Pulsar guidance`() {
        val criticalStageSafety = StageSafetyMessage(
            code = "hario_switch_hot_glass_thermal_shock",
            severity = StageSafetySeverity.CRITICAL,
        )
        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            DurableBrewSessionGuidanceRequest(
                methodFamilyId = "steep_and_release",
                brewerProfileId = "hario_switch",
                currentStage = stage(
                    stageId = StageId("hario_switch_open_valve"),
                    contentId = StageContentId("hario_switch_open_valve"),
                    safetyMessages = listOf(criticalStageSafety),
                ),
            ),
        )

        assertEquals(
            DurableBrewGuidanceAvailability.NoGuidanceCatalogForProfile(
                MethodFamilyId("steep_and_release"),
                BrewerProfileId("hario_switch"),
            ),
            resolution.availability,
        )
        assertTrue(resolution.routineContent.isEmpty())
        assertTrue(resolution.criticalContent.isEmpty())
        assertEquals(listOf(criticalStageSafety), resolution.criticalStageSafetyMessages)
    }

    @Test
    fun `missing stage content remains explicit while global safety remains visible`() {
        val criticalStageSafety = StageSafetyMessage(
            code = "pulsar_overflow_risk",
            severity = StageSafetySeverity.CRITICAL,
        )
        val unknownStage = stage(
            stageId = StageId("pulsar_unknown_stage"),
            contentId = StageContentId("pulsar_unknown_stage"),
            safetyMessages = listOf(criticalStageSafety),
        )

        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            request(stage = unknownStage),
        )

        assertEquals(
            DurableBrewGuidanceAvailability.MissingStageContent(
                StageId("pulsar_unknown_stage"),
                StageContentId("pulsar_unknown_stage"),
            ),
            resolution.availability,
        )
        assertTrue(resolution.criticalContent.any { content ->
            content.id == StageContentId("pulsar_hot_liquid_safety")
        })
        assertEquals(listOf(criticalStageSafety), resolution.criticalStageSafetyMessages)
    }

    @Test
    fun `invalid persisted identifiers retain stage safety and never pick a default profile`() {
        val criticalStageSafety = StageSafetyMessage(
            code = "session_safety_still_visible",
            severity = StageSafetySeverity.CRITICAL,
        )

        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            DurableBrewSessionGuidanceRequest(
                methodFamilyId = "valve_controlled_no_bypass",
                brewerProfileId = "PULSAR",
                currentStage = stage(safetyMessages = listOf(criticalStageSafety)),
            ),
        )

        assertEquals(
            DurableBrewGuidanceAvailability.InvalidBrewerProfileId("PULSAR"),
            resolution.availability,
        )
        assertNull(resolution.policy)
        assertTrue(resolution.routineContent.isEmpty())
        assertEquals(listOf(criticalStageSafety), resolution.criticalStageSafetyMessages)
    }

    @Test
    fun `raw known profile preferences feed the existing policy precedence`() {
        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            request(
                level = null,
                preferences = DurableBrewSessionGuidancePreferences(
                    profileOverrides = mapOf("pulsar_standard" to GuidancePresentationLevel.FOCUSED),
                    familyPreferences = mapOf(
                        "valve_controlled_no_bypass" to GuidancePresentationLevel.CONCISE,
                    ),
                ),
            ),
        )

        assertEquals(GuidancePresentationLevel.FOCUSED, resolution.policy?.level)
        assertEquals(GuidancePolicySource.PROFILE_OVERRIDE, resolution.policy?.source)
    }

    @Test
    fun `required visual without an asset ID is reported instead of substituted`() {
        val resolution = DurableBrewSessionGuidanceResolver().resolve(
            request(stage = stage(requiresIllustration = true)),
        )

        assertEquals(DurableBrewGuidanceVisualStatus.RequiredAssetIdMissing, resolution.visualStatus)
    }

    @Test
    fun `unapproved matching asset is reported explicitly and is not available`() {
        val assetId = InstructionAssetId(
            "instruction_valve_controlled_no_bypass_pulsar_standard_pulsar_bloom_default",
        )
        val pendingAsset = InstructionAssetRecord(
            id = assetId,
            familyId = FAMILY,
            profileId = PROFILE,
            stageId = BLOOM_STAGE,
            contentId = BLOOM_CONTENT,
            drawableRes = 1,
            altTextRes = 2,
            companionInstructionRes = 3,
            mandatoryForFullGuidance = true,
            safetySensitive = false,
            provenance = InstructionAssetProvenance(
                promptDocument = "docs/brewing/instruction-assets.md",
                promptRevision = "v1",
            ),
            review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
        )
        val resolver = DurableBrewSessionGuidanceResolver(
            instructionAssets = InstructionAssetCatalog(listOf(pendingAsset)),
        )

        val resolution = resolver.resolve(
            request(stage = stage(instructionAssetId = assetId)),
        )

        assertEquals(
            DurableBrewGuidanceVisualStatus.NotApproved(pendingAsset),
            resolution.visualStatus,
        )
        assertFalse(resolution.visualStatus is DurableBrewGuidanceVisualStatus.Approved)
    }

    private fun request(
        stage: CurrentBrewStagePresentation = stage(),
        level: GuidancePresentationLevel? = GuidancePresentationLevel.FULL,
        preferences: DurableBrewSessionGuidancePreferences? = null,
    ): DurableBrewSessionGuidanceRequest = DurableBrewSessionGuidanceRequest(
        methodFamilyId = FAMILY.value,
        brewerProfileId = PROFILE.value,
        currentStage = stage,
        preferences = preferences ?: DurableBrewSessionGuidancePreferences(sessionOverride = level),
    )

    private fun stage(
        stageId: StageId = BLOOM_STAGE,
        contentId: StageContentId = BLOOM_CONTENT,
        instructionAssetId: InstructionAssetId? = null,
        requiresIllustration: Boolean = false,
        safetyMessages: List<StageSafetyMessage> = emptyList(),
    ): CurrentBrewStagePresentation = CurrentBrewStagePresentation(
        stageInstanceId = StageInstanceId(stageId, occurrence = 1),
        action = BrewStageAction.BLOOM,
        contentId = contentId,
        instructionAssetId = instructionAssetId,
        requiresIllustration = requiresIllustration,
        runStatus = StageRunStatus.ACTIVE,
        elapsedActiveMillis = 0L,
        completion = BrewStageCompletionPresentation.Manual,
        safetyMessages = safetyMessages,
    )

    private fun stageContent(
        visibility: GuidanceVisibilityPolicy,
    ): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = BLOOM_CONTENT,
        familyId = FAMILY,
        profileId = PROFILE,
        stageId = BLOOM_STAGE,
        placement = BuiltInGuidancePlacement.LIVE_STAGE,
        text = GuidanceTextMetadata(
            primaryInstruction = "Carry out the current stage.",
            conciseInstruction = "Do the current stage.",
            altText = "A brewer during the current stage.",
        ),
        visibility = visibility,
    )

    private fun utilityContent(): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = StageContentId("pulsar_live_utility"),
        familyId = FAMILY,
        profileId = PROFILE,
        placement = BuiltInGuidancePlacement.UTILITY,
        text = GuidanceTextMetadata(
            primaryInstruction = "Keep the scale visible.",
            conciseInstruction = "Watch the scale.",
            altText = "A scale beside a brewer.",
        ),
        visibility = GuidanceVisibilityPolicy(
            visibleIn = setOf(GuidancePresentationLevel.UTILITIES_ONLY),
        ),
    )

    private fun globalSafetyContent(): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = StageContentId("pulsar_global_safety"),
        familyId = FAMILY,
        profileId = PROFILE,
        placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
        text = GuidanceTextMetadata(
            primaryInstruction = "Keep hot liquid stable.",
            warning = "Hot liquid can burn.",
            altText = "A stable brewer containing hot liquid.",
        ),
        visibility = GuidanceVisibilityPolicy(visibleIn = emptySet(), alwaysVisible = true),
        safetyCritical = true,
    )

    private companion object {
        val FAMILY = MethodFamilyId("valve_controlled_no_bypass")
        val PROFILE = BrewerProfileId("pulsar_standard")
        val BLOOM_STAGE = StageId("pulsar_bloom")
        val BLOOM_CONTENT = StageContentId("pulsar_bloom")
    }
}
