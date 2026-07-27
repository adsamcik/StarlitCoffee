package com.adsamcik.starlitcoffee.domain.brewing

import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewingCatalogTest {

    @Test
    fun `legacy V60 resolves to an explicit conservative profile`() {
        val reference = LegacyBrewingAdapter.fromLegacy("V60", null)

        val profileId = (reference.brewerProfile as CatalogResolution.Known).value
        val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(profileId))

        assertEquals("v60_unspecified", profile.id.value)
        assertEquals("manual_gravity", profile.familyId.value)
        assertTrue(reference.equipment.filterSelection is FilterSelection.Unspecified)
    }

    @Test
    fun `legacy Pulsar filter maps to a physical filter stack`() {
        val reference = LegacyBrewingAdapter.fromLegacy("PULSAR", "METAL_19K")

        val stack = reference.equipment.filterSelection as FilterSelection.Stack

        assertEquals("pulsar_19k_metal", stack.entries.single().filterProfileId.value)
        assertFalse(reference.equipment.wasInvalidForMethod)
    }

    @Test
    fun `filter on a non Pulsar legacy recipe is retained but not treated as equipment`() {
        val reference = LegacyBrewingAdapter.fromLegacy("V60", "METAL_40K")

        assertTrue(reference.equipment.filterSelection is FilterSelection.Unspecified)
        assertEquals("METAL_40K", reference.equipment.rawLegacyFilterId)
        assertTrue(reference.equipment.wasInvalidForMethod)
    }

    @Test
    fun `unknown legacy method remains inspectable`() {
        val reference = LegacyBrewingAdapter.fromLegacy("FUTURE_BREWER", null)

        val resolution = reference.brewerProfile as CatalogResolution.Unknown

        assertEquals("FUTURE_BREWER", resolution.rawId)
    }
}
