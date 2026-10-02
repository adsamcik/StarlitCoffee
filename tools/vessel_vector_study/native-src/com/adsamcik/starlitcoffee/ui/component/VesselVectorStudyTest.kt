package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Experimental native comparison, injected by native.init.gradle only.
 *
 * Production icons use the original complete square viewport at 34 dp, centered
 * in 48 dp cells with 4 dp spacing/padding. The study candidates retain the same
 * viewport; they are neither cropped nor enlarged to compensate for their art.
 * Image/painterResource deliberately preserves all colors without tinting.
 */
@RunWith(AndroidJUnit4::class)
class VesselVectorStudyTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var detail by mutableStateOf(false)
    private var pixelsPerDp = 1f
    private var stripColor = 0
    private val vessels = listOf(
        Vessel("espresso", "Espresso", R.drawable.vessel_icon_espresso, R.drawable.vessel_vector_study_espresso),
        Vessel("cortado", "Cortado", R.drawable.vessel_icon_cortado, R.drawable.vessel_vector_study_cortado),
        Vessel("cappuccino", "Cappuccino", R.drawable.vessel_icon_cappuccino, R.drawable.vessel_vector_study_cappuccino),
        Vessel("mug", "Mug", R.drawable.vessel_icon_mug, R.drawable.vessel_vector_study_mug),
        Vessel("travel", "Travel", R.drawable.vessel_icon_travel, R.drawable.vessel_vector_study_travel),
    )

    @Test
    fun lightNativeRasterVectorComparison() = captureComparison(dark = false)

    @Test
    fun darkNativeRasterVectorComparison() = captureComparison(dark = true)

    private fun captureComparison(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        composeRule.setContent {
            StarlitCoffeeTheme(darkTheme = dark, dynamicColor = false) {
                pixelsPerDp = LocalDensity.current.density
                stripColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
                Surface(Modifier.width(360.dp), color = MaterialTheme.colorScheme.surface) {
                    if (detail) DetailSheet() else ProductionSheet()
                }
            }
        }
        assertImageBounds(34.dp)
        saveCapture("study_sheet", "$theme-production-34dp")
        saveCapture("raster_strip", "$theme-raster-strip-34dp")
        saveCapture("vector_strip", "$theme-vector-strip-34dp")

        composeRule.runOnIdle { detail = true }
        assertImageBounds(96.dp)
        saveCapture("study_sheet", "$theme-detail-96dp")

        File(outputDirectory(), "$theme-metadata.txt").writeText(
            "theme=$theme\ndynamicColor=false\npixelsPerDp=$pixelsPerDp\n" +
                "surfaceContainerLow=#${Integer.toHexString(stripColor)}\n" +
                "productionImageDp=34\nproductionCellDp=48\n" +
                "productionSpacingDp=4\nproductionHorizontalPaddingDp=4\n" +
                "detailImageDp=96\ncontentScale=Fit\ntint=none\n",
        )
    }

    @Composable
    private fun ProductionSheet() {
        Column(
            Modifier.testTag("study_sheet").padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Native comparison · 34 dp", style = MaterialTheme.typography.titleMedium)
            Text("Original raster", style = MaterialTheme.typography.labelMedium)
            ProductionStrip(vector = false)
            Text("Vector candidate", style = MaterialTheme.typography.labelMedium)
            ProductionStrip(vector = true)
        }
    }

    @Composable
    private fun ProductionStrip(vector: Boolean) {
        val kind = if (vector) "vector" else "raster"
        Surface(
            modifier = Modifier.testTag("${kind}_strip"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vessels.forEach { vessel ->
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        VesselImage(vessel, vector, 34.dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun DetailSheet() {
        Column(
            Modifier.testTag("study_sheet").padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Native detail · 96 dp", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(104.dp))
                Text("Raster", Modifier.width(108.dp), style = MaterialTheme.typography.labelMedium)
                Text("Vector", style = MaterialTheme.typography.labelMedium)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vessels.forEach { vessel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vessel.label, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium)
                            VesselImage(vessel, vector = false, size = 96.dp)
                            Spacer(Modifier.width(12.dp))
                            VesselImage(vessel, vector = true, size = 96.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
        }
    }

    @Composable
    private fun VesselImage(vessel: Vessel, vector: Boolean, size: Dp) {
        val kind = if (vector) "vector" else "raster"
        Image(
            painter = painterResource(if (vector) vessel.vector else vessel.raster),
            contentDescription = "${vessel.label} $kind",
            modifier = Modifier.size(size).testTag("${kind}_${vessel.name}"),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit,
        )
    }

    private fun assertImageBounds(size: Dp) {
        composeRule.waitForIdle()
        vessels.forEach { vessel ->
            listOf("raster", "vector").forEach { kind ->
                val image = composeRule.onNodeWithTag("${kind}_${vessel.name}").assertIsDisplayed()
                val bounds = image.fetchSemanticsNode().boundsInRoot
                assertEquals("$kind ${vessel.name} width", size.value * pixelsPerDp, bounds.width, 0.5f)
                assertEquals("$kind ${vessel.name} height", size.value * pixelsPerDp, bounds.height, 0.5f)
            }
        }
    }

    private fun outputDirectory(): File =
        File(context.getExternalFilesDir(null), "vessel-vector-study").apply {
            assertTrue("Could not create $absolutePath", isDirectory || mkdirs())
        }

    private fun saveCapture(tag: String, name: String) {
        composeRule.waitForIdle()
        File(outputDirectory(), "$name.png").outputStream().use { output ->
            assertTrue(composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private data class Vessel(
        val name: String,
        val label: String,
        @DrawableRes val raster: Int,
        @DrawableRes val vector: Int,
    )
}
