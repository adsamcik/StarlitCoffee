package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionTestFixtures
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionContentSourceTest {
    private val preferences = DurableBrewSessionGuidancePreferences(sessionOverride = GuidancePresentationLevel.FULL)
    private val encodedExact by lazy { asset(BuiltInP1ExactGuidanceCatalog.ASSET_NAME) }
    private val gate by lazy {
        P1ExactRecipeReleaseGate(
            BuiltInP1ExactGuidanceLoadResult.Loaded(BuiltInP1ExactGuidanceCatalog.decode(encodedExact)),
            BuiltInInstructionAssetCatalog.catalog,
        )
    }
    private val reviewedGuides by lazy {
        ReviewedMethodGuideLibrary.decode(asset(ReviewedMethodGuideLibrary.ASSET_PATH))
    }

    @Test
    fun exactLiveAndReaderShareSourceWithoutWeakeningItsGate() {
        val id = BuiltInRecipeId("chemex_42_700")
        val original = requireNotNull(BuiltInP1RecipeCatalog.find(id))
        val plan = requireNotNull(BuiltInP1ExactStagePlanCatalog.find(id))
        val stages = plan.nodes.map { (it as StagePlanNode.Stage).definition }
        val recipe = ActiveBrewSessionTestFixtures.recipe().copy(
            builtInRecipeId = id.value,
            methodFamilyId = original.methodFamilyId.value,
            brewerProfileId = original.brewerProfileId.value,
            equipment = EquipmentConfigurationSnapshotV1(original.brewerProfileId.value),
            quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 42.0, brewWaterInputG = 700.0),
        )
        val selected = resolveBrewSessionContentSource(recipe, stages, gate, preferences, language = "en")
        val reader = requireNotNull(selected.reader)
        assertNotNull(reader.exactGuide)
        assertFalse(selected.executionBlocked)
        assertEquals(gate.shouldGateSession(recipe, stages), selected.executionBlocked)
        for (stage in stages) {
            val live = selected.resolver.resolve(DurableBrewSessionGuidanceRequest(
                recipe.methodFamilyId, recipe.brewerProfileId, present(stage), preferences,
            ))
            val expected = reader.resolution.content.single { it.id == stage.contentId }
            val actual = (live.criticalContent + live.routineContent).single { it.id == stage.contentId }
            assertEquals(expected.instruction, actual.instruction)
            assertEquals(expected.target, actual.target)
            assertEquals(expected.visualContentRevision, actual.visualContentRevision)
        }

        val noArtGate = P1ExactRecipeReleaseGate(
            BuiltInP1ExactGuidanceLoadResult.Loaded(BuiltInP1ExactGuidanceCatalog.decode(encodedExact)),
            InstructionAssetCatalog(emptyList()),
        )
        val blocked = resolveBrewSessionContentSource(recipe, stages, noArtGate, preferences, language = "en")
        assertTrue(blocked.executionBlocked)
        assertEquals(noArtGate.shouldGateSession(recipe, stages), blocked.executionBlocked)
        assertNull(blocked.reader)
        val live = blocked.resolver.resolve(DurableBrewSessionGuidanceRequest(
            recipe.methodFamilyId, recipe.brewerProfileId, present(stages.first()), preferences,
        ))
        assertTrue(live.routineContent.isEmpty())
        assertTrue(live.criticalContent.isEmpty())
    }

    @Test
    fun mismatchedReviewedPlanCannotBorrowGenericOrReviewedGuidance() {
        val guide = reviewedGuides.first { it.methodId == "chemex" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val changedStages = request.stagePlan.stages.map { it.definition }.reversed()
        val selected = resolveBrewSessionContentSource(
            request.recipe, changedStages, gate, preferences, language = "en",
        )
        assertTrue(selected.executionBlocked)
        assertNull(selected.reader)
        assertTrue(selected.requiredWarnings.isEmpty())
        val live = selected.resolver.resolve(DurableBrewSessionGuidanceRequest(
            request.recipe.methodFamilyId, request.recipe.brewerProfileId,
            present(changedStages.first()), preferences,
        ))
        assertTrue(live.availability is DurableBrewGuidanceAvailability.NoGuidanceCatalogForProfile)
        assertTrue(live.routineContent.isEmpty())
        assertTrue(live.criticalContent.isEmpty())
    }

    @Test
    fun reviewedOperationalWarningsRemainStageScopedUnderUtilitiesOnly() {
        val guide = reviewedGuides.first { it.methodId == "espresso" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val stages = request.stagePlan.stages.map { it.definition }
        val utilities = DurableBrewSessionGuidancePreferences(
            sessionOverride = GuidancePresentationLevel.UTILITIES_ONLY,
        )
        val selected = resolveBrewSessionContentSource(request.recipe, stages, gate, utilities, language = "en")
        val shot = guide.steps.single { it.id == "start_shot" }
        val clean = guide.steps.single { it.id == "clean" }
        val shotWarnings = requireNotNull(selected.requiredWarnings[guide.contentId(shot)])
        val cleanWarnings = requireNotNull(selected.requiredWarnings[guide.contentId(clean)])
        assertEquals(guide.safetyForStep(shot.id), shotWarnings)
        assertEquals(guide.safetyForStep(clean.id), cleanWarnings)
        assertTrue(shotWarnings.any { it.contains("Never unlock") })
        assertFalse(cleanWarnings.any { it.contains("Never unlock") })
        assertFalse(selected.executionBlocked)
        val stage = stages.single { it.contentId == guide.contentId(shot) }
        val live = selected.resolver.resolve(DurableBrewSessionGuidanceRequest(
            request.recipe.methodFamilyId, request.recipe.brewerProfileId, present(stage), utilities,
        ))
        assertEquals(GuidancePresentationLevel.UTILITIES_ONLY, live.policy?.level)
        // Required operational warnings are separately consumable, not recovered from optional copy.
        assertEquals(shotWarnings, selected.requiredWarnings[stage.contentId])
    }

    private fun present(stage: BrewStageDefinition) = CurrentBrewStagePresentation(
        stageInstanceId = StageInstanceId(stage.id, 1),
        action = stage.action,
        contentId = stage.contentId,
        instructionAssetId = stage.instructionAssetId,
        requiresIllustration = stage.requiresIllustration,
        runStatus = StageRunStatus.ACTIVE,
        elapsedActiveMillis = 0L,
        completion = BrewStageCompletionPresentation.Manual,
        safetyMessages = stage.safetyMessages,
    )

    private fun asset(path: String): String = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).first(File::isFile).readText()
}
