package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P1LearnContentSelectorTest {

    @Test
    fun `steep and release Learn content follows its physical source plan`() {
        val resolution = resolve(HarioSwitchWorkflow.STEEP_AND_RELEASE)

        assertEquals(LearnGuidanceCatalogAvailability.Available, resolution.availability)
        assertEquals(
            listOf(
                "hario_switch_insert_and_rinse_filter",
                "hario_switch_close_valve",
                "hario_switch_add_coffee",
                "hario_switch_add_water",
                "hario_switch_agitate",
                "hario_switch_steep",
                "hario_switch_open_valve",
                "hario_switch_observe_drawdown",
                "hario_switch_remove_and_serve",
            ).map(::StageContentId),
            livePrimaryContentIds(resolution),
        )
        assertFalse(resolution.content.any { content ->
            content.id == StageContentId("hario_switch_open_valve_for_manual_gravity") ||
                content.id == StageContentId("hario_switch_pour_water")
        })
    }

    @Test
    fun `manual gravity Learn content follows its physical source plan`() {
        val resolution = resolve(HarioSwitchWorkflow.MANUAL_GRAVITY)

        assertEquals(LearnGuidanceCatalogAvailability.Available, resolution.availability)
        assertEquals(
            listOf(
                "hario_switch_insert_and_rinse_filter",
                "hario_switch_open_valve_for_manual_gravity",
                "hario_switch_add_coffee",
                "hario_switch_pour_water",
                "hario_switch_observe_drawdown",
                "hario_switch_remove_and_serve",
            ).map(::StageContentId),
            livePrimaryContentIds(resolution),
        )
        assertFalse(resolution.content.any { content ->
            content.id == StageContentId("hario_switch_close_valve") ||
                content.id == StageContentId("hario_switch_add_water") ||
                content.id == StageContentId("hario_switch_steep")
        })
    }

    @Test
    fun `Hario Switch without a workflow exposes only common content and requests a choice`() {
        val resolution = LearnGuidanceCatalogResolver().resolve(
            LearnGuidanceCatalogRequest(
                methodFamilyId = "steep_and_release",
                brewerProfileId = HARIO_SWITCH_PROFILE_ID.value,
                preferences = DurableBrewSessionGuidancePreferences(
                    sessionOverride = GuidancePresentationLevel.FULL,
                ),
            ),
        )

        assertEquals(
            LearnGuidanceCatalogAvailability.HarioSwitchWorkflowRequired(HARIO_SWITCH_PROFILE_ID),
            resolution.availability,
        )
        assertEquals(
            listOf(
                "hario_switch_insert_and_rinse_filter",
                "hario_switch_add_coffee",
                "hario_switch_observe_drawdown",
                "hario_switch_remove_and_serve",
            ).map(::StageContentId),
            livePrimaryContentIds(resolution),
        )
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("hario_switch_global_safety")
        })
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("hario_switch_live_targets")
        })
    }

    private fun resolve(workflow: HarioSwitchWorkflow): LearnGuidanceCatalogResolution =
        LearnGuidanceCatalogResolver().resolve(
            LearnGuidanceCatalogRequest(
                methodFamilyId = "steep_and_release",
                brewerProfileId = HARIO_SWITCH_PROFILE_ID.value,
                preferences = DurableBrewSessionGuidancePreferences(
                    sessionOverride = GuidancePresentationLevel.FULL,
                ),
                harioSwitchWorkflow = workflow,
            ),
        )

    private fun livePrimaryContentIds(
        resolution: LearnGuidanceCatalogResolution,
    ): List<StageContentId> = resolution.content
        .filter { content ->
            content.placement == BuiltInGuidancePlacement.LIVE_STAGE &&
                !content.id.value.endsWith("_safety")
        }
        .map(ResolvedLearnGuidanceContent::id)

    private companion object {
        val HARIO_SWITCH_PROFILE_ID = BrewerProfileId("hario_switch")
    }
}
