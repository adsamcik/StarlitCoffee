package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.navigation.reviewedLearningProfiles
import com.adsamcik.starlitcoffee.ui.guidance.learnResolution
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewedGuideNativeUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val guides get() = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
        .bufferedReader().use { it.readText() })

    @Test fun allSeventeenNativeLibraryEntriesHaveUniqueProfilesAndReadableRoutes() {
        val profiles = reviewedLearningProfiles(guides, emptyList())
        assertEquals(17, profiles.size)
        assertEquals(17, profiles.map { it.profileId }.distinct().size)
        assertEquals(17, profiles.flatMap { it.guides }.map { it.stableId }.distinct().size)
        var selected = ""
        rule.setContent { StarlitCoffeeTheme(dynamicColor = true) {
            LearningLibraryScreen(profiles, LearningLibraryAvailability.AVAILABLE,
                onOpenGuide = { _, guide -> selected = guide.stableId }, onBack = {})
        } }
        rule.onNodeWithText(guides.first().name).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(guides.first().id, selected) }
        save("reviewed-native-library")
    }

    @Test fun volumeGuideAt200PercentRetainsUntimedReaderAndPostFillClockOrigin() {
        val guide = guides.first { it.methodId == "hario-switch" }
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    Box(Modifier.width(320.dp)) {
                        LearnBrewerScreen(guide.methodName, guide.learnResolution(), onBack = {}, reviewedGuide = guide,
                            initiallyReading = true, initialStepIndex = 2)
                    }
                }
            }
        }
        rule.onAllNodesWithText("240.0 mL")[0].performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(guide.steps[2].instruction).performScrollTo().assertIsDisplayed()
        save("reviewed-native-switch-200-dark")
        rule.onNodeWithText(context.getString(R.string.action_next)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(guide.steps[3].instruction).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.action_start_brewing)).assertDoesNotExist()
        save("reviewed-native-switch-stage-wait")
    }

    @Test fun ChemexOverviewAndBrewEntryKeepReviewedAmountsAndRecipeDetailsReachable() {
        val guide = guides.first { it.methodId == "chemex" }
        var starts = 0
        rule.setContent { StarlitCoffeeTheme(dynamicColor = true) {
            LearnBrewerScreen(guide.methodName, guide.learnResolution(), onBack = {}, reviewedGuide = guide,
                onBrewGuide = { starts++ })
        } }
        rule.onNodeWithText("30 g").assertIsDisplayed()
        rule.onNodeWithText("480 g").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_brew_this_guide)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, starts) }
        rule.onNodeWithText(context.getString(R.string.action_read_guide)).performScrollTo().performClick()
        rule.onNodeWithText(guide.steps.first().instruction).performScrollTo().assertIsDisplayed()
        save("reviewed-native-chemex-reader")
    }

    private fun save(name: String) {
        val dir = File(context.getExternalFilesDir(null), "brewing-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
