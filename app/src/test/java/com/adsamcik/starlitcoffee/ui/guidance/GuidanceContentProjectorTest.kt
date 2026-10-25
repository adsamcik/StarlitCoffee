package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceContentProjectorTest {
    @Test
    fun textOnlyDoesNotRequireImageDescription() {
        val projected = content(GuidanceTextMetadata(
            primaryInstruction = "Pour gently.",
            conciseInstruction = "Pour.",
        )).projectGuidanceContent(GuidancePresentationLevel.CONCISE)

        assertEquals("Pour.", projected.instruction)
        assertEquals("", projected.accessibleAltText)
        assertNull(projected.target)
    }

    @Test
    fun captionOnlyAndTargetOnlyPreserveIndependentSlots() {
        val caption = content(GuidanceTextMetadata(altText = "Water below the fitted paper rim."))
            .projectGuidanceContent(GuidancePresentationLevel.FULL)
        val target = content(GuidanceTextMetadata(target = "150 g cumulative"))
            .projectGuidanceContent(GuidancePresentationLevel.FOCUSED)

        assertNull(caption.instruction)
        assertEquals("Water below the fitted paper rim.", caption.accessibleAltText)
        assertNull(target.instruction)
        assertEquals("150 g cumulative", target.target)
        assertEquals("", target.accessibleAltText)
    }

    @Test
    fun missingDensityDerivesBaseWhileSelectedNullsRemainOmitted() {
        val record = content(
            GuidanceTextMetadata(primaryInstruction = "Base instruction.", conciseInstruction = "Base.",
                explanation = "Base explanation.", target = "Base target"),
        ).copy(authoredPresentations = mapOf(
            GuidancePresentationLevel.FOCUSED to AuthoredGuidancePresentation(target = "Focused target"),
        ))

        val focused = record.projectGuidanceContent(GuidancePresentationLevel.FOCUSED)
        assertNull(focused.instruction)
        assertNull(focused.explanation)
        assertEquals("", focused.accessibleAltText)
        assertEquals("Focused target", focused.target)

        val full = record.projectGuidanceContent(GuidancePresentationLevel.FULL)
        assertEquals("Base instruction.", full.instruction)
        assertEquals("Base explanation.", full.explanation)
        assertEquals("Base target", full.target)
    }

    @Test
    fun omittedAuthoredWarningCannotRemoveMandatoryBaseSafety() {
        val record = content(GuidanceTextMetadata(warning = "Keep the hot brewer on its server.")).copy(
            safetyCritical = true,
            visibility = GuidanceVisibilityPolicy(alwaysVisible = true),
            authoredPresentations = mapOf(
                GuidancePresentationLevel.UTILITIES_ONLY to AuthoredGuidancePresentation(),
            ),
        )

        val projected = record.projectGuidanceContent(GuidancePresentationLevel.UTILITIES_ONLY)
        assertNull(projected.instruction)
        assertEquals("Keep the hot brewer on its server.", projected.warning)
    }

    @Test
    fun learnAndDurableUseTheSameSparseProjection() {
        val record = content(GuidanceTextMetadata(primaryInstruction = "Full instruction.")).copy(
            authoredPresentations = mapOf(
                GuidancePresentationLevel.FOCUSED to AuthoredGuidancePresentation(target = "150 g cumulative"),
            ),
        )
        val catalogs = listOf(BuiltInGuidanceCatalog(listOf(record)))
        val preferences = DurableBrewSessionGuidancePreferences(sessionOverride = GuidancePresentationLevel.FOCUSED)
        val stage = CurrentBrewStagePresentation(
            stageInstanceId = StageInstanceId(StageId("projected_stage"), 1),
            action = BrewStageAction.POUR,
            contentId = record.id,
            instructionAssetId = null,
            requiresIllustration = false,
            runStatus = StageRunStatus.ACTIVE,
            elapsedActiveMillis = 0L,
            completion = BrewStageCompletionPresentation.Manual,
            safetyMessages = emptyList(),
        )
        val learn = LearnGuidanceCatalogResolver(guidanceCatalogs = catalogs).resolve(
            LearnGuidanceCatalogRequest("manual_gravity", "v60_02", preferences,
                exactStageOrder = listOf(stage.stageId)),
        ).content.single()
        val durable = DurableBrewSessionGuidanceResolver(guidanceCatalogs = catalogs).resolve(
            DurableBrewSessionGuidanceRequest("manual_gravity", "v60_02", stage, preferences),
        ).routineContent.single()

        assertEquals("", learn.instruction)
        assertEquals("150 g cumulative", learn.target)
        assertEquals(learn.instruction, durable.instruction)
        assertEquals(learn.target, durable.target)
        assertEquals(learn.altText, durable.altText)
        assertNull(learn.explanation)
        assertNull(durable.explanation)
    }

    @Test
    fun optionalOperationalFieldsInvalidateArtworkWithoutChangingShippedDefaultContracts() {
        val original = content(GuidanceTextMetadata(primaryInstruction = "Pour", altText = "Pour into the brewer"))
        val oldParts = listOf("test", original.familyId.value, original.profileId?.value.orEmpty(), original.id.value,
            original.placement.name, "Pour", "", "", "", "", "Pour into the brewer")
        assertEquals(visualContractRevision(oldParts), original.withVisualContract("test").visualContentRevision)
        val targetOnly = original.copy(text = original.text.copy(target = "150 g cumulative"))
        val controlOnly = original.copy(text = original.text.copy(controlRequirements = listOf(GuidanceOperationalCue.VALVE_STATE)))
        org.junit.Assert.assertNotEquals(original.withVisualContract("test").visualContentRevision,
            targetOnly.withVisualContract("test").visualContentRevision)
        org.junit.Assert.assertNotEquals(original.withVisualContract("test").visualContentRevision,
            controlOnly.withVisualContract("test").visualContentRevision)
    }

    private fun content(text: GuidanceTextMetadata) = BuiltInGuidanceContent(
        id = StageContentId("projected_step"),
        familyId = MethodFamilyId("manual_gravity"),
        profileId = BrewerProfileId("v60_02"),
        stageId = StageId("projected_stage"),
        placement = BuiltInGuidancePlacement.LIVE_STAGE,
        text = text,
    )
}
