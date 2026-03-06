package com.adsamcik.starlitcoffee.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractionParsingTest {

    // --- Prompt Template Construction ---

    @Test
    fun `prompt includes front and back text`() {
        val prompt = PromptTemplates.buildUserMessage(
            frontText = "YÖDER COFFEE CO.\nGEDEB",
            backText = "Washed · Heirloom\n250g",
        )
        assert("YÖDER COFFEE CO." in prompt)
        assert("Washed · Heirloom" in prompt)
        assert("Front of bag:" in prompt)
        assert("Back of bag:" in prompt)
    }

    @Test
    fun `prompt handles null front text`() {
        val prompt = PromptTemplates.buildUserMessage(
            frontText = null,
            backText = "Washed · Heirloom",
        )
        assert("Washed · Heirloom" in prompt)
    }

    @Test
    fun `prompt handles null back text`() {
        val prompt = PromptTemplates.buildUserMessage(
            frontText = "YÖDER COFFEE CO.",
            backText = null,
        )
        assert("YÖDER COFFEE CO." in prompt)
    }

    @Test
    fun `system instruction includes extraction instructions`() {
        val instruction = PromptTemplates.SYSTEM_INSTRUCTION
        assert("JSON" in instruction)
        assert("null" in instruction)
        assert("name" in instruction)
        assert("roaster" in instruction)
    }

    // --- JSON Response Parsing ---

    @Test
    fun `valid JSON parses to AiExtractionResult`() {
        val json = """
            {
                "name": "Gedeb",
                "roaster": "Yöder Coffee Co.",
                "origin": "Ethiopia",
                "region": "Yirgacheffe",
                "farm": null,
                "variety": "Heirloom",
                "altitude": "1950-2100 masl",
                "processType": "Washed",
                "tastingNotes": "Bergamot, peach tea, raw honey",
                "roastLevel": null,
                "roastDate": "2026-02-20",
                "weight": "250g"
            }
        """.trimIndent()

        val result = LiteRtLabelExtractor.parseResponse(json)

        assertNotNull(result)
        assertEquals("Gedeb", result!!.name)
        assertEquals("Yöder Coffee Co.", result.roaster)
        assertEquals("Ethiopia", result.origin)
        assertEquals("Yirgacheffe", result.region)
        assertNull(result.farm)
        assertEquals("Heirloom", result.variety)
        assertEquals("1950-2100 masl", result.altitude)
        assertEquals("Washed", result.processType)
        assertEquals("Bergamot, peach tea, raw honey", result.tastingNotes)
        assertNull(result.roastLevel)
        assertEquals("2026-02-20", result.roastDate)
        assertEquals("250g", result.weight)
    }

    @Test
    fun `JSON with markdown fences is parsed correctly`() {
        val json = """
            ```json
            {"name": "Test", "roaster": "Roaster", "origin": null, "region": null, "farm": null, "variety": null, "altitude": null, "processType": null, "tastingNotes": null, "roastLevel": null, "roastDate": null, "weight": null}
            ```
        """.trimIndent()

        val result = LiteRtLabelExtractor.parseResponse(json)

        assertNotNull(result)
        assertEquals("Test", result!!.name)
        assertEquals("Roaster", result.roaster)
    }

    @Test
    fun `malformed JSON returns null`() {
        val result = LiteRtLabelExtractor.parseResponse("this is not json at all")
        assertNull(result)
    }

    @Test
    fun `empty string returns null`() {
        val result = LiteRtLabelExtractor.parseResponse("")
        assertNull(result)
    }

    @Test
    fun `partial JSON with only some fields parses correctly`() {
        val json = """{"name": "Test Blend", "origin": "Brazil"}"""

        val result = LiteRtLabelExtractor.parseResponse(json)

        assertNotNull(result)
        assertEquals("Test Blend", result!!.name)
        assertEquals("Brazil", result.origin)
        assertNull(result.roaster)
        assertNull(result.variety)
    }

    @Test
    fun `JSON null values become Kotlin null`() {
        val json = """{"name": "Test", "roaster": null, "origin": null}"""

        val result = LiteRtLabelExtractor.parseResponse(json)

        assertNotNull(result)
        assertEquals("Test", result!!.name)
        assertNull(result.roaster)
        assertNull(result.origin)
    }

    @Test
    fun `JSON empty string values become null`() {
        val json = """{"name": "Test", "roaster": "", "origin": "Brazil"}"""

        val result = LiteRtLabelExtractor.parseResponse(json)

        assertNotNull(result)
        assertEquals("Test", result!!.name)
        assertNull(result.roaster)
        assertEquals("Brazil", result.origin)
    }

    // --- AiExtractionResult Conversion ---

    @Test
    fun `toOcrExtractionResult preserves all fields`() {
        val ai = AiExtractionResult(
            name = "Test",
            roaster = "Roaster",
            origin = "Brazil",
            region = "Cerrado",
            variety = "Bourbon",
            processType = "Natural",
            altitude = "1200 masl",
            tastingNotes = "Chocolate, nutty",
            roastLevel = "Medium",
            roastDate = "2026-01-15",
            weight = "250g",
        )
        val ocr = ai.toOcrExtractionResult()

        assertEquals("Test", ocr.name)
        assertEquals("Roaster", ocr.roaster)
        assertEquals("Brazil", ocr.origin)
        assertEquals("Cerrado", ocr.region)
        assertEquals("Bourbon", ocr.variety)
        assertEquals("Natural", ocr.processType)
        assertEquals("1200 masl", ocr.altitude)
        assertEquals("Chocolate, nutty", ocr.tastingNotes)
        assertEquals("Medium", ocr.roastLevel)
        assertEquals("2026-01-15", ocr.roastDate)
        assertEquals("250g", ocr.weight)
    }
}
