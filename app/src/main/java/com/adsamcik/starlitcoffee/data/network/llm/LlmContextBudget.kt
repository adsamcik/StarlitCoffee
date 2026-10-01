package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Character budgets bound optional grounding, not the label text or a tokenizer estimate. */
internal object LlmContextBudget {
    const val VOCABULARY_JSON_CHARS = 2048
    const val EXISTING_FIELDS_JSON_CHARS = 2048
    const val SUGGESTIONS_JSON_CHARS = 1024
    const val REFERENCE_VALUE_CHARS = 96

    /** Keep complete values in their existing relevance order; never manufacture truncated names. */
    fun referenceLists(groups: Map<String, List<String>>, budget: Int = VOCABULARY_JSON_CHARS): JsonObject {
        val accepted = linkedMapOf<String, JsonElement>()
        groups.forEach { (key, values) ->
            val selected = mutableListOf<JsonElement>()
            values.filter { it.isNotBlank() && it.length <= REFERENCE_VALUE_CHARS }.forEach { value ->
                val candidate = JsonArray(selected + JsonPrimitive(value))
                if (JsonObject(accepted + (key to candidate)).toString().length <= budget) {
                    selected += JsonPrimitive(value)
                }
            }
            if (selected.isNotEmpty()) accepted[key] = JsonArray(selected)
        }
        return JsonObject(accepted)
    }

    fun objectEntries(entries: Map<String, JsonElement>, budget: Int = EXISTING_FIELDS_JSON_CHARS): JsonObject {
        val accepted = linkedMapOf<String, JsonElement>()
        entries.forEach { (key, value) ->
            val oversized = key.length >= budget || (value is JsonPrimitive && value.content.length > budget)
            if (!oversized && JsonObject(accepted + (key to value)).toString().length <= budget) {
                accepted[key] = value
            }
        }
        return JsonObject(accepted)
    }
}

/** At most one smaller attempt, selected only by the stable numeric Mindlayer overflow code. */
internal class LlmPromptAttempt(
    initialPrompt: String,
    initialSystemPrompt: String,
    private val compactPrompts: () -> Pair<String, String>,
) {
    var prompt: String = initialPrompt
        private set
    var systemPrompt: String = initialSystemPrompt
        private set
    private var compacted = false

    fun compactAfter(error: MindlayerException): Boolean {
        if (compacted || error.code != MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT) return false
        val (compact, compactSystem) = compactPrompts()
        if (compact.length.toLong() + compactSystem.length >= prompt.length.toLong() + systemPrompt.length) return false
        compacted = true
        prompt = compact
        systemPrompt = compactSystem
        return true
    }
}
