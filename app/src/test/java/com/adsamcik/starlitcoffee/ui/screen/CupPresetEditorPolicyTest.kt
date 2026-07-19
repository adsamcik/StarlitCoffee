package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.viewmodel.CupPresetEditorOperation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CupPresetEditorPolicyTest {

    @Test
    fun `edit form is blocked until presets have loaded`() {
        assertTrue(
            shouldBlockPresetEditor(
                presetId = 42L,
                presetsLoaded = false,
                presetExists = false,
                hydrated = false,
            ),
        )
        assertTrue(
            shouldBlockPresetEditor(
                presetId = 42L,
                presetsLoaded = true,
                presetExists = true,
                hydrated = false,
            ),
        )
    }

    @Test
    fun `new preset form does not wait for repository hydration`() {
        assertFalse(
            shouldBlockPresetEditor(
                presetId = null,
                presetsLoaded = false,
                presetExists = false,
                hydrated = false,
            ),
        )
    }

    @Test
    fun `missing preset does not remain blocked after repository loads`() {
        assertFalse(
            shouldBlockPresetEditor(
                presetId = 42L,
                presetsLoaded = true,
                presetExists = false,
                hydrated = false,
            ),
        )
    }

    @Test
    fun `mutable inputs and navigation are disabled for every persistence operation`() {
        assertTrue(
            areCupPresetEditorInteractionsEnabled(
                operation = CupPresetEditorOperation.IDLE,
                completionPending = false,
            ),
        )
        assertFalse(
            areCupPresetEditorInteractionsEnabled(
                operation = CupPresetEditorOperation.SAVING,
                completionPending = false,
            ),
        )
        assertFalse(
            areCupPresetEditorInteractionsEnabled(
                operation = CupPresetEditorOperation.DELETING,
                completionPending = false,
            ),
        )
    }

    @Test
    fun `interactions stay disabled while successful completion is navigating away`() {
        assertFalse(
            areCupPresetEditorInteractionsEnabled(
                operation = CupPresetEditorOperation.IDLE,
                completionPending = true,
            ),
        )
    }
}
