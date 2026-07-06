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
    fun `normalizes Typica Mejorado variety and its Mejorado alias`() {
        val full = CoffeeMetadataNormalizer.normalizeField("variety", "Typica Mejorado", Locale.ENGLISH)
        val alias = CoffeeMetadataNormalizer.normalizeField("variety", "Mejorado", Locale.ENGLISH)

        assertNotNull(full)
        assertEquals("TYPICA_MEJORADO", full!!.canonicalKey)
        assertEquals("Typica Mejorado", full.value)
        assertEquals("TYPICA_MEJORADO", alias!!.canonicalKey)
    }

    @Test
    fun `normalizes Nepal origin`() {
        val normalized = CoffeeMetadataNormalizer.normalizeField("origin", "Nepal", Locale.ENGLISH)

        assertNotNull(normalized)
        assertEquals("NEPAL", normalized!!.canonicalKey)
        assertEquals("Nepal", normalized.value)
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
            locale = Locale.forLanguageTag("cs"),
        )

        assertEquals("Etiopie", localized)
    }

    @Test
    fun `localizes stored process id to requested locale`() {
        val localized = CoffeeMetadataNormalizer.displayProcessType(
            processTypeId = "WASHED",
            fallbackRaw = "Washed",
            locale = Locale.forLanguageTag("es"),
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
            locale = Locale.forLanguageTag("cs"),
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
            locale = Locale.forLanguageTag("cs"),
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
            locale = Locale.forLanguageTag("es"),
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
            locale = Locale.forLanguageTag("fr"),
        )

        assertEquals("Washed", localized)
    }

    @Test
    fun `regionsForOrigin accepts localized aliases when finding canonical regions`() {
        val regions = CoffeeMetadataNormalizer.regionsForOrigin(
            originValue = "Etiopie",
            locale = Locale.forLanguageTag("cs"),
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

    @Test
    fun `containsDecafMarker handles expanded language coverage`() {
        // French
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Café décaféiné du Pérou"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("sans caféine"))
        // Portuguese / Spanish
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Descafeinado natural"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("sem cafeína"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("sin cafeína"))
        // Italian
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("caffè decaffeinato"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("senza caffeina"))
        // German / Dutch
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Koffeinfreier Kaffee"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("ohne Koffein"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("cafeïnevrij"))
        // Nordic / Hungarian / Turkish / Romanian
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("koffeinfri kaffe"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("uden koffein"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("kofeiiniton kahvi"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("koffeinmentes kávé"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("kafeinsiz kahve"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("cafea decofeinizată"))
    }

    @Test
    fun `containsDecafMarker uses token-boundary matching to avoid false positives`() {
        // "decaffeine" substring lives inside "decafeinating-something" patterns but the
        // existing list historically only matched "decaf" as a loose substring. Ensure
        // tokens that merely contain "caf" aren't flagged.
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("Decadent roast"))
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("Full caffeine blend"))
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("Highly caffeinated"))
    }

    @Test
    fun `containsDecafMarker respects negation prefixes`() {
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("not decaf"))
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("This is a non-decaf blend"))
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("no decaf available"))
        // Only the immediately preceding token negates — a negation earlier in a sentence
        // followed by a separate decaf clause still counts as decaf.
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("not your usual roast, decaf edition"))
    }

    @Test
    fun `containsDecafMarker handles Swiss Water and ethyl acetate phrasing via decaf token`() {
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Swiss Water Decaf"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("Ethyl Acetate decaffeinated"))
        assertTrue(CoffeeMetadataNormalizer.containsDecafMarker("CO2 Process Decaf Colombia"))
        // Plain "Swiss Water" or "Ethyl Acetate" without the decaf word is intentionally
        // not flagged — these processes aren't exclusively decaf.
        assertFalse(CoffeeMetadataNormalizer.containsDecafMarker("Ethyl Acetate natural"))
    }
}
