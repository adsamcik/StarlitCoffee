package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MethodRecognitionVectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun acceptedMethodFamilyRendersAtSmallSizesInLightTheme() = verifyFamily(dark = false)

    @Test
    fun acceptedMethodFamilyRendersAtSmallSizesInDarkTheme() = verifyFamily(dark = true)

    private fun verifyFamily(dark: Boolean) {
        composeRule.setContent { MethodSheet(dark) }
        val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "native-method-family").apply { mkdirs() }
        File(evidence, if (dark) "dark.png" else "light.png").outputStream().use {
            composeRule.onNodeWithTag("method_sheet").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        EquipmentVisual.methodKeys.forEach { key ->
            listOf(24, 34).forEach { size ->
                val pixels = composeRule.onNodeWithTag("${key}_$size").captureToImage().toPixelMap()
                val background = pixels[0, 0]
                var ink = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val pixel = pixels[x, y]
                    val difference = abs(pixel.red - background.red) + abs(pixel.green - background.green) +
                        abs(pixel.blue - background.blue)
                    if (difference > 0.1f) ink++
                }
                val area = pixels.width * pixels.height
                assertTrue("$key at $size dp has visible tinted contours", ink > area * 0.03f)
                assertTrue("$key at $size dp retains negative space", ink < area * 0.85f)
            }
        }
    }

    @Composable
    private fun MethodSheet(dark: Boolean) {
        StarlitCoffeeTheme(dynamicColor = false, darkTheme = dark) {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp).testTag("method_sheet")) {
                FlowRow(maxItemsInEachRow = 5) {
                    EquipmentVisual.methodKeys.forEach { key ->
                        Column(Modifier.padding(4.dp)) {
                            Row {
                                listOf(24, 34).forEach { size ->
                                    Box(Modifier.background(MaterialTheme.colorScheme.surface).testTag("${key}_$size")) {
                                        EquipmentIcon(requireNotNull(EquipmentVisual.methodKey(key)), Modifier.size(size.dp))
                                    }
                                }
                            }
                            Text(key, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
