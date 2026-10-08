package com.adsamcik.starlitcoffee.arch

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSmallIconArchitectureTest {
    @Test
    fun `every notification builder uses the shared small icon appearance`() {
        val sourceRoot = resolveMainSourceDir()
        val sourceFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .toList()
        val builderCount = sourceFiles.sumOf { file ->
            NOTIFICATION_BUILDER.findAll(file.readText()).count()
        }
        val sharedAppearanceCount = sourceFiles
            .filterNot { it.name == APPEARANCE_FILE_NAME }
            .sumOf { file ->
                SHARED_APPEARANCE_CALL.findAll(file.readText()).count()
            }
        val directIconSetters = sourceFiles
            .filterNot { it.name == APPEARANCE_FILE_NAME }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (DIRECT_SMALL_ICON_CALL.containsMatchIn(line)) {
                        "${file.relativeTo(sourceRoot)}:${index + 1}"
                    } else {
                        null
                    }
                }
            }

        assertTrue("Expected at least one production notification builder", builderCount > 0)
        assertEquals(
            "Every notification builder must call withStarlitSmallIcon()",
            builderCount,
            sharedAppearanceCount,
        )
        assertTrue(
            "Small icons must be selected only by NotificationAppearance.kt: $directIconSetters",
            directIconSetters.isEmpty(),
        )

        val appearanceSource = File(
            sourceRoot,
            "com/adsamcik/starlitcoffee/notification/$APPEARANCE_FILE_NAME",
        ).readText()
        assertTrue(
            "The shared appearance must use the dedicated notification drawable",
            "setSmallIcon(R.drawable.ic_notification_starlit)" in appearanceSource,
        )
    }

    @Test
    fun `notification icon density assets are white alpha glyphs at 24 dp`() {
        val resRoot = resolveMainResDir()
        val expectedSizes = linkedMapOf(
            "drawable-mdpi" to 24,
            "drawable-hdpi" to 36,
            "drawable-xhdpi" to 48,
            "drawable-xxhdpi" to 72,
            "drawable-xxxhdpi" to 96,
        )

        expectedSizes.forEach { (directory, expectedSize) ->
            val file = File(resRoot, "$directory/ic_notification_starlit.png")
            assertTrue("Missing notification icon density asset: $file", file.isFile)
            val image = ImageIO.read(file)
            assertNotNull("Unreadable notification icon density asset: $file", image)
            assertEquals("Unexpected width for $file", expectedSize, image.width)
            assertEquals("Unexpected height for $file", expectedSize, image.height)

            var hasTransparentPixel = false
            var hasVisiblePixel = false
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    val alpha = argb ushr 24 and 0xFF
                    if (alpha == 0) {
                        hasTransparentPixel = true
                    } else {
                        hasVisiblePixel = true
                        assertEquals("Visible red channel must be white in $file", 255, argb ushr 16 and 0xFF)
                        assertEquals("Visible green channel must be white in $file", 255, argb ushr 8 and 0xFF)
                        assertEquals("Visible blue channel must be white in $file", 255, argb and 0xFF)
                    }
                }
            }
            assertTrue("Notification icon needs transparent padding: $file", hasTransparentPixel)
            assertTrue("Notification icon must contain a visible glyph: $file", hasVisiblePixel)
        }
    }

    private fun resolveMainSourceDir(): File =
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate src/main/java (cwd=${File(".").absolutePath})")

    private fun resolveMainResDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate src/main/res (cwd=${File(".").absolutePath})")

    private companion object {
        const val APPEARANCE_FILE_NAME = "NotificationAppearance.kt"
        val NOTIFICATION_BUILDER = Regex("""\b(?:NotificationCompat|Notification)\.Builder\s*\(""")
        val SHARED_APPEARANCE_CALL = Regex("""\.withStarlitSmallIcon\s*\(""")
        val DIRECT_SMALL_ICON_CALL = Regex("""\.setSmallIcon\s*\(""")
    }
}
