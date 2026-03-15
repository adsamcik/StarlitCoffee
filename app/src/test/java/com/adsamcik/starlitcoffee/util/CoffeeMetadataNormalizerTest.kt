package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeMetadataNormalizerTest {

    // --- Canonical Matching ---

    @Test
    fun `normalizes multilingual origin aliases to one canonical id`() {
        val english = CoffeeMetadataNormalizer.normalizeField("origin", "Ethiopia", Locale.ENGLISH)
        val czech = CoffeeMetadataNormalizer.normalizeField("origin", "Etiopie", Locale.ENGLISH)
        val spanish = CoffeeMetadataNormalizer.normalizeField("origin", "Etiopia", Locale.ENGLISH)
        val abbreviation = CoffeeMetadataNormalizer.normalizeField("origin", "ETH", Locale.ENGLISH)

        assertNotNull(english)
        assertEquals("ETHIOPIA", english!!.canonicalKey)
        assertEquals("ETHIOPIA", czech!!.canonicalKey)
        assertEquals("ETHIOPIA", spanish!!.canonicalKey)
        assertEquals("ETHIOPIA", abbreviation!!.canonicalKey)
        assertEquals("Ethiopia", english.value)
    }

    @Test
    fun `normalizes gesha alias to geisha variety`() {
        val normalized = CoffeeMetadataNormalizer.normalizeField("variety", "Gesha", Locale.ENGLISH)

        assertNotNull(normalized)
        assertEquals("GEISHA", normalized!!.canonicalKey)
        assertEquals("Geisha", normalized.value)
    }

    @Test
    fun `normalizes multilingual tasting notes to canonical ids`() {
        val normalized = CoffeeMetadataNormalizer.normalizeField(
            fieldName = "tastingNotes",
            rawValue = "Lesní jahoda, Yuzu, Zelený čaj",
            locale = Locale.ENGLISH,
        )

        assertNotNull(normalized)
        assertEquals("green_tea,wild_strawberry,yuzu", normalized!!.canonicalKey)
        assertEquals("Wild strawberry, Yuzu, Green tea", normalized.value)
    }

    // --- Localization ---

    @Test
    fun `localizes stored origin id to requested locale`() {
        val localized = CoffeeMetadataNormalizer.displayOrigin(
            originId = "ETHIOPIA",
            fallbackRaw = "Ethiopia",
            locale = Locale("cs"),
        )

        assertEquals("Etiopie", localized)
    }

    @Test
    fun `localizes stored process id to requested locale`() {
        val localized = CoffeeMetadataNormalizer.displayProcessType(
            processTypeId = "WASHED",
            fallbackRaw = "Washed",
            locale = Locale("es"),
        )

        assertEquals("Lavado", localized)
    }

    @Test
    fun `resolveBagMetadata infers origin from region and localizes display`() {
        val resolved = CoffeeMetadataNormalizer.resolveBagMetadata(
            CoffeeBagEntity(
                name = "Vacation bag",
                region = "Guji",
            ),
            locale = Locale("cs"),
        )

        assertEquals("ETHIOPIA", resolved.originId)
        assertEquals("Etiopie", resolved.origin)
        assertEquals("GUJI", resolved.regionId)
        assertEquals("Guji", resolved.region)
    }

    @Test
    fun `applyToBagEntity preserves raw multilingual strings while storing canonical ids`() {
        val normalizedBag = CoffeeMetadataNormalizer.applyToBagEntity(
            bag = CoffeeBagEntity(name = "Vacation bag"),
            origin = "Etiopie",
            region = "Guji",
            roastLevel = "Světlé",
            processType = "Lavado",
            variety = "Gesha",
            tastingNotes = "Lesní jahoda, Zelený čaj",
            locale = Locale.ENGLISH,
        )

        assertEquals("Etiopie", normalizedBag.origin)
        assertEquals("ETHIOPIA", normalizedBag.originId)
        assertEquals("Guji", normalizedBag.region)
        assertEquals("GUJI", normalizedBag.regionId)
        assertEquals("Světlé", normalizedBag.roastLevel)
        assertEquals("LIGHT", normalizedBag.roastLevelIds)
        assertEquals("Lavado", normalizedBag.processType)
        assertEquals("WASHED", normalizedBag.processTypeId)
        assertEquals("Gesha", normalizedBag.variety)
        assertEquals("GEISHA", normalizedBag.varietyIds)
        assertEquals("Lesní jahoda, Zelený čaj", normalizedBag.tastingNotes)
        assertEquals("green_tea,wild_strawberry", normalizedBag.tasteNoteIds)
    }

    // --- Edge Cases ---

    @Test
    fun `normalizes accentless duplicate tasting notes to stable canonical ids`() {
        val normalized = CoffeeMetadataNormalizer.normalizeField(
            fieldName = "tastingNotes",
            rawValue = "LESNI JAHODA, lesní jahoda, ZELENY CAJ, zelený čaj",
            locale = Locale("cs"),
        )

        assertNotNull(normalized)
        assertEquals("green_tea,wild_strawberry", normalized!!.canonicalKey)
        assertEquals("Lesní jahoda, Zelený čaj", normalized.value)
    }

    @Test
    fun `normalizeBagMetadata localizes canonical values to requested locale`() {
        val normalized = CoffeeMetadataNormalizer.normalizeBagMetadata(
            origin = "Äthiopien",
            region = "Guji",
            roastLevel = "Světlé",
            processType = "Praný",
            variety = "Gesha",
            tastingNotes = "Lesní jahoda, Zelený čaj",
            locale = Locale("es"),
        )

        assertEquals("ETHIOPIA", normalized.originId)
        assertEquals("Etiopía", normalized.origin)
        assertEquals("GUJI", normalized.regionId)
        assertEquals("Guji", normalized.region)
        assertEquals("LIGHT", normalized.roastLevelIds)
        assertEquals("Claro", normalized.roastLevel)
        assertEquals("WASHED", normalized.processTypeId)
        assertEquals("Lavado", normalized.processType)
        assertEquals("GEISHA", normalized.varietyIds)
        assertEquals("Geisha", normalized.variety)
        assertEquals("green_tea,wild_strawberry", normalized.tasteNoteIds)
        assertEquals("Fresa silvestre, Té verde", normalized.tastingNotes)
    }

    @Test
    fun `displayTastingNotes keeps prettified raw fallback when canonical ids are incomplete`() {
        val localized = CoffeeMetadataNormalizer.displayTastingNotes(
            tasteNoteIds = "wild_strawberry",
            fallbackRaw = "lesní jahoda, dragon fruit",
            locale = Locale.ENGLISH,
        )

        assertEquals("Lesní jahoda, Dragon fruit", localized)
    }

    @Test
    fun `resolveBagMetadata does not infer origin from ambiguous region`() {
        val resolved = CoffeeMetadataNormalizer.resolveBagMetadata(
            CoffeeBagEntity(
                name = "Borderlands",
                region = "Kivu",
            ),
            locale = Locale.ENGLISH,
        )

        assertNull(resolved.originId)
        assertNull(resolved.origin)
        assertEquals("KIVU", resolved.regionId)
        assertEquals("Kivu", resolved.region)
    }

    @Test
    fun `displayProcessType falls back to default label for unsupported locale`() {
        val localized = CoffeeMetadataNormalizer.displayProcessType(
            processTypeId = "WASHED",
            fallbackRaw = "Lavado",
            locale = Locale("fr"),
        )

        assertEquals("Washed", localized)
    }

    @Test
    fun `regionsForOrigin accepts localized aliases when finding canonical regions`() {
        val regions = CoffeeMetadataNormalizer.regionsForOrigin(
            originValue = "Etiopie",
            locale = Locale("cs"),
        )

        assertTrue(regions.contains(CoffeeRegion.Known.GUJI))
        assertTrue(regions.contains(CoffeeRegion.Known.YIRGACHEFFE))
        assertTrue(regions.none { it == CoffeeRegion.Known.HUILA })
    }

    @Test
    fun `containsDecafMarker recognizes multilingual decaf aliases`() {
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Kolumbie Tumbaga decaf"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Bez kofeinu"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Entkoffeiniert"))
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("Caffeinated espresso roast"))
    }
}
