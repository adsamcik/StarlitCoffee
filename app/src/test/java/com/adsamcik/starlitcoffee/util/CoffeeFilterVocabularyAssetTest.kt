package com.adsamcik.starlitcoffee.util

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the shipped `assets/coffee_filter_vocabulary.json` parses into
 * [CoffeeFilterVocabulary] and produces sensible hints for a realistic bag OCR.
 * This guards the asset itself (schema + coverage), not just the matcher.
 */
class CoffeeFilterVocabularyAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadAsset(): CoffeeFilterVocabulary {
        val file = listOf(
            File("src/main/assets/coffee_filter_vocabulary.json"),
            File("app/src/main/assets/coffee_filter_vocabulary.json"),
        ).firstOrNull { it.isFile }
            ?: error("coffee_filter_vocabulary.json asset not found (cwd=${File(".").absolutePath})")
        return json.decodeFromString<CoffeeFilterVocabulary>(file.readText())
    }

    @Test
    fun `bundled asset parses with broad per-field coverage`() {
        val vocab = loadAsset()

        assertTrue("origins", vocab.origins.size >= 30)
        assertTrue("varieties", vocab.varieties.size >= 40)
        assertTrue("processTypes", vocab.processTypes.size >= 20)
        assertTrue("roastLevels", vocab.roastLevels.size >= 5)
        assertTrue("tastingNotes", vocab.tastingNotes.size >= 60)
        assertTrue("regions", vocab.regions.size >= 30)
    }

    @Test
    fun `bundled asset grounds a realistic bag label`() {
        val vocab = loadAsset()
        val ocr = buildString {
            appendLine("HOLYWATER COFFEE")
            appendLine("ETHIOPIA — Guji")
            appendLine("Variety: Gesha")
            appendLine("Process: Washed")
            appendLine("Roast: Light")
            appendLine("Notes: Blueberry, Jasmine, Milk Chocolate")
        }

        val hints = CoffeeVocabularyMatcher.match(ocr, vocab)

        assertTrue("origin", hints.origins.contains("Ethiopia"))
        assertTrue("region", hints.regions.contains("Guji"))
        assertTrue("variety", hints.varieties.contains("Geisha"))
        assertTrue("process", hints.processTypes.contains("Washed"))
        assertTrue("roast", hints.roastLevels.contains("Light"))
        assertTrue("notes", hints.tastingNotes.contains("Blueberry"))
        assertTrue("notes", hints.tastingNotes.contains("Milk Chocolate"))
    }
}
