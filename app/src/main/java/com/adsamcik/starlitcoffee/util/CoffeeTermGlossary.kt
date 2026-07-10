package com.adsamcik.starlitcoffee.util

/**
 * A deliberately tiny, high-precision glossary of localized coffee tasting-note
 * terms that the on-device translate pass (Gemma 4 E2B) measurably mistranslates
 * or drops.
 *
 * The on-device translate-eval benchmark ([TranslateMultilingualEvalTest] in
 * `androidTest`) showed the model already self-translates the vast majority of
 * coffee vocabulary — concept fields (origin/process/roast) at ~97–100% and
 * Western-European tasting notes at ~90%+, with a ~1% source-leak rate. This
 * glossary therefore does NOT try to duplicate the model's job; it covers only
 * the empirically-hard residue:
 *  - obscure **Czech** botanical / berry words (`angrešt` → gooseberry,
 *    `bezový květ` → elderflower) and the false-friend `hořká` (= *bitter*, which
 *    the model misread as "hot"),
 *  - **Nordic** berry words (`solbær`, `krusbär`, `bringebær`, `vadelma`, …) that
 *    the model dropped or hallucinated.
 *
 * [matches] returns the entries whose source term is present as a whole
 * word/phrase in a piece of OCR text; [MindlayerLlmInferenceProvider] injects
 * those (and only those) into the translate prompt as exact-mapping hints. The
 * matcher is deterministic and unit-tested, in line with the scan pipeline's
 * "OCR normalization stays deterministic and covered by unit tests" rule.
 */
object CoffeeTermGlossary {

    /** One localized [source] term and its canonical English [english] rendering. */
    data class Entry(val source: String, val english: String) {
        /** Diacritic-folded, lower-cased, single-spaced form used for matching. */
        val normalizedSource: String = normalize(source)
    }

    private val MULTISPACE = Regex("\\s+")

    private fun normalize(value: String): String =
        CoffeeMetadataNormalizer.normalizeSearch(value).replace(MULTISPACE, " ").trim()

    /**
     * The glossary. Kept small on purpose — every entry is a term the model was
     * observed to get wrong, not merely a term that could be translated.
     */
    val entries: List<Entry> = listOf(
        // --- Czech (cs): obscure botanicals/berries + the hořká false-friend ---
        Entry("angrešt", "gooseberry"),
        Entry("bezový květ", "elderflower"),
        Entry("broskev", "peach"),
        Entry("hořká čokoláda", "dark chocolate"),
        Entry("hořká", "bitter"),
        Entry("hrozno", "grape"),
        Entry("hruška", "pear"),
        Entry("ibišek", "hibiscus"),
        Entry("fialka", "violet"),
        Entry("višně", "sour cherry"),
        Entry("švestka", "plum"),
        Entry("ostružina", "blackberry"),
        Entry("černý rybíz", "blackcurrant"),
        // --- Nordic (da/nb/sv/fi): berry words the model dropped/hallucinated ---
        Entry("solbær", "blackcurrant"),
        Entry("hyldeblomst", "elderflower"),
        Entry("krusbär", "gooseberry"),
        Entry("stikkelsbær", "gooseberry"),
        Entry("bringebær", "raspberry"),
        Entry("hindbær", "raspberry"),
        Entry("hallon", "raspberry"),
        Entry("vadelma", "raspberry"),
        Entry("mustaherukka", "blackcurrant"),
        Entry("puolukka", "lingonberry"),
        Entry("tyttebær", "lingonberry"),
        Entry("lingon", "lingonberry"),
    )

    /**
     * Entries whose source term appears as a whole word/phrase in [ocrText].
     * When a longer phrase matches (`hořká čokoláda`), the sub-phrase entry
     * (`hořká`) is suppressed so the prompt shows only the most specific mapping.
     */
    fun matches(ocrText: String): List<Entry> {
        if (ocrText.isBlank()) return emptyList()
        val haystack = " ${normalize(ocrText)} "
        val hit = entries.filter {
            it.normalizedSource.isNotBlank() && haystack.contains(" ${it.normalizedSource} ")
        }
        if (hit.size <= 1) return hit
        return hit.filterNot { entry ->
            hit.any { other ->
                other !== entry &&
                    other.normalizedSource != entry.normalizedSource &&
                    " ${other.normalizedSource} ".contains(" ${entry.normalizedSource} ")
            }
        }
    }
}
