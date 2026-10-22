package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.R
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
        val overflow = content("pulsar_overflow_safety", BuiltInGuidancePlacement.LIVE_STAGE, critical = true)
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
            compactLiveWarning(StageContentId("pulsar_hot_liquid_safety"), profile))
        assertEquals(R.string.msg_brew_pulsar_overflow,
            compactLiveWarning(StageContentId("pulsar_overflow_safety"), profile))
        assertEquals(R.string.msg_brew_pulsar_pour_safety,
            compactLiveWarning(StageContentId("reviewed_pulsar_v1_open_and_pulse"), profile))
        assertNull(compactLiveWarning(StageContentId("unfamiliar_safety"), profile))
        assertNull(compactLiveWarning(StageContentId("pulsar_hot_liquid_safety"), "v60_02"))
        assertNull(compactLiveWarning(StageContentId("pulsar_hot_liquid_safety"), null))
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
