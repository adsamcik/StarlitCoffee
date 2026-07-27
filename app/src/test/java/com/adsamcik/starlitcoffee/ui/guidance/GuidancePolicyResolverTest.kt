package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidancePolicyResolverTest {

    @Test
    fun `session override has precedence over profile and family preferences`() {
        val resolved = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = FAMILY,
                brewerProfileId = PROFILE,
                sessionOverride = GuidancePresentationLevel.UTILITIES_ONLY,
                profileOverrides = mapOf(PROFILE to GuidancePresentationLevel.FOCUSED),
                familyPreferences = mapOf(FAMILY to GuidancePresentationLevel.CONCISE),
            ),
        )

        assertEquals(GuidancePresentationLevel.UTILITIES_ONLY, resolved.level)
        assertEquals(GuidancePolicySource.SESSION_OVERRIDE, resolved.source)
    }

    @Test
    fun `profile override has precedence over family preference`() {
        val resolved = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = FAMILY,
                brewerProfileId = PROFILE,
                profileOverrides = mapOf(PROFILE to GuidancePresentationLevel.FOCUSED),
                familyPreferences = mapOf(FAMILY to GuidancePresentationLevel.CONCISE),
            ),
        )

        assertEquals(GuidancePresentationLevel.FOCUSED, resolved.level)
        assertEquals(GuidancePolicySource.PROFILE_OVERRIDE, resolved.source)
    }

    @Test
    fun `family preference is used when no higher-precedence choice exists`() {
        val resolved = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = FAMILY,
                brewerProfileId = PROFILE,
                familyPreferences = mapOf(FAMILY to GuidancePresentationLevel.CONCISE),
            ),
        )

        assertEquals(GuidancePresentationLevel.CONCISE, resolved.level)
        assertEquals(GuidancePolicySource.FAMILY_PREFERENCE, resolved.source)
    }

    @Test
    fun `new family and profile begin with the safe Full default`() {
        val resolved = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(methodFamilyId = FAMILY, brewerProfileId = PROFILE),
        )

        assertEquals(GuidancePresentationLevel.FULL, resolved.level)
        assertEquals(GuidancePolicySource.SAFE_DEFAULT, resolved.source)
    }

    @Test
    fun `all supported levels including Custom round trip through the resolver`() {
        GuidancePresentationLevel.entries.forEach { expectedLevel ->
            val resolved = GuidancePolicyResolver.resolve(
                GuidancePolicyContext(
                    methodFamilyId = FAMILY,
                    brewerProfileId = PROFILE,
                    sessionOverride = expectedLevel,
                ),
            )

            assertEquals(expectedLevel, resolved.level)
            assertEquals(GuidancePolicySource.SESSION_OVERRIDE, resolved.source)
        }
    }

    @Test
    fun `a profile override is ignored when no current profile is selected`() {
        val resolved = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = FAMILY,
                profileOverrides = mapOf(PROFILE to GuidancePresentationLevel.UTILITIES_ONLY),
                familyPreferences = mapOf(FAMILY to GuidancePresentationLevel.CONCISE),
            ),
        )

        assertEquals(GuidancePresentationLevel.CONCISE, resolved.level)
        assertEquals(GuidancePolicySource.FAMILY_PREFERENCE, resolved.source)
    }

    @Test
    fun `critical safety remains visible even when routine content is hidden`() {
        val utilitiesOnly = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = FAMILY,
                sessionOverride = GuidancePresentationLevel.UTILITIES_ONLY,
            ),
        )
        val fullOnly = GuidanceVisibilityPolicy(
            visibleIn = setOf(GuidancePresentationLevel.FULL),
        )

        assertFalse(utilitiesOnly.isVisible(fullOnly, safetyCritical = false))
        assertTrue(utilitiesOnly.isVisible(fullOnly, safetyCritical = true))
    }

    private companion object {
        val FAMILY = MethodFamilyId("valve_controlled_no_bypass")
        val PROFILE = BrewerProfileId("pulsar_standard")
    }
}
