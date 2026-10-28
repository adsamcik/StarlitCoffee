package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual retained routes and ViewModel, without a database, saved preferences or inventory. */
@RunWith(AndroidJUnit4::class)
class CenteredLegacyBrewScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun retainedTimerUsesCenteredPresentation() = checkLegacy(bloom = false, large = false)
    @Test fun retainedTimerUsesCenteredPresentationAt200Percent() = checkLegacy(bloom = false, large = true)
    @Test fun retainedBloomUsesCenteredPresentation() = checkLegacy(bloom = true, large = false)
    @Test fun retainedBloomUsesCenteredPresentationAt200Percent() = checkLegacy(bloom = true, large = true)

    private fun checkLegacy(bloom: Boolean, large: Boolean) {
        val store = ViewModelStore()
        lateinit var brew: BrewViewModel
        val sprites = BloomSpritesheetIds.associateWith { if (it == "coffee_flower") 1 else 0 }
        rule.runOnIdle {
            brew = BrewViewModel()
            store.put("centered-legacy", brew)
            brew.setMethod(if (bloom) BrewMethod.PULSAR else BrewMethod.FRENCH_PRESS)
        }
        try {
            rule.setContent {
                val density = LocalDensity.current
                val viewportDensity = if (large) LocalWindowInfo.current.containerSize.width / 320f else density.density
                CompositionLocalProvider(LocalDensity provides Density(viewportDensity, if (large) 2f else 1f)) {
                    StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                        if (bloom) {
                            BloomTimerScreen(brew, bloomSpritesheetWeights = sprites, dimModeEnabled = false,
                                onNavigateToBrew = {}, onBack = {})
                        } else {
                            BrewTimerScreen(brew, bloomSpritesheetWeights = sprites, dimModeEnabled = false, onBack = {})
                        }
                    }
                }
            }
            if (bloom) {
                // The retained route marks bloom after the real clock first ticks; no elapsed value is assumed.
                rule.waitUntil(5_000L) { brew.uiState.value.bloomMarkedAtSeconds != null }
                rule.onNodeWithText(context.getString(R.string.label_remaining)).assertIsDisplayed()
            }
            rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation)).assertIsDisplayed()
            rule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).performClick()
            rule.onNodeWithContentDescription(context.getString(R.string.action_resume)).assertIsDisplayed()
            rule.runOnIdle { assertFalse(brew.uiState.value.timerRunning) }
            rule.onAllNodesWithTag("approved_instruction_asset", useUnmergedTree = true).assertCountEquals(0)
            rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target)).assertDoesNotExist()
            val name = "centered-legacy-" + (if (bloom) "bloom" else "timer") + if (large) "-200" else ""
            save(name)
            rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performClick()
            rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed()
            rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
            rule.runOnIdle { assertFalse("Reading Help must preserve the paused clock", brew.uiState.value.timerRunning) }
            rule.onNodeWithContentDescription(context.getString(R.string.action_resume)).performClick()
            rule.runOnIdle { assertTrue(brew.uiState.value.timerRunning) }
            rule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).performClick()
            rule.runOnIdle { assertFalse(brew.uiState.value.timerRunning) }
        } finally {
            rule.runOnIdle { store.clear() }
        }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "minimal-brew-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
