package com.adsamcik.starlitcoffee.data.brewing.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewingSnapshotCodecTest {

    @Test
    fun `recipe snapshot round trips unknown future values`() {
        val snapshot = sampleRecipe(
            outputModel = OutputModelSnapshotV1(kind = "FUTURE_OUTPUT", internalRetentionG = 3.0),
        )

        val decoded = BrewingSnapshotCodec.decodeRecipe(BrewingSnapshotCodec.encodeRecipe(snapshot))

        val restored = (decoded as SnapshotDecodeResult.Decoded).value
        assertEquals("future_profile", restored.brewerProfileId)
        assertEquals("FUTURE_OUTPUT", restored.outputModel.kind)
        assertEquals("FUTURE_HEAT", restored.technique.heatStrategy)
    }

    @Test
    fun `newer schema is retained as unsupported instead of defaulted`() {
        val decoded = BrewingSnapshotCodec.decodeRecipe("{\"schemaVersion\":2}")

        val unsupported = decoded as SnapshotDecodeResult.UnsupportedVersion
        assertEquals(2, unsupported.schemaVersion)
    }

    @Test
    fun `missing schema is invalid rather than assumed`() {
        val decoded = BrewingSnapshotCodec.decodeRecipe("{}")

        assertTrue(decoded is SnapshotDecodeResult.Invalid)
    }

    private fun sampleRecipe(outputModel: OutputModelSnapshotV1) = BrewRecipeSnapshotV1(
        methodFamilyId = "manual_gravity",
        brewerProfileId = "future_profile",
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = "future_profile"),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 340.0),
        ratioDefinition = RatioDefinitionSnapshotV1("DRY_COFFEE_DOSE", "BREW_WATER_INPUT"),
        technique = RecipeTechniqueSnapshotV1(heatStrategy = "FUTURE_HEAT"),
        outputModel = outputModel,
    )
}
