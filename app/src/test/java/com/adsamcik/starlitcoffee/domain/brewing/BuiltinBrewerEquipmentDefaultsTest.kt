package com.adsamcik.starlitcoffee.domain.brewing

import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinBrewerEquipmentDefaultsTest {

    private val defaults = BuiltinBrewerEquipmentDefaults()
    private val validator = EquipmentCompatibilityValidator(BuiltinBrewingCatalog.instance)

    @Test
    fun `each supported P1 profile receives a compatible explicit equipment snapshot`() {
        BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds.forEach { profileId ->
            val configuration = requireNotNull(defaults.create(profileId))

            assertEquals(profileId, configuration.brewerProfileId)
            assertTrue(validator.validate(configuration).canStart)
            assertEquals(null, configuration.capacityOverrideG)
            assertTrue(configuration.accessoryIds.isEmpty())
            assertEquals(null, configuration.basketId)
            assertEquals(HeatSourceClass.NONE, configuration.heatSource)
        }
    }

    @Test
    fun `cezve is explicitly unfiltered rather than left unspecified`() {
        val configuration = requireNotNull(defaults.create(BrewerProfileId("cezve_generic")))

        assertEquals(FilterSelection.IntentionallyUnfiltered, configuration.filterSelection)
    }

    @Test
    fun `automatic batch chooses the deterministic cone-paper default`() {
        val configuration = requireNotNull(defaults.create(BrewerProfileId("automatic_batch_generic")))
        val selection = configuration.filterSelection as FilterSelection.Stack

        assertEquals("cone_paper", selection.entries.single().filterProfileId.value)
    }

    @Test
    fun `unknown or unsupported profiles stay unavailable`() {
        assertNull(defaults.create(BrewerProfileId("future_brewer")))
        assertNull(defaults.create(BrewerProfileId("pulsar_standard")))
    }
}
