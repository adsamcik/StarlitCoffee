package com.adsamcik.starlitcoffee.scan.model

/**
 * Structured telemetry for a single scan session.
 * Emitted as JSON log at scan end for debugging and analytics.
 */
data class ScanTelemetry(
    val sessionDurationMs: Long,
    val framesProcessed: Int,
    val framesRejected: Int,
    val goldenFrameCount: Int,
    val bestGoldenFrameScore: Float,
    val consensusCycles: Int,
    val fieldsResolved: Int,
    val fieldsTotal: Int,
    val fieldSources: Map<String, String>,
    val convergenceTimeMs: Map<String, Long>,
    val llmEscalated: Boolean,
    val llmFieldsRequested: Set<String>,
    val llmLatencyMs: Long?,
    val llmSuccess: Boolean?,
    val llmTokensUsed: Int?,
    val sideFlipDetected: Boolean,
    val qualityRelaxationTriggered: Boolean,
    val scanOutcome: String,
    val perfStats: Map<String, com.adsamcik.starlitcoffee.scan.observability.PerfStatsSnapshot>? = null,
) {
    fun toJson(): String = buildString {
        append('{')
        append("\"sessionDurationMs\":$sessionDurationMs")
        append(",\"framesProcessed\":$framesProcessed")
        append(",\"framesRejected\":$framesRejected")
        append(",\"goldenFrameCount\":$goldenFrameCount")
        append(",\"bestGoldenFrameScore\":$bestGoldenFrameScore")
        append(",\"consensusCycles\":$consensusCycles")
        append(",\"fieldsResolved\":$fieldsResolved")
        append(",\"fieldsTotal\":$fieldsTotal")
        append(",\"fieldSources\":")
        appendJsonMap(fieldSources)
        append(",\"convergenceTimeMs\":")
        appendJsonLongMap(convergenceTimeMs)
        append(",\"llmEscalated\":$llmEscalated")
        append(",\"llmFieldsRequested\":")
        appendJsonStringSet(llmFieldsRequested)
        append(",\"llmLatencyMs\":${llmLatencyMs ?: "null"}")
        append(",\"llmSuccess\":${llmSuccess ?: "null"}")
        append(",\"llmTokensUsed\":${llmTokensUsed ?: "null"}")
        append(",\"sideFlipDetected\":$sideFlipDetected")
        append(",\"qualityRelaxationTriggered\":$qualityRelaxationTriggered")
        append(",\"scanOutcome\":\"${escapeJson(scanOutcome)}\"")
        if (perfStats != null) {
            append(",\"perfStats\":{")
            perfStats.entries.forEachIndexed { i, (name, snap) ->
                if (i > 0) append(',')
                append("\"${escapeJson(name)}\":")
                append("{\"count\":${snap.count}")
                append(",\"min\":${snap.min}")
                append(",\"max\":${snap.max}")
                append(",\"avg\":${snap.avg}")
                append(",\"latest\":${snap.latest}")
                if (snap.p95 != null) append(",\"p95\":${snap.p95}")
                append('}')
            }
            append('}')
        }
        append('}')
    }

    private fun StringBuilder.appendJsonMap(map: Map<String, String>) {
        append('{')
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append("\"${escapeJson(k)}\":\"${escapeJson(v)}\"")
        }
        append('}')
    }

    private fun StringBuilder.appendJsonLongMap(map: Map<String, Long>) {
        append('{')
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append("\"${escapeJson(k)}\":$v")
        }
        append('}')
    }

    private fun StringBuilder.appendJsonStringSet(set: Set<String>) {
        append('[')
        set.forEachIndexed { i, v ->
            if (i > 0) append(',')
            append("\"${escapeJson(v)}\"")
        }
        append(']')
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
