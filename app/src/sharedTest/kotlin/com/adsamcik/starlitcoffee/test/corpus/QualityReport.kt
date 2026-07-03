package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-field outcome tallies and the derived quality metrics.
 *
 * Metric definitions (denominators guarded; a rate is `null` when undefined):
 *  - visible        = exact + partial + wrong + missing   (GT was a value)
 *  - notVisible     = hallucinated + abstained            (GT was not_visible)
 *  - produced       = exact + partial + wrong + hallucinated (model emitted something)
 *  - exactAccuracy  = exact / total
 *  - exactAccuracyCeiling = visible / total  (max exactAccuracy a PERFECT extractor
 *      could reach: correct abstentions on absent fields are never "exact", so they
 *      cap the score below 100% — read exactAccuracy against THIS, not 100%)
 *  - decisionAccuracy = (exact + abstained) / total  (the honest "did the right
 *      thing" rate: a correct extraction OR a correct abstention both count as a win)
 *  - practicalAccuracy = (exact + partial + abstained) / total  (most forgiving
 *      honest rate: an acceptable partial — e.g. a superset name or a high-recall
 *      tasting-note list — also counts. Read next to exactAccuracy to see how much
 *      of the gap is strict-matching artifact vs. genuine model error.)
 *  - recall         = exact / visible                     (strict, exact-only)
 *  - softRecall     = (exact + 0.5*partial) / visible     (graduated: partial half-credit)
 *  - partialRecall  = (exact + partial) / visible         (partial full-credit)
 *  - precision      = exact / produced
 *  - abstentionRate = abstained / notVisible
 *  - hallucinationRate = hallucinated / notVisible
 */
@Serializable
data class FieldMetrics(
    val exact: Int = 0,
    val partial: Int = 0,
    val wrong: Int = 0,
    val missing: Int = 0,
    val hallucinated: Int = 0,
    val abstained: Int = 0,
) {
    val total: Int get() = exact + partial + wrong + missing + hallucinated + abstained
    val visible: Int get() = exact + partial + wrong + missing
    val notVisible: Int get() = hallucinated + abstained
    val produced: Int get() = exact + partial + wrong + hallucinated

    val exactAccuracy: Double? get() = ratio(exact, total)

    /**
     * Max exactAccuracy a *perfect* extractor could reach. Correct abstentions
     * on absent fields are never scored EXACT, so they hold the ceiling below
     * 100%. exactAccuracy should always be read against this, not against 100%.
     */
    val exactAccuracyCeiling: Double? get() = ratio(visible, total)

    /**
     * Honest "did the right thing" rate over all cells: a correct extraction
     * (EXACT) OR a correct abstention (ABSTAINED) both count as a win. Unlike
     * exactAccuracy this can reach 100%.
     */
    val decisionAccuracy: Double? get() = ratio(exact + abstained, total)

    /**
     * Most forgiving honest rate over all cells: a correct value (EXACT), an
     * acceptable partial (PARTIAL), OR a correct abstention all count. For the
     * free-text fields (name, tastingNotes) whose strict EXACT threshold is
     * deliberately harsh, this reveals how much of the exactAccuracy gap is a
     * strict-matching artifact rather than a genuine model defect.
     */
    val practicalAccuracy: Double? get() = ratio(exact + partial + abstained, total)

    val recall: Double? get() = ratio(exact, visible)

    /**
     * Graduated recall over visible fields: EXACT full credit, PARTIAL half.
     * The truth for a free-text field sits between [recall] (partial=0) and
     * [partialRecall] (partial=1); this brackets it with a single number.
     */
    val softRecall: Double? get() = if (visible <= 0) null else (exact + 0.5 * partial) / visible

    val partialRecall: Double? get() = ratio(exact + partial, visible)
    val precision: Double? get() = ratio(exact, produced)
    val abstentionRate: Double? get() = ratio(abstained, notVisible)
    val hallucinationRate: Double? get() = ratio(hallucinated, notVisible)

    operator fun plus(other: FieldMetrics) = FieldMetrics(
        exact + other.exact,
        partial + other.partial,
        wrong + other.wrong,
        missing + other.missing,
        hallucinated + other.hallucinated,
        abstained + other.abstained,
    )

    companion object {
        fun of(outcome: FieldOutcome): FieldMetrics = when (outcome) {
            FieldOutcome.EXACT -> FieldMetrics(exact = 1)
            FieldOutcome.PARTIAL -> FieldMetrics(partial = 1)
            FieldOutcome.WRONG -> FieldMetrics(wrong = 1)
            FieldOutcome.MISSING -> FieldMetrics(missing = 1)
            FieldOutcome.HALLUCINATED -> FieldMetrics(hallucinated = 1)
            FieldOutcome.ABSTAINED -> FieldMetrics(abstained = 1)
        }

        private fun ratio(num: Int, den: Int): Double? =
            if (den <= 0) null else num.toDouble() / den.toDouble()
    }
}

@Serializable
data class FieldReport(val field: String, val gated: Boolean, val metrics: FieldMetrics)

@Serializable
data class TierReport(val tier: String, val bags: Int, val metrics: FieldMetrics)

@Serializable
data class BagReport(val bagId: String, val tier: String?, val metrics: FieldMetrics)

/**
 * Aggregated quality report over a set of [BagScore]s. NOT pass/fail — it
 * carries the accuracy numbers. Serializes to JSON (machine-readable) and a
 * compact text table (human-readable logcat / file).
 */
@Serializable
data class QualityReport(
    val label: String,
    val bagsEvaluated: Int,
    val overall: FieldMetrics,
    val perField: List<FieldReport>,
    val perTier: List<TierReport>,
    val perBag: List<BagReport>,
) {
    fun toJson(): String = prettyJson.encodeToString(this)

    fun toText(): String = buildString {
        appendLine("===== QUALITY REPORT: $label =====")
        appendLine("Bags evaluated: $bagsEvaluated")
        appendLine(
            "Overall: exactAcc=${pct(overall.exactAccuracy)}/${pct(overall.exactAccuracyCeiling)}ceil " +
                "decisionAcc=${pct(overall.decisionAccuracy)} practicalAcc=${pct(overall.practicalAccuracy)} " +
                "recall=${pct(overall.recall)} softRecall=${pct(overall.softRecall)} " +
                "partialRecall=${pct(overall.partialRecall)} precision=${pct(overall.precision)} " +
                "halluc=${pct(overall.hallucinationRate)} abstention=${pct(overall.abstentionRate)}",
        )
        appendLine(
            "  (exactAcc tops out at its ceiling, not 100%, because correct abstentions on absent " +
                "fields are never \"exact\". decisionAcc counts a right value OR a right blank; " +
                "practicalAcc also credits an acceptable partial. For free-text fields (name, " +
                "tastingNotes) read exactAcc against softRecall/partialRecall to separate strict-" +
                "matching artifact from real model error.)",
        )
        appendLine(
            "  recall 95% CI: \u00b1${ciPct(overall.exact, overall.visible)}pp " +
                "(n=${overall.visible} visible cells) \u2014 treat deltas smaller than the CI as noise.",
        )
        appendLine()
        appendLine("Per-field (\u2713 exact \u2248 partial \u2717 wrong ? missing + halluc \u00b7 abstain):")
        for (f in perField) {
            val m = f.metrics
            appendLine(
                "  ${gateMark(f.gated)}${f.field.padEnd(13)} " +
                    "\u2713${m.exact} \u2248${m.partial} \u2717${m.wrong} ?${m.missing} " +
                    "+${m.hallucinated} \u00b7${m.abstained}   " +
                    "exactAcc=${pct(m.exactAccuracy)} recall=${pct(m.recall)} " +
                    "softRecall=${pct(m.softRecall)} partialRecall=${pct(m.partialRecall)} " +
                    "halluc=${pct(m.hallucinationRate)}",
            )
        }
        appendLine()
        appendLine("Per-tier:")
        for (t in perTier) {
            appendLine(
                "  ${t.tier.padEnd(4)} bags=${t.bags} exactAcc=${pct(t.metrics.exactAccuracy)} " +
                    "decisionAcc=${pct(t.metrics.decisionAccuracy)} practicalAcc=${pct(t.metrics.practicalAccuracy)} " +
                    "recall=${pct(t.metrics.recall)}\u00b1${ciPct(t.metrics.exact, t.metrics.visible)}pp " +
                    "partialRecall=${pct(t.metrics.partialRecall)} " +
                    "hallucination=${pct(t.metrics.hallucinationRate)}",
            )
        }
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

        /** Build the aggregate report from per-bag scores. */
        fun from(label: String, scores: List<BagScore>): QualityReport {
            val gateKeys = CorpusFields.gateFields.map { it.metadataKey }.toSet()

            val perField = CorpusFields.metadataKeys.map { key ->
                val metrics = scores
                    .flatMap { it.fields }
                    .filter { it.metadataKey == key }
                    .fold(FieldMetrics()) { acc, fs -> acc + FieldMetrics.of(fs.outcome) }
                FieldReport(field = key, gated = key in gateKeys, metrics = metrics)
            }

            val overall = perField.fold(FieldMetrics()) { acc, fr -> acc + fr.metrics }

            val perTier = scores.groupBy { it.tier ?: "?" }.toSortedMap().map { (tier, tierScores) ->
                val metrics = tierScores
                    .flatMap { it.fields }
                    .fold(FieldMetrics()) { acc, fs -> acc + FieldMetrics.of(fs.outcome) }
                TierReport(tier = tier, bags = tierScores.size, metrics = metrics)
            }

            val perBag = scores.map { bag ->
                val metrics = bag.fields.fold(FieldMetrics()) { acc, fs -> acc + FieldMetrics.of(fs.outcome) }
                BagReport(bagId = bag.bagId, tier = bag.tier, metrics = metrics)
            }

            return QualityReport(
                label = label,
                bagsEvaluated = scores.size,
                overall = overall,
                perField = perField,
                perTier = perTier,
                perBag = perBag,
            )
        }

        private fun pct(value: Double?): String =
            if (value == null) "n/a" else "${(value * 100).toInt()}%"

        /** 95% Wilson CI half-width (± percentage points) for successes/total. */
        private fun ciPct(successes: Int, total: Int): String {
            val hw = Stats.wilson95HalfWidthPct(successes, total) ?: return "n/a"
            return hw.toInt().toString()
        }

        private fun gateMark(gated: Boolean): String = if (gated) "* " else "  "
    }
}
