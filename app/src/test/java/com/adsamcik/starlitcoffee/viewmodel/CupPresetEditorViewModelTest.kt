package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CupPresetEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: BlockingCupPresetDao
    private lateinit var viewModel: CupPresetEditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        dao = BlockingCupPresetDao()
        viewModel = CupPresetEditorViewModel(CupPresetRepository(dao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save remains in progress until persistence completes and emits one completion`() =
        runTest(dispatcher) {
            viewModel.savePreset(TEST_PRESET, isNew = true)

            assertEquals(CupPresetEditorOperation.SAVING, viewModel.uiState.value.operation)
            assertNull(viewModel.uiState.value.completion)

            dao.releaseWrites.complete(Unit)
            advanceUntilIdle()

            assertEquals(CupPresetEditorOperation.IDLE, viewModel.uiState.value.operation)
            assertEquals(CupPresetEditorCompletion.SAVED, viewModel.uiState.value.completion)
            viewModel.consumeCompletion()
            assertNull(viewModel.uiState.value.completion)
        }

    @Test
    fun `delete emits completion only after persistence completes`() = runTest(dispatcher) {
        viewModel.deletePreset(TEST_PRESET.copy(id = 7L))

        assertEquals(CupPresetEditorOperation.DELETING, viewModel.uiState.value.operation)

        dao.releaseWrites.complete(Unit)
        advanceUntilIdle()

        assertEquals(CupPresetEditorCompletion.DELETED, viewModel.uiState.value.completion)
    }

    @Test
    fun `delete failure is distinct and can be retried`() = runTest(dispatcher) {
        dao.failWrites = true
        viewModel.deletePreset(TEST_PRESET.copy(id = 7L))
        dao.releaseWrites.complete(Unit)
        advanceUntilIdle()

        assertEquals(CupPresetEditorFailure.DELETE, viewModel.uiState.value.failure)
        assertEquals(CupPresetEditorOperation.IDLE, viewModel.uiState.value.operation)

        viewModel.consumeFailure()
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.completion == null)
    }

    private class BlockingCupPresetDao : CupPresetDao {
        private val data = MutableStateFlow<List<CupPresetEntity>>(emptyList())
        val releaseWrites = CompletableDeferred<Unit>()
        var failWrites = false

        override fun getAll(): Flow<List<CupPresetEntity>> = data
        override suspend fun getCount(): Int = data.value.size

        override suspend fun insert(preset: CupPresetEntity): Long {
            releaseWrites.await()
            data.value += preset.copy(id = 1L)
            return 1L
        }

        override suspend fun insertAll(presets: List<CupPresetEntity>) {
            releaseWrites.await()
            data.value += presets
        }

        override suspend fun update(preset: CupPresetEntity) {
            releaseWrites.await()
            data.value = data.value.map { if (it.id == preset.id) preset else it }
        }

        override suspend fun delete(preset: CupPresetEntity) {
            releaseWrites.await()
            if (failWrites) error("delete failed")
            data.value = data.value.filterNot { it.id == preset.id }
        }

        override suspend fun deleteAll() {
            releaseWrites.await()
            data.value = emptyList()
        }

        override suspend fun replaceAll(presets: List<CupPresetEntity>) {
            releaseWrites.await()
            data.value = presets
        }
    }

    private companion object {
        val TEST_PRESET = CupPreset(
            name = "Mug",
            iconName = "mug",
            doseG = 20f,
            waterMl = 300f,
        )
    }
}
