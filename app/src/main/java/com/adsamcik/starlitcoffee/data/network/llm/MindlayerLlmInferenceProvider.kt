package com.adsamcik.starlitcoffee.data.network.llm

import android.content.Context
import android.graphics.BitmapFactory
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.mindlayer.sdk.ConnectionState
import com.mindlayer.sdk.Mindlayer
import com.mindlayer.sdk.MindlayerEvent
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
            var tokenCount = 0

            mindlayer.chatWithImage(sessionId!!, prompt, bitmap).events.collect { event ->
                when (event) {
                    is MindlayerEvent.TextDelta -> {
                        responseText.append(event.text)
                        tokenCount++
                    }
                    is MindlayerEvent.Error -> throw RuntimeException(event.message)
                    else -> { /* Done or other events — no action needed */ }
                }
            }

            bitmap.recycle()
            android.util.Log.d("MindlayerLlm", "LLM inference complete: $tokenCount tokens, ${responseText.length} chars")
            val result = parseResponse(responseText.toString(), request.fieldsNeeded)
            when (result) {
                is LlmExtractionResult.Success -> result.copy(tokensUsed = tokenCount)
                else -> result
            }
        } catch (e: Exception) {
            bitmap.recycle()
            LlmExtractionResult.Failed("Inference failed: ${e.message}", retryable = true)
        }
    }

    /** Release resources. Call when the provider is no longer needed. */
    fun close() {
        sessionId = null
        mindlayer.disconnect()
    }

    /** Pre-warm: connect to the Mindlayer service early so the first LLM call doesn't pay full init. */
    suspend fun ensureConnected() {
        mindlayer.awaitConnected()
    }

    companion object {
        internal fun buildSystemPrompt(): String = """
You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

For each field, report your confidence:
- "found": You can clearly see or read this on the label
- "uncertain": You think you see something but it is unclear or partially occluded
- "not_visible": This information is not visible on the label

Response format (JSON only, no markdown):
{
  "fields": {
    "name": {"value": "Ethiopia Yirgacheffe", "status": "found"},
    "roaster": {"value": "Counter Culture", "status": "found"},
    "origin": {"value": "Ethiopia", "status": "found"},
    "variety": {"value": null, "status": "not_visible"},
    "process": {"value": "Washed", "status": "uncertain"},
    "roastLevel": {"value": null, "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine, citrus", "status": "found"},
    "altitude": {"value": "1900-2100 masl", "status": "found"},
    "weight": {"value": "340g", "status": "found"},
    "roastDate": {"value": null, "status": "not_visible"}
  }
}

Rules:
- Use "not_visible" when you cannot see the information. Never guess.
- Use "uncertain" when text is blurry, partially occluded, or ambiguous.
- Use "found" only when you can clearly read or determine the value.
- Respond with ONLY a JSON object. No markdown fences or explanation.
""".trimIndent()

        internal fun buildExtractionPrompt(request: LlmExtractionRequest): String = buildString {
            append("Extract coffee bag information from this label image.")

            if (request.existingFields.isNotEmpty()) {
                val userFields = request.existingFields.filter { it.value.source == FieldSource.USER }
                val llmFields = request.existingFields.filter { it.value.source == FieldSource.LLM }
                val ocrFields = request.existingFields.filter { it.value.source == FieldSource.OCR }
                val lookupFields = request.existingFields.filter { it.value.source == FieldSource.LOOKUP }

                append("\n\nContext from prior extraction (JSON):")
                append("\n{")

                if (userFields.isNotEmpty()) {
                    append("\n  \"user_confirmed\": {")
                    userFields.forEach { (k, v) -> append("\n    \"$k\": \"${v.value}\",") }
                    append("\n  },")
                }
                if (lookupFields.isNotEmpty()) {
                    append("\n  \"barcode_lookup\": {")
                    lookupFields.forEach { (k, v) -> append("\n    \"$k\": \"${v.value}\",") }
                    append("\n  },")
                }
                if (ocrFields.isNotEmpty()) {
                    append("\n  \"ocr_detected\": {")
                    ocrFields.forEach { (k, v) -> append("\n    \"$k\": \"${v.value}\",") }
                    append("\n  },")
                }
                if (llmFields.isNotEmpty()) {
                    append("\n  \"previous_ai_run\": {")
                    llmFields.forEach { (k, v) -> append("\n    \"$k\": \"${v.value}\",") }
                    append("\n  },")
                }
                append("\n}")

                append("\n\nRules for existing values:")
                append("\n- user_confirmed: Treat as ground truth. Do not contradict.")
                append("\n- barcode_lookup: High confidence database match. Only correct if clearly wrong on the label.")
                append("\n- ocr_detected: Algorithmic text detection. Verify against what you see and correct if needed.")
                append("\n- previous_ai_run: From a prior AI pass. Verify independently — do not blindly repeat.")
                append("\n\nFocus on fields not yet identified.")
            }

            if (request.fieldsNeeded.isNotEmpty()) {
                append("\n\nFields needed: ${request.fieldsNeeded.joinToString(", ")}")
            }

            request.knownFieldValues?.let { known ->
                val parts = mutableListOf<String>()

                known.origins.takeIf { it.isNotEmpty() }?.let { origins ->
                    parts.add("Known origins: ${origins.take(20).joinToString(", ")}")
                }
                known.varieties.takeIf { it.isNotEmpty() }?.let { varieties ->
                    parts.add("Known varieties: ${varieties.take(15).joinToString(", ")}")
                }
                known.processTypes.takeIf { it.isNotEmpty() }?.let { processes ->
                    parts.add("Known processes: ${processes.take(10).joinToString(", ")}")
                }
                known.roasters.takeIf { it.isNotEmpty() }?.let { roasters ->
                    parts.add("Known roasters: ${roasters.take(20).joinToString(", ")}")
                }

                if (parts.isNotEmpty()) {
                    append("\n\nReference vocabulary from user's coffee collection:")
                    parts.forEach { append("\n- $it") }
                    append("\nPrefer these values when a match is close. Do not force a match if the label clearly says something different.")
                }
            }

            if (!request.rawOcrText.isNullOrBlank()) {
                append("\n\nRaw OCR text detected on the label:")
                append("\n\"\"\"")
                append("\n${request.rawOcrText}")
                append("\n\"\"\"")
                append("\nUse this text to verify and correct what you see in the image. The OCR may have errors.")
            }

            append("\n\nRespond with JSON only.")
        }

        internal val fieldMapping = mapOf(
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

        internal fun parseResponse(
            response: String,
            fieldsNeeded: Set<String>,
        ): LlmExtractionResult {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = Json { ignoreUnknownKeys = true }
            val jsonObj = try {
                json.parseToJsonElement(cleaned).jsonObject
            } catch (e: Exception) {
                return LlmExtractionResult.Failed(
                    "Failed to parse LLM response as JSON: ${e.message}",
                )
            }

            // Support both nested {"fields": {...}} and flat {"name": "value"} formats
            val fieldsObj = jsonObj["fields"]?.jsonObject ?: jsonObj

            val candidates = mutableListOf<BagFieldCandidate>()
            for ((jsonKey, fieldName) in fieldMapping) {
                if (fieldsNeeded.isNotEmpty() && fieldName !in fieldsNeeded) continue

                val fieldEntry = fieldsObj[jsonKey]
                val value: String?
                val status: String

                if (fieldEntry is JsonObject) {
                    // New nested format: {"value": "...", "status": "found"}
                    value = fieldEntry["value"]?.let {
                        if (it is JsonNull) null else it.jsonPrimitive.contentOrNull
                    }
                    status = fieldEntry["status"]?.jsonPrimitive?.contentOrNull ?: "found"
                } else if (fieldEntry != null && fieldEntry !is JsonNull) {
                    // Legacy flat format: "name": "value"
                    value = fieldEntry.jsonPrimitive.contentOrNull
                    status = "found"
                } else {
                    value = null
                    status = "not_visible"
                }

                if (!value.isNullOrBlank() && status != "not_visible") {
                    candidates.add(
                        BagFieldCandidate(
                            fieldName = fieldName,
                            value = value.trim(),
                            sourceType = BagFieldSourceType.LLM,
                            confidenceHint = when (status) {
                                "found" -> BagFieldConfidence.HIGH
                                "uncertain" -> BagFieldConfidence.LOW
                                else -> BagFieldConfidence.MEDIUM
                            },
                        ),
                    )
                }
            }

            return LlmExtractionResult.Success(fieldCandidates = candidates)
        }
    }
}
