package com.adsamcik.starlitcoffee.data.model

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun grinderCatalogAsset(): String = listOf(
    File("src/main/assets/grinders.json"), File("app/src/main/assets/grinders.json"),
).first { it.isFile }.readText()

class GrinderCatalogTest {
    private val catalog = GrinderDataSource.fromJson(grinderCatalogAsset())

    @Test
    fun `every advertised grinder has complete dial metadata and directly sourced guidance`() {
        assertEquals(setOf("1zpresso-zp6-special", "comandante-c40", "fellow-ode-gen2",
            "baratza-encore-esp", "niche-zero"), catalog.grinders.map { it.id }.toSet())
        catalog.grinders.forEach { grinder ->
            assertNotNull(grinder.dial)
            assertTrue(grinder.sourceUrls.isNotEmpty())
            assertTrue(catalog.recommendations.any { it.grinderId == grinder.id })
        }
        catalog.recommendations.forEach { recommendation ->
            val grinder = catalog.grinders.single { it.id == recommendation.grinderId }
            val dial = requireNotNull(grinder.dial)
            assertTrue(dial.accepts(recommendation.suggestedStart))
            assertTrue(dial.accepts(recommendation.rangeStart))
            assertTrue(dial.accepts(recommendation.rangeEnd))
            assertTrue(recommendation.sourceUrls.isNotEmpty())
        }
    }

    @Test
    fun `picker never offers a grinder that would fall back to generic guidance`() {
        BrewMethod.entries.forEach { method ->
            (listOf<FilterType?>(null) + FilterType.entries).forEach { filter ->
                catalog.grindersFor(method, filter).forEach { grinder ->
                    assertNotNull(catalog.recommendationFor(grinder.id, method, filter))
                }
            }
        }
        assertEquals(listOf("comandante-c40", "baratza-encore-esp", "niche-zero"),
            catalog.grindersFor(BrewMethod.ESPRESSO, null).map { it.id })
        assertEquals(listOf("1zpresso-zp6-special", "fellow-ode-gen2"),
            catalog.grindersFor(BrewMethod.PULSAR, FilterType.PAPER).map { it.id })
        assertTrue(catalog.grindersFor(BrewMethod.PULSAR, FilterType.METAL_19K).isEmpty())
        assertTrue(catalog.grindersFor(BrewMethod.PULSAR, FilterType.METAL_40K).isEmpty())
    }

    @Test
    fun `Encore ESP uses its own manufacturer points rather than the original Encore chart`() {
        mapOf(BrewMethod.ESPRESSO to 15f, BrewMethod.AEROPRESS to 22f,
            BrewMethod.V60 to 25f, BrewMethod.FRENCH_PRESS to 32f).forEach { (method, setting) ->
            val recommendation = requireNotNull(catalog.recommendationFor("baratza-encore-esp", method, null))
            assertEquals(setting, recommendation.suggestedStart, 0f)
            assertEquals(setting, recommendation.rangeStart, 0f)
            assertEquals(setting, recommendation.rangeEnd, 0f)
        }
    }

    @Test
    fun `invalid Ode positions cannot enter the supported catalog`() {
        assertThrows(IllegalArgumentException::class.java) {
            GrinderDataSource.fromJson(alterRecommendation("fellow-ode-gen2", "suggestedStart", JsonPrimitive(5.2)))
        }
    }

    @Test
    fun `uncited recommendations cannot enter the supported catalog`() {
        assertThrows(IllegalArgumentException::class.java) {
            GrinderDataSource.fromJson(alterRecommendation("niche-zero", "sourceUrls", JsonArray(emptyList())))
        }
    }

    private fun alterRecommendation(id: String, key: String, value: kotlinx.serialization.json.JsonElement): String {
        val original = Json.parseToJsonElement(grinderCatalogAsset()).jsonObject
        var replaced = false
        val recommendations = original.getValue("recommendations").jsonArray.map { element ->
            val record = element.jsonObject
            if (!replaced && record["grinderId"] == JsonPrimitive(id)) {
                replaced = true
                JsonObject(record + (key to value))
            } else {
                record
            }
        }
        return JsonObject(original + ("recommendations" to JsonArray(recommendations))).toString()
    }
}
