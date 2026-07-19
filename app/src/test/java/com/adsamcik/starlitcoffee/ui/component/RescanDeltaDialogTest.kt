package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.DateParser
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescanDeltaDialogTest {

    @Test
    fun `localized controlled aliases do not create false deltas`() {
        val deltas = buildFieldDeltas(
            bag = CoffeeBagEntity(
                name = "Lot 1",
                origin = "Ethiopia",
                processType = "Washed",
                variety = "Geisha",
            ),
            resolvedFields = mapOf(
                "origin" to "Etiopie",
                "processType" to "Lavado",
                "variety" to "Gesha",
            ),
            locale = Locale.forLanguageTag("es"),
        )

        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `richer controlled value does not collapse to broader canonical id`() {
        val deltas = buildFieldDeltas(
            bag = CoffeeBagEntity(name = "Lot 1", processType = "Anaerobic Natural"),
            resolvedFields = mapOf("processType" to "Natural"),
            locale = Locale.ENGLISH,
        )

        assertEquals(listOf("Process"), deltas.map(FieldDelta::label))
    }

    @Test
    fun `decaf delta applies true to false and clears process`() {
        val bag = CoffeeBagEntity(
            name = "Decaf",
            isDecaf = true,
            decafProcess = "SWISS_WATER",
        )
        val fields = mapOf("isDecaf" to "false")

        val deltas = buildFieldDeltas(bag, fields)
        val updated = applyDeltaToBag(bag, fields)

        assertEquals(listOf("Decaf"), deltas.map(FieldDelta::label))
        assertFalse(updated.isDecaf)
        assertNull(updated.decafProcess)
    }

    @Test
    fun `decaf delta applies false to true`() {
        val bag = CoffeeBagEntity(name = "Regular", isDecaf = false)

        val updated = applyDeltaToBag(bag, mapOf("isDecaf" to "Decaf"))

        assertTrue(updated.isDecaf)
    }

    @Test
    fun `matching package weight does not restore consumed coffee`() {
        val bag = CoffeeBagEntity(
            name = "Part-used bag",
            initialWeightG = 250f,
            weightG = 100f,
        )

        val deltas = buildFieldDeltas(bag, mapOf("weight" to "250 g"))
        val updated = applyDeltaToBag(bag, mapOf("weight" to "250 g"))

        assertTrue(deltas.isEmpty())
        assertEquals(250f, updated.initialWeightG)
        assertEquals(100f, updated.weightG)
    }

    @Test
    fun `corrected package weight updates baseline without restoring consumed coffee`() {
        val bag = CoffeeBagEntity(
            name = "Part-used bag",
            initialWeightG = 250f,
            weightG = 100f,
        )

        val deltas = buildFieldDeltas(bag, mapOf("weight" to "300 g"))
        val updated = applyDeltaToBag(bag, mapOf("weight" to "300 g"))

        assertEquals(
            listOf(FieldDelta("Weight", "250g", "300g")),
            deltas,
        )
        assertEquals(300f, updated.initialWeightG)
        assertEquals(100f, updated.weightG)
    }

    @Test
    fun `unknown package baseline falls back to remaining weight and preserves inventory`() {
        val bag = CoffeeBagEntity(
            name = "Legacy bag",
            initialWeightG = null,
            weightG = 100f,
        )

        val deltas = buildFieldDeltas(bag, mapOf("weight" to "250 g"))
        val updated = applyDeltaToBag(bag, mapOf("weight" to "250 g"))

        assertEquals(
            listOf(FieldDelta("Weight", "100g", "250g")),
            deltas,
        )
        assertEquals(250f, updated.initialWeightG)
        assertEquals(100f, updated.weightG)
    }

    @Test
    fun `farm altitude and date changes are included and applied`() {
        val oldRoastDate = requireNotNull(DateParser.parse("2026-01-10"))
        val oldExpiryDate = requireNotNull(DateParser.parse("2026-07-10"))
        val fields = mapOf(
            "farm" to "Chelbesa",
            "altitude" to "1950-2200 masl",
            "roastDate" to "2026-01-11",
            "expiryDate" to "2026-08-10",
        )
        val bag = CoffeeBagEntity(
            name = "Lot 1",
            farm = "Worka",
            altitude = "1900 masl",
            roastDate = oldRoastDate,
            expiryDate = oldExpiryDate,
        )

        val deltas = buildFieldDeltas(bag, fields, locale = Locale.ENGLISH)
        val updated = applyDeltaToBag(bag, fields)

        assertEquals(
            setOf("Farm", "Altitude", "Roast date", "Expiry date"),
            deltas.map(FieldDelta::label).toSet(),
        )
        assertEquals("Chelbesa", updated.farm)
        assertEquals("1950-2200 masl", updated.altitude)
        assertEquals(DateParser.parse("2026-01-11"), updated.roastDate)
        assertEquals(DateParser.parse("2026-08-10"), updated.expiryDate)
    }

    @Test
    fun `reviewed photos keep update action available without metadata changes`() {
        val deltas = buildFieldDeltas(
            bag = CoffeeBagEntity(name = "Lot 1", photoUris = "file:///old.jpg"),
            resolvedFields = emptyMap(),
            reviewedPhotoUris = "file:///front.jpg,file:///back.jpg",
        )

        assertEquals(listOf("Photos"), deltas.map(FieldDelta::label))
    }

    @Test
    fun `rescan photo mapping preserves reviewed metadata`() {
        val updated = applyRescannedPhotoMapping(
            updatedBag = CoffeeBagEntity(
                id = 7L,
                name = "Lot 1",
                farm = "Chelbesa",
                altitude = "2100 masl",
                isDecaf = true,
            ),
            permanentUris = "file:///front.webp,file:///back.webp",
            thumbnailUri = "file:///thumb.webp",
            scanSessionId = "rescan-session",
        )

        assertEquals("Chelbesa", updated.farm)
        assertEquals("2100 masl", updated.altitude)
        assertTrue(updated.isDecaf)
        assertEquals("file:///thumb.webp", updated.photoUri)
        assertEquals("file:///front.webp,file:///back.webp", updated.photoUris)
        assertEquals("rescan-session", updated.scanSessionId)
    }
}
