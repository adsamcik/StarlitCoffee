package com.adsamcik.starlitcoffee.data.model

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemexResourceCoverageTest {
    private val resources = File("src/main/res").takeIf { it.isDirectory }
        ?: File("app/src/main/res")
    private val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    @Test
    fun `every supported locale appends Chemex without changing method array positions`() {
        val locales = parser.parse(File(resources, "xml/locales_config.xml")).getElementsByTagName("locale")
        assertEquals(
            listOf("PULSAR", "V60", "FRENCH_PRESS", "AEROPRESS", "ESPRESSO", "MOKA_POT", "COLD_BREW", "CHEMEX"),
            BrewMethod.entries.map { it.name },
        )
        for (index in 0 until locales.length) {
            val locale = locales.item(index).attributes.getNamedItem("android:name").nodeValue
            val directory = File(resources, if (locale == "en") "values" else "values-$locale")
            val labels = File(directory, if (locale == "en") "strings.xml" else "display_labels.xml")
            val arrays = parser.parse(labels).getElementsByTagName("string-array")
            val methods = (0 until arrays.length).map { arrays.item(it) }
                .single { it.attributes.getNamedItem("name").nodeValue == "brew_method_names" }
            val items = methods.childNodes
            val names = (0 until items.length).map { items.item(it) }
                .filter { it.nodeName == "item" }.map { it.textContent }
            assertEquals("$locale method count", BrewMethod.entries.size, names.size)
            assertEquals("$locale Chemex ordinal", "Chemex", names[BrewMethod.CHEMEX.ordinal])
        }
    }

    @Test
    fun `Chemex preparation and timer resources cover all supported locales`() {
        val keys = setOf("prep_tip_chemex", "instruction_chemex_bloom", "instruction_chemex_pour",
            "instruction_chemex_drawdown", "label_filter_chemex_bonded")
        val directories = resources.listFiles().orEmpty().filter { File(it, "chemex_strings.xml").isFile }
        assertEquals(23, directories.size)
        directories.forEach { directory ->
            val strings = parser.parse(File(directory, "chemex_strings.xml")).getElementsByTagName("string")
            val entries = (0 until strings.length).map { strings.item(it) }
            assertEquals(directory.name, keys, entries.map { it.attributes.getNamedItem("name").nodeValue }.toSet())
            assertTrue(directory.name, entries.all { it.textContent.isNotBlank() })
        }
    }
}
