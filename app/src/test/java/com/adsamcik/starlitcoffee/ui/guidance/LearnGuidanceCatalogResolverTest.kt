package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnGuidanceCatalogResolverTest {

    @Test
    fun `P1 Learn resolves the entire profile curriculum and global safety`() {
        val resolution = LearnGuidanceCatalogResolver().resolve(
            request(
                methodFamilyId = "restricted_flow_gravity_concentrate",
                brewerProfileId = "vietnamese_phin",
                level = GuidancePresentationLevel.FULL,
            ),
        )

        assertEquals(LearnGuidanceCatalogAvailability.Available, resolution.availability)
        assertEquals(GuidancePresentationLevel.FULL, resolution.policy?.level)
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("vietnamese_phin_observe_first_drip") &&
                content.placement == BuiltInGuidancePlacement.LIVE_STAGE
        })
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("vietnamese_phin_global_safety") && content.safetyCritical
        })
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("vietnamese_phin_live_targets") &&
                content.placement == BuiltInGuidancePlacement.UTILITY
        })
    }

    @Test
    fun `Learn uses profile preference before family preference and compacts text`() {
        val resolution = LearnGuidanceCatalogResolver().resolve(
            LearnGuidanceCatalogRequest(
                methodFamilyId = "automatic_batch",
                brewerProfileId = "automatic_batch_generic",
                preferences = DurableBrewSessionGuidancePreferences(
                    profileOverrides = mapOf(
                        "automatic_batch_generic" to GuidancePresentationLevel.CONCISE,
                    ),
                    familyPreferences = mapOf("automatic_batch" to GuidancePresentationLevel.FULL),
                ),
            ),
        )

        val startMachine = resolution.content.single { content ->
            content.id == StageContentId("automatic_batch_generic_start_machine")
        }
        assertEquals(GuidancePresentationLevel.CONCISE, resolution.policy?.level)
        assertEquals(GuidancePolicySource.PROFILE_OVERRIDE, resolution.policy?.source)
        assertEquals("Start the machine and keep clear.", startMachine.instruction)
        assertNull(startMachine.explanation)
    }

    @Test
    fun `critical safety remains visible in Learn utilities only mode`() {
        val resolution = LearnGuidanceCatalogResolver().resolve(
            request(
                methodFamilyId = "heated_unfiltered",
                brewerProfileId = "cezve_generic",
                level = GuidancePresentationLevel.UTILITIES_ONLY,
            ),
        )

        assertEquals(LearnGuidanceCatalogAvailability.Available, resolution.availability)
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("cezve_generic_global_safety") &&
                content.safetyCritical &&
                content.warning?.isNotBlank() == true
        })
        assertTrue(resolution.content.any { content ->
            content.id == StageContentId("cezve_generic_apply_gentle_heat_safety") &&
                content.safetyCritical &&
                content.warning?.isNotBlank() == true
        })
    }

    @Test
    fun `known profile without curriculum remains unavailable instead of falling back`() {
        val resolution = LearnGuidanceCatalogResolver().resolve(
            request(
                methodFamilyId = "full_immersion_press",
                brewerProfileId = "french_press_generic",
                level = GuidancePresentationLevel.FULL,
            ),
        )

        assertEquals(
            LearnGuidanceCatalogAvailability.NoGuidanceCatalogForProfile(
                MethodFamilyId("full_immersion_press"),
                BrewerProfileId("french_press_generic"),
            ),
            resolution.availability,
        )
        assertTrue(resolution.content.isEmpty())
    }

    @Test
    fun `invalid and mismatched profile scope never resolve a different curriculum`() {
        val invalid = LearnGuidanceCatalogResolver().resolve(
            request(
                methodFamilyId = "manual_gravity",
                brewerProfileId = "HARIO_SWITCH",
                level = GuidancePresentationLevel.FULL,
            ),
        )
        val mismatch = LearnGuidanceCatalogResolver().resolve(
            request(
                methodFamilyId = "manual_gravity",
                brewerProfileId = "hario_switch",
                level = GuidancePresentationLevel.FULL,
            ),
        )

        assertEquals(
            LearnGuidanceCatalogAvailability.InvalidBrewerProfileId("HARIO_SWITCH"),
            invalid.availability,
        )
        assertEquals(
            LearnGuidanceCatalogAvailability.ProfileFamilyMismatch(
                profileId = BrewerProfileId("hario_switch"),
                requestedFamilyId = MethodFamilyId("manual_gravity"),
                catalogueFamilyId = MethodFamilyId("steep_and_release"),
            ),
            mismatch.availability,
        )
        assertFalse(mismatch.content.any { content ->
            content.id.value.startsWith("pulsar_")
        })
    }

    private fun request(
        methodFamilyId: String,
        brewerProfileId: String,
        level: GuidancePresentationLevel,
    ): LearnGuidanceCatalogRequest = LearnGuidanceCatalogRequest(
        methodFamilyId = methodFamilyId,
        brewerProfileId = brewerProfileId,
        preferences = DurableBrewSessionGuidancePreferences(sessionOverride = level),
    )
}
