package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.ui.component.brewingSetSummary
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native layout and interaction evidence for the controls immediately above the keypad. */
@RunWith(AndroidJUnit4::class)
class CalculatorToolbarUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var pixelsPerDp = 1f
    private var expectedSummary = ""
    private val home = BrewingSet("home", method = BrewMethod.PULSAR,
        setup = CalculatorSetup(17f, filterType = "PAPER"))
    private val work = home.copy(id = "work", name = "Work")
    private val cups = listOf(
        CupPreset(1, "Espresso", "espresso", 18f, 36f),
        CupPreset(2, "Cortado", "cortado", 18f, 130f),
        CupPreset(3, "Cappuccino", "cappuccino", 18f, 180f),
        CupPreset(4, "Mug", "mug", 22f, 374f),
        CupPreset(5, "Travel", "travel", 25f, 425f),
    )

    @Test
    fun normalLightToolbarHasSeparateAccessibleControls() {
        show()
        assertNormalLayout()
        val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onNodeWithTag("brewing_set_picker").assert(buttonRole)
        composeRule.onNodeWithTag("calculator_ratio_picker").assert(buttonRole)
        composeRule.onNodeWithTag("calculator_ratio_picker")
            .assertContentDescriptionEquals(context.getString(R.string.cd_ratio, "17"))
        saveCapture("normal-light")
    }

    @Test
    fun normalDarkToolbarKeepsTheSameTouchTargetsAndHierarchy() {
        show(dark = true)
        assertNormalLayout()
        saveCapture("normal-dark")
    }

    @Test
    fun ratioAndSetPickersKeepTheirActionsAndManagementEntry() {
        val selected = mutableListOf<String>()
        val ratios = mutableListOf<Float>()
        var manageCount = 0
        show(onSet = selected::add, onRatio = ratios::add, onManage = { manageCount++ })

        composeRule.onNodeWithTag("calculator_ratio_picker").performClick()
        composeRule.onNodeWithText("1:18").performClick()
        composeRule.onNodeWithTag("calculator_ratio_picker")
            .assertContentDescriptionEquals(context.getString(R.string.cd_ratio, "18"))
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("choose_brewing_set_work").performClick()
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("manage_brewing_sets").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(18f), ratios)
            assertEquals(listOf("work"), selected)
            assertEquals(1, manageCount)
        }
    }

    @Test
    fun narrowLargeTextLayoutScrollsCupsWithoutMovingBackspace() {
        val longSet = home.copy(name = "Home setup with a very long name for weekend slow brewing",
            setup = home.setup.copy(grinderId = "1zpresso-zp6-special"))
        val pressed = mutableListOf<Long>()
        var backspaces = 0
        var resumed: String? = null
        show(widthDp = 288, fontScale = 1.6f, sets = listOf(longSet, work),
            sessionId = "saved-brew", onResume = { resumed = it },
            onPreset = { pressed.add(it.id) }, onBackspace = { backspaces++ })

        assertTouchTarget("brewing_set_picker")
        assertTouchTarget("calculator_ratio_picker")
        assertEquals(288f * pixelsPerDp, bounds("brewing_set_picker").width, 0.5f)
        assertTrue(bounds("brewing_set_picker").bottom <= bounds("calculator_ratio_picker").top)
        assertEquals(bounds("calculator_backspace").height, bounds("calculator_ratio_picker").height, 0.5f)
        assertSeparate(bounds("brewing_set_picker"), bounds("calculator_ratio_picker"))
        val pickerTexts = composeRule.onNodeWithTag("brewing_set_picker").fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        assertTrue("The full setup name must remain accessible", pickerTexts.contains(requireNotNull(longSet.name)))
        assertTrue("The complete equipment summary must remain accessible", pickerTexts.contains(expectedSummary))
        assertEquals(288f * pixelsPerDp, bounds("toolbar_capture").width, 0.5f)
        val backspaceBefore = bounds("calculator_backspace")
        assertTouchTarget("calculator_backspace")
        assertSeparate(bounds("calculator_cup_presets"), backspaceBefore)
        saveCapture("narrow-large-text-long-setup")

        composeRule.onNodeWithTag("calculator_cup_presets").performScrollToIndex(4)
        composeRule.onNodeWithTag("calculator_preset_5").assertIsDisplayed()
        assertTouchTarget("calculator_preset_5")
        assertSeparate(bounds("calculator_preset_5"), bounds("calculator_backspace"))
        assertEquals(backspaceBefore, bounds("calculator_backspace"))
        composeRule.onNodeWithTag("calculator_preset_5").performClick()
        composeRule.onNodeWithTag("calculator_backspace").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_resume)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(5L), pressed)
            assertEquals(1, backspaces)
            assertEquals("saved-brew", resumed)
        }
        saveCapture("narrow-large-text-last-cup")
    }

    @Test
    fun emptyPresetsKeepBackspaceVisibleAndUsable() {
        var backspaces = 0
        show(widthDp = 288, presets = emptyList(), onBackspace = { backspaces++ })
        cups.forEach { composeRule.onNodeWithTag("calculator_preset_${it.id}").assertDoesNotExist() }
        composeRule.onNodeWithTag("calculator_preset_bar").assertDoesNotExist()
        assertTouchTarget("calculator_backspace")
        composeRule.onNodeWithTag("calculator_backspace").performClick()
        composeRule.runOnIdle { assertEquals(1, backspaces) }
        saveCapture("empty-presets")
    }

    @Test
    fun hiddenPresetsLeaveBrewerRatioAndBackspaceAboveTheWorkingKeypad() {
        val digits = mutableListOf<Char>()
        var backspaces = 0
        show(showCupPresets = false, onDigit = digits::add, onBackspace = { backspaces++ })
        composeRule.onNodeWithTag("calculator_preset_bar").assertDoesNotExist()
        assertTouchTarget("brewing_set_picker")
        assertTouchTarget("calculator_ratio_picker")
        assertTouchTarget("calculator_backspace")
        composeRule.onNodeWithText("7").performClick()
        composeRule.onNodeWithTag("calculator_backspace").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf('7'), digits)
            assertEquals(1, backspaces)
        }
        saveCapture("hidden-presets")
    }

    @Test
    fun customPresetsBeyondFiveRemainReachableAndShowTheirSavedVolume() {
        val custom = CupPreset(6, "Weekend", "mug", 30f, 510f)
        val pressed = mutableListOf<Long>()
        show(presets = cups + custom, onPreset = { pressed.add(it.id) })
        composeRule.onNodeWithTag("calculator_cup_presets").performScrollToIndex(5)
        composeRule.onNodeWithTag("calculator_preset_6").assertIsDisplayed()
            .assertContentDescriptionEquals(custom.name)
            .assertTextEquals("510 ${context.getString(R.string.unit_ml)}")
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf(6L), pressed) }
    }

    private fun show(
        widthDp: Int = 380,
        fontScale: Float = 1f,
        dark: Boolean = false,
        sets: List<BrewingSet> = listOf(home, work),
        presets: List<CupPreset> = cups,
        showCupPresets: Boolean = true,
        sessionId: String? = null,
        onSet: (String) -> Unit = {},
        onRatio: (Float) -> Unit = {},
        onPreset: (CupPreset) -> Unit = {},
        onBackspace: () -> Unit = {},
        onDigit: (Char) -> Unit = {},
        onResume: (String) -> Unit = {},
        onManage: () -> Unit = {},
    ) {
        val grinderData = GrinderDataSource.getInstance(context)
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark, dynamicColor = false) {
                val density = LocalDensity.current
                pixelsPerDp = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    var selectedId by remember { mutableStateOf(sets.first().id) }
                    var ratio by remember { mutableStateOf(sets.first().setup.ratio) }
                    expectedSummary = brewingSetSummary(sets.first { it.id == selectedId }, grinderData)
                    Surface(Modifier.width(widthDp.dp)) {
                        Column(Modifier.testTag("toolbar_capture").padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrewSettingsToolbar(sets, selectedId, sets.first { it.id == selectedId }.method,
                                grinderData, ratio,
                                onSetChange = { selectedId = it; onSet(it) },
                                onRatioChange = { ratio = it; onRatio(it) },
                                recoverableSessionId = sessionId, onResumeSession = onResume, onManage = onManage,
                                onBackspace = onBackspace)
                            CalculatorKeyboard(presets, showCupPresets, hasValidExpression = true,
                                isCompactHeight = false, isTallHeight = false,
                                onDigit = onDigit, onDecimal = {}, onOperator = {}, onPreset = onPreset,
                                onClear = {}, onBrew = {})
                        }
                    }
                }
            }
        }
    }

    private fun assertNormalLayout() {
        val controls = listOf("brewing_set_picker", "calculator_ratio_picker", "calculator_backspace") +
            cups.map { "calculator_preset_${it.id}" }
        controls.forEach(::assertTouchTarget)
        assertEquals(bounds("brewing_set_picker").height, bounds("calculator_ratio_picker").height, 0.5f)
        controls.map(::bounds).forEachIndexed { index, rect ->
            controls.drop(index + 1).forEach { assertSeparate(rect, bounds(it)) }
        }
        assertEquals(380f * pixelsPerDp, bounds("toolbar_capture").width, 0.5f)
    }

    private fun assertTouchTarget(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        val rect = bounds(tag)
        val minimum = 48f * pixelsPerDp - 0.5f
        assertTrue("$tag width is ${rect.width / pixelsPerDp}dp", rect.width >= minimum)
        assertTrue("$tag height is ${rect.height / pixelsPerDp}dp", rect.height >= minimum)
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertSeparate(first: Rect, second: Rect) {
        assertTrue("Controls overlap: $first and $second",
            first.right <= second.left + 0.5f || second.right <= first.left + 0.5f ||
                first.bottom <= second.top + 0.5f || second.bottom <= first.top + 0.5f)
    }

    private fun saveCapture(name: String) {
        composeRule.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "calculator-toolbar").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(composeRule.onNodeWithTag("toolbar_capture").captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
