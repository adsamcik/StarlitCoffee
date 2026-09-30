package com.adsamcik.starlitcoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredRecipePayload
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.BrewingSetCodec
import com.adsamcik.starlitcoffee.data.model.BrewingSetSelection
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CalculatorSetupCodec
import com.adsamcik.starlitcoffee.data.model.FilterType

internal object BrewingSetKeys {
    val SETS = stringPreferencesKey("brewing_sets_v1")
    val ACTIVE_ID = stringPreferencesKey("active_brewing_set_id")
    val RECIPES_IMPORTED = booleanPreferencesKey("brewing_set_recipes_imported")
}

internal fun readBrewingSetSelection(prefs: Preferences): BrewingSetSelection {
    val stored = prefs[BrewingSetKeys.SETS]?.let(BrewingSetCodec::decode).orEmpty()
    val fallback = legacyBrewingSetSelection(prefs)
    val sets = stored.ifEmpty { fallback.sets }
    val active = prefs[BrewingSetKeys.ACTIVE_ID]?.takeIf { id -> sets.any { it.id == id } }
        ?: fallback.activeId.takeIf { id -> sets.any { it.id == id } }
        ?: sets.first().id
    return BrewingSetSelection(sets, active)
}

internal fun legacyBrewingSetSelection(prefs: Preferences): BrewingSetSelection {
    val enabled = prefs[UserPreferenceKeys.ENABLED_METHODS]?.mapNotNull { name ->
        BrewMethod.entries.find { it.name == name }
    }?.toSet() ?: BrewMethod.entries.toSet()
    val requested = BrewMethod.entries.find { it.name == prefs[UserPreferenceKeys.DEFAULT_METHOD] } ?: BrewMethod.PULSAR
    val selection = normalizeMethodSelection(enabled, requested)
    val sets = BrewMethod.entries.filter { it in selection.enabledMethods }.map { method ->
        val target = when {
            prefs[UserPreferenceKeys.DEFAULT_INPUT_DIRECTION] != "WATER" -> CalculatorQuantityTarget.COFFEE
            CalculatorQuantityTarget.WATER_IN.isAvailableFor(method) -> CalculatorQuantityTarget.WATER_IN
            CalculatorQuantityTarget.IN_CUP.isAvailableFor(method) -> CalculatorQuantityTarget.IN_CUP
            else -> CalculatorQuantityTarget.COFFEE
        }
        val saved = prefs[calculatorSetupKey(method)]?.let { CalculatorSetupCodec.decode(it, method) }
        BrewingSet(
            id = "legacy:${method.name}", method = method,
            setup = saved ?: CalculatorSetup(
                ratio = method.defaultRatio, quantity = target.name,
                filterType = FilterType.entries.find { it.name == prefs[UserPreferenceKeys.DEFAULT_FILTER_TYPE] }
                    ?.name.takeIf { method == BrewMethod.PULSAR },
                grinderId = prefs[UserPreferenceKeys.SELECTED_GRINDER_ID],
            ),
        )
    }
    return BrewingSetSelection(sets, "legacy:${selection.defaultMethod.name}")
}

internal fun writeBrewingSetSelection(prefs: MutablePreferences, selection: BrewingSetSelection) {
    prefs[BrewingSetKeys.SETS] = BrewingSetCodec.encode(selection.sets)
    prefs[BrewingSetKeys.ACTIVE_ID] = selection.activeId
    val active = selection.active
    val methods = selection.sets.map { it.method }.toSet()
    // Keep the existing preparation and stable brewer-profile APIs coherent during migration.
    prefs[UserPreferenceKeys.ENABLED_METHODS] = methods.map { it.name }.toSet()
    prefs[UserPreferenceKeys.DEFAULT_METHOD] = active.method.name
    prefs[UserPreferenceKeys.ENABLED_BREWER_PROFILE_IDS] = methods.map { legacyBrewerProfileId(it.name) }.toSet()
    prefs[UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID] = legacyBrewerProfileId(active.method.name)
    active.setup.filterType?.let { prefs[UserPreferenceKeys.DEFAULT_FILTER_TYPE] = it }
        ?: prefs.remove(UserPreferenceKeys.DEFAULT_FILTER_TYPE)
    active.setup.grinderId?.let { prefs[UserPreferenceKeys.SELECTED_GRINDER_ID] = it }
        ?: prefs.remove(UserPreferenceKeys.SELECTED_GRINDER_ID)
    prefs[calculatorSetupKey(active.method)] = CalculatorSetupCodec.encode(active.setup)
}

internal class DataStoreBrewingSetWriter(private val dataStore: DataStore<Preferences>) : BrewingSetWriter {
    override suspend fun initializeBrewingSets(savedRecipes: List<SavedRecipeEntity>?) {
        dataStore.edit { prefs ->
            var selection = readBrewingSetSelection(prefs)
            if (savedRecipes != null && prefs[BrewingSetKeys.RECIPES_IMPORTED] != true) {
                val imported = savedRecipes.mapNotNull(::importedSet)
                    .filter { candidate -> selection.sets.none { it.id == candidate.id } }
                selection = selection.copy(sets = selection.sets + imported)
                prefs[BrewingSetKeys.RECIPES_IMPORTED] = true
            }
            if (prefs[UserPreferenceKeys.ONBOARDING_COMPLETED] == true || prefs[BrewingSetKeys.SETS] != null ||
                selection.sets.any { it.id.startsWith("favorite:") }) {
                writeBrewingSetSelection(prefs, selection)
            }
        }
    }

    override suspend fun saveBrewingSet(set: BrewingSet) = change { it.upsert(set) }
    override suspend fun selectBrewingSet(id: String) = change { it.select(id) }
    override suspend fun deleteBrewingSet(id: String) = change { it.remove(id) }
    override suspend fun updateBrewingSetSetup(id: String, revision: Int, setup: CalculatorSetup) =
        change { it.remember(id, revision, setup) }

    private suspend fun change(transform: (BrewingSetSelection) -> BrewingSetSelection) {
        dataStore.edit { prefs -> writeBrewingSetSelection(prefs, transform(readBrewingSetSelection(prefs))) }
    }

    private fun importedSet(recipe: SavedRecipeEntity): BrewingSet? {
        val method = BrewMethod.entries.find { it.name == recipe.method } ?: return null
        val payload = BrewingPersistenceMapper.recipeRecord(recipe).payload as? StoredRecipePayload.Versioned
        val setup = payload?.snapshot?.calculatorSetup?.validatedFor(method) ?: return null
        return BrewingSet("favorite:${recipe.id}", recipe.coffeeName?.trim()?.takeIf { it.isNotEmpty() },
            method, setup).validated()
    }
}
