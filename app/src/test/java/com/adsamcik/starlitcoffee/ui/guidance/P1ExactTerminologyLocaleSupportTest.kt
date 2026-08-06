package com.adsamcik.starlitcoffee.ui.guidance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ExactTerminologyLocaleSupportTest {

    @Test
    fun `strict terminology decoder supports every declared locale and writing system`() {
        val references = projectFile(
            "src/main/assets/${BuiltInP1ExactTerminologyCatalog.REFERENCE_ASSET_NAME}",
            "app/src/main/assets/${BuiltInP1ExactTerminologyCatalog.REFERENCE_ASSET_NAME}",
        ).readText()
        val englishGlossary = projectFile(
            "src/main/res/raw/p1_exact_terminology.json",
            "app/src/main/res/raw/p1_exact_terminology.json",
        ).readText()

        assertEquals(
            P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
            LOCALIZED_FIXTURES.keys,
        )
        LOCALIZED_FIXTURES.forEach { (locale, fixture) ->
            val glossary = englishGlossary
                .replace("\"locale\": \"en\"", "\"locale\": \"$locale\"")
                .replace("Show English terms", "${fixture.uiLabel} · show")
                .replace("Hide English terms", "${fixture.uiLabel} · hide")
                .replace("English terminology", fixture.uiLabel)
                .replace("\"preferred_local\": \"drawdown\"", "\"preferred_local\": \"${fixture.localTerm}\"")
                .let { glossary ->
                    val drawdown = glossary.indexOf("\"concept_id\": \"drawdown\"")
                    val policy = glossary.indexOf(
                        "\"english_reference_policy\": \"established_local_usage\"",
                        startIndex = drawdown,
                    )
                    if (locale == "en") glossary else glossary.replaceRange(
                        policy,
                        policy + "\"english_reference_policy\": \"established_local_usage\"".length,
                        "\"english_reference_policy\": \"contextual_first_occurrence\"",
                    )
                }
            val catalog = BuiltInP1ExactTerminologyCatalog.decode(
                encodedReferences = references,
                encodedGlossary = glossary,
                activeLocaleTag = locale,
            )

            assertEquals(locale, catalog.localeTag)
            if (locale == "en") {
                assertFalse(catalog.hasDistinctEnglishReferences)
            } else {
                assertTrue("Expected distinct reference support for $locale", catalog.hasDistinctEnglishReferences)
            }
        }
    }

    private fun projectFile(vararg candidates: String): File = candidates
        .map(::File)
        .first(File::isFile)

    private data class LocaleFixture(
        val uiLabel: String,
        val localTerm: String,
    )

    private companion object {
        val LOCALIZED_FIXTURES = linkedMapOf(
            "en" to LocaleFixture("English terminology", "drawdown"),
            "bg" to LocaleFixture("Пример", "местен термин"),
            "cs" to LocaleFixture("Příklad", "místní termín"),
            "da" to LocaleFixture("Eksempel", "lokalt begreb"),
            "de" to LocaleFixture("Beispiel", "lokaler Begriff"),
            "el" to LocaleFixture("Παράδειγμα", "τοπικός όρος"),
            "es" to LocaleFixture("Ejemplo", "término local"),
            "et" to LocaleFixture("Näide", "kohalik termin"),
            "fi" to LocaleFixture("Esimerkki", "paikallinen termi"),
            "fr" to LocaleFixture("Exemple", "terme local"),
            "hr" to LocaleFixture("Primjer", "lokalni izraz"),
            "hu" to LocaleFixture("Példa", "helyi kifejezés"),
            "it" to LocaleFixture("Esempio", "termine locale"),
            "lt" to LocaleFixture("Pavyzdys", "vietinis terminas"),
            "lv" to LocaleFixture("Piemērs", "vietējais termins"),
            "nl" to LocaleFixture("Voorbeeld", "lokale term"),
            "pl" to LocaleFixture("Przykład", "lokalny termin"),
            "pt" to LocaleFixture("Exemplo", "termo local"),
            "ro" to LocaleFixture("Exemplu", "termen local"),
            "sk" to LocaleFixture("Príklad", "miestny termín"),
            "sl" to LocaleFixture("Primer", "lokalni izraz"),
            "sv" to LocaleFixture("Exempel", "lokal term"),
            "zh" to LocaleFixture("示例", "本地术语"),
        )
    }
}