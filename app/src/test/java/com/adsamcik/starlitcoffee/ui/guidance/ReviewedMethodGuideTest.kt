package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.guides.stagePlan
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionCoordinator
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionEffectHandler
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.FakeActiveBrewSessionDao
import com.adsamcik.starlitcoffee.data.brewing.session.SessionEffectDelivery
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotCodec
import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReviewedMethodGuideTest {
    private val guides = ReviewedMethodGuideLibrary.decode(listOf(
        File("src/main/assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"),
        File("app/src/main/assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"),
    ).first(File::isFile).readText())
    private val gate = P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Unavailable("Old catalog unavailable"),
        InstructionAssetCatalog(emptyList()))

    @Test
    fun `all seventeen source procedures have executable manual stages and frozen readable guidance`() {
        assertEquals(17, guides.size)
        guides.forEach { guide ->
            val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), 81L, measuredDoseG = 22.0)
            val definitions = request.stagePlan.stages.map { it.definition }
            assertTrue(guide.id, definitions.all { it.completionMode == StageCompletionMode.Manual })
            assertEquals(guide.steps.map { guide.contentId(it) }, definitions.map { it.contentId })
            assertEquals(81L, request.executionContext.coffeeBagId)
            assertFalse(guide.id, gate.shouldGateSession(request.recipe, definitions))
            val frozen = BrewingSnapshotCodec.decodeRecipe(BrewingSnapshotCodec.encodeRecipe(request.recipe))
                as SnapshotDecodeResult.Decoded<com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1>
            assertEquals(request.recipe, frozen.value)
            val readAhead = requireNotNull(resolveBrewReadAheadGuide(frozen.value, definitions, gate,
                DurableBrewSessionGuidancePreferences()))
            assertEquals(guide, readAhead.reviewedGuide)
            assertEquals(guide.steps.size, readAhead.resolution.content.size)
            assertEquals(guide.steps.map { it.instruction }, readAhead.resolution.content.map { it.instruction })
        }
    }

    @Test
    fun `Chemex 30 480 remains a different procedure from 42 700`() {
        val guide = guides.first { it.methodId == "chemex" }
        val legacy = requireNotNull(BuiltInP1RecipeCatalog.find(BuiltInRecipeId("chemex_42_700")))
        assertEquals("chemex_30_480", guide.id)
        assertEquals(30.0, guide.doseG!!, 0.0)
        assertEquals(480.0, guide.waterG!!, 0.0)
        assertEquals(8, guide.steps.size)
        assertEquals(42.0, legacy.quantities.dryCoffeeDoseG, 0.0)
        assertEquals(700.0, legacy.quantities.brewWaterInputG!!, 0.0)
    }

    @Test
    fun `handling warnings remain visible and cleanup warnings appear at the relevant action`() {
        guides.forEach { guide ->
            val visibleWarnings = guide.steps.flatMap { guide.safetyForStep(it.id) }.toSet()
            val applicable = guide.safety.filterNot { guide.methodId == "aeropress" &&
                (it.contains("XL") || it.contains("Variable Flow")) }.toSet()
            assertEquals(guide.id, applicable, visibleWarnings)
            guide.learnResolution().content.filter { it.warning != null }.forEach { assertTrue(it.safetyCritical) }
        }
        val chemex = guides.first { it.methodId == "chemex" }
        assertFalse(chemex.safetyForStep("prepare").any { it.contains("washing") })
        assertTrue(chemex.safetyForStep("serve").any { it.contains("washing") })
        val espresso = guides.first { it.methodId == "espresso" }
        assertTrue(espresso.safetyForStep("start_shot").any { it.contains("Never unlock") })
    }

    @Test
    fun `volume sources never invent water mass and espresso cup target is independent`() {
        listOf("hario-switch", "phin", "siphon").forEach { method ->
            val guide = guides.first { it.methodId == method }
            val recipe = ReviewedGuideStartFactory.create(guide, BrewUiState(), null).recipe
            assertNull(recipe.quantities.brewWaterInputG)
            assertEquals(guide.waterMl, recipe.quantities.brewWaterInputMl)
            assertEquals("BREW_WATER_VOLUME", recipe.ratioDefinition.denominator)
        }
        val espresso = ReviewedGuideStartFactory.create(guides.first { it.methodId == "espresso" }, BrewUiState(), null).recipe
        assertNull(espresso.quantities.brewWaterInputG)
        assertEquals(36.0, espresso.quantities.targetBeverageYieldG!!, 0.0)
        assertEquals("BEVERAGE_YIELD", espresso.ratioDefinition.denominator)
    }

    @Test
    fun `equipment fill recipes require actual dose rather than inventing spoon mass`() {
        listOf("moka-pot", "percolator").forEach { method ->
            val guide = guides.first { it.methodId == method }
            assertTrue(runCatching { ReviewedGuideStartFactory.create(guide, BrewUiState(), null) }.isFailure)
            val recipe = ReviewedGuideStartFactory.create(guide, BrewUiState(), null, 21.3).recipe
            assertEquals(21.3, recipe.quantities.dryCoffeeDoseG, 0.0)
            assertNull(recipe.quantities.brewWaterInputG)
        }
    }

    @Test
    fun `volume and unknown fill values stay honest in persisted presentation and sharing`() {
        listOf("moka-pot", "percolator", "hario-switch", "phin", "siphon").forEach { method ->
            val guide = guides.first { it.methodId == method }
            val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null, 21.3)
            val presentation = request.executionContext.logPresentation
            assertFalse(presentation.hasMassRatio)
            assertEquals(0.0, presentation.ratio, 0.0)
            val log = com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper.withBrewRecordSnapshot(
                com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity(method = guide.methodName,
                    doseG = presentation.doseG.toFloat(), waterG = presentation.waterG.toFloat(), ratio = 0f),
                com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecordSnapshotV1(recipe = request.recipe), null)
            val text = com.adsamcik.starlitcoffee.ui.component.brewShareQuantities(log)
            assertFalse(text.contains("1:0"))
            assertFalse(text.contains("💧 0g"))
            assertEquals(guide.waterMl != null, text.contains("mL"))
        }
    }

    @Test
    fun `post fill and post pour waits use stage clocks while timestamped schedules use physical brew clock`() {
        val switch = guides.first { it.methodId == "hario-switch" }.stagePlan().stages[3].definition.advanceConstraint
        assertEquals(120_000L, switch.notBeforeStageElapsedMillis)
        assertNull(switch.notBeforeBrewElapsedMillis)
        val v60 = guides.first { it.methodId == "v60" }.stagePlan().stages[3].definition.advanceConstraint
        assertEquals(30_000L, v60.notBeforeStageElapsedMillis)
        assertNull(v60.notBeforeBrewElapsedMillis)
        val chemex = guides.first { it.methodId == "chemex" }.stagePlan().stages[3].definition.advanceConstraint
        assertEquals(45_000L, chemex.notBeforeBrewElapsedMillis)
    }

    @Test
    fun `mismatched frozen plan quantities profile or provenance cannot borrow reviewed guidance`() {
        val guide = guides.first { it.methodId == "chemex" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(method = BrewMethod.CHEMEX), null)
        val definitions = request.stagePlan.stages.map { it.definition }
        assertFalse(gate.shouldGateSession(request.recipe, definitions))
        assertTrue(gate.shouldGateSession(request.recipe.copy(quantities = request.recipe.quantities.copy(dryCoffeeDoseG = 42.0)), definitions))
        assertTrue(gate.shouldGateSession(request.recipe.copy(brewerProfileId = "v60_02"), definitions))
        assertTrue(gate.shouldGateSession(request.recipe.copy(sourceMetadata = null), definitions))
        assertTrue(gate.shouldGateSession(request.recipe, definitions.reversed()))
        assertNull(resolveBrewReadAheadGuide(request.recipe, definitions.reversed(), gate, DurableBrewSessionGuidancePreferences()))
    }

    @Test
    fun `all method read ahead leaves the physical session stage and custom reminder unchanged`() = runTest {
        var clock = 1_000L
        val dao = FakeActiveBrewSessionDao()
        val coordinator = BrewSessionCoordinator(ActiveBrewSessionRepository(dao),
            ClockedSessionEngine(MonotonicClock { clock }, WallClock { clock }),
            object : BrewSessionEffectHandler {
                override suspend fun deliver(effect: com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect,
                    session: com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession) = SessionEffectDelivery.Delivered
            })
        guides.forEach { guide ->
            val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null, 21.3)
            val created = coordinator.createOrResume(request)
            check(created is BrewSessionOperationResult.Active) { "${guide.id}: $created" }
            repeat(guide.steps.indexOfFirst { it.id == guide.originStepId }) {
                val prep = coordinator.dispatch(request.sessionId, SessionEvent.ManualAdvance())
                check(prep is BrewSessionOperationResult.Active) { "${guide.id}: $prep" }
            }
            val result = coordinator.dispatch(request.sessionId, SessionEvent.Start())
            check(result is BrewSessionOperationResult.Active) { "${guide.id}: $result" }
            val started = result
            assertTrue(guide.id, started.session.runtime.hasPhysicalClockStarted)
            coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(
                requireNotNull(started.session.runtime.currentStage).instanceId, 600_000L))
            val before = requireNotNull(dao.current(request.sessionId.value))
            val frozen = (ActiveBrewSessionEntityMapper.restore(before) as ActiveBrewSessionRestoreResult.Restored).value
            val reader = requireNotNull(resolveBrewReadAheadGuide(frozen.recipe,
                frozen.runtime.stagePlan.stages.map { it.definition }, gate, DurableBrewSessionGuidancePreferences()))
            reader.resolution.content.reversed().forEach { assertTrue(it.instruction.isNotBlank()) }
            assertEquals(guide.id, before, dao.current(request.sessionId.value))
            clock += 500L
        }
    }
}
