package com.adsamcik.starlitcoffee.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionRequest
import com.adsamcik.starlitcoffee.data.network.llm.LlmExtractionResult
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device validation for the translate-first pass' flavour-note handling
 * (the `TRANSLATE_SYSTEM_PROMPT` hardening). Feeds SYNTHETIC source-language OCR
 * text — with the exact fruit tokens that were leaking through untranslated
 * (Czech `meruňka`, Italian `mirtillo`, French `prune`) — straight into the real
 * [MindlayerLlmInferenceProvider.extractBagFields], bypassing OCR / PaddleOCR.
 * It asserts the extracted `tastingNotes` come out in English, not the source
 * language.
 *
 * This is the surgical alternative to the full corpus benchmark: the flavour
 * bug lives entirely in the text/translate pass, so a real image + OCR capture
 * is unnecessary. It needs a reachable, approved Mindlayer service with a Gemma
 * model loaded; when that is absent the test [assumeTrue]-skips cleanly (so it is
 * inert on unprovisioned CI, like the other on-device benchmark tests).
 *
 * Run it explicitly:
 *   adb shell am instrument -w -e class \
 *     com.adsamcik.starlitcoffee.benchmark.TranslatePassFlavourInstrumentedTest \
 *     com.adsamcik.starlitcoffee.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class TranslatePassFlavourInstrumentedTest {

    private data class Case(
        val lang: String,
        val ocr: String,
        val expectEnglish: List<String>,
        val forbidSource: List<String>,
    )

    private val cases = listOf(
        Case(
            lang = "it",
            ocr = """
                --- FRONT ---
                Luna Chiara
                Officina Alba
                Etiopia · Sidamo
                Note di degustazione: mirtillo, bergamotto, cacao
                Processo: Naturale
                250 g
            """.trimIndent(),
            expectEnglish = listOf("blueberry", "cocoa"),
            forbidSource = listOf("mirtillo", "cacao"),
        ),
        Case(
            lang = "fr",
            ocr = """
                --- FRONT ---
                Rive Claire
                Brûlerie du Port
                Colombie · Huila
                Notes de dégustation: prune, chocolat, mandarine
                Process: Lavé
                250 g
            """.trimIndent(),
            expectEnglish = listOf("plum", "mandarin"),
            forbidSource = listOf("prune", "mandarine"),
        ),
        Case(
            lang = "cs",
            ocr = """
                --- FRONT ---
                Jitřenka
                Pražírna Alba
                Etiopie · Sidamo
                Chuťové tóny: meruňka, karamel, krvavý pomeranč
                Zpracování: Praná
                250 g
            """.trimIndent(),
            expectEnglish = listOf("apricot", "caramel"),
            forbidSource = listOf("meruňka", "meruňk"),
        ),
    )

    @Test
    fun translatePassRendersFlavourNotesInEnglish() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = MindlayerLlmInferenceProvider(context)
        assumeTrue(
            "Mindlayer LLM not available — start the service and approve the app, then retry.",
            QualityTestSupport.awaitTrue { llm.isAvailable() },
        )

        val failures = mutableListOf<String>()
        for (case in cases) {
            val request = LlmExtractionRequest(
                imageBytes = ByteArray(0),
                existingFields = emptyMap(),
                fieldsNeeded = setOf("tastingNotes"),
                rawOcrText = case.ocr,
                knownFieldValues = null,
            )
            val result = llm.extractBagFields(request)
            val notes = (result as? LlmExtractionResult.Success)
                ?.fieldCandidates
                ?.firstOrNull { it.fieldName == "tastingNotes" }
                ?.value
                ?.lowercase()
            Log.i(TAG, "[${case.lang}] tastingNotes -> ${notes ?: "<none>"}")

            if (notes == null) {
                failures += "[${case.lang}] no tastingNotes extracted"
                continue
            }
            val leaked = case.forbidSource.filter { notes.contains(it.lowercase()) }
            val hits = case.expectEnglish.filter { notes.contains(it.lowercase()) }
            if (leaked.isNotEmpty()) failures += "[${case.lang}] leaked source token(s) $leaked in \"$notes\""
            if (hits.isEmpty()) {
                failures += "[${case.lang}] expected English of ${case.expectEnglish} but got \"$notes\""
            }
        }

        assertTrue("Translate-pass flavour check failures:\n${failures.joinToString("\n")}", failures.isEmpty())
        // Redundant guard so the intent is explicit even if the message above is skimmed.
        assertFalse("Some flavour notes were not translated to English", failures.any { it.contains("leaked") })
    }

    private companion object {
        private const val TAG = "TranslateFlavourTest"
    }
}
