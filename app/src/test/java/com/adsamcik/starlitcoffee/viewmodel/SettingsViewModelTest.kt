package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.repository.CupPresetResetter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `method selection is one guarded atomic write`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var persistedMethods = emptySet<BrewMethod>()
        var persistedDefault = BrewMethod.PULSAR
        val store = object : TestUserPreferencesStore() {
            override suspend fun updateMethodSelection(
                enabledMethods: Set<BrewMethod>,
                defaultMethod: BrewMethod,
            ) {
                calls++
                persistedMethods = enabledMethods
                persistedDefault = defaultMethod
                gate.await()
            }
        }
        val viewModel = SettingsViewModel(store)

        viewModel.updateMethodSelection(setOf(BrewMethod.V60), BrewMethod.V60)
        viewModel.updateDefaultMethod(setOf(BrewMethod.PULSAR), BrewMethod.PULSAR)

        assertEquals(1, calls)
        assertEquals(SettingsOperation.SAVING, viewModel.uiState.value.operation)
        assertEquals(setOf(BrewMethod.V60), persistedMethods)
        assertEquals(BrewMethod.V60, persistedDefault)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SettingsOperation.IDLE, viewModel.uiState.value.operation)
    }

    @Test
    fun `cup preset reset waits blocks repeats and reports completion`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val resetter = CupPresetResetter {
            calls++
            gate.await()
        }
        val viewModel = SettingsViewModel(TestUserPreferencesStore(), resetter)

        viewModel.resetCupPresets()
        viewModel.resetCupPresets()

        assertEquals(1, calls)
        assertEquals(SettingsOperation.RESETTING_CUP_PRESETS, viewModel.uiState.value.operation)
        assertNull(viewModel.uiState.value.completion)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SettingsCompletion.CUP_PRESETS_RESET, viewModel.uiState.value.completion)
    }

    @Test
    fun `cup preset reset failure remains retryable`() = runTest(dispatcher) {
        var shouldFail = true
        val resetter = CupPresetResetter {
            if (shouldFail) error("transaction failure")
        }
        val viewModel = SettingsViewModel(TestUserPreferencesStore(), resetter)

        viewModel.resetCupPresets()
        advanceUntilIdle()
        assertEquals(SettingsFailure.RESET_CUP_PRESETS, viewModel.uiState.value.failure)
        assertEquals(SettingsOperation.IDLE, viewModel.uiState.value.operation)

        shouldFail = false
        viewModel.resetCupPresets()
        advanceUntilIdle()
        assertEquals(SettingsCompletion.CUP_PRESETS_RESET, viewModel.uiState.value.completion)
    }

    @Test
    fun `diagnostic clear requires every store to persist`() = runTest(dispatcher) {
        var succeeds = false
        val viewModel = SettingsViewModel(
            preferences = TestUserPreferencesStore(),
            diagnosticHistoryClearer = DiagnosticHistoryClearer { succeeds },
        )

        viewModel.clearDiagnostics()
        advanceUntilIdle()
        assertEquals(SettingsFailure.CLEAR_DIAGNOSTICS, viewModel.uiState.value.failure)

        succeeds = true
        viewModel.clearDiagnostics()
        advanceUntilIdle()
        assertEquals(SettingsCompletion.DIAGNOSTICS_CLEARED, viewModel.uiState.value.completion)
        assertFalse(viewModel.uiState.value.operation != SettingsOperation.IDLE)
    }

    @Test
    fun `settings write failure clears pending state for retry`() = runTest(dispatcher) {
        var shouldFail = true
        val store = object : TestUserPreferencesStore() {
            override suspend fun updateSkipMethodSelection(enabled: Boolean) {
                if (shouldFail) error("DataStore failure")
            }
        }
        val viewModel = SettingsViewModel(store)

        viewModel.updateSkipMethodSelection(true)
        advanceUntilIdle()
        assertEquals(SettingsFailure.SAVE, viewModel.uiState.value.failure)
        assertEquals(SettingsOperation.IDLE, viewModel.uiState.value.operation)

        shouldFail = false
        viewModel.updateSkipMethodSelection(true)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.operation == SettingsOperation.IDLE)
    }
}
