package com.adsamcik.starlitcoffee.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/** A named equipment combination with its own last calculator input. */
@Serializable
data class BrewingSet(
    val id: String,
    val name: String? = null,
    val method: BrewMethod,
    val setup: CalculatorSetup = CalculatorSetup(ratio = method.defaultRatio),
    val revision: Int = 0,
) {
    fun validated(): BrewingSet? {
        val trimmed = name?.trim()
        if (id.isBlank() || revision < 0) return null
        if (trimmed?.isBlank() == true || (trimmed?.length ?: 0) > 80) return null
        return setup.validatedFor(method)?.let { copy(name = trimmed, setup = it) }
    }

    /** Equipment edits preserve calculator input already saved by another screen. */
    fun editedOver(current: BrewingSet?): BrewingSet = if (current == null) this else copy(
        setup = if (method == current.method) current.setup.copy(
            filterType = setup.filterType, grinderId = setup.grinderId,
        ) else setup,
        revision = current.revision + 1,
    )
}

data class BrewingSetSelection(val sets: List<BrewingSet>, val activeId: String) {
    val active: BrewingSet get() = sets.first { it.id == activeId }

    fun upsert(set: BrewingSet): BrewingSetSelection {
        val valid = requireNotNull(set.validated())
        val existing = sets.find { it.id == valid.id }
        val edited = valid.editedOver(existing)
        return if (existing == null) copy(sets = sets + edited, activeId = edited.id)
        else copy(sets = sets.map { if (it.id == edited.id) edited else it })
    }

    fun select(id: String): BrewingSetSelection = if (sets.any { it.id == id }) copy(activeId = id) else this

    fun remove(id: String): BrewingSetSelection {
        if (sets.size <= 1 || sets.none { it.id == id }) return this
        val remaining = sets.filterNot { it.id == id }
        return copy(sets = remaining, activeId = activeId.takeIf { it != id } ?: remaining.first().id)
    }

    fun remember(id: String, revision: Int, setup: CalculatorSetup): BrewingSetSelection = copy(
        sets = sets.map { set ->
            if (set.id == id && set.revision == revision) {
                setup.validatedFor(set.method)?.let { set.copy(setup = it) } ?: set
            } else set
        },
    )
}

object BrewingSetCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(sets: List<BrewingSet>): String = json.encodeToString(sets)

    /** One damaged or future record must not discard the other saved sets. */
    fun decode(raw: String): List<BrewingSet> = try {
        json.parseToJsonElement(raw).jsonArray.mapNotNull { record ->
            try {
                json.decodeFromJsonElement(BrewingSet.serializer(), record).validated()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.distinctBy { it.id }
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}
