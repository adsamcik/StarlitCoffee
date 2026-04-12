package com.adsamcik.starlitcoffee.scan.benchmark

enum class FieldScoreType { EXACT, SEMANTIC, WRONG, MISSING, HALLUCINATED, CORRECT_NULL }

data class FieldScore(
    val fieldName: String,
    val scoreType: FieldScoreType,
    val extracted: String?,
    val groundTruth: String?,
    val details: String? = null,
)

data class SessionScore(
    val bagId: String,
    val fieldScores: List<FieldScore>,
    val outcome: SessionOutcome,
    val correctCount: Int,
    val totalOnLabel: Int,
    val hallucinatedCount: Int,
)

enum class SessionOutcome { COMPLETE, PARTIAL, FAILED }

data class BatchScore(
    val sessionScores: List<SessionScore>,
    val overallSuccessRate: Float,
    val overallHallucinationRate: Float,
    val perFieldAccuracy: Map<String, Float>,
    val verdict: BenchmarkVerdict,
)

/** SHIP: ≥70% session success, ≤20% hallucination.  ITERATE: 50-70% success.  RETHINK: <50% success or >30% hallucination. */
enum class BenchmarkVerdict { SHIP, ITERATE, RETHINK }
