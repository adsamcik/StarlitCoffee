package com.adsamcik.starlitcoffee.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroundingCheckerTest {

    // --- Levenshtein Distance ---

    @Test
    fun `levenshtein distance of identical strings is 0`() {
        assertEquals(0, GroundingChecker.levenshteinDistance("hello", "hello"))
    }

    @Test
    fun `levenshtein distance of completely different strings`() {
        assertEquals(3, GroundingChecker.levenshteinDistance("abc", "xyz"))
    }

    @Test
    fun `levenshtein distance with single substitution`() {
        assertEquals(1, GroundingChecker.levenshteinDistance("cat", "bat"))
    }

    @Test
    fun `levenshtein distance with empty string`() {
        assertEquals(5, GroundingChecker.levenshteinDistance("hello", ""))
        assertEquals(5, GroundingChecker.levenshteinDistance("", "hello"))
    }

    // --- Normalized Similarity ---

    @Test
    fun `identical strings have similarity 1_0`() {
        assertEquals(1.0, GroundingChecker.normalizedLevenshteinSimilarity("test", "test"), 0.001)
    }

    @Test
    fun `empty strings have similarity 0_0`() {
        assertEquals(0.0, GroundingChecker.normalizedLevenshteinSimilarity("test", ""), 0.001)
    }

    @Test
    fun `similar strings have high similarity`() {
        // "Ethiopia" vs "Ethopia" (1 deletion) → distance 1, maxLen 8, sim = 0.875
        val sim = GroundingChecker.normalizedLevenshteinSimilarity("ethiopia", "ethopia")
        assert(sim > 0.75) { "Expected similarity > 0.75, got $sim" }
    }

    // --- Field Classification: GROUNDED ---

    @Test
    fun `exact match in OCR text is GROUNDED`() {
        val result = GroundingChecker.classifyField(
            "roaster",
            "Yöder Coffee Co.",
            "yöder coffee co.\ngedeb\nethiopia · yirgacheffe zone",
        )
        assertEquals(FieldConfidence.GROUNDED, result)
    }

    @Test
    fun `case-insensitive match is GROUNDED`() {
        val result = GroundingChecker.classifyField(
            "origin",
            "Ethiopia",
            "ethiopia · yirgacheffe zone\nwashed · heirloom",
        )
        assertEquals(FieldConfidence.GROUNDED, result)
    }

    @Test
    fun `fuzzy match with OCR typo is GROUNDED`() {
        // "Yirgacheffe" vs "Yirgachefe" (common OCR error — 1 char off)
        val result = GroundingChecker.classifyField(
            "region",
            "Yirgacheffe",
            "yirgachefe zone\nethiopia",
        )
        assertEquals(FieldConfidence.GROUNDED, result)
    }

    @Test
    fun `token overlap match is GROUNDED`() {
        val result = GroundingChecker.classifyField(
            "tastingNotes",
            "Bergamot, peach tea, raw honey",
            "tasting notes: bergamot, peach tea, raw honey sweetness",
        )
        assertEquals(FieldConfidence.GROUNDED, result)
    }

    @Test
    fun `multi-value field with most values present is GROUNDED`() {
        val result = GroundingChecker.classifyField(
            "variety",
            "Bourbon, Caturra",
            "bourbon & caturra blend\nlot 47",
        )
        assertEquals(FieldConfidence.GROUNDED, result)
    }

    // --- Field Classification: INFERRED ---

    @Test
    fun `country inferred from known region is INFERRED`() {
        val result = GroundingChecker.classifyField(
            "origin",
            "Ethiopia",
            "gedeb, yirgacheffe zone\nwashed process",
        )
        assertEquals(FieldConfidence.INFERRED, result)
    }

    @Test
    fun `process type inferred from synonym is INFERRED`() {
        val result = GroundingChecker.classifyField(
            "processType",
            "Natural",
            "sun-dried on raised beds\nsingle origin",
        )
        assertEquals(FieldConfidence.INFERRED, result)
    }

    @Test
    fun `Colombia inferred from Huila is INFERRED`() {
        val result = GroundingChecker.classifyField(
            "origin",
            "Colombia",
            "finca la esperanza\nhuila\n1800 masl",
        )
        assertEquals(FieldConfidence.INFERRED, result)
    }

    // --- Field Classification: UNGROUNDED ---

    @Test
    fun `completely hallucinated field is UNGROUNDED`() {
        val result = GroundingChecker.classifyField(
            "roastLevel",
            "Dark roast",
            "yöder coffee co.\ngedeb\nethiopia\nwashed\nheirloom",
        )
        assertEquals(FieldConfidence.UNGROUNDED, result)
    }

    @Test
    fun `fabricated roaster name is UNGROUNDED`() {
        val result = GroundingChecker.classifyField(
            "roaster",
            "Phantom Coffee Labs",
            "square mile coffee roasters\nthe filter blend",
        )
        assertEquals(FieldConfidence.UNGROUNDED, result)
    }

    // --- Full Grounding Pipeline ---

    @Test
    fun `ground populates fieldConfidence map`() {
        val aiResult = AiExtractionResult(
            name = "Gedeb",
            roaster = "Yöder Coffee Co.",
            origin = "Ethiopia",
            processType = "Natural",
            roastLevel = "Dark",
        )
        val ocrText = "yöder coffee co.\ngedeb\nethiopia\nwashed\nheirloom"

        val grounded = GroundingChecker.ground(aiResult, ocrText)

        assertEquals(FieldConfidence.GROUNDED, grounded.fieldConfidence["name"])
        assertEquals(FieldConfidence.GROUNDED, grounded.fieldConfidence["roaster"])
        assertEquals(FieldConfidence.GROUNDED, grounded.fieldConfidence["origin"])
        assertEquals(FieldConfidence.UNGROUNDED, grounded.fieldConfidence["processType"])
        assertEquals(FieldConfidence.UNGROUNDED, grounded.fieldConfidence["roastLevel"])
    }

    @Test
    fun `withoutUngrounded removes hallucinated fields`() {
        val aiResult = AiExtractionResult(
            name = "Gedeb",
            origin = "Ethiopia",
            roastLevel = "Dark",
            fieldConfidence = mapOf(
                "name" to FieldConfidence.GROUNDED,
                "origin" to FieldConfidence.GROUNDED,
                "roastLevel" to FieldConfidence.UNGROUNDED,
            ),
        )
        val safe = aiResult.withoutUngrounded()

        assertNotNull(safe.name)
        assertNotNull(safe.origin)
        assertNull(safe.roastLevel)
    }

    // --- Null / Empty Handling ---

    @Test
    fun `null fields are not added to confidence map`() {
        val aiResult = AiExtractionResult(name = "Test", roaster = null)
        val grounded = GroundingChecker.ground(aiResult, "test label text")

        assert("name" in grounded.fieldConfidence)
        assert("roaster" !in grounded.fieldConfidence)
    }

    @Test
    fun `empty OCR text marks all fields as UNGROUNDED`() {
        val aiResult = AiExtractionResult(name = "Test", origin = "Colombia")
        val grounded = GroundingChecker.ground(aiResult, "")

        assertEquals(FieldConfidence.UNGROUNDED, grounded.fieldConfidence["name"])
        assertEquals(FieldConfidence.UNGROUNDED, grounded.fieldConfidence["origin"])
    }
}
