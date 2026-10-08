package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRecognitionPreferenceTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `approval continuation waits for opt-in to finish saving`() = runTest(dispatcher) {
        val save = CompletableDeferred<Unit>()
        val store = object : TestUserPreferencesStore() {
            override suspend fun updateLabelRecognitionPreference(preference: RecognitionPreference) {
                save.await()
                state.value = state.value.copy(labelRecognitionPreference = preference)
            }
        }
        val model = SettingsViewModel(store)
        var continued = false
        val operation = launch {
            continued = model.saveLabelRecognitionPreference(RecognitionPreference.ENABLED)
        }
        assertFalse(continued)
        assertEquals(SettingsOperation.SAVING, model.uiState.value.operation)
        save.complete(Unit)
        operation.join()
        assertTrue(continued)
        assertEquals(SettingsOperation.IDLE, model.uiState.value.operation)
    }

    @Test
    fun `failed opt-in keeps the previous choice and allows retry`() = runTest(dispatcher) {
        var shouldFail = true
        var saved = RecognitionPreference.DISABLED
        val store = object : TestUserPreferencesStore(UserPreferences(labelRecognitionPreference = saved)) {
            override suspend fun updateLabelRecognitionPreference(preference: RecognitionPreference) {
                if (shouldFail) error("DataStore failure")
                saved = preference
            }
        }
        val model = SettingsViewModel(store)
        assertFalse(model.saveLabelRecognitionPreference(RecognitionPreference.ENABLED))
        assertEquals(RecognitionPreference.DISABLED, saved)
        assertEquals(SettingsFailure.SAVE, model.uiState.value.failure)
        shouldFail = false
        assertTrue(model.saveLabelRecognitionPreference(RecognitionPreference.ENABLED))
        assertEquals(RecognitionPreference.ENABLED, saved)
    }

    @Test
    fun `another settings write blocks opt-in without overwriting it`() = runTest(dispatcher) {
        val save = CompletableDeferred<Unit>()
        var recognitionWrites = 0
        val store = object : TestUserPreferencesStore() {
            override suspend fun updateShowCupPresets(enabled: Boolean) { save.await() }
            override suspend fun updateLabelRecognitionPreference(preference: RecognitionPreference) { recognitionWrites++ }
        }
        val model = SettingsViewModel(store)
        model.updateShowCupPresets(false)
        assertFalse(model.saveLabelRecognitionPreference(RecognitionPreference.ENABLED))
        assertEquals(0, recognitionWrites)
        assertEquals(SettingsOperation.SAVING, model.uiState.value.operation)
        save.complete(Unit)
    }

    @Test
    fun `cancelling the save releases the settings gate`() = runTest(dispatcher) {
        val save = CompletableDeferred<Unit>()
        val store = object : TestUserPreferencesStore() {
            override suspend fun updateLabelRecognitionPreference(preference: RecognitionPreference) { save.await() }
        }
        val model = SettingsViewModel(store)
        val operation = launch { model.saveLabelRecognitionPreference(RecognitionPreference.ENABLED) }
        assertEquals(SettingsOperation.SAVING, model.uiState.value.operation)
        operation.cancel()
        operation.join()
        assertEquals(SettingsOperation.IDLE, model.uiState.value.operation)
    }
}
