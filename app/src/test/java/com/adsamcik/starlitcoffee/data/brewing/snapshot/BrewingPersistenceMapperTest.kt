package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewingPersistenceMapperTest {

    @Test
    fun `legacy row remains a conservative legacy payload`() {
        val record = BrewingPersistenceMapper.recipeRecord(
            SavedRecipeEntity(method = "V60", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        val legacy = record.payload as StoredRecipePayload.Legacy
        assertEquals("v60_unspecified", (legacy.reference.brewerProfile as com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution.Known).value.value)
    }

    @Test
    fun `versioned log keeps immutable snapshot and session identity`() {
        val snapshot = BrewRecordSnapshotV1(
            recipe = recipe(),
            sourceSessionId = "session-1",
        )
        val mapped = BrewingPersistenceMapper.withBrewRecordSnapshot(
            BrewLogEntity(method = "V60", doseG = 20f, waterG = 340f, ratio = 17f),
            snapshot,
        )

        val record = BrewingPersistenceMapper.brewLogRecord(mapped)

        assertEquals("manual_gravity", mapped.methodFamilyId)
        assertEquals("v60_02", mapped.brewerProfileId)
        assertEquals("session-1", mapped.sourceSessionId)
        assertTrue(record.payload is StoredBrewRecordPayload.Versioned)
    }

    private fun recipe() = BrewRecipeSnapshotV1(
        methodFamilyId = "manual_gravity",
        brewerProfileId = "v60_02",
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = "v60_02"),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 340.0),
        ratioDefinition = RatioDefinitionSnapshotV1("DRY_COFFEE_DOSE", "BREW_WATER_INPUT"),
        outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
    )
}
