package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.ui.brewing.BrewReaderContent
import com.adsamcik.starlitcoffee.ui.brewing.BrewStepContent
import com.adsamcik.starlitcoffee.ui.brewing.approvedBrewStepIllustration
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real native layout of deliberately sparse packets, without changing a session or source contract. */
class AdaptiveBrewContentTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun targetsOnlyAt200PercentKeepsTargetsAndStepNavigationWithoutEmptySections() {
        val target = BrewStageReferenceCuePresentation.Mass(QuantityRole.BREW_WATER_INPUT,
            StageMassReference.BREW_CUMULATIVE, StageTargetQualifier.EXACT, 340.0, 340.0)
        render(BrewReaderContent("sparse-targets", steps = listOf(
            BrewStepContent(StageContentId("pour"), title = "Pour", referenceCues = listOf(target)),
            BrewStepContent(StageContentId("drain"), title = "Drain", completionCue = "Wait until dripping slows"),
        )), large = true)
        rule.onNodeWithText("340 g").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.heading_guide_why)).assertDoesNotExist()
        save("adaptive-targets-only-200")
        rule.onNodeWithText(context.getString(R.string.action_next)).performClick()
        rule.onNodeWithText("Wait until dripping slows").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun imageOnlyStepKeepsAdmittedIllustrationWithoutInventingAnInstruction() {
        val caption = context.getString(R.string.cd_brew_water_height_target)
        val asset = requireNotNull(BuiltInInstructionAssetCatalog.catalog.find(
            InstructionAssetId("instruction_scene_pulsar_water_height_default")))
        render(BrewReaderContent("sparse-image", steps = listOf(BrewStepContent(
            StageContentId("water_height"), title = "Water level",
            illustration = approvedBrewStepIllustration(asset, caption),
        ))))
        rule.onNodeWithContentDescription(caption).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.heading_guide_why)).assertDoesNotExist()
        save("adaptive-image-only")
    }

    @Test
    fun noTeachingContentKeepsAuthorizedBrewEntryWithoutEmptyReaderActions() {
        var starts = 0
        render(BrewReaderContent("no-teaching-content"), reading = false, onBrew = { starts++ })
        rule.onNodeWithText(context.getString(R.string.action_read_guide)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.action_guide_steps)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.action_brew_this_guide)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, starts) }
    }

    private fun render(content: BrewReaderContent, large: Boolean = false, reading: Boolean = true,
        onBrew: (() -> Unit)? = null) {
        rule.setContent {
            val density = LocalDensity.current
            val scale = if (large) LocalWindowInfo.current.containerSize.width / 320f else density.density
            CompositionLocalProvider(LocalDensity provides Density(scale, if (large) 2f else 1f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    LearnBrewerScreen("Pulsar", content, onBack = {}, initiallyReading = reading, onBrewGuide = onBrew)
                }
            }
        }
    }

    private fun save(name: String) {
        val directory = File(context.getExternalFilesDir(null), "adaptive-brew-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
