package com.adsamcik.starlitcoffee.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.test.corpus.QualityReport
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reduces the per-bag [FullPipelineBagRecord]s written by
 * [FullPipelineBenchmarkTest] (one process per bag) into two comparable
 * [QualityReport]s — text-only vs the full text->vision->combine pipeline — plus
 * a delta summary so the vision/combine contribution is visible at a glance.
 *
 * Run last, after the per-bag loop, in its own process:
 * `scanBenchmark -PfullPipeline` invokes it automatically. Reads
 * `full-pipeline-records.jsonl`, writes
 * `full-pipeline-text-only.{txt,json}`, `full-pipeline-combined.{txt,json}`,
 * and `full-pipeline-delta.txt`.
 */
@RunWith(AndroidJUnit4::class)
class FullPipelineAggregateTest {

    @Test
    fun aggregateFullPipelineRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = FullPipelineBenchmarkTest.recordsFile(context)
        assumeTrue(
            "No full-pipeline records at ${file.absolutePath} — run the per-bag benchmark first " +
                "(scanBenchmark -PfullPipeline).",
            file.isFile && file.length() > 0,
        )

        val records = file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { json.decodeFromString<FullPipelineBagRecord>(line) }.getOrNull() }
        assertTrue("Records file present but no parseable records.", records.isNotEmpty())

        val textReport = QualityReport.from("full-pipeline-text-only", records.map { it.textScore })
        val combinedReport = QualityReport.from("full-pipeline-combined", records.map { it.combinedScore })

        QualityTestSupport.writeReport(context, textReport, "full-pipeline-text-only")
        QualityTestSupport.writeReport(context, combinedReport, "full-pipeline-combined")

        val delta = buildDeltaSummary(records.size, textReport, combinedReport)
        val deltaFile = java.io.File(CorpusFixture.fixturesDir(context), "full-pipeline-delta.txt")
        deltaFile.writeText(delta)
        delta.lineSequence().forEach { Log.i(CorpusFixture.BENCHMARK_TAG, it) }
    }

    private fun buildDeltaSummary(
        bagCount: Int,
        text: QualityReport,
        combined: QualityReport,
    ): String = buildString {
        appendLine("===== FULL-PIPELINE DELTA (text-only -> text+vision+combine) =====")
        appendLine("Bags: $bagCount")
        appendLine(
            "Overall exactAcc:    ${pct(text.overall.exactAccuracy)} -> ${pct(combined.overall.exactAccuracy)}  " +
                "(${signed(text.overall.exactAccuracy, combined.overall.exactAccuracy)})",
        )
        appendLine(
            "Overall recall:      ${pct(text.overall.recall)} -> ${pct(combined.overall.recall)}  " +
                "(${signed(text.overall.recall, combined.overall.recall)})",
        )
        appendLine(
            "Overall decisionAcc: ${pct(text.overall.decisionAccuracy)} -> ${pct(combined.overall.decisionAccuracy)}  " +
                "(${signed(text.overall.decisionAccuracy, combined.overall.decisionAccuracy)})",
        )
        appendLine(
            "Overall halluc:      ${pct(text.overall.hallucinationRate)} -> ${pct(combined.overall.hallucinationRate)}  " +
                "(${signed(text.overall.hallucinationRate, combined.overall.hallucinationRate)})",
        )
        appendLine()
        appendLine("Per-field exactAcc (text -> combined, delta):")
        val combinedByField = combined.perField.associateBy { it.field }
        for (tf in text.perField) {
            val cf = combinedByField[tf.field] ?: continue
            appendLine(
                "  ${tf.field.padEnd(13)} ${pct(tf.metrics.exactAccuracy)} -> ${pct(cf.metrics.exactAccuracy)}  " +
                    "(${signed(tf.metrics.exactAccuracy, cf.metrics.exactAccuracy)})",
            )
        }
    }

    private fun pct(value: Double?): String = if (value == null) "n/a" else "${(value * 100).toInt()}%"

    private fun signed(from: Double?, to: Double?): String {
        if (from == null || to == null) return "n/a"
        val deltaPts = ((to - from) * 100).toInt()
        return if (deltaPts >= 0) "+${deltaPts}pp" else "${deltaPts}pp"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
