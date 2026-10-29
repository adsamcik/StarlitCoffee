package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewWarning
import com.adsamcik.starlitcoffee.ui.guidance.liveSourceWarnings
import java.io.File
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.PulsarLearnGuideCatalog
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.liveSourceMetadata
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBrewGuidanceTest {
    @Test
    fun `current operation stays primary while safety and supporting content stay available`() {
        val utility = content("valve", BuiltInGuidancePlacement.UTILITY)
        val pour = content("pour", BuiltInGuidancePlacement.LIVE_STAGE)
        val safety = content("hot", BuiltInGuidancePlacement.GLOBAL_SAFETY, critical = true)

        val selected = selectLiveBrewGuidance(resolution(listOf(utility, pour), listOf(safety)))

        assertEquals(pour, selected.primary)
        assertEquals(listOf(safety, utility), selected.supporting)
    }

    @Test
    fun `basic timer moves only familiar Pulsar handling advice to Help`() {
        val pour = content("pulsar_manual_brew", BuiltInGuidancePlacement.LIVE_STAGE)
        val hot = content("pulsar_hot_liquid_safety", BuiltInGuidancePlacement.GLOBAL_SAFETY, critical = true)
            .copy(warning = canonicalWarning("pulsar_hot_liquid_safety"))
        val overflow = content("pulsar_overflow_safety", BuiltInGuidancePlacement.LIVE_STAGE, critical = true)
            .copy(warning = canonicalWarning("pulsar_overflow_safety"))
        val unfamiliar = content("equipment_fault", BuiltInGuidancePlacement.GLOBAL_SAFETY, critical = true)
        val source = resolution(listOf(pour), listOf(hot, overflow, unfamiliar))
        val profile = PulsarLearnGuideCatalog.profileId.value

        val timer = selectLiveBrewGuidance(source, profile, minimal = true)
        assertEquals(pour, timer.primary)
        assertEquals(listOf(unfamiliar), timer.supporting)
        assertEquals(listOf(hot, overflow, unfamiliar), selectLiveBrewGuidance(source, profile).supporting)
        assertEquals(listOf(hot, overflow, unfamiliar),
            selectLiveBrewGuidance(source, "another_profile", minimal = true).supporting)
        assertEquals(listOf(hot, overflow, unfamiliar), source.criticalContent)
    }

    @Test
    fun `cold timer consolidates safety only when refrigeration is already visible`() {
        val safety = content("cold_immersion_generic_global_safety", BuiltInGuidancePlacement.GLOBAL_SAFETY, critical = true)
            .copy(warning = canonicalWarning("cold_immersion_generic_global_safety"))
        val source = resolution(emptyList(), listOf(safety))
        val withRefrigeration = source.copy(stageSafetyMessages = listOf(
            StageSafetyMessage("food_refrigerate_4c_during_steep", StageSafetySeverity.WARNING)))

        assertEquals(listOf(safety),
            selectLiveBrewGuidance(source, "cold_immersion_generic", minimal = true).supporting)
        assertTrue(selectLiveBrewGuidance(withRefrigeration, "cold_immersion_generic", minimal = true)
            .supporting.isEmpty())
        assertEquals(listOf(safety), selectLiveBrewGuidance(withRefrigeration, "cold_immersion_generic").supporting)
        assertEquals(source.criticalContent, withRefrigeration.criticalContent)
    }

    @Test
    fun `only the reviewed Pulsar safety bindings receive concise live copy`() {
        val profile = PulsarLearnGuideCatalog.profileId.value
        assertEquals(R.string.msg_brew_pulsar_hot_liquid,
            liveSourceMetadata(StageContentId("pulsar_hot_liquid_safety"), profile).warningSummary)
        assertEquals(R.string.msg_brew_pulsar_overflow,
            liveSourceMetadata(StageContentId("pulsar_overflow_safety"), profile).warningSummary)
        assertEquals(R.string.msg_brew_pulsar_pour_safety,
            liveSourceMetadata(StageContentId("reviewed_pulsar_v1_open_and_pulse"), profile).warningSummary)
        assertNull(liveSourceMetadata(StageContentId("unfamiliar_safety"), profile).warningSummary)
        assertNull(liveSourceMetadata(StageContentId("pulsar_hot_liquid_safety"), "v60_02").warningSummary)
        assertNull(liveSourceMetadata(StageContentId("pulsar_hot_liquid_safety"), null).warningSummary)
    }

    @Test
    fun `critical version of current operation wins duplicate routine content`() {
        val routine = content("serve", BuiltInGuidancePlacement.LIVE_STAGE)
        val critical = routine.copy(warning = "Lift only by the black base.", safetyCritical = true)

        val selected = selectLiveBrewGuidance(resolution(listOf(routine), listOf(critical)))

        assertEquals(critical, selected.primary)
        assertTrue(selected.supporting.isEmpty())
        val criticalOnly = selectLiveBrewGuidance(resolution(emptyList(), listOf(critical)))
        assertEquals(critical, criticalOnly.primary)
    }

    @Test
    fun `water-height target is scoped to the exact Pulsar pouring stages`() {
        val profile = PulsarLearnGuideCatalog.profileId.value
        val mainPour = StageContentId("${PulsarLearnGuideCatalog.GUIDE_ID}_stage_06_instruction")

        assertTrue(hasPulsarWaterHeightTarget(mainPour, profile))
        assertTrue(hasPulsarWaterHeightTarget(StageContentId("reviewed_pulsar_v1_open_and_pulse"), profile))
        assertFalse(hasPulsarWaterHeightTarget(mainPour, null))
        assertFalse(hasPulsarWaterHeightTarget(mainPour, "v60_02"))
        assertFalse(hasPulsarWaterHeightTarget(
            StageContentId("${PulsarLearnGuideCatalog.GUIDE_ID}_stage_07_instruction"), profile,
        ))
    }

    @Test
    fun `current cumulative target retains its source role and qualifier`() {
        val added = BrewStageReferenceCuePresentation.Mass(
            QuantityRole.BREW_WATER_INPUT, StageMassReference.STAGE_ADDED, StageTargetQualifier.EXACT, 280.0, 280.0,
        )
        val cumulative = added.copy(reference = StageMassReference.BREW_CUMULATIVE, minimumGrams = 340.0, maximumGrams = 340.0)
        val yield = added.copy(role = QuantityRole.BEVERAGE_YIELD, minimumGrams = 36.0, maximumGrams = 36.0)

        assertEquals(cumulative, primaryLiveBrewCue(listOf(added, cumulative)))
        assertEquals(yield, primaryLiveBrewCue(listOf(yield)))
    }

    @Test
    fun `omitted guidance preserves the physical target and required safety at minimal density`() {
        val target = BrewStageReferenceCuePresentation.Mass(QuantityRole.BREW_WATER_INPUT,
            StageMassReference.BREW_CUMULATIVE, StageTargetQualifier.EXACT, 340.0, 340.0)
        val projected = projectLiveBrewContent(resolution(emptyList(), emptyList()), targetCue = target,
            minimal = true, showSupportingCopy = false, requiredWarnings = listOf("Disconnect power before opening."))

        assertNull(projected.step)
        assertNull(projected.visualCue)
        assertNull(projected.instruction)
        assertEquals(target, projected.target)
        assertEquals(listOf("Disconnect power before opening."), projected.warnings.map { it.text })
        assertTrue(projected.warnings.single().critical)
    }

    @Test
    fun `focused layout retains unfamiliar warnings while known routine advice stays in Help`() {
        val unknown = content("new_pressure_step", BuiltInGuidancePlacement.LIVE_STAGE, critical = true)
            .copy(warning = "Release pressure before opening.")
        val projected = projectLiveBrewContent(resolution(emptyList(), listOf(unknown)),
            showSupportingCopy = false)
        assertNull(projected.instruction)
        assertEquals(listOf("Release pressure before opening."), projected.warnings.map { it.text })
    }

    @Test
    fun `target-only content has no instruction or reserved illustration`() {
        val targetOnly = content("pour", BuiltInGuidancePlacement.LIVE_STAGE)
            .copy(instruction = "", altText = "", target = "Maintain the source water line")
        val projected = projectLiveBrewContent(resolution(listOf(targetOnly), emptyList()))
        assertEquals(targetOnly.target, projected.step?.target)
        assertNull(projected.step?.instruction)
        assertNull(projected.step?.illustration)
        assertNull(projected.instruction)
        assertTrue(projected.warnings.isEmpty())
    }

    @Test
    fun `known focused pour never hides a newly supplied required warning`() {
        val pour = content("reviewed_pulsar_v1_open_and_pulse", BuiltInGuidancePlacement.LIVE_STAGE, critical = true)
            .copy(warning = "Familiar reviewed handling advice")
        val projected = projectLiveBrewContent(resolution(emptyList(), listOf(pour)),
            equipmentProfileId = PulsarLearnGuideCatalog.profileId.value, showSupportingCopy = false,
            requiredWarnings = listOf("Familiar reviewed handling advice", "Disconnect power before opening."))
        assertEquals(listOf("Familiar reviewed handling advice", "Disconnect power before opening."), projected.warnings.map { it.text })
        assertTrue(projected.warnings.all { it.summaryResource == null })
    }

    @Test
    fun `direct live packets display supplied instruction unless explicitly omitted`() {
        val step = com.adsamcik.starlitcoffee.ui.brewing.BrewStepContent(StageContentId("heat"), instruction = "Turn off heat")
        assertEquals("Turn off heat", com.adsamcik.starlitcoffee.ui.brewing.LiveBrewContent(step = step).instruction)
        assertNull(com.adsamcik.starlitcoffee.ui.brewing.LiveBrewContent(step = step, instruction = null).instruction)
    }

    @Test
    fun `no guidance strips every instructional image but retains the target and safety`() {
        val source = illustratedPour()
        val profile = PulsarLearnGuideCatalog.profileId.value
        val target = BrewStageReferenceCuePresentation.Mass(QuantityRole.BREW_WATER_INPUT,
            StageMassReference.BREW_CUMULATIVE, StageTargetQualifier.EXACT, 340.0, 340.0)
        val guided = projectLiveBrewContent(source, profile, target)
        val basic = projectLiveBrewContent(source, profile, target, minimal = true,
            requiredWarnings = listOf("Disconnect power before opening."))

        assertTrue(guided.step?.illustration != null && guided.visualCue != null)
        assertNull(basic.step?.illustration)
        assertNull(basic.visualCue)
        assertEquals(target, basic.target)
        assertEquals(listOf("Release pressure before opening.", "Disconnect power before opening."),
            basic.warnings.map { it.text })
        assertTrue(basic.warnings.all { it.critical })
        assertTrue(source.visualStatus is DurableBrewGuidanceVisualStatus.Approved)
    }

    @Test
    fun `utilities policy suppresses images even without a caller minimal flag`() {
        val source = illustratedPour().copy(policy = com.adsamcik.starlitcoffee.ui.guidance.ResolvedGuidancePolicy(
            PulsarLearnGuideCatalog.familyId,
            PulsarLearnGuideCatalog.profileId,
            com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel.UTILITIES_ONLY,
            com.adsamcik.starlitcoffee.ui.guidance.GuidancePolicySource.SESSION_OVERRIDE,
        ))
        val projected = projectLiveBrewContent(source, PulsarLearnGuideCatalog.profileId.value)
        assertNull(projected.step?.illustration)
        assertNull(projected.visualCue)
        assertEquals("Release pressure before opening.", projected.warnings.single().text)
    }

    @Test
    fun `reviewed handling stays concise while full source and unknown warnings survive`() {
        val path = "src/main/assets/" + ReviewedMethodGuideLibrary.ASSET_PATH
        val source = sequenceOf(File(path), File("app/" + path)).first(File::isFile).readText()
        val safety = ReviewedMethodGuideLibrary.decode(source).single { it.methodId == "pulsar" }.safety
        val original = LiveBrewWarning(safety.joinToString("\n"), critical = true)
        val profile = PulsarLearnGuideCatalog.profileId.value
        val bloomId = StageContentId("reviewed_pulsar_v1_open_start_bloom")
        fun live(warning: LiveBrewWarning) = liveSourceWarnings(listOf(warning), profile, bloomId, BrewStageAction.BLOOM)
        assertTrue(live(original).isEmpty())
        val removal = liveSourceWarnings(listOf(original), profile,
            StageContentId("reviewed_pulsar_v1_drain_and_serve"), BrewStageAction.RELEASE)
        assertEquals(listOf(R.string.warning_live_lift_base), removal.map { it.summaryResource })
        assertTrue(removal.single().critical)
        assertEquals(safety.joinToString("\n"), original.text)
        assertEquals(listOf(original), liveSourceWarnings(listOf(original), "other_profile", bloomId, BrewStageAction.BLOOM))
        assertEquals(listOf(original), liveSourceWarnings(listOf(original), profile, StageContentId("custom"), BrewStageAction.BLOOM))
        assertEquals(listOf(original), liveSourceWarnings(listOf(original), profile, bloomId, BrewStageAction.CUSTOM))
        val unfamiliar = LiveBrewWarning("Disconnect power before opening.", critical = true)
        assertEquals(listOf(unfamiliar), live(unfamiliar))
        val changed = LiveBrewWarning(safety[0] + " New handling requirement.", critical = true)
        assertEquals(listOf(changed), live(changed))
    }

    @Test
    fun `live target follows the current task without repeating spent water during drawdown`() {
        val water = BrewStageReferenceCuePresentation.Mass(QuantityRole.BREW_WATER_INPUT,
            StageMassReference.BREW_CUMULATIVE, StageTargetQualifier.EXACT, 340.0, 340.0)
        val dose = water.copy(role = QuantityRole.DRY_COFFEE_DOSE,
            reference = StageMassReference.RECIPE_TOTAL, minimumGrams = 20.0, maximumGrams = 20.0)
        val yield = dose.copy(role = QuantityRole.BEVERAGE_YIELD, minimumGrams = 36.0, maximumGrams = 36.0)
        fun stage(action: BrewStageAction) = CurrentBrewStagePresentation(
            StageInstanceId(StageId("target"), 1), action, StageContentId("target"), null, false,
            StageRunStatus.ACTIVE, 0L, BrewStageCompletionPresentation.Manual,
            safetyMessages = emptyList(), referenceCues = listOf(water, dose))
        assertEquals(dose, liveHeroTarget(stage(BrewStageAction.ADD_COFFEE), water))
        assertEquals(water, liveHeroTarget(stage(BrewStageAction.BLOOM), water))
        assertEquals(water, liveHeroTarget(stage(BrewStageAction.OBSERVE), water))
        assertEquals(water, liveHeroTarget(stage(BrewStageAction.CUSTOM), water))
        val drawdown = stage(BrewStageAction.RELEASE).copy(contentId = StageContentId("reviewed_pulsar_v1_drain_and_serve"))
        assertNull(liveHeroTarget(drawdown, water, PulsarLearnGuideCatalog.profileId.value))
        assertEquals(water, liveHeroTarget(drawdown, water, "other_profile"))
        val timing = BrewStageReferenceCuePresentation.Time(
            com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference.STAGE_DURATION,
            StageTargetQualifier.EXACT, 60_000L, 60_000L)
        assertEquals(timing, liveHeroTarget(stage(BrewStageAction.CUSTOM), timing))
        val countdown = stage(BrewStageAction.BLOOM).copy(
            completion = BrewStageCompletionPresentation.Countdown(60_000L, 30_000L))
        assertNull(liveHeroTarget(countdown, timing))
        val approximate = timing.copy(qualifier = StageTargetQualifier.APPROXIMATE)
        assertEquals(approximate, liveHeroTarget(countdown, approximate))
        assertEquals(yield, liveHeroTarget(stage(BrewStageAction.PRESS), yield))
    }

    private fun illustratedPour(): DurableBrewSessionGuidanceResolution {
        val pour = content("reviewed_pulsar_v1_open_and_pulse", BuiltInGuidancePlacement.LIVE_STAGE, critical = true)
            .copy(warning = "Release pressure before opening.")
        val asset = requireNotNull(liveSourceMetadata(pour.id, PulsarLearnGuideCatalog.profileId.value).visualCue).asset
        return resolution(emptyList(), listOf(pour)).copy(
            visualStatus = DurableBrewGuidanceVisualStatus.Approved(asset, pour.id, "Water level above the bed"),
        )
    }

    private fun canonicalWarning(id: String): String? =
        com.adsamcik.starlitcoffee.ui.guidance.LegacyBuiltInGuidanceCatalog.catalog.find(StageContentId(id))?.text?.warning

    private fun resolution(
        routine: List<ResolvedBrewGuidanceContent>,
        critical: List<ResolvedBrewGuidanceContent>,
    ) = DurableBrewSessionGuidanceResolution(
        policy = null,
        availability = DurableBrewGuidanceAvailability.Available,
        routineContent = routine,
        criticalContent = critical,
        stageSafetyMessages = emptyList(),
        visualStatus = DurableBrewGuidanceVisualStatus.NotRequested,
    )

    private fun content(id: String, placement: BuiltInGuidancePlacement, critical: Boolean = false) = ResolvedBrewGuidanceContent(
        id = StageContentId(id),
        placement = placement,
        instruction = id,
        target = null,
        completionCue = null,
        explanation = null,
        tip = null,
        nextAction = null,
        controlRequirements = emptyList(),
        warning = null,
        utilities = emptyList(),
        altText = id,
        safetyCritical = critical,
    )
}
