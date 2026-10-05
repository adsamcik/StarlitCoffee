package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionTestFixtures
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrewReadAheadGuideTest {
    private val id = BuiltInRecipeId("chemex_42_700")
    private val source = requireNotNull(BuiltInP1RecipeCatalog.find(id))
    private val plan = requireNotNull(BuiltInP1ExactStagePlanCatalog.find(id))
    private val stages = plan.nodes.map { (it as StagePlanNode.Stage).definition }
    private val preferences = DurableBrewSessionGuidancePreferences()
    private val recipe = ActiveBrewSessionTestFixtures.recipe().copy(
        builtInRecipeId = id.value,
        methodFamilyId = source.methodFamilyId.value,
        brewerProfileId = source.brewerProfileId.value,
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = source.brewerProfileId.value),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 42.0, brewWaterInputG = 700.0),
    )
    private val gate by lazy {
        val encoded = listOf(File("src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
            File("app/src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}")).first(File::isFile).readText()
        P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Loaded(BuiltInP1ExactGuidanceCatalog.decode(encoded)),
            BuiltInInstructionAssetCatalog.catalog)
    }

    @Test
    fun `read ahead preserves the actual Chemex source and typed timing origins without altering its plan`() {
        val before = stages.toList()
        val reader = requireNotNull(resolveBrewReadAheadGuide(recipe, stages, gate, preferences))
        val exactGuide = requireNotNull(reader.exactGuide)
        assertEquals(42.0, exactGuide.recipe.quantities.dryCoffeeDoseG, 0.0)
        assertEquals(700.0, exactGuide.recipe.quantities.brewWaterInputG!!, 0.0)
        assertEquals(stages.map { it.contentId }, reader.resolution.content.filter {
            it.placement == BuiltInGuidancePlacement.LIVE_STAGE
        }.map { it.id })
        stages.forEach {
            val facts = requireNotNull(exactGuide.stageFactsByContentId[it.contentId])
            assertEquals(it.referenceTargets, facts.referenceTargets)
            assertEquals(it.action, facts.action)
        }
        assertEquals(before, stages)
        val preparation = requireNotNull(exactGuide.stageFactsByContentId[stages.first().contentId])
        assertNull(preparation.addedWater)
        assertNull(preparation.cumulativeWater)
        assertEquals("Fit the paper", preparation.title)
    }

    @Test
    fun `arbitrary calculator dose cannot borrow the fixed source guide`() {
        assertNotNull(resolveBrewReadAheadGuide(recipe, stages, gate, preferences))
        assertNull(resolveBrewReadAheadGuide(recipe.copy(quantities = recipe.quantities.copy(dryCoffeeDoseG = 30.0)),
            stages, gate, preferences))
        assertNull(resolveBrewReadAheadGuide(recipe.copy(quantities = recipe.quantities.copy(brewWaterInputG = 480.0)),
            stages, gate, preferences))
        assertNull(resolveBrewReadAheadGuide(recipe.copy(equipment = recipe.equipment.copy(brewerProfileId = "other")),
            stages, gate, preferences))
    }

    @Test
    fun `authored teaching copy requires the reviewed stage identity and action`() {
        val stage = requireNotNull(gate.guidanceFor(id)).stages.first()
        assertEquals("Fit the paper", P1GuideTeachingCopy.forStage(stage)?.title)
        assertNull(P1GuideTeachingCopy.forStage(stage.copy(action = "A revised action")))
        assertNull(P1GuideTeachingCopy.forStage(stage.copy(sourceStageId = "reordered_stage")))
        assertNull(P1GuideTeachingCopy.forStage(stage.copy(order = 2)))
    }

    @Test
    fun `changed plan or unavailable review cannot substitute current guidance`() {
        val edited = stages.toMutableList()
        edited[0] = edited[0].copy(completionMode = StageCompletionMode.Countdown(12_345L))
        assertNull(resolveBrewReadAheadGuide(recipe, edited, gate, preferences))
        assertNull(resolveBrewReadAheadGuide(recipe.copy(builtInRecipeId = null), stages, gate, preferences))
        val unreviewedGate = P1ExactRecipeReleaseGate(
            BuiltInP1ExactGuidanceLoadResult.Loaded(requireNotNull(gate.guidanceFor(id)).let {
                val encoded = listOf(File("src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
                    File("app/src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}")).first(File::isFile).readText()
                BuiltInP1ExactGuidanceCatalog.decode(encoded)
            }), InstructionAssetCatalog(emptyList()),
        )
        assertNull(resolveBrewReadAheadGuide(recipe, stages, unreviewedGate, preferences))
    }
}
