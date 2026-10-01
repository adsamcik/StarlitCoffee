package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The chooser's finished flower must match the played animation's finish. */
@RunWith(AndroidJUnit4::class)
class BloomSpritesheetRenderingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyFinalPreviewMatchesPlayedFinishInBothThemes() {
        val selected = mutableStateOf(BloomSpritesheetOptions.first())
        val dark = mutableStateOf(false)
        val showPreview = mutableStateOf(false)
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark.value, dynamicColor = false) {
                Surface {
                    if (showPreview.value) {
                        BloomSpritesheetFinalFramePreview(
                            option = selected.value,
                            contentDescription = null,
                            modifier = Modifier.size(148.dp).testTag("sprite"),
                        )
                    } else {
                        BloomSpritesheetAnimation(
                            bloomCountdownSeconds = 0,
                            bloomDurationSeconds = 45,
                            selectedSpritesheetId = selected.value.id,
                            modifier = Modifier.size(148.dp).testTag("sprite"),
                            isRunning = false,
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            BloomSpritesheetOptions.forEach { option ->
                composeRule.runOnIdle {
                    selected.value = option
                    dark.value = isDark
                    showPreview.value = false
                }
                // Render in the same location: 148dp can land on half pixels at
                // some device densities, so adjacent canvases are not equivalent.
                val played = capture()
                composeRule.runOnIdle { showPreview.value = true }
                val preview = capture()
                assertEquals(played.width, preview.width)
                assertEquals(played.height, preview.height)
                assertArrayEquals("${option.id}, dark=$isDark", pixels(played), pixels(preview))
            }
        }
    }

    @Test
    fun everyAnimationRendersDistinctGrowthStagesInBothThemes() {
        val selected = mutableStateOf(BloomSpritesheetOptions.first())
        val dark = mutableStateOf(false)
        val remaining = mutableStateOf(48)
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark.value, dynamicColor = false) {
                Surface {
                    BloomSpritesheetAnimation(
                        bloomCountdownSeconds = remaining.value,
                        bloomDurationSeconds = 48,
                        selectedSpritesheetId = selected.value.id,
                        modifier = Modifier.size(148.dp).testTag("sprite"),
                        isRunning = false,
                    )
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            BloomSpritesheetOptions.forEach { option ->
                val stages = listOf(48, 24, 0).map { seconds ->
                    composeRule.runOnIdle {
                        selected.value = option
                        dark.value = isDark
                        remaining.value = seconds
                    }
                    capture().also { saveCapture(it, "${option.id}_${if (isDark) "dark" else "light"}_$seconds") }
                }
                stages.zipWithNext().forEach { (before, after) ->
                    val a = pixels(before)
                    val b = pixels(after)
                    val changedPixels = a.indices.count { a[it] != b[it] }
                    assertTrue("${option.id}, dark=$isDark must show readable growth", changedPixels > 64)
                }
            }
        }
    }

    private fun capture(): ImageBitmap = composeRule.onNodeWithTag("sprite").captureToImage()

    private fun pixels(image: ImageBitmap): IntArray = IntArray(image.width * image.height).also {
        image.readPixels(it)
    }

    private fun saveCapture(image: ImageBitmap, name: String) {
        if (InstrumentationRegistry.getArguments().getString("bloomCapture") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "bloom-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
