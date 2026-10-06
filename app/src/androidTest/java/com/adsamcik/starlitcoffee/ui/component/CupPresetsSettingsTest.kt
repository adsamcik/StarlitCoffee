package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CupPresetsSettingsTest {
    @get:Rule val composeRule = createComposeRule()
    private var pixelsPerDp = 1f
    private val cups = CupPresetRepository.defaultPresets.mapIndexed { index, cup -> cup.copy(id = index + 1L) }

    @Test
    fun listActionsAndVisibilityHaveSeparateTouchTargets() {
        var added = 0
        var reset = 0
        val edited = mutableListOf<Long>()
        val visibility = mutableListOf<Boolean>()
        show(onAdd = { added++ }, onEdit = { edited.add(it) }, onReset = { reset++ },
            onShow = { visibility.add(it) })
        assertTargets()
        composeRule.onNodeWithTag("settings_show_cup_presets").assertIsOn().performClick().assertIsOff()
        cups.forEach { composeRule.onNodeWithTag("cup_presets_edit_${it.id}").performClick() }
        composeRule.onNodeWithTag("cup_presets_add").performClick()
        composeRule.onNodeWithTag("cup_presets_reset").assertDoesNotExist()
        composeRule.onNodeWithTag("cup_presets_options").performClick()
        composeRule.onNodeWithTag("cup_presets_reset").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(false), visibility)
            assertEquals(cups.map { it.id }, edited)
            assertEquals(1, added)
            assertEquals(1, reset)
        }
    }

    @Test
    fun normalLightCard() { show(); assertTargets(); capture("cup-presets-light") }

    @Test
    fun normalDarkCard() { show(dark = true); assertTargets(); capture("cup-presets-dark") }

    @Test
    fun narrowCardWithLargeTextKeepsEveryActionReachable() {
        show(width = 288, fontScale = 1.6f)
        assertTargets()
        capture("cup-presets-large-text")
    }

    @Test
    fun pendingMutationDisablesAllEntryPoints() {
        show(enabled = false)
        (cups.map { "cup_presets_edit_${it.id}" } +
            listOf("settings_show_cup_presets", "cup_presets_add", "cup_presets_options"))
            .forEach { composeRule.onNodeWithTag(it).assertIsNotEnabled() }
    }

    @Test
    fun emptyListStillOffersAddAndCalculatorVisibility() {
        var added = false
        show(presets = emptyList(), onAdd = { added = true })
        composeRule.onNodeWithTag("settings_show_cup_presets").assertIsDisplayed()
        composeRule.onNodeWithTag("cup_presets_add").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(added) }
    }

    private fun show(
        width: Int = 380,
        fontScale: Float = 1f,
        dark: Boolean = false,
        enabled: Boolean = true,
        presets: List<CupPreset> = cups,
        onShow: (Boolean) -> Unit = {},
        onEdit: (Long) -> Unit = {},
        onAdd: () -> Unit = {},
        onReset: () -> Unit = {},
    ) {
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark, dynamicColor = false) {
                val density = LocalDensity.current
                pixelsPerDp = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    var visible by remember { mutableStateOf(true) }
                    Surface(Modifier.width(width.dp)) {
                        Column(Modifier.testTag("cup_presets_capture").padding(16.dp)) {
                            CupPresetsSettings(presets, visible, enabled,
                                onShowOnCalculatorChange = { visible = it; onShow(it) },
                                onEdit = onEdit, onAdd = onAdd, onReset = onReset)
                        }
                    }
                }
            }
        }
    }

    private fun assertTargets() {
        val tags = cups.map { "cup_presets_edit_${it.id}" } +
            listOf("settings_show_cup_presets", "cup_presets_add", "cup_presets_options")
        tags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag has a small touch target: $bounds", bounds.width >= 48f * pixelsPerDp - 1f &&
                bounds.height >= 48f * pixelsPerDp - 1f)
        }
        val boxes = tags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        boxes.forEachIndexed { index, first -> boxes.drop(index + 1).forEach { second ->
            assertTrue("Touch targets overlap: $first and $second", first.right <= second.left + 1f ||
                second.right <= first.left + 1f || first.bottom <= second.top + 1f || second.bottom <= first.top + 1f)
        } }
        composeRule.onNodeWithTag("cup_presets_edit_1").assertTextEquals("Espresso", "36 ml")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "cup-presets").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(composeRule.onNodeWithTag("cup_presets_capture").captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
