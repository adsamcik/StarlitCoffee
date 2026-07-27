package com.adsamcik.starlitcoffee.domain.brewing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentCompatibilityValidatorTest {

    private val validator = EquipmentCompatibilityValidator(BuiltinBrewingCatalog.instance)

    @Test
    fun `Pulsar accepts its 19K filter profile`() {
        val result = validator.validate(
            EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("pulsar_standard"),
                filterSelection = FilterSelection.Stack(
                    listOf(FilterStackEntry(FilterProfileId("pulsar_19k_metal"), 0)),
                ),
            ),
        )

        assertTrue(result.canStart)
    }

    @Test
    fun `V60 rejects a Pulsar-specific metal filter`() {
        val result = validator.validate(
            EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("v60_02"),
                filterSelection = FilterSelection.Stack(
                    listOf(FilterStackEntry(FilterProfileId("pulsar_40k_metal"), 0)),
                ),
            ),
        )

        assertFalse(result.canStart)
        assertTrue(result.issues.any { it.code == "filter_incompatible_with_brewer" })
    }

    @Test
    fun `cezve can explicitly be unfiltered while V60 cannot`() {
        val cezve = validator.validate(
            EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("cezve_generic"),
                filterSelection = FilterSelection.IntentionallyUnfiltered,
            ),
        )
        val v60 = validator.validate(
            EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("v60_02"),
                filterSelection = FilterSelection.IntentionallyUnfiltered,
            ),
        )

        assertTrue(cezve.canStart)
        assertFalse(v60.canStart)
    }
}
