package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PhysicalBrewClock
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionHeroTest {
    @Test
    fun `manual pour and retained bloom share the original first-water animation boundary`() {
        val base = runtime(totalElapsed = 40_000L, stageElapsed = 40_000L)
        val stages = base.stagePlan.stages.toMutableList().also { stages ->
            val wait = stages[2]
            stages[2] = wait.copy(definition = wait.definition.copy(action = BrewStageAction.BLOOM,
                advanceConstraint = StageAdvanceConstraint(notBeforeBrewElapsedMillis = 60_000L)))
        }
        val pouring = base.copy(stagePlan = base.stagePlan.copy(stages = stages))
        val waiting = pouring.copy(currentStageIndex = 2, totalActiveElapsedMillis = 45_000L,
            stageProgress = listOf(pouring.stageProgress[0],
                StageRuntimeProgress(StageRunStatus.COMPLETED, elapsedActiveMillis = 40_000L),
                StageRuntimeProgress(StageRunStatus.ACTIVE, elapsedActiveMillis = 5_000L)))
        val pourBloom = requireNotNull(brewSessionBloomState(presentation(pouring), pouring))
        val waitBloom = requireNotNull(brewSessionBloomState(presentation(waiting), waiting))
        assertEquals(60, pourBloom.durationSeconds)
        assertEquals(20, pourBloom.countdownSeconds)
        assertEquals(60, waitBloom.durationSeconds)
        assertEquals(15, waitBloom.countdownSeconds)
    }

    @Test
    fun `bloom is absent from methods without a bloom action`() {
        val runtime = runtime(bloomAction = BrewStageAction.STEEP)
        assertNull(brewSessionBloomState(presentation(runtime), runtime))
        assertNull(brewSessionBloomState(presentation(runtime), null))
    }

    @Test
    fun `bloom starts as a bud and retains its final pose after the bloom stage`() {
        val before = runtime(currentIndex = 0)
        val after = runtime(currentIndex = 2)
        assertNull(brewSessionBloomState(presentation(before), before)!!.countdownSeconds)
        assertEquals(0, brewSessionBloomState(presentation(after), after)!!.countdownSeconds)
    }

    @Test
    fun `countdown uses the current presentation without finishing a second early`() {
        val runtime = runtime(completion = StageCompletionMode.Countdown(30_000L), stageElapsed = 10_001L)
        val bloom = brewSessionBloomState(presentation(runtime), runtime)!!
        assertEquals(30, bloom.durationSeconds)
        assertEquals(20, bloom.countdownSeconds)
        assertTrue(bloom.isRunning)
        val pausedGuide = runtime.copy(isGuidancePaused = true)
        assertTrue(brewSessionBloomState(presentation(pausedGuide), pausedGuide)!!.isRunning)
    }

    @Test
    fun `brew-clock boundary is relative to actual bloom start rather than the whole brew`() {
        val runtime = runtime(totalElapsed = 45_000L, stageElapsed = 15_000L,
            advanceConstraint = StageAdvanceConstraint(notBeforeBrewElapsedMillis = 60_000L))
        val bloom = brewSessionBloomState(presentation(runtime), runtime)!!
        assertEquals(30, bloom.durationSeconds)
        assertEquals(15, bloom.countdownSeconds)
    }

    @Test
    fun `source completion cue uses the actual bloom start and untimed manual bloom remains a bud`() {
        val sourceTimed = runtime(totalElapsed = 45_000L, stageElapsed = 15_000L,
            referenceTargets = StageReferenceTargets(timeTargets = listOf(StageTimeTarget(
                StageTimeReference.BREW_ELAPSED_AT_COMPLETION, StageTargetId("end"),
                StageTargetQualifier.EXACT, 60_000L))))
        assertEquals(15, brewSessionBloomState(presentation(sourceTimed), sourceTimed)!!.countdownSeconds)
        val manual = runtime(stageElapsed = 100_000L, totalElapsed = 100_000L)
        val bloom = brewSessionBloomState(presentation(manual), manual)!!
        assertNull(bloom.countdownSeconds)
        assertFalse(bloom.isRunning)
    }

    @Test
    fun `unknown physical clock does not invent animated bloom progress`() {
        val running = runtime(completion = StageCompletionMode.Countdown(30_000L))
        val unknown = running.copy(physicalClock = PhysicalBrewClock(running.currentStage!!.instanceId,
            hasStarted = true, originKnown = false))
        assertNull(brewSessionBloomState(presentation(unknown), unknown)!!.countdownSeconds)
    }

    @Test
    fun `recipe summary preserves source volume and espresso yield without inventing water`() {
        val volumeRecipe = recipe(BrewQuantitiesSnapshotV1(15.0, brewWaterInputMl = 250.0))
        val volume = brewHeroQuantities(volumeRecipe)
        assertEquals(250.0, volume.last().amount, 0.0)
        assertEquals(R.string.unit_ml, volume.last().unitRes)
        val espresso = recipe(BrewQuantitiesSnapshotV1(18.0, targetBeverageYieldG = 36.0), "espresso")
        assertEquals(R.string.guidance_cue_beverage_yield_target, brewHeroQuantities(espresso).last().labelRes)
        assertEquals(36.0, brewHeroQuantities(espresso).last().amount, 0.0)
        assertEquals(1, brewHeroQuantities(recipe(BrewQuantitiesSnapshotV1(18.0))).size)
        assertTrue(brewHeroQuantities(null).isEmpty())
    }

    private fun presentation(runtime: SessionRuntimeState) =
        ActiveBrewSessionPresentationMapper.map(runtime) as ActiveBrewSessionPresentation.Available

    private fun runtime(
        currentIndex: Int = 1,
        completion: StageCompletionMode = StageCompletionMode.Manual,
        totalElapsed: Long = 10_000L,
        stageElapsed: Long = 10_000L,
        advanceConstraint: StageAdvanceConstraint = StageAdvanceConstraint(),
        referenceTargets: StageReferenceTargets = StageReferenceTargets(),
        bloomAction: BrewStageAction = BrewStageAction.BLOOM,
    ): SessionRuntimeState {
        val stages = listOf(BrewStageAction.PREPARE, bloomAction, BrewStageAction.POUR).mapIndexed { index, action ->
            val id = StageId("stage_$index")
            CompiledBrewStage(StageInstanceId(id, 1), BrewStageDefinition(
                id, action, StageContentId("content_$index"),
                completionMode = if (index == 1) completion else StageCompletionMode.Manual,
                advanceConstraint = if (index == 1) advanceConstraint else StageAdvanceConstraint(),
                referenceTargets = if (index == 1) referenceTargets else StageReferenceTargets(),
            ))
        }
        return SessionRuntimeState(SessionId("hero-test"),
            CompiledStagePlan(StagePlanId("hero_test"), 1, stages), BrewSessionStatus.RUNNING, currentIndex,
            stages.mapIndexed { index, _ -> StageRuntimeProgress(when {
                index < currentIndex -> StageRunStatus.COMPLETED
                index == currentIndex -> StageRunStatus.ACTIVE
                else -> StageRunStatus.PENDING
            }, elapsedActiveMillis = if (index == currentIndex) stageElapsed else 0L) },
            totalActiveElapsedMillis = totalElapsed)
    }

    private fun recipe(quantities: BrewQuantitiesSnapshotV1, family: String = "manual_gravity") = BrewRecipeSnapshotV1(
        methodFamilyId = family,
        brewerProfileId = "test_brewer",
        equipment = EquipmentConfigurationSnapshotV1("test_brewer"),
        quantities = quantities,
        ratioDefinition = RatioDefinitionSnapshotV1("BREW_WATER_INPUT", "DRY_COFFEE_DOSE"),
        outputModel = OutputModelSnapshotV1("BREW_WATER_MINUS_RETENTION"),
    )
}
