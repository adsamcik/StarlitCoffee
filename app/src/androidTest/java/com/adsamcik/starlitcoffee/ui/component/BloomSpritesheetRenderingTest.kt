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
        val options = optionsForRun()
        val selected = mutableStateOf(options.first())
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
            options.forEach { option ->
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
        val options = optionsForRun()
        // Sampling elapsed time uniformly intentionally repeats some early poses
        // under delayed growth. This audit still captures every distinct art pose.
        val duration = 10_000
        val countdowns = if (InstrumentationRegistry.getArguments().getString("bloomAllFrames") == "true") {
            (0..24).map { frame ->
                (duration downTo 0).first { seconds ->
                    resolveBloomFrameIndex(resolveBloomProgress(seconds, duration), 25) == frame
                }
            }
        } else {
            listOf(duration, duration / 2, 0)
        }
        val selected = mutableStateOf(options.first())
        val dark = mutableStateOf(false)
        val remaining = mutableStateOf(duration)
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark.value, dynamicColor = false) {
                Surface {
                    BloomSpritesheetAnimation(
                        bloomCountdownSeconds = remaining.value,
                        bloomDurationSeconds = duration,
                        selectedSpritesheetId = selected.value.id,
                        modifier = Modifier.size(148.dp).testTag("sprite"),
                        isRunning = false,
                    )
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            options.forEach { option ->
                val stages = countdowns.map { seconds ->
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

    @Test
    fun runningGrowthPausesRestartsAndFinishesWithTheCountdown() {
        val option = optionsForRun().first()
        val remaining = mutableStateOf(60)
        val running = mutableStateOf(true)
        val showPreview = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    if (showPreview.value) {
                        BloomSpritesheetFinalFramePreview(
                            option = option,
                            contentDescription = null,
                            modifier = Modifier.size(148.dp).testTag("sprite"),
                        )
                    } else {
                        BloomSpritesheetAnimation(
                            bloomCountdownSeconds = remaining.value,
                            bloomDurationSeconds = 60,
                            selectedSpritesheetId = option.id,
                            modifier = Modifier.size(148.dp).testTag("sprite"),
                            isRunning = running.value,
                        )
                    }
                }
            }
        }
        val seed = capture()
        composeRule.runOnIdle { remaining.value = 30 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_000)
        val halfway = capture()
        assertTrue(!pixels(seed).contentEquals(pixels(halfway)))

        composeRule.runOnIdle { running.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        val paused = capture()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertArrayEquals(pixels(paused), pixels(capture()))

        composeRule.runOnIdle {
            running.value = true
            remaining.value = 1
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_000)
        val unfinished = capture()
        composeRule.runOnIdle { remaining.value = 0 }
        composeRule.mainClock.advanceTimeByFrame()
        val finished = capture()
        composeRule.runOnIdle { showPreview.value = true }
        composeRule.mainClock.advanceTimeByFrame()
        val preview = capture()
        assertTrue(!pixels(unfinished).contentEquals(pixels(preview)))
        assertArrayEquals(pixels(preview), pixels(finished))

        composeRule.runOnIdle {
            showPreview.value = false
            remaining.value = 0
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertArrayEquals(pixels(preview), pixels(capture()))

        composeRule.runOnIdle {
            running.value = false
            remaining.value = 60
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertArrayEquals(pixels(seed), pixels(capture()))
    }

    private fun optionsForRun(): List<BloomSpritesheetOption> {
        val onlyId = InstrumentationRegistry.getArguments().getString("bloomOnly")
            ?: return BloomSpritesheetOptions
        return BloomSpritesheetOptions.filter { it.id == onlyId }.also {
            require(it.isNotEmpty()) { "Unknown bloomOnly animation ID: $onlyId" }
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
