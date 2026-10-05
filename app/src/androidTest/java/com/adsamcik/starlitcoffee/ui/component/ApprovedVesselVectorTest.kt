package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ApprovedVesselVectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allApprovedContoursRenderAtNativeSmallSize() {
        composeRule.setContent {
            CompositionLocalProvider(LocalContentColor provides Color.Black) {
                FlowRow(maxItemsInEachRow = 7) {
                    availablePresetIcons.forEach { key ->
                        Box(Modifier.background(Color.White).testTag(key)) {
                            PresetIcon(key, null, Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        availablePresetIcons.forEach { key ->
            val pixels = composeRule.onNodeWithTag(key).captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].red < 0.5f) ink++
            }
            assertTrue("$key has visible native contours", ink > 5)
            assertTrue("$key retains transparent negative space", ink < pixels.width * pixels.height * 0.8f)
        }
        assertEquals(presetIconRes("bowl"), presetIconRes("custom"))
        assertEquals(presetIconRes("mug"), presetIconRes("unknown-key"))
    }
}
