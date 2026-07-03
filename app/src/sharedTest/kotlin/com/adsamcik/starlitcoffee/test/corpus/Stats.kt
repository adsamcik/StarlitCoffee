package com.adsamcik.starlitcoffee.test.corpus

import kotlin.math.sqrt

/**
 * Small statistics helpers for reading benchmark rates honestly.
 *
 * The synthetic corpus (and any real-bag validation set) has small per-field /
 * per-tier sample sizes, so a raw percentage is meaningless without a
 * confidence interval. A Wilson score interval is preferred over the naive
 * Wald interval because it behaves well at small n and near 0/1, where Wald is
 * badly miscalibrated. Use the half-width to decide whether a claimed delta
 * (e.g. "+10pp") is real or within noise at the current sample size.
 */
object Stats {

    /** 95% two-sided z. */
    private const val Z95 = 1.959963984540054

    /**
     * 95% Wilson score interval for [successes]/[total] as (lower, upper) in
     * 0..1, or null when [total] <= 0.
     */
    fun wilson95(successes: Int, total: Int): Pair<Double, Double>? {
        if (total <= 0) return null
        val n = total.toDouble()
        val p = successes.coerceIn(0, total).toDouble() / n
        val z = Z95
        val z2 = z * z
        val denom = 1.0 + z2 / n
        val center = (p + z2 / (2 * n)) / denom
        val margin = z * sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / denom
        return (center - margin).coerceIn(0.0, 1.0) to (center + margin).coerceIn(0.0, 1.0)
    }

    /**
     * Half-width of the 95% Wilson interval in percentage points (0..100), a
     * single "± this many pp" figure for a rate. Null when [total] <= 0.
     */
    fun wilson95HalfWidthPct(successes: Int, total: Int): Double? {
        val (lo, hi) = wilson95(successes, total) ?: return null
        return (hi - lo) / 2.0 * 100.0
    }
}
