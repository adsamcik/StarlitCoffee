package com.adsamcik.starlitcoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CalculatorSetupCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewingSetPreferencesTest {
    private val home = BrewingSet("home", "Home", BrewMethod.PULSAR,
        CalculatorSetup(18f, tokens = listOf(CalcToken.Number("20")), filterType = "PAPER"))

    @Test
    fun `legacy selection and method input migrate without applying filter ratios to espresso`() = runTest {
        val espresso = CalculatorSetup(2.5f, "IN_CUP", listOf(CalcToken.Number("45")), grinderId = "niche-zero")
        val data = MemoryPreferences(mutablePreferencesOf(
            UserPreferenceKeys.ONBOARDING_COMPLETED to true,
            UserPreferenceKeys.ENABLED_METHODS to setOf("PULSAR", "ESPRESSO"),
            UserPreferenceKeys.DEFAULT_METHOD to "ESPRESSO",
            UserPreferenceKeys.DEFAULT_FILTER_TYPE to "PAPER",
            calculatorSetupKey(BrewMethod.ESPRESSO) to CalculatorSetupCodec.encode(espresso),
        ))
        val writer = DataStoreBrewingSetWriter(data)
        writer.initializeBrewingSets()
        val sets = readBrewingSetSelection(data.data.value)
        assertEquals("legacy:ESPRESSO", sets.activeId)
        assertEquals(espresso, sets.active.setup)
        assertEquals(17f, sets.sets.first { it.method == BrewMethod.PULSAR }.setup.ratio, 0f)
        assertEquals(legacyBrewerProfileId("ESPRESSO"), data.data.value[UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID])
        writer.initializeBrewingSets()
        assertEquals(sets, readBrewingSetSelection(data.data.value))
    }

    @Test
    fun `bad legacy filter falls back safely and water preference maps to espresso yield`() {
        val prefs = mutablePreferencesOf(
            UserPreferenceKeys.ENABLED_METHODS to setOf("PULSAR", "ESPRESSO"),
            UserPreferenceKeys.DEFAULT_FILTER_TYPE to "FUTURE",
            UserPreferenceKeys.DEFAULT_INPUT_DIRECTION to "WATER",
        )
        val sets = legacyBrewingSetSelection(prefs).sets
        assertNull(sets.first { it.method == BrewMethod.PULSAR }.setup.filterType)
        assertEquals("IN_CUP", sets.first { it.method == BrewMethod.ESPRESSO }.setup.quantity)
        assertTrue(sets.all { it.validated() != null })
    }

    @Test
    fun `initializing before onboarding does not persist all available methods`() = runTest {
        val data = MemoryPreferences()
        DataStoreBrewingSetWriter(data).initializeBrewingSets(emptyList())
        assertNull(data.data.value[BrewingSetKeys.SETS])
    }

    @Test
    fun `named calculator favorites import once and stay deleted without changing recipe records`() = runTest {
        val data = MemoryPreferences(mutablePreferencesOf(UserPreferenceKeys.ONBOARDING_COMPLETED to true))
        val writer = DataStoreBrewingSetWriter(data)
        val snapshot = BrewRecipeSnapshotV1(
            methodFamilyId = "manual_gravity", brewerProfileId = "pulsar",
            equipment = EquipmentConfigurationSnapshotV1("pulsar"),
            quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 360.0),
            ratioDefinition = RatioDefinitionSnapshotV1("DRY_COFFEE_DOSE", "BREW_WATER_INPUT"),
            outputModel = OutputModelSnapshotV1("BREW_WATER_MINUS_RETENTION"), calculatorSetup = home.setup,
        )
        val named = BrewingPersistenceMapper.withRecipeSnapshot(
            SavedRecipeEntity(id = 42L, coffeeName = "Home", method = "PULSAR", ratio = 18f, doseG = 20f, waterG = 360f),
            snapshot,
        )
        val legacy = named.copy(id = 43L, recipeSnapshotJson = null)
        writer.initializeBrewingSets(listOf(named, legacy))
        val sets = readBrewingSetSelection(data.data.value)
        assertEquals(home.setup, sets.sets.single { it.id == "favorite:42" }.setup)
        assertFalse(sets.sets.any { it.id == "favorite:43" })
        assertEquals("legacy:PULSAR", sets.activeId)
        writer.deleteBrewingSet("favorite:42")
        writer.initializeBrewingSets(listOf(named))
        assertFalse(readBrewingSetSelection(data.data.value).sets.any { it.id == "favorite:42" })
        assertEquals(home.setup, (BrewingPersistenceMapper.recipeRecord(named).payload as
            com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredRecipePayload.Versioned).snapshot.calculatorSetup)
    }

    @Test
    fun `atomic writes preserve newer input through equipment edits and active deletion`() = runTest {
        val data = MemoryPreferences()
        val writer = DataStoreBrewingSetWriter(data)
        writer.saveBrewingSet(home)
        writer.updateBrewingSetSetup(home.id, 0, home.setup.copy(ratio = 16f))
        writer.saveBrewingSet(home.copy(name = "Home filter", setup = home.setup.copy(grinderId = "fellow-ode-gen2")))
        writer.updateBrewingSetSetup(home.id, 0, home.setup)
        val edited = readBrewingSetSelection(data.data.value).active
        assertEquals(16f, edited.setup.ratio, 0f)
        assertEquals("Home filter", edited.name)
        assertEquals(edited.setup.grinderId, data.data.value[UserPreferenceKeys.SELECTED_GRINDER_ID])
        writer.deleteBrewingSet(home.id)
        val remaining = readBrewingSetSelection(data.data.value)
        assertFalse(remaining.sets.any { it.id == home.id })
        assertEquals(remaining.active.method.name, data.data.value[UserPreferenceKeys.DEFAULT_METHOD])
    }

    @Test
    fun `onboarding commits two full recipes and their active identity together`() = runTest {
        val data = MemoryPreferences()
        val espresso = BrewingSet("espresso", "Work", BrewMethod.ESPRESSO,
            CalculatorSetup(2f, "IN_CUP", listOf(CalcToken.Number("36")), grinderId = "niche-zero"))
        completeBrewingSetOnboarding(data, listOf(home, espresso), espresso.id)
        val selection = readBrewingSetSelection(data.data.value)
        assertEquals(listOf(home.copy(revision = 1), espresso.copy(revision = 1)), selection.sets)
        assertEquals(espresso.id, selection.activeId)
        assertEquals(true, data.data.value[UserPreferenceKeys.ONBOARDING_COMPLETED])
        assertEquals("ESPRESSO", data.data.value[UserPreferenceKeys.DEFAULT_METHOD])
        // A late duplicate submission must not replace a completed user's sets.
        completeBrewingSetOnboarding(data, listOf(home), home.id)
        assertEquals(selection, readBrewingSetSelection(data.data.value))
    }

    @Test
    fun `explicit recipe save survives reload and rejects a stale equipment revision`() = runTest {
        val data = MemoryPreferences()
        val writer = DataStoreBrewingSetWriter(data)
        writer.saveBrewingSet(home)
        val changed = home.copy(setup = home.setup.copy(ratio = 16f, tokens = listOf(CalcToken.Number("25"))))
        writer.saveBrewingSetRecipe(changed)
        val saved = readBrewingSetSelection(data.data.value).active
        assertEquals(changed.setup, saved.setup)
        assertEquals(1, saved.revision)
        writer.updateBrewingSetSetup(home.id, 0, home.setup)
        assertEquals(saved, readBrewingSetSelection(data.data.value).active)
        val before = data.data.value
        assertTrue(runCatching { writer.saveBrewingSetRecipe(home) }.isFailure)
        assertEquals(before, data.data.value)
    }

    @Test
    fun `invalid onboarding selection writes neither sets nor completion`() = runTest {
        val data = MemoryPreferences()
        assertTrue(runCatching { completeBrewingSetOnboarding(data, listOf(home), "missing") }.isFailure)
        assertNull(data.data.value[BrewingSetKeys.SETS])
        assertNull(data.data.value[UserPreferenceKeys.ONBOARDING_COMPLETED])
    }

    private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
