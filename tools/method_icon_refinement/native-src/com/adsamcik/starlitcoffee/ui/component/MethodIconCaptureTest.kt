package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Visual capture fixture; injected only by native.init.gradle, not the app test suite. */
class MethodIconCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    private val methods = listOf(
        BrewMethod.PULSAR to "Pulsar",
        BrewMethod.AEROPRESS to "AeroPress",
        BrewMethod.ESPRESSO to "Espresso",
        BrewMethod.CHEMEX to "Chemex",
    )

    @Test fun lightProductionArtwork() = capture(dark = false)
    @Test fun darkProductionArtwork() = capture(dark = true)

    private fun capture(dark: Boolean) {
        var pixelsPerDp = 1f
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark, dynamicColor = false) {
                pixelsPerDp = LocalDensity.current.density
                Surface(Modifier.width(360.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.testTag("sheet").padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Brewing sets", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.testTag("badges"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            methods.forEach { (method, label) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    EquipmentVisualBadge(EquipmentVisual.method(method), selected = method == BrewMethod.PULSAR)
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        Text("24 · 28 · 34 · 44 dp", style = MaterialTheme.typography.labelLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            methods.forEach { (method, label) -> SizeRow(method, label) }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        methods.forEach { (method, _) ->
            listOf(24, 28, 34, 44).forEach { size ->
                val node = composeRule.onNodeWithTag("${method.name}_$size").assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertEquals(size * pixelsPerDp, bounds.width, 0.5f)
                assertEquals(size * pixelsPerDp, bounds.height, 0.5f)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "method-icon-refinement")
        assertTrue(output.isDirectory || output.mkdirs())
        val theme = if (dark) "dark" else "light"
        File(output, "$theme.png").outputStream().use {
            assertTrue(composeRule.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(output, "$theme.txt").writeText("pixelsPerDp=$pixelsPerDp\ndynamicColor=false\nbadge=48dp\niconSizes=24,28,34,44dp\n")
    }

    @Composable
    private fun SizeRow(method: BrewMethod, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.labelMedium)
            listOf(24, 28, 34, 44).forEach { size ->
                EquipmentIcon(EquipmentVisual.method(method),
                    Modifier.size(size.dp).testTag("${method.name}_$size"))
            }
        }
    }
}
