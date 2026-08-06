package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ExactTerminologyCatalogTest {

    @Test
    fun `canonical English glossary decodes without redundant references`() {
        val catalog = BuiltInP1ExactTerminologyCatalog.decode(
            encodedReferences = referenceAsset().readText(),
            encodedGlossary = englishGlossary().readText(),
            activeLocaleTag = "en",
        )

        assertEquals("en", catalog.localeTag)
        assertFalse(catalog.hasDistinctEnglishReferences)
        assertTrue(catalog.referencesFor(DRAWDOWN_STAGE).isEmpty())
    }

    @Test
    fun `localized glossary resolves ordered distinct English references`() {
        val catalog = BuiltInP1ExactTerminologyCatalog.decode(
            encodedReferences = referenceAsset().readText(),
            encodedGlossary = czechGlossary(),
            activeLocaleTag = "cs",
        )

        assertTrue(catalog.hasDistinctEnglishReferences)
        assertEquals(
            listOf(
                BrewingTerminologyReference(
                    conceptId = "brewer_dripper",
                    preferredLocal = "dripper",
                    canonicalEnglish = "brewer / dripper",
                    englishReferencePolicy = BrewingEnglishReferencePolicy.CONTEXTUAL_WHEN_RELEVANT,
                ),
                BrewingTerminologyReference(
                    conceptId = "drawdown",
                    preferredLocal = "dokapání",
                    canonicalEnglish = "drawdown",
                    englishReferencePolicy = BrewingEnglishReferencePolicy.CONTEXTUAL_FIRST_OCCURRENCE,
                ),
            ),
            catalog.referencesFor(DRAWDOWN_STAGE),
        )
        assertEquals("Zobrazit anglické termíny", catalog.uiCopy.showEnglishTerms)
    }

    @Test
    fun `preview glossary decodes without claiming review`() {
        val catalog = BuiltInP1ExactTerminologyCatalog.decode(
            encodedReferences = referenceAsset().readText(),
            encodedGlossary = projectFile(
                "src/main/res/raw-cs/p1_exact_terminology.json",
                "app/src/main/res/raw-cs/p1_exact_terminology.json",
            ).readText(),
            activeLocaleTag = "cs",
        )

        assertEquals(P1ExactLocalizationStatus.PREVIEW, catalog.localizationStatus)
    }

    @Test
    fun `fallback glossary for another locale fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            BuiltInP1ExactTerminologyCatalog.decode(
                encodedReferences = referenceAsset().readText(),
                encodedGlossary = englishGlossary().readText(),
                activeLocaleTag = "cs",
            )
        }
    }

    @Test
    fun `unknown stage concept fails strict decoding`() {
        val references = referenceAsset().readText()
            .replace("\r\n", "\n")
            .replace(
                "\"concept_ids\": [\n        \"brewer_dripper\",\n        \"drawdown\"\n      ]",
                "\"concept_ids\": [\n        \"brewer_dripper\",\n        \"unknown_term\"\n      ]",
            )

        assertThrows(IllegalArgumentException::class.java) {
            BuiltInP1ExactTerminologyCatalog.decode(
                encodedReferences = references,
                encodedGlossary = englishGlossary().readText(),
                activeLocaleTag = "en",
            )
        }
    }

    private fun czechGlossary(): String {
        var glossary = englishGlossary().readText()
            .replace("\"locale\": \"en\"", "\"locale\": \"cs\"")
            .replace(
                "Canonical evidence library and Starlit Coffee implementation review",
                "Native Czech coffee reviewer",
            )
            .replace("Show English terms", "Zobrazit anglické termíny")
            .replace("Hide English terms", "Skrýt anglické termíny")
            .replace("English terminology", "Anglická terminologie")
        glossary = localizedTerm(
            glossary,
            conceptId = "brewer_dripper",
            source = "brewer / dripper",
            localized = "dripper",
            policy = "contextual_when_relevant",
        )
        return localizedTerm(
            glossary,
            conceptId = "drawdown",
            source = "drawdown",
            localized = "dokapání",
            policy = "contextual_first_occurrence",
        )
    }

    private fun localizedTerm(
        glossary: String,
        conceptId: String,
        source: String,
        localized: String,
        policy: String,
    ): String {
        val start = glossary.indexOf("\"concept_id\": \"$conceptId\"")
        require(start >= 0)
        val end = glossary.indexOf("}", start)
        val block = glossary.substring(start, end)
            .replace("\"preferred_local\": \"$source\"", "\"preferred_local\": \"$localized\"")
            .replace(
                "\"english_reference_policy\": \"established_local_usage\"",
                "\"english_reference_policy\": \"$policy\"",
            )
        return glossary.replaceRange(start, end, block)
    }


    private fun referenceAsset(): File = projectFile(
        "src/main/assets/${BuiltInP1ExactTerminologyCatalog.REFERENCE_ASSET_NAME}",
        "app/src/main/assets/${BuiltInP1ExactTerminologyCatalog.REFERENCE_ASSET_NAME}",
    )

    private fun englishGlossary(): File = projectFile(
        "src/main/res/raw/p1_exact_terminology.json",
        "app/src/main/res/raw/p1_exact_terminology.json",
    )

    private fun projectFile(vararg candidates: String): File = candidates
        .map(::File)
        .first(File::isFile)

    private companion object {
        val DRAWDOWN_STAGE = StageContentId("p1_v60_official_15_250_stage_05_instruction")
    }
}