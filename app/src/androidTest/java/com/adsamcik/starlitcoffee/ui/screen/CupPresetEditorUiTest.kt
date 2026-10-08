package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.CupPresetEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CupPresetEditorUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun editingNamePreservesFractionalAmountAndLegacyMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = CupPresetRepository(db.cupPresetDao())
        val original = CupPreset(name = "Espresso", iconName = "espresso", doseG = 18f, waterMl = 36.5f,
            sortOrder = 3, isDefault = true, colorHex = "#8B4513")
        val id = runBlocking { repository.addPreset(original) }
        val viewModel = CupPresetEditorViewModel(repository)
        val store = ViewModelStore().apply { put("editor", viewModel) }
        var exits = 0
        try {
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    CupPresetEditorScreen(viewModel, id, onBack = { exits++ })
                }
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("cup_preset_name"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("cup_preset_volume").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("36.5")),
            )
            composeRule.onNodeWithTag("cup_preset_name").performTextReplacement("Morning espresso")
            composeRule.onNodeWithTag("cup_preset_save").performClick()
            composeRule.waitUntil(10_000) { exits == 1 }
            val saved = runBlocking { withTimeout(10_000) {
                repository.presets.first { it.single().name == "Morning espresso" }.single()
            } }
            assertEquals(original.copy(id = id, name = "Morning espresso"), saved)
        } finally {
            composeRule.runOnIdle { store.clear() }
            db.close()
        }
    }
}
