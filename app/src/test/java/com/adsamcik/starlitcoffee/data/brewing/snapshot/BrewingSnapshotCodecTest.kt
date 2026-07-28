package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewingSnapshotCodecTest {

    @Test
    fun `recipe snapshot round trips unknown future values`() {
        val snapshot = sampleRecipe(
            outputModel = OutputModelSnapshotV1(kind = "FUTURE_OUTPUT", internalRetentionG = 3.0),
            builtInRecipeId = "future_profile_recipe",
        )

        val decoded = BrewingSnapshotCodec.decodeRecipe(BrewingSnapshotCodec.encodeRecipe(snapshot))

        val restored = (decoded as SnapshotDecodeResult.Decoded).value
        assertEquals("future_profile", restored.brewerProfileId)
        assertEquals("future_profile_recipe", restored.builtInRecipeId)
        assertEquals("FUTURE_OUTPUT", restored.outputModel.kind)
        assertEquals("FUTURE_HEAT", restored.technique.heatStrategy)
    }

    @Test
    fun `source faithful recipe semantics survive an encoded snapshot`() {
        val definition = requireNotNull(
            BuiltInP1RecipeCatalog.find(BuiltInRecipeId("v60_kurasu_flash_16_150_70")),
        )
        val snapshot = BuiltInP1RecipeSnapshotMapper.enrich(
            snapshot = sampleRecipe(
                outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
                methodFamilyId = definition.methodFamilyId.value,
                brewerProfileId = definition.brewerProfileId.value,
            ),
            definition = definition,
        )

        val decoded = BrewingSnapshotCodec.decodeRecipe(BrewingSnapshotCodec.encodeRecipe(snapshot))
        val restored = (decoded as SnapshotDecodeResult.Decoded).value

        assertEquals(definition.id.value, restored.builtInRecipeId)
        assertEquals(2, restored.ratioSemantics.size)
        assertEquals(9.375, restored.ratioSemantics.first().ratioValue!!, 0.0)
        assertEquals(
            listOf("BREW_WATER_INPUT", "ICE"),
            restored.ratioSemantics.last().includedDenominatorRoles,
        )
        assertEquals("USER_EXACT", restored.temperatureSemantics?.basis)
        assertEquals("APPROXIMATE_WITH_OBSERVATION", restored.expectedTimeSemantics?.basis)
        assertEquals("DRAWDOWN_AND_BREW_ICE_MELT", restored.completionSemantics)
        assertEquals(definition.sourceBrewerProfileId.value, restored.sourceMetadata?.sourceBrewerProfileId)
        assertEquals(BuiltInP1RecipeCatalog.SOURCE_SHA256, restored.sourceMetadata?.sourceSha256)
        assertEquals(definition.orderedStageCount, restored.sourceMetadata?.orderedStageCount)
    }

    @Test
    fun `legacy recipe snapshot without built-in identity remains decodable`() {
        val encoded = BrewingSnapshotCodec.encodeRecipe(
            sampleRecipe(outputModel = OutputModelSnapshotV1(kind = "FUTURE_OUTPUT")),
        )

        val decoded = BrewingSnapshotCodec.decodeRecipe(encoded)

        val restored = (decoded as SnapshotDecodeResult.Decoded).value
        assertNull(restored.builtInRecipeId)
        assertTrue(restored.ratioSemantics.isEmpty())
        assertNull(restored.temperatureSemantics)
        assertNull(restored.expectedTimeSemantics)
        assertNull(restored.sourceMetadata)
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

    private fun sampleRecipe(
        outputModel: OutputModelSnapshotV1,
        builtInRecipeId: String? = null,
        methodFamilyId: String = "manual_gravity",
        brewerProfileId: String = "future_profile",
    ) = BrewRecipeSnapshotV1(
        methodFamilyId = methodFamilyId,
        brewerProfileId = brewerProfileId,
        builtInRecipeId = builtInRecipeId,
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = brewerProfileId),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 340.0),
        ratioDefinition = RatioDefinitionSnapshotV1("DRY_COFFEE_DOSE", "BREW_WATER_INPUT"),
        technique = RecipeTechniqueSnapshotV1(heatStrategy = "FUTURE_HEAT"),
        outputModel = outputModel,
    )
}
