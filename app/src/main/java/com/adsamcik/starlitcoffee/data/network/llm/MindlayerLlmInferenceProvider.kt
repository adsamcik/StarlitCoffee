package com.adsamcik.starlitcoffee.data.network.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.mindlayer.sdk.ConnectionState
import com.mindlayer.sdk.InferenceBackend
import com.mindlayer.sdk.Mindlayer
import com.mindlayer.sdk.MindlayerException
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MINDLAYER_LLM_TAG = "MindlayerLlm"
private const val MAX_DECODED_IMAGE_EDGE_PX = 1600

private fun decodeImageCapped(imageBytes: ByteArray): Bitmap? {
    @Suppress("TooGenericExceptionCaught")
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = if (longestEdge > 0) {
            computeSampleSize(longestEdge, MAX_DECODED_IMAGE_EDGE_PX)
        } else {
            1
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    } catch (error: OutOfMemoryError) {
        android.util.Log.w(MINDLAYER_LLM_TAG, "Image decode ran out of memory", error)
        null
    } catch (e: Exception) {
        android.util.Log.w(MINDLAYER_LLM_TAG, "Image decode failed", e)
        null
    }
}

private fun computeSampleSize(longestEdge: Int, maxEdge: Int): Int {
    var sample = 1
    while (longestEdge / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    return sample
}

/**
 * LLM inference provider that uses the Mindlayer on-device LLM service.
 *
 * Sends coffee bag label images to a model running locally on the device
 * via the Mindlayer SDK. The LLM extracts structured coffee bag metadata
 * (name, roaster, origin, tasting notes, etc.) from the label image.
 *
 * Each extraction runs as a stateless one-shot via [Mindlayer.generateWithImage]:
 * no conversation history is carried over between bags, so the model never sees
 * a previous scan's image or response. This is essential for field-extraction
 * correctness — the SDK would otherwise accumulate turns in its HistoryStore
 * and replay them as context.
 *
 * Sampling is tuned for deterministic JSON (low temperature, narrow topK/topP)
 * rather than chat defaults (0.7 / 40 / 0.95).
 *
 * No cloud, no API keys, fully private, works offline.
 */
class MindlayerLlmInferenceProvider(
    context: Context,
) : LlmInferenceProvider {

    private val mindlayer: Mindlayer = Mindlayer.connect(context.applicationContext)
    @Volatile
    private var connectionFailed: Boolean = false

    override fun isAvailable(): Boolean {
        val state = mindlayer.connectionState.value
        val isConnectingOrConnected = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
        if (isConnectingOrConnected) {
            connectionFailed = false
        }
        return isConnectingOrConnected || connectionFailed
    }

    override suspend fun extractBagFields(
        request: LlmExtractionRequest,
    ): LlmExtractionResult = withContext(Dispatchers.IO) {
        // Run the entire extraction off the main thread:
        //  * BitmapFactory.decodeByteArray and Mindlayer's MediaTransfer.fromBitmap
        //    are documented as blocking ("should be called from a background thread").
        //  * The Mindlayer SDK does not switch dispatchers internally — every binder
        //    call and per-token flow resumption runs on the caller's dispatcher.
        // When called from viewModelScope.launch (Main), this would otherwise
        // back up ML Kit completion callbacks and delay ImageProxy.close() on
        // the analyzer thread, visibly freezing the camera preview while the
        // model is generating tokens.
        // Boundary catch: Mindlayer's binder service can fail with various
        // service-disconnection or remote exceptions; the SDK surfaces them
        // via `awaitConnected`. Treat any non-cancellation throwable as
        // service-unavailable so the consensus engine falls back gracefully.
        @Suppress("TooGenericExceptionCaught")
        try {
            awaitMindlayerConnected()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmExtractionResult.Unavailable(
                "Mindlayer service not available: ${e.message}",
            )
        }

        val bitmap = decodeImageCapped(request.imageBytes)
            ?: return@withContext LlmExtractionResult.Failed(
                "Failed to decode image",
                retryable = false,
            )

        val prompt = buildExtractionPrompt(request)
        val extended = useExtendedSchema(request)
        val structuredContext = buildStructuredOutputExtraContext(extended)

        // try/finally guarantees the bitmap is recycled exactly once across
        // every exit path — success, MindlayerException, generic Exception,
        // or CancellationException propagation. The previous structure
        // duplicated `bitmap.recycle()` in four places, which is safe today
        // (Bitmap.recycle is idempotent on AOSP) but easy to break later
        // by adding a new return without remembering to recycle.
        //
        // Boundary catch on the generic `Exception` branch: model inference
        // can throw anything from JSON parsing failures to native crashes;
        // mapping all of them to a retryable Failed lets the consensus
        // engine try again on the next golden frame.
        @Suppress("TooGenericExceptionCaught")
        try {
            // Stateless one-shot — SDK creates a fresh session, runs inference,
            // then destroys it. No history is carried over to the next extraction.
            val responseText = withTimeout(EXTRACTION_TIMEOUT_MS) {
                mindlayer.generateWithImage(prompt, bitmap) {
                    systemPrompt(buildSystemPrompt(extended))
                    maxTokens(MAX_TOKENS)
                    temperature(EXTRACTION_TEMPERATURE)
                    topK(EXTRACTION_TOP_K)
                    topP(EXTRACTION_TOP_P)
                    // Opt-in structured output. Services that predate the feature
                    // ignore the extraContext JSON and degrade to plain generation.
                    extraContext(structuredContext)
                }
            }
            android.util.Log.d(
                MINDLAYER_LLM_TAG,
                "LLM inference complete: ${responseText.length} chars",
            )
            parseResponse(responseText, request.fieldsNeeded)
        } catch (_: TimeoutCancellationException) {
            LlmExtractionResult.Failed(
                "Inference timed out after ${EXTRACTION_TIMEOUT_MS / 1000}s",
                retryable = true,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation must propagate so callers (e.g. LlmEscalationCoordinator)
            // know the work was abandoned and don't apply stale results to a
            // session that has already been torn down.
            throw e
        } catch (e: MindlayerException) {
            LlmExtractionResult.Failed(
                "Inference failed: ${e.message}",
                retryable = e.code != "UNSUPPORTED_TOOL_CALL",
            )
        } catch (e: Exception) {
            LlmExtractionResult.Failed("Inference failed: ${e.message}", retryable = true)
        } finally {
            bitmap.recycle()
        }
    }

    /** Release resources. Call when the provider is no longer needed. */
    fun close() {
        mindlayer.disconnect()
    }

    /**
     * Pre-warm: connect to the Mindlayer service AND load the inference engine
     * so the first extraction doesn't pay the 5–10 s engine init cost.
     *
     * Runs on [Dispatchers.IO] — `awaitConnected` performs a binder transaction
     * and `prewarm` blocks until the engine is loaded; both must stay off the
     * main thread to avoid freezing the camera preview during the first scan.
     */
    override suspend fun prewarm() {
        withContext(Dispatchers.IO) {
            awaitMindlayerConnected()
            mindlayer.prewarm(PREWARM_BACKEND)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun awaitMindlayerConnected() {
        try {
            mindlayer.awaitConnected()
            connectionFailed = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            connectionFailed = true
            throw e
        }
    }

    /**
     * Backwards-compatible alias for [prewarm]. Older call sites used this
     * name before the interface gained a [prewarm] hook; kept for any
     * external test that still references it directly.
     */
    @Deprecated("Use prewarm() instead", ReplaceWith("prewarm()"))
    suspend fun ensureConnected() = prewarm()

    companion object {
        /** Max tokens per extraction. Enough for full 10-field JSON with tasting notes. */
        private const val MAX_TOKENS = 2048

        /** Low temperature for deterministic structured JSON output. */
        private const val EXTRACTION_TEMPERATURE = 0.1f

        /** Narrow topK — JSON schema is rigid; we don't want creative tokens. */
        private const val EXTRACTION_TOP_K = 20

        /** Tight topP for structured output. */
        private const val EXTRACTION_TOP_P = 0.9f

        /** Preferred backend for prewarm. GPU is the standard fast path. */
        private val PREWARM_BACKEND = InferenceBackend.GPU

        /** Bounded generation time so a wedged model cannot hang a scan forever. */
        internal const val EXTRACTION_TIMEOUT_MS = 60_000L

        internal fun buildSystemPrompt(extended: Boolean = false): String =
            if (extended) SYSTEM_PROMPT_14 else SYSTEM_PROMPT_10

        /**
         * Decide whether to request the extended 14-field schema.
         *
         * Eval shows that without OCR or existing-field grounding, the extended
         * schema **regresses** (extra fields → more confabulation on sparse
         * labels). We only opt into it when there is grounding context that
         * lets the model anchor the extra fields (region/farm/expiryDate/isDecaf).
         */
        internal fun useExtendedSchema(request: LlmExtractionRequest): Boolean =
            !request.rawOcrText.isNullOrBlank() || request.existingFields.isNotEmpty()

        private val SYSTEM_PROMPT_10 = """
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

        private val SYSTEM_PROMPT_14 = """
You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

For each field, report your confidence:
- "found": You can clearly see or read this on the label
- "uncertain": You think you see something but it is unclear or partially occluded
- "not_visible": This information is not visible on the label

Response format (JSON only, no markdown):
{
  "fields": {
    "name":         {"value": "Ethiopia Yirgacheffe",  "status": "found"},
    "roaster":      {"value": "Counter Culture",       "status": "found"},
    "origin":       {"value": "Ethiopia",              "status": "found"},
    "region":       {"value": "Yirgacheffe",           "status": "found"},
    "farm":         {"value": null,                    "status": "not_visible"},
    "variety":      {"value": null,                    "status": "not_visible"},
    "process":      {"value": "Washed",                "status": "uncertain"},
    "roastLevel":   {"value": null,                    "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine",    "status": "found"},
    "altitude":     {"value": "1900-2100 masl",        "status": "found"},
    "weight":       {"value": "340g",                  "status": "found"},
    "roastDate":    {"value": "2026-03-01",            "status": "found"},
    "expiryDate":   {"value": "2026-09-01",            "status": "found"},
    "isDecaf":      {"value": false,                   "status": "found"}
  }
}

Field notes:
- roastLevel: common values are Light, Medium, Dark — but also accept roaster-style labels like "Filter", "Espresso", or "Omni" when that is what the label says.
- roastDate/expiryDate: normalize to YYYY-MM-DD if possible; otherwise emit the string as printed on the label.
- isDecaf: boolean. Only set true when the label explicitly says decaf / bezkofeinová / decaffeinated; false when clearly regular / caffeinated; not_visible otherwise.
- Labels may be in English or Czech — translate tasting notes to English when confident; keep proper nouns verbatim.

Rules:
- Use "not_visible" when you cannot see the information. Never guess.
- Use "uncertain" when text is blurry, partially occluded, or ambiguous.
- Use "found" only when you can clearly read or determine the value.
- Respond with ONLY a JSON object. No markdown fences or explanation.
""".trimIndent()

        /**
         * Build the `extraContextJson` payload requesting structured output
         * from the Mindlayer service. Uses the PROMPT_AND_VALIDATE strategy —
         * safer than TOOL_ROUTING when multimodal (image) inputs are in play.
         * On services that predate the structured-output engine, this payload
         * is silently ignored.
         */
        internal fun buildStructuredOutputExtraContext(extended: Boolean): String {
            val schema = if (extended) SCHEMA_14 else SCHEMA_10
            val envelope = buildJsonObject {
                put(
                    "structured_output",
                    buildJsonObject {
                        put("schema", schema)
                        put("strategy", JsonPrimitive("prompt_and_validate"))
                        put("max_retries", JsonPrimitive(2))
                    },
                )
            }
            return Json.encodeToString(JsonObject.serializer(), envelope)
        }

        private val SCHEMA_10: JsonObject = buildFieldsSchema(
            listOf(
                "name", "roaster", "origin", "variety", "process", "roastLevel",
                "tastingNotes", "altitude", "weight", "roastDate",
            ),
            booleanField = null,
        )

        private val SCHEMA_14: JsonObject = buildFieldsSchema(
            listOf(
                "name", "roaster", "origin", "region", "farm", "variety",
                "process", "roastLevel", "tastingNotes", "altitude",
                "weight", "roastDate", "expiryDate",
            ),
            booleanField = "isDecaf",
        )

        private fun buildFieldsSchema(
            stringFields: List<String>,
            booleanField: String?,
        ): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "fields",
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    stringFields.forEach { name ->
                                        put(name, fieldEntrySchema(valueTypes = listOf("string", "null")))
                                    }
                                    if (booleanField != null) {
                                        put(
                                            booleanField,
                                            fieldEntrySchema(valueTypes = listOf("boolean", "null")),
                                        )
                                    }
                                },
                            )
                            put("required", buildJsonArray { add("fields") })
                        },
                    )
                },
            )
            put("required", buildJsonArray { add("fields") })
        }

        private fun fieldEntrySchema(valueTypes: List<String>): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "value",
                        buildJsonObject {
                            put(
                                "type",
                                buildJsonArray { valueTypes.forEach { add(JsonPrimitive(it)) } },
                            )
                        },
                    )
                    put(
                        "status",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "enum",
                                buildJsonArray {
                                    add(JsonPrimitive("found"))
                                    add(JsonPrimitive("uncertain"))
                                    add(JsonPrimitive("not_visible"))
                                },
                            )
                        },
                    )
                },
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("value"))
                    add(JsonPrimitive("status"))
                },
            )
        }

        internal fun buildExtractionPrompt(request: LlmExtractionRequest): String = buildString {
            append("Extract coffee bag information from this label image.")

            if (request.existingFields.isNotEmpty()) {
                val grouped = request.existingFields.entries.groupBy { it.value.source }
                val userFields = grouped[FieldSource.USER].orEmpty()
                val llmFields = grouped[FieldSource.LLM].orEmpty()
                val ocrFields = grouped[FieldSource.OCR].orEmpty()
                val lookupFields = grouped[FieldSource.LOOKUP].orEmpty()

                // Serialise as real JSON rather than hand-concatenating — a
                // label value containing `"}` or `\n` would otherwise break
                // out of the context block and be read as prompt instructions.
                val contextObj = buildJsonObject {
                    if (userFields.isNotEmpty()) {
                        put("user_confirmed", entriesToJsonObject(userFields))
                    }
                    if (lookupFields.isNotEmpty()) {
                        put("barcode_lookup", entriesToJsonObject(lookupFields))
                    }
                    if (ocrFields.isNotEmpty()) {
                        put("ocr_detected", entriesToJsonObject(ocrFields))
                    }
                    if (llmFields.isNotEmpty()) {
                        put("previous_ai_run", entriesToJsonObject(llmFields))
                    }
                }
                append("\n\nContext from prior extraction (JSON):\n")
                append(Json.encodeToString(JsonObject.serializer(), contextObj))

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
                    append(
                        "\nPrefer these values when a match is close. " +
                            "Do not force a match if the label clearly says something different.",
                    )
                }
            }

            if (!request.rawOcrText.isNullOrBlank()) {
                // Escape backslash + triple-quote so OCR text cannot terminate
                // the delimiter block and inject prompt instructions.
                val safe = request.rawOcrText
                    .replace("\\", "\\\\")
                    .replace("\"\"\"", "\\\"\\\"\\\"")
                append("\n\nRaw OCR text detected on the label:")
                append("\n\"\"\"")
                append("\n$safe")
                append("\n\"\"\"")
                append("\nUse this text to verify and correct what you see in the image. The OCR may have errors.")
            }

            append("\n\nRespond with JSON only.")
        }

        private fun entriesToJsonObject(
            entries: List<Map.Entry<String, com.adsamcik.starlitcoffee.scan.model.FieldContext>>,
        ): JsonObject = buildJsonObject {
            entries.forEach { (k, v) -> put(k, JsonPrimitive(v.value)) }
        }

        internal val fieldMapping = mapOf(
            "name" to "name",
            "roaster" to "roaster",
            "origin" to "origin",
            "region" to "region",
            "farm" to "farm",
            "variety" to "variety",
            "process" to "processType",
            "roastLevel" to "roastLevel",
            "tastingNotes" to "tastingNotes",
            "altitude" to "altitude",
            "weight" to "weight",
            "roastDate" to "roastDate",
            "expiryDate" to "expiryDate",
            "isDecaf" to "isDecaf",
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
            // Boundary catch: a malformed LLM response can throw
            // SerializationException, IllegalArgumentException, or anything
            // else from the parser; the only useful product behaviour is to
            // surface a single Failed result and let the consensus engine
            // try the next golden frame.
            @Suppress("TooGenericExceptionCaught")
            val jsonObj = try {
                json.parseToJsonElement(cleaned).jsonObject
            } catch (e: Exception) {
                return LlmExtractionResult.Failed(
                    "Failed to parse LLM response as JSON: ${e.message}",
                )
            }

            // Support both nested {"fields": {...}} and flat {"name": "value"} formats
            val fieldsObj = jsonObj["fields"]?.jsonObject ?: jsonObj

            val candidates = fieldMapping.mapNotNull { (jsonKey, fieldName) ->
                if (fieldsNeeded.isNotEmpty() && fieldName !in fieldsNeeded) {
                    null
                } else {
                    extractFieldCandidate(fieldName, fieldsObj[jsonKey])
                }
            }
            return LlmExtractionResult.Success(fieldCandidates = candidates)
        }

        /**
         * Map one entry from the parsed JSON to a [BagFieldCandidate].
         *
         * Supports two response shapes:
         *  * Nested `{"value": "...", "status": "found"}` (current schema).
         *  * Legacy flat `"name": "value"` (older deployments).
         *
         * Returns `null` when the field is missing, marked `not_visible`,
         * or has an empty value.
         */
        private fun extractFieldCandidate(
            fieldName: String,
            fieldEntry: kotlinx.serialization.json.JsonElement?,
        ): BagFieldCandidate? {
            val (value, status) = when {
                fieldEntry is JsonObject -> {
                    val v = fieldEntry["value"]?.let { e ->
                        if (e is JsonNull) null else e.jsonPrimitive.contentOrNull
                    }
                    val s = fieldEntry["status"]?.jsonPrimitive?.contentOrNull ?: "found"
                    v to s
                }
                fieldEntry != null && fieldEntry !is JsonNull -> {
                    fieldEntry.jsonPrimitive.contentOrNull to "found"
                }
                else -> null to "not_visible"
            }
            if (value.isNullOrBlank() || status == "not_visible") return null
            return BagFieldCandidate(
                fieldName = fieldName,
                value = value.trim(),
                sourceType = BagFieldSourceType.LLM,
                confidenceHint = when (status) {
                    "found" -> BagFieldConfidence.HIGH
                    "uncertain" -> BagFieldConfidence.LOW
                    else -> BagFieldConfidence.MEDIUM
                },
            )
        }
    }
}
