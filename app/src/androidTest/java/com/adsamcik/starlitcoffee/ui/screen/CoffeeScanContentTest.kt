package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native layout and interaction evidence for the shared surface, without opening a camera or recognizing a label. */
@RunWith(AndroidJUnit4::class)
class CoffeeScanContentTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = mutableListOf<File>()
    private var pixelsPerDp = 1f

    @After
    fun deleteFixtures() {
        fixtures.forEach(File::delete)
    }

    @Test
    fun captureAndReviewRemainReachableAndRetainedPhotosStayVisibleWhileBusy() {
        val state = ScanHarness()
        show(state)
        listOf("import", "capture", "auto", "manual").forEach {
            control(it).assertIsDisplayed().assertIsEnabled()
        }
        control("review").assertDoesNotExist()
        capture("scan-empty-light")

        val photos = listOf(photo("Las Flores", "Colombia"), photo("April Coffee", "250 g"))
        rule.runOnIdle { state.photos.value = photos }
        control("manual").assertDoesNotExist()
        control("review").assertIsDisplayed().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, state.reviews)
            assertEquals(photos, state.photos.value)
            state.busy.value = true
            state.dark.value = true
        }
        listOf("import", "capture", "auto", "review").forEach {
            control(it).assertIsDisplayed().assertIsNotEnabled()
        }
        repeat(2) { index ->
            rule.onNodeWithContentDescription(removePhotoDescription(index)).assertIsDisplayed().assertIsNotEnabled()
        }
        capture("scan-retained-busy-dark")

        rule.runOnIdle {
            state.busy.value = false
            state.choosingCoffee.value = true
            state.photos.value = emptyList()
            state.dark.value = false
        }
        control("import").assertIsDisplayed().assertIsEnabled()
        control("capture").assertIsDisplayed().assertIsEnabled()
        listOf("auto", "manual", "review").forEach { control(it).assertDoesNotExist() }
        capture("scan-choose-coffee-light")
        assertControlGeometry(choosingCoffee = true)
    }

    @Test
    fun narrow200PercentRetainedPhotoReviewAndCodeSelectionKeepControlsAndPreviewUsable() {
        val configuration = context.resources.configuration
        assumeTrue(
            "Run on the configured device at 320 dp with system font_scale 2.0.",
            configuration.screenWidthDp <= 330 && configuration.fontScale >= 1.9f,
        )
        val state = ScanHarness().apply {
            photos.value = listOf(photo("Las Flores", "Colombia"), photo("April Coffee", "250 g"))
            error.value = R.string.msg_scan_capture_failed
        }
        show(state)
        for (dark in listOf(false, true)) {
            rule.runOnIdle { state.dark.value = dark }
            capture(if (dark) "scan-retained-200-dark" else "scan-retained-200-light")
            control("review").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithTag("coffee-scan-error").assertIsDisplayed()
            repeat(2) { index ->
                rule.onNodeWithContentDescription(removePhotoDescription(index)).assertIsDisplayed()
            }
            assertControlGeometry(choosingCoffee = false)
            assertTextFits()
        }
        rule.runOnIdle {
            state.choosingCoffee.value = true
            state.photos.value = emptyList()
            state.error.value = R.string.msg_scan_code_not_found
        }
        capture("scan-choose-coffee-200-dark")
        listOf("auto", "manual", "review").forEach { control(it).assertDoesNotExist() }
        assertControlGeometry(choosingCoffee = true)
        assertTextFits()
    }

    private fun show(state: ScanHarness) {
        rule.setContent {
            pixelsPerDp = LocalDensity.current.density
            StarlitCoffeeTheme(darkTheme = state.dark.value, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        CoffeeScanContent(
                            photos = state.photos.value,
                            choosingCoffee = state.choosingCoffee.value,
                            cameraReady = true,
                            frameReady = false,
                            autoEnabled = true,
                            busy = state.busy.value,
                            guidanceResId = R.string.msg_scan_coffee_aim,
                            errorResId = state.error.value,
                            onBack = {},
                            onImport = {},
                            onCapture = {},
                            onToggleAuto = {},
                            onRemovePhoto = { uri -> state.photos.value = state.photos.value - uri },
                            onReview = { state.reviews++ },
                            preview = { Box(Modifier.fillMaxSize().background(Color(0xFF615D55))) },
                        )
                    }
                }
            }
        }
    }

    private fun assertControlGeometry(choosingCoffee: Boolean) {
        val root = rule.onNodeWithTag("coffee-scan").fetchSemanticsNode().boundsInRoot
        val names = listOf("import", "capture") + if (choosingCoffee) emptyList() else listOf("auto", "review")
        val bounds = names.map { name ->
            control(name).assertIsDisplayed().assertIsEnabled()
            control(name).fetchSemanticsNode().boundsInRoot.also { box ->
                assertTrue("$name leaves the usable screen: $box / $root", contained(root, box))
                assertTrue("$name has too little touch area: $box", box.width >= 48f * pixelsPerDp - 1f &&
                    box.height >= 48f * pixelsPerDp - 1f)
            }
        }
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertFalse("Scan controls overlap: $first / $second", first.overlaps(second))
            }
        }
        val preview = rule.onNodeWithTag("coffee-scan-preview").fetchSemanticsNode().boundsInRoot
        assertTrue("Camera preview has no useful height: $preview", preview.height >= 160f * pixelsPerDp)
        assertTrue("Camera preview leaves the usable screen: $preview / $root", contained(root, preview))
        rule.onAllNodesWithTag("coffee-scan-error").fetchSemanticsNodes().forEach { error ->
            assertFalse("Scan error covers the camera: ${error.boundsInRoot} / $preview", error.boundsInRoot.overlaps(preview))
        }
    }

    private fun contained(outer: Rect, inner: Rect): Boolean = inner.left >= outer.left - 1f &&
        inner.top >= outer.top - 1f && inner.right <= outer.right + 1f && inner.bottom <= outer.bottom + 1f

    private fun assertTextFits() {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.forEach { layout ->
                assertFalse("Text is clipped vertically: ${layout.layoutInput.text}", layout.didOverflowHeight)
                // String Text reconstructs its diagnostic paragraph at the parent's maximum width.
                // Check the painted line spans, excluding that unused space around a short label.
                repeat(layout.lineCount) { line ->
                    val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                    assertTrue("Text is clipped horizontally: ${layout.layoutInput.text}", lineWidth <= layout.size.width + 1f)
                    assertFalse("Text is ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                }
            }
        }
    }

    private fun control(name: String) = rule.onNodeWithTag("coffee-scan-$name")

    private fun removePhotoDescription(index: Int) = "${context.getString(R.string.cd_remove_photo)}: " +
        context.getString(R.string.format_cd_scan_photo, index + 1)

    private fun photo(name: String, details: String): String {
        val file = File.createTempFile("coffee-scan-ui-", ".jpg", context.cacheDir).also(fixtures::add)
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(113, 79, 55))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(249, 240, 219) }
        canvas.drawRoundRect(20f, 60f, 220f, 250f, 12f, 12f, paint)
        paint.color = android.graphics.Color.rgb(59, 40, 30)
        paint.textSize = 25f
        canvas.drawText(name, 30f, 120f, paint)
        paint.textSize = 19f
        canvas.drawText(details, 30f, 170f, paint)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally {
            bitmap.recycle()
        }
        return file.toURI().toString()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val configuration = context.resources.configuration
        val output = File(context.getExternalFilesDir(null),
            "unified-coffee-scan/$name-${configuration.screenWidthDp}x${configuration.screenHeightDp}dp-" +
                "${(configuration.fontScale * 100).toInt()}pct.png")
        output.parentFile?.mkdirs()
        // Keep the settled Android window as well as the Compose layer capture.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(100, 3_000)
        val window = requireNotNull(automation.takeScreenshot())
        try {
            File(output.parentFile, "${output.nameWithoutExtension}-screen.png").outputStream().use {
                assertTrue(window.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            window.recycle()
        }
        val screenshot = rule.onRoot().captureToImage().asAndroidBitmap()
        try {
            output.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private class ScanHarness {
        val photos = mutableStateOf(emptyList<String>())
        val choosingCoffee = mutableStateOf(false)
        val busy = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val error = mutableStateOf<Int?>(null)
        var reviews = 0
    }
}
