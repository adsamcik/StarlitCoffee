package com.adsamcik.starlitcoffee.test.corpus

import java.text.Normalizer

/** Whether a field participates in the Q0 best-case hard gate or is report-only. */
enum class GatePolicy { GATE, REPORT_ONLY }

/** Result of comparing one extracted value against ground truth. */
enum class MatchLevel { EXACT, PARTIAL, NONE }

/** Compares an extracted value against the ground-truth value for one field. */
fun interface FieldComparator {
    fun compare(expected: String, actual: String): MatchLevel
}

/**
 * Single source of truth tying together, per field:
 *  - [metadataKey]   the LLM-side key used in corpus sidecars (`process`)
 *  - [appFieldName]  the app-internal candidate field name (`processType`)
 *  - [gatePolicy]    whether it is part of the Q0 must-pass gate
 *  - [comparator]    how an extracted value is matched to ground truth
 *
 * The split between [metadataKey] and [appFieldName] is the bug the critique
 * flagged: the corpus says `process`, the extractor emits `processType`. All
 * scoring, reporting, and gating MUST route through this mapping so a value is
 * never miscounted as missing/hallucinated due to a naming mismatch.
 */
data class FieldSpec(
    val metadataKey: String,
    val appFieldName: String,
    val gatePolicy: GatePolicy,
    val comparator: FieldComparator,
)

/**
 * The 14-field contract. [metadataKey] -> [appFieldName] mirrors
 * `MindlayerLlmInferenceProvider.fieldMapping`; a JVM unit test
 * (`FieldSpecTest`) asserts the two never drift apart.
 *
 * Gate selection (Q0 best case): the prominent, unambiguous identity fields a
 * studio-perfect label must always yield — name, roaster, origin, process,
 * roastLevel, weight. The remaining fields stay report-only because they carry
 * formatting/translation/ordering variance (altitude ranges, tasting-note
 * order, free-text producer names) that would make a 100% gate flaky without
 * adding much signal. Tune the gate by flipping [GatePolicy] here — this table
 * is the contract.
 */
object CorpusFields {

    val ALL: List<FieldSpec> = listOf(
        FieldSpec("name", "name", GatePolicy.GATE, FieldComparators.Text),
        FieldSpec("roaster", "roaster", GatePolicy.GATE, FieldComparators.Text),
        FieldSpec("origin", "origin", GatePolicy.GATE, FieldComparators.Origin),
        FieldSpec("region", "region", GatePolicy.REPORT_ONLY, FieldComparators.Text),
        FieldSpec("farm", "farm", GatePolicy.REPORT_ONLY, FieldComparators.Text),
        FieldSpec("variety", "variety", GatePolicy.REPORT_ONLY, FieldComparators.TokenSet),
        FieldSpec("process", "processType", GatePolicy.GATE, FieldComparators.Process),
        FieldSpec("roastLevel", "roastLevel", GatePolicy.GATE, FieldComparators.RoastLevel),
        FieldSpec("tastingNotes", "tastingNotes", GatePolicy.REPORT_ONLY, FieldComparators.NoteSet),
        FieldSpec("altitude", "altitude", GatePolicy.REPORT_ONLY, FieldComparators.NumericRange),
        FieldSpec("weight", "weight", GatePolicy.GATE, FieldComparators.Weight),
        FieldSpec("roastDate", "roastDate", GatePolicy.REPORT_ONLY, FieldComparators.IsoDate),
        FieldSpec("expiryDate", "expiryDate", GatePolicy.REPORT_ONLY, FieldComparators.IsoDate),
        FieldSpec("isDecaf", "isDecaf", GatePolicy.REPORT_ONLY, FieldComparators.Bool),
    )

    val byMetadataKey: Map<String, FieldSpec> = ALL.associateBy { it.metadataKey }
    val byAppFieldName: Map<String, FieldSpec> = ALL.associateBy { it.appFieldName }
    val metadataKeys: List<String> = ALL.map { it.metadataKey }
    val appFieldNames: List<String> = ALL.map { it.appFieldName }
    val gateFields: List<FieldSpec> = ALL.filter { it.gatePolicy == GatePolicy.GATE }
}

/**
 * Field-type-specific comparators. Deliberately NOT one global `contains`
 * rule — `"Colombia Decaf".contains("Colombia")` and `"not decaf"
 * .contains("decaf")` would silently pass wrong values and hollow out the
 * gate. Each comparator canonicalizes for its field type, then compares.
 */
object FieldComparators {

    /** Lowercase, strip accents, collapse whitespace, drop edge punctuation. */
    fun normalize(value: String): String {
        val deaccented = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return deaccented
            .lowercase()
            .replace(Regex("[\\u2018\\u2019\\u201c\\u201d]"), "")
            .replace(Regex("[^a-z0-9/+&.\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(value: String): Set<String> =
        normalize(value).split(Regex("[\\s,/&]+")).filter { it.isNotBlank() }.toSet()

    /** Free text (name, roaster, region, farm): exact-normalized, else token-containment partial. */
    val Text = FieldComparator { expected, actual ->
        val e = normalize(expected)
        val a = normalize(actual)
        when {
            e.isEmpty() || a.isEmpty() -> MatchLevel.NONE
            e == a -> MatchLevel.EXACT
            // Whole-token containment in either direction (e.g. "north axis" in
            // "north axis roastery"). Token-level, so "decaf" never matches
            // "not decaf" the way raw substring would.
            tokens(expected).isNotEmpty() &&
                (tokens(expected).all { it in tokens(actual) } ||
                    tokens(actual).all { it in tokens(expected) }) -> MatchLevel.PARTIAL
            else -> MatchLevel.NONE
        }
    }

    /** Comma/slash separated multi-value sets (variety). */
    val TokenSet = FieldComparator { expected, actual ->
        val e = tokens(expected)
        val a = tokens(actual)
        when {
            e.isEmpty() || a.isEmpty() -> MatchLevel.NONE
            e == a -> MatchLevel.EXACT
            else -> {
                val intersection = e.intersect(a).size.toDouble()
                val union = e.union(a).size.toDouble()
                if (union > 0 && intersection / union >= JACCARD_PARTIAL) MatchLevel.PARTIAL
                else MatchLevel.NONE
            }
        }
    }

    /**
     * Tasting notes as a set of comma-separated PHRASES (not word tokens).
     *
     * Word-token Jaccard is unfair to multi-word notes — "dark chocolate" vs
     * "chocolate" scores poorly because "dark" dilutes the overlap. This
     * comparator matches phrase-to-phrase and credits substring containment
     * ("chocolate" covers "dark chocolate"), then grades by recall of the
     * ground-truth notes with a light precision guard.
     *
     * It deliberately does NO cross-language matching: an untranslated note
     * ("mirtillo" for "blueberry") must still score wrong, because surfacing
     * non-English notes in the app is a real defect, not a formatting nicety.
     */
    val NoteSet = FieldComparator { expected, actual ->
        val e = phrases(expected)
        val a = phrases(actual)
        when {
            e.isEmpty() || a.isEmpty() -> MatchLevel.NONE
            else -> {
                val covered = e.count { gt -> a.any { act -> phrasesMatch(gt, act) } }
                val matchedActual = a.count { act -> e.any { gt -> phrasesMatch(gt, act) } }
                val recall = covered.toDouble() / e.size
                val precision = matchedActual.toDouble() / a.size
                when {
                    recall >= 1.0 && precision >= NOTE_PRECISION_OK -> MatchLevel.EXACT
                    recall >= NOTE_RECALL_PARTIAL -> MatchLevel.PARTIAL
                    else -> MatchLevel.NONE
                }
            }
        }
    }

    private fun aliasComparator(canonical: Map<String, String>) = FieldComparator { expected, actual ->
        fun canon(v: String): String = canonical[normalize(v)] ?: normalize(v)
        when {
            normalize(actual).isEmpty() -> MatchLevel.NONE
            canon(expected) == canon(actual) -> MatchLevel.EXACT
            canon(expected).isNotEmpty() && canon(actual).contains(canon(expected)) -> MatchLevel.PARTIAL
            else -> MatchLevel.NONE
        }
    }

    /** Country names with the corpus's de/cs/it/fr label translations. */
    val Origin = aliasComparator(
        buildAlias("ethiopia", "aethiopien", "etiopie", "etiopia", "ethiopie") +
            buildAlias("colombia", "kolumbien", "kolumbie", "colombie") +
            buildAlias("brazil", "brasilien", "brazilie", "brasile", "bresil") +
            buildAlias("kenya", "kenia", "kena") +
            buildAlias("rwanda", "ruanda") +
            buildAlias("guatemala") +
            buildAlias("peru", "perou"),
    )

    /** Process methods with multilingual label variants. */
    val Process = aliasComparator(
        buildAlias("washed", "lave", "gewaschen", "promyte", "lavato", "lavata") +
            buildAlias("natural", "naturale", "naturel", "naturlich", "prirodni", "naturally") +
            buildAlias("honey", "miel", "miele") +
            buildAlias("sugarcane ea decaf", "ethyl acetate decaf", "ea decaf"),
    )

    /** Roast / brew style with multilingual label variants. */
    val RoastLevel = aliasComparator(
        buildAlias("filter", "filtre", "filtr", "filterrostung") +
            buildAlias("light", "hell", "legere", "svetla", "leggera") +
            buildAlias("espresso") +
            buildAlias("omni"),
    )

    /** Altitude ranges: compare the set of numbers (ignores "m"/"masl"/dash style). */
    val NumericRange = FieldComparator { expected, actual ->
        val e = numbers(expected)
        val a = numbers(actual)
        when {
            e.isEmpty() || a.isEmpty() -> MatchLevel.NONE
            e == a -> MatchLevel.EXACT
            e.intersect(a).isNotEmpty() -> MatchLevel.PARTIAL
            else -> MatchLevel.NONE
        }
    }

    /** Weight: compare integer grams parsed from the value. */
    val Weight = FieldComparator { expected, actual ->
        val e = numbers(expected).firstOrNull()
        val a = numbers(actual).firstOrNull()
        if (e != null && a != null && e == a) MatchLevel.EXACT else MatchLevel.NONE
    }

    /** Dates: compare the set of numeric components (handles 2026-05-28 vs 28.05.2026). */
    val IsoDate = FieldComparator { expected, actual ->
        val e = numbers(expected).toSet()
        val a = numbers(actual).toSet()
        when {
            e.isEmpty() || a.isEmpty() -> MatchLevel.NONE
            // Component sets are order-insensitive, so YYYY-MM-DD and DD.MM.YYYY
            // for the same day compare equal.
            e == a -> MatchLevel.EXACT
            // Day + month agree but the year differs (or is 2-digit) -> partial.
            e.intersect(a).size >= 2 -> MatchLevel.PARTIAL
            else -> MatchLevel.NONE
        }
    }

    /** Boolean isDecaf. */
    val Bool = FieldComparator { expected, actual ->
        val e = expected.trim().lowercase()
        val a = actual.trim().lowercase()
        if ((e == "true" || e == "false") && e == a) MatchLevel.EXACT else MatchLevel.NONE
    }

    private fun numbers(value: String): List<Int> =
        Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()

    /** Split a tasting-note string into normalized comma/slash-separated phrases. */
    private fun phrases(value: String): List<String> =
        value.split(Regex("[,/;]+")).map { normalize(it) }.filter { it.isNotBlank() }

    /**
     * Two notes match when they are equal, one contains the other as a whole
     * phrase ("chocolate" in "dark chocolate"), or every word of the shorter
     * note appears in the longer. Pure string logic — never cross-language.
     */
    private fun phrasesMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true
        val aw = a.split(' ').filter { it.isNotBlank() }.toSet()
        val bw = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (aw.isEmpty() || bw.isEmpty()) return false
        val shorter = if (aw.size <= bw.size) aw else bw
        val longer = if (aw.size <= bw.size) bw else aw
        return shorter.all { it in longer }
    }

    private fun buildAlias(canonical: String, vararg variants: String): Map<String, String> =
        (listOf(canonical) + variants).associateWith { canonical }

    private const val JACCARD_PARTIAL = 0.34
    private const val NOTE_PRECISION_OK = 0.6
    private const val NOTE_RECALL_PARTIAL = 0.5
}
