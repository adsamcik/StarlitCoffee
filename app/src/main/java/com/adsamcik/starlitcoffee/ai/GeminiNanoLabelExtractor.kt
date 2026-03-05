package com.adsamcik.starlitcoffee.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [AiLabelExtractor] implementation using Gemini Nano via ML Kit GenAI Prompt API.
 *
 * Runs fully on-device — no network, no API key, no cloud costs.
 * Requires a device with AICore and Gemini Nano support (typically Pixel 9+, Samsung S24+).
 */
class GeminiNanoLabelExtractor : AiLabelExtractor {

    private val generativeModel by lazy { Generation.getClient() }

    override suspend fun isAvailable(): Boolean {
        return try {
            generativeModel.checkStatus() == FeatureStatus.AVAILABLE
        } catch (e: Exception) {
            Log.d(TAG, "Gemini Nano availability check failed: ${e.message}")
            false
        }
    }

    override suspend fun extract(frontText: String?, backText: String?): AiExtractionResult? {
        if (frontText.isNullOrBlank() && backText.isNullOrBlank()) return null

        return try {
            val prompt = PromptTemplates.buildExtractionPrompt(frontText, backText)

            val response = generativeModel.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.1f
                    topK = 5
                },
            )

            val responseText = response.candidates.firstOrNull()?.text
            if (responseText.isNullOrBlank()) {
                Log.d(TAG, "Gemini Nano returned empty response")
                return null
            }

            parseJsonResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Nano extraction failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GeminiNanoExtractor"

        /**
         * Parses the LLM's JSON response into an [AiExtractionResult].
         * Tolerant of extra whitespace, markdown fences, and partial responses.
         */
        internal fun parseJsonResponse(rawResponse: String): AiExtractionResult? {
            return try {
                // Strip markdown code fences if present
                val cleaned = rawResponse
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                if (cleaned.isEmpty()) return null

                val json = Json.parseToJsonElement(cleaned) as? JsonObject ?: return null

                AiExtractionResult(
                    name = json.nullableString("name"),
                    roaster = json.nullableString("roaster"),
                    origin = json.nullableString("origin"),
                    region = json.nullableString("region"),
                    farm = json.nullableString("farm"),
                    variety = json.nullableString("variety"),
                    altitude = json.nullableString("altitude"),
                    processType = json.nullableString("processType"),
                    tastingNotes = json.nullableString("tastingNotes"),
                    roastLevel = json.nullableString("roastLevel"),
                    roastDate = json.nullableString("roastDate"),
                    weight = json.nullableString("weight"),
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Extracts a nullable string from a [JsonObject].
         * Returns null for JSON null, missing keys, and blank strings.
         */
        private fun JsonObject.nullableString(key: String): String? {
            val element = get(key) ?: return null
            if (element is JsonNull) return null
            val value = element.jsonPrimitive.content
            return value.ifBlank { null }
        }
    }
}
