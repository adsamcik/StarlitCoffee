package com.adsamcik.starlitcoffee.ui.guidance

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidValuesParityVerifierTest {

    @Test
    fun `all shipped locales match the aggregated base resource set`() {
        val report = AndroidValuesParityVerifier.verify(resourceRoot())

        assertTrue(report.failureDescription(), report.isValid)
        assertEquals(23, report.localeReports.size + 1)
        assertTrue("Expected translated resources", report.sourceResourceCount > 0)
    }

    @Test
    fun `parity scans every XML file in a locale directory`() = withTemporaryResourceTree(
        englishFiles = mapOf(
            "strings.xml" to resources("<string name=\"label_base\">Base</string>"),
            "brewing_guidance.xml" to resources("<string name=\"instruction_new\">Pour carefully</string>"),
        ),
        localizedFiles = mapOf(
            "strings.xml" to resources("<string name=\"label_base\">Přehled</string>"),
        ),
    ) { resourceRoot ->
        val report = AndroidValuesParityVerifier.verify(resourceRoot)
        val locale = report.localeReports.single()

        assertFalse(report.isValid)
        assertEquals(setOf(ValueResourceKey("string", "instruction_new")), locale.missingKeys)
    }

    @Test
    fun `indexed format arguments may be reordered across locales`() = withTemporaryResourceTree(
        englishFiles = mapOf(
            "strings.xml" to resources(
                "<string name=\"format_recipe\">%1\$s needs %2\$d g</string>",
                "<plurals name=\"format_count\"><item quantity=\"one\">%1\$d cup</item>" +
                    "<item quantity=\"other\">%1\$d cups</item></plurals>",
            ),
        ),
        localizedFiles = mapOf(
            "strings.xml" to resources(
                "<string name=\"format_recipe\">%2\$d g pro %1\$s</string>",
                "<plurals name=\"format_count\"><item quantity=\"one\">%1\$d šálek</item>" +
                    "<item quantity=\"few\">%1\$d šálky</item>" +
                    "<item quantity=\"other\">%1\$d šálků</item></plurals>",
            ),
        ),
    ) { resourceRoot ->
        val report = AndroidValuesParityVerifier.verify(resourceRoot)

        assertTrue(report.failureDescription(), report.isValid)
    }

    @Test
    fun `format type changes are reported`() = withTemporaryResourceTree(
        englishFiles = mapOf(
            "strings.xml" to resources("<string name=\"format_weight\">%1\$.0f g</string>"),
        ),
        localizedFiles = mapOf(
            "strings.xml" to resources("<string name=\"format_weight\">%1\$s g</string>"),
        ),
    ) { resourceRoot ->
        val report = AndroidValuesParityVerifier.verify(resourceRoot)
        val mismatch = report.localeReports.single().formatMismatches.single()

        assertFalse(report.isValid)
        assertEquals(ValueResourceKey("string", "format_weight"), mismatch.key)
    }

    @Test
    fun `base-only unreviewed copy may be explicitly non-translatable`() =
        withTemporaryResourceTree(
            englishFiles = mapOf(
                "strings.xml" to resources(
                    "<string name=\"label_base\">Base</string>",
                    "<string name=\"technical_copy\" translatable=\"false\">Reviewed English only</string>",
                ),
            ),
            localizedFiles = mapOf(
                "strings.xml" to resources("<string name=\"label_base\">Přehled</string>"),
            ),
        ) { resourceRoot ->
            assertTrue(AndroidValuesParityVerifier.verify(resourceRoot).isValid)
        }

    private fun resourceRoot(): File = listOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).firstOrNull(File::isDirectory)
        ?: error("Could not find Android resources from ${File(".").absolutePath}")

    private fun withTemporaryResourceTree(
        englishFiles: Map<String, String>,
        localizedFiles: Map<String, String>,
        block: (File) -> Unit,
    ) {
        val root = Files.createTempDirectory("starlit-values-parity").toFile()
        try {
            val resources = File(root, "res")
            writeLocaleConfig(File(resources, "xml/locales_config.xml"))
            englishFiles.forEach { (name, text) -> write(File(resources, "values/$name"), text) }
            localizedFiles.forEach { (name, text) -> write(File(resources, "values-cs/$name"), text) }
            block(resources)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeLocaleConfig(file: File) = write(
        file,
        """
            <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
                <locale android:name="en" />
                <locale android:name="cs" />
            </locale-config>
        """.trimIndent(),
    )

    private fun resources(vararg entries: String): String = buildString {
        appendLine("<resources>")
        entries.forEach(::appendLine)
        append("</resources>")
    }

    private fun write(file: File, text: String) {
        checkNotNull(file.parentFile).mkdirs()
        file.writeText(text)
    }
}
