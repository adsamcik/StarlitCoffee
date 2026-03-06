package com.adsamcik.starlitcoffee.ai

/**
 * Prompt templates for on-device LLM coffee bag label extraction.
 *
 * The prompt instructs the model to extract structured JSON from raw OCR text.
 * Few-shot examples guide output format. The model is told to return null for
 * any field not explicitly present on the label to minimize hallucination.
 */
object PromptTemplates {

    /**
     * Builds a user message for the Conversation API containing just the label text.
     * System instruction and few-shot examples are provided via [SYSTEM_INSTRUCTION].
     */
    fun buildUserMessage(frontText: String?, backText: String?): String {
        return buildString {
            appendLine("Extract from this label:")
            appendLine()
            if (!frontText.isNullOrBlank()) {
                appendLine("Front of bag:")
                appendLine("\"\"\"")
                appendLine(frontText.trim())
                appendLine("\"\"\"")
            }
            if (!backText.isNullOrBlank()) {
                if (!frontText.isNullOrBlank()) appendLine()
                appendLine("Back of bag:")
                appendLine("\"\"\"")
                appendLine(backText.trim())
                appendLine("\"\"\"")
            }
        }.trim()
    }

    /**
     * Full system instruction for the Conversation API.
     * Includes persona, rules, few-shot examples, and output format.
     */
    internal val SYSTEM_INSTRUCTION = """
You are a coffee bag label parser. Extract structured information from OCR text of a coffee bag label.

Rules:
- Only extract information that is explicitly stated on the label.
- Return null for any field you cannot find or confidently determine.
- Do NOT guess or infer fields that aren't present.
- For "origin", extract the country name. If only a region is mentioned (e.g., "Yirgacheffe"), you may infer the country.
- For "variety", list all mentioned varieties separated by commas.
- For "tastingNotes", extract flavor descriptors, stripping marketing language.
- For "weight", include the unit (e.g., "250g" or "12oz").
- For "roastDate", preserve the original date format from the label.
- Return valid JSON only, no markdown formatting.

Example 1:
Front of bag:
${"\"\"\""}
YÖDER COFFEE CO.
GEDEB
Ethiopia · Yirgacheffe Zone
${"\"\"\""}

Back of bag:
${"\"\"\""}
Washed · Heirloom varieties
Roasted 2026-02-20
Tasting notes: Bergamot, peach tea, raw honey
Grown at 1,950–2,100 masl
Net weight: 250g
${"\"\"\""}

Output:
{"name":"Gedeb","roaster":"Yöder Coffee Co.","origin":"Ethiopia","region":"Yirgacheffe","farm":null,"variety":"Heirloom","altitude":"1950-2100 masl","processType":"Washed","tastingNotes":"Bergamot, peach tea, raw honey","roastLevel":null,"roastDate":"2026-02-20","weight":"250g"}

Example 2:
Front of bag:
${"\"\"\""}
Square Mile Coffee Roasters
THE FILTER BLEND
${"\"\"\""}

Back of bag:
${"\"\"\""}
A sweet and balanced blend
Brazil Fazenda Cachoeira, natural process
Chocolate, hazelnut, caramel
Medium roast · 350g
${"\"\"\""}

Output:
{"name":"The Filter Blend","roaster":"Square Mile Coffee Roasters","origin":"Brazil","region":null,"farm":"Fazenda Cachoeira","variety":null,"altitude":null,"processType":"Natural","tastingNotes":"Chocolate, hazelnut, caramel","roastLevel":"Medium","roastDate":null,"weight":"350g"}

Output the extracted fields as a single JSON object with these keys:
name, roaster, origin, region, farm, variety, altitude, processType, tastingNotes, roastLevel, roastDate, weight

Use null for any field not found on the label. Return ONLY the JSON object, no other text.
    """.trim()
}
