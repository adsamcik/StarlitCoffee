package com.adsamcik.starlitcoffee.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [AiLabelExtractor] implementation backed by LiteRT-LM with Gemma 3n.
 *
 * Uses the Conversation API so the system instruction (persona + few-shot examples)
 * lives in [ConversationConfig.systemInstruction], while each extraction request
 * is a single user message.
 */
class LiteRtLabelExtractor(private val modelPath: String) : AiLabelExtractor {

    private var engine: Engine? = null

    /** Loads the LiteRT-LM engine. Must be called before [extract]. */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
            )
            engine = Engine(config).also { it.initialize() }
        }
    }

    override suspend fun isAvailable(): Boolean = engine != null

    override suspend fun extract(frontText: String?, backText: String?): AiExtractionResult? {
        val eng = engine ?: return null
        if (frontText.isNullOrBlank() && backText.isNullOrBlank()) return null

        return try {
            withContext(Dispatchers.IO) {
                val conversation = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(PromptTemplates.SYSTEM_INSTRUCTION),
                        samplerConfig = SamplerConfig(topK = 5, topP = 0.9, temperature = 0.1),
                    ),
                )
                conversation.use { conv ->
                    val userMessage = PromptTemplates.buildUserMessage(frontText, backText)
                    val response = conv.sendMessage(userMessage)
                    parseResponse(response.toString())
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Releases model resources. */
    fun close() {
        engine?.close()
        engine = null
    }

    internal companion object {

        /**
         * Parses a JSON response string into [AiExtractionResult].
         * Handles markdown code fences, missing fields, and null/empty values.
         */
        fun parseResponse(responseText: String): AiExtractionResult? {
            val jsonStr = responseText
                .replace("```json", "").replace("```", "")
                .trim()
                .let { text ->
                    val start = text.indexOf('{')
                    val end = text.lastIndexOf('}')
                    if (start >= 0 && end > start) text.substring(start, end + 1) else return null
                }

            return try {
                val json = Json.parseToJsonElement(jsonStr).jsonObject

                fun getString(key: String): String? =
                    json[key]?.let { element ->
                        if (element is JsonNull) return@let null
                        element.jsonPrimitive.contentOrNull?.ifBlank { null }
                    }

                AiExtractionResult(
                    name = getString("name"),
                    roaster = getString("roaster"),
                    origin = getString("origin"),
                    region = getString("region"),
                    farm = getString("farm"),
                    variety = getString("variety"),
                    altitude = getString("altitude"),
                    processType = getString("processType"),
                    tastingNotes = getString("tastingNotes"),
                    roastLevel = getString("roastLevel"),
                    roastDate = getString("roastDate"),
                    weight = getString("weight"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
