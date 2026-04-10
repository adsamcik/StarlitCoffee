package com.adsamcik.starlitcoffee.data.network.llm

import android.content.Context
import android.graphics.BitmapFactory
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.mindlayer.sdk.ConnectionState
import com.mindlayer.sdk.Mindlayer
import com.mindlayer.sdk.MindlayerEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LLM inference provider that uses the Mindlayer on-device LLM service.
 *
 * Sends coffee bag label images to a model running locally on the device
 * via the Mindlayer SDK. The LLM extracts structured coffee bag metadata
 * (name, roaster, origin, tasting notes, etc.) from the label image.
 *
 * No cloud, no API keys, fully private, works offline.
 */
class MindlayerLlmInferenceProvider(
    context: Context,
) : LlmInferenceProvider {

    private val mindlayer: Mindlayer = Mindlayer.connect(context.applicationContext)
    private var sessionId: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun isAvailable(): Boolean {
        return mindlayer.connectionState.value == ConnectionState.CONNECTED
    }

    override suspend fun extractBagFields(request: LlmExtractionRequest): LlmExtractionResult {
        try {
            mindlayer.awaitConnected()
        } catch (e: Exception) {
            return LlmExtractionResult.Unavailable(
                "Mindlayer service not available: ${e.message}",
            )
        }

        if (sessionId == null) {
            try {
                sessionId = mindlayer.createSession {
                    systemPrompt(buildSystemPrompt())
                    maxTokens(2048)
                    backend("CPU")
                }
            } catch (e: Exception) {
                return LlmExtractionResult.Failed(
                    "Failed to create session: ${e.message}",
                    retryable = true,
                )
            }
        }

        val bitmap = BitmapFactory.decodeByteArray(
            request.imageBytes,
            0,
            request.imageBytes.size,
        ) ?: return LlmExtractionResult.Failed("Failed to decode image", retryable = false)

        val prompt = buildExtractionPrompt(request)

        return try {
            val responseText = StringBuilder()

            mindlayer.chatWithImage(sessionId!!, prompt, bitmap).collect { event ->
                when (event) {
                    is MindlayerEvent.TextDelta -> responseText.append(event.text)
                    is MindlayerEvent.Error -> throw RuntimeException(event.message)
                    else -> { /* Done or other events — no action needed */ }
                }
            }

            bitmap.recycle()
            parseResponse(responseText.toString(), request.fieldsNeeded)
        } catch (e: Exception) {
            bitmap.recycle()
            LlmExtractionResult.Failed("Inference failed: ${e.message}", retryable = true)
        }
    }

    private fun buildSystemPrompt(): String = """
You are a coffee bag label analyzer. When given an image of a coffee bag label, extract structured information and respond ONLY with a JSON object.

Extract these fields when visible:
- name: The coffee name/blend name
- roaster: The roasting company name
- origin: Country or region of origin
- variety: Coffee variety (e.g., Bourbon, Typica, Gesha)
- process: Processing method (e.g., Washed, Natural, Honey)
- roastLevel: Roast level (Light, Medium, Medium-Dark, Dark)
- tastingNotes: Comma-separated flavor notes
- altitude: Growing altitude if mentioned
- weight: Package weight with unit
- roastDate: Roast date if visible

Respond with ONLY a JSON object. Use null for fields you cannot determine.
Do NOT include markdown fences or explanation.
""".trimIndent()

    private fun buildExtractionPrompt(request: LlmExtractionRequest): String = buildString {
        append("Extract coffee bag information from this label image.")

        if (request.existingFields.isNotEmpty()) {
            append("\n\nAlready known: ")
            request.existingFields.forEach { (key, value) ->
                append("$key=$value, ")
            }
            append("\nFocus on fields not yet identified.")
        }

        if (request.fieldsNeeded.isNotEmpty()) {
            append("\n\nFields needed: ${request.fieldsNeeded.joinToString(", ")}")
        }

        append("\n\nRespond with JSON only.")
    }

    private fun parseResponse(
        response: String,
        fieldsNeeded: Set<String>,
    ): LlmExtractionResult {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonObj = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (e: Exception) {
            return LlmExtractionResult.Failed(
                "Failed to parse LLM response as JSON: ${e.message}",
            )
        }

        val fieldMapping = mapOf(
            "name" to "name",
            "roaster" to "roaster",
            "origin" to "origin",
            "variety" to "variety",
            "process" to "processType",
            "roastLevel" to "roastLevel",
            "tastingNotes" to "tastingNotes",
            "altitude" to "altitude",
            "weight" to "weight",
            "roastDate" to "roastDate",
        )

        val candidates = mutableListOf<BagFieldCandidate>()
        for ((jsonKey, fieldName) in fieldMapping) {
            if (fieldsNeeded.isNotEmpty() && fieldName !in fieldsNeeded) continue

            val value = jsonObj[jsonKey]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank()) {
                candidates.add(
                    BagFieldCandidate(
                        fieldName = fieldName,
                        value = value.trim(),
                        sourceType = BagFieldSourceType.LLM,
                        confidenceHint = BagFieldConfidence.MEDIUM,
                    ),
                )
            }
        }

        return LlmExtractionResult.Success(fieldCandidates = candidates)
    }

    /** Release resources. Call when the provider is no longer needed. */
    fun close() {
        sessionId = null
        mindlayer.disconnect()
    }
}
