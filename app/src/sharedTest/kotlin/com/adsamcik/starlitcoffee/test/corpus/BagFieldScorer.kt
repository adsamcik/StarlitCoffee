package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.Serializable

/**
 * Outcome of scoring one field for one bag against ground truth.
 *
 * Designed so the aggregate report can compute the eval vocabulary the team
 * already uses (precision / recall / abstention / hallucination) plus exact
 * accuracy — see [QualityReport].
 */
@Serializable
enum class FieldOutcome(val symbol: String, val label: String) {
    /** Extracted value matches ground truth after canonicalization. */
    EXACT("\u2713", "exact"),

    /** Overlaps ground truth but not a full match (token overlap, partial date). */
    PARTIAL("\u2248", "partial"),

    /** Extracted a value that disagrees with a visible ground-truth value. */
    WRONG("\u2717", "wrong"),

    /** Ground truth is visible but the model produced nothing. */
    MISSING("?", "missing"),

    /** Ground truth is not_visible but the model invented a value (hallucination). */
    HALLUCINATED("+", "hallucinated"),

    /** Ground truth is not_visible and the model correctly abstained. */
    ABSTAINED("\u00b7", "abstained"),
}

/** Per-field score for a single bag. */
@Serializable
data class FieldScore(
    val metadataKey: String,
    val expected: String?,
    val actual: String?,
    val outcome: FieldOutcome,
)

/** All field scores for one bag, plus its tier. */
@Serializable
data class BagScore(
    val bagId: String,
    val tier: String?,
    val fields: List<FieldScore>,
)

/** Result of the Q0 best-case gate for one bag. */
data class GateResult(
    val bagId: String,
    val tier: String?,
    val passed: Boolean,
    val failures: List<GateFailure>,
)

data class GateFailure(
    val metadataKey: String,
    val expected: String?,
    val actual: String?,
    val outcome: FieldOutcome,
)

/**
 * Pure scoring of model extractions against the synthetic corpus ground truth.
 *
 * Android-free and deterministic so the classification rules are exercised by
 * fast JVM unit tests, while the instrumented tests feed it real on-device
 * model output. Extractions are keyed by the APP field name (`processType`);
 * ground truth is keyed by the METADATA key (`process`); [CorpusFields] bridges
 * the two.
 */
object BagFieldScorer {

    /**
     * Classify one field.
     *
     * @param spec        field contract (mapping + comparator)
     * @param expected    ground-truth value, or null when not_visible/absent
     * @param actual      extracted value, or null/blank when the model abstained
     */
    fun scoreField(spec: FieldSpec, expected: String?, actual: String?): FieldOutcome {
        val hasExpected = !expected.isNullOrBlank()
        val hasActual = !actual.isNullOrBlank()
        return when {
            !hasExpected && !hasActual -> FieldOutcome.ABSTAINED
            !hasExpected && hasActual -> FieldOutcome.HALLUCINATED
            hasExpected && !hasActual -> FieldOutcome.MISSING
            else -> when (spec.comparator.compare(expected!!, actual!!)) {
                MatchLevel.EXACT -> FieldOutcome.EXACT
                MatchLevel.PARTIAL -> FieldOutcome.PARTIAL
                MatchLevel.NONE -> FieldOutcome.WRONG
            }
        }
    }

    /**
     * Score every field of [bag]. [extractedByAppField] maps the app-internal
     * candidate field name (e.g. `processType`) to the extracted value.
     */
    fun scoreBag(
        bag: CoffeeBagFixture,
        extractedByAppField: Map<String, String?>,
    ): BagScore {
        val fieldScores = CorpusFields.ALL.map { spec ->
            val expected = bag.groundTruth(spec.metadataKey)
            val actual = extractedByAppField[spec.appFieldName]?.takeIf { it.isNotBlank() }
            FieldScore(
                metadataKey = spec.metadataKey,
                expected = expected,
                actual = actual,
                outcome = scoreField(spec, expected, actual),
            )
        }
        return BagScore(bagId = bag.id, tier = bag.captureTier, fields = fieldScores)
    }

    /**
     * Evaluate the Q0 best-case gate for one bag.
     *
     * A gate field passes when, for a VISIBLE ground-truth value, the outcome is
     * [FieldOutcome.EXACT] (canonicalized). PARTIAL/WRONG/MISSING fail the gate.
     * Gate fields whose ground truth is not_visible are skipped (no false
     * negative for fields the bag doesn't carry). Non-gate fields never affect
     * the gate — they live in the quality report only.
     */
    fun evaluateGate(score: BagScore): GateResult {
        val gateKeys = CorpusFields.gateFields.map { it.metadataKey }.toSet()
        val failures = score.fields
            .filter { it.metadataKey in gateKeys && !it.expected.isNullOrBlank() }
            .filter { it.outcome != FieldOutcome.EXACT }
            .map { GateFailure(it.metadataKey, it.expected, it.actual, it.outcome) }
        return GateResult(
            bagId = score.bagId,
            tier = score.tier,
            passed = failures.isEmpty(),
            failures = failures,
        )
    }
}
