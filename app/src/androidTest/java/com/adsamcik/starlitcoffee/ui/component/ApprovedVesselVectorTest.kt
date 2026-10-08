package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ApprovedVesselVectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allApprovedContoursRenderAtNativeSmallSize() {
        composeRule.setContent {
            Column {
                listOf("light", "dark").forEach { theme ->
                    val background = if (theme == "light") Color.White else Color(0xFF202129)
                    val foreground = if (theme == "light") Color.Black else Color(0xFFDFE1EC)
                    CompositionLocalProvider(LocalContentColor provides foreground) {
                        listOf(24, 32).forEach { size ->
                            FlowRow(maxItemsInEachRow = 7,
                                modifier = Modifier.background(background).testTag("vessel_family_${theme}_$size")) {
                                availablePresetIcons.forEach { key ->
                                    Box(Modifier.padding(8.dp).background(background).testTag("${theme}_${key}_$size")) {
                                        PresetIcon(key, null, Modifier.size(size.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf("light", "dark").forEach { theme ->
            listOf(24, 32).forEach { size ->
                availablePresetIcons.forEach { key ->
                    val pixels = composeRule.onNodeWithTag("${theme}_${key}_$size").captureToImage().toPixelMap()
                    var ink = 0
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        if ((theme == "light" && pixels[x, y].red < 0.5f) ||
                            (theme == "dark" && pixels[x, y].red > 0.5f)) ink++
                    }
                    assertTrue("$key has visible native contours at $size dp in $theme", ink > 5)
                    assertTrue("$key retains negative space at $size dp in $theme",
                        ink < pixels.width * pixels.height * 0.8f)
                }
                saveCapture("vessel_family_${theme}_$size")
            }
        }
        assertEquals(presetIconRes("bowl"), presetIconRes("custom"))
        assertEquals(presetIconRes("mug"), presetIconRes("unknown-key"))
    }

    private fun saveCapture(tag: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "vessel-language").apply { mkdirs() }
        File(directory, "$tag.png").outputStream().use {
            assertTrue(composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
