package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.scan.observability.ScanSessionSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun ScanHistoryDialog(
    sessions: List<ScanSessionSummary>,
    onDismiss: () -> Unit,
    onShareSession: (ScanSessionSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan History") },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    text = "No scan sessions recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sessions, key = { it.sessionId }) { session ->
                        SessionCard(
                            session = session,
                            onShare = { onShareSession(session) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SessionCard(
    session: ScanSessionSummary,
    onShare: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timestamp = remember(session.startedAt) { dateFormat.format(Date(session.startedAt)) }
    val durationSec = remember(session.durationMs) {
        String.format(Locale.US, "%.1f", session.durationMs / 1000.0)
    }
    val outcomeColor = when (session.outcome) {
        "complete" -> Color(0xFF2E7D32)
        "partial" -> Color(0xFFF9A825)
        "cancelled" -> Color(0xFF757575)
        "error" -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Session: $timestamp",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Outcome: ${session.outcome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = outcomeColor,
                )
                Text(
                    text = "Duration: ${durationSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Fields: ${session.fieldsResolved}/${session.fieldsTotal}  " +
                    "LLM: ${if (session.llmFired) "✓ (${session.llmLatencyMs}ms, ${session.llmTokensUsed} tok)" else "✗"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Frames: ${session.framesProcessed} processed, ${session.framesRejected} rejected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Golden: ${session.goldenFrameCount} frames, best score: ${
                    String.format(Locale.US, "%.1f", session.bestGoldenFrameScore)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (session.failureReason != null) {
                Text(
                    text = "Failure: ${session.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (session.perfJson != null) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲ Collapse Performance" else "▼ Expand Performance")
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    PerfSection(perfJson = session.perfJson)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share This Session")
            }
        }
    }
}

@Composable
private fun PerfSection(perfJson: String) {
    val perfMap = remember(perfJson) { parsePerfJson(perfJson) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Component Timings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (perfMap.isEmpty()) {
                Text(
                    text = "No timing data available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                perfMap.forEach { (name, stats) ->
                    Text(
                        text = buildString {
                            append(name.padEnd(14))
                            append("avg=${formatMs(stats.avg)}")
                            if (stats.p95 != null) {
                                append("  p95=${formatMs(stats.p95)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class PerfEntry(
    val avg: Float,
    val p95: Float?,
    val min: Float?,
    val max: Float?,
    val latest: Float?,
    val count: Int?,
)

private fun parsePerfJson(raw: String): Map<String, PerfEntry> {
    return try {
        val root = json.parseToJsonElement(raw).jsonObject
        root.entries.associate { (key, element) ->
            val obj = element.jsonObject
            key to PerfEntry(
                avg = obj["avg"]?.jsonPrimitive?.float ?: 0f,
                p95 = obj["p95"]?.jsonPrimitive?.floatOrNull,
                min = obj["min"]?.jsonPrimitive?.floatOrNull,
                max = obj["max"]?.jsonPrimitive?.floatOrNull,
                latest = obj["latest"]?.jsonPrimitive?.floatOrNull,
                count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun formatMs(value: Float): String =
    if (value >= 1000f) {
        String.format(Locale.US, "%.1fs", value / 1000f)
    } else {
        String.format(Locale.US, "%.0fms", value)
    }

fun formatSessionForShare(session: ScanSessionSummary): String = buildString {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    appendLine("=== Scan Session Report ===")
    appendLine()
    appendLine("Session ID: ${session.sessionId}")
    appendLine("Time: ${dateFormat.format(Date(session.startedAt))} → ${dateFormat.format(Date(session.endedAt))}")
    appendLine("Duration: ${String.format(Locale.US, "%.1f", session.durationMs / 1000.0)}s")
    appendLine("Outcome: ${session.outcome}")
    if (session.failureReason != null) {
        appendLine("Failure: ${session.failureReason}")
    }
    appendLine()
    appendLine("--- Fields ---")
    appendLine("Resolved: ${session.fieldsResolved} / ${session.fieldsTotal}")
    appendLine()
    appendLine("--- Frames ---")
    appendLine("Processed: ${session.framesProcessed}")
    appendLine("Rejected: ${session.framesRejected}")
    appendLine("Golden: ${session.goldenFrameCount} (best score: ${String.format(Locale.US, "%.1f", session.bestGoldenFrameScore)})")
    appendLine()
    appendLine("--- LLM ---")
    if (session.llmFired) {
        appendLine("Status: fired (${session.llmCallCount} calls)")
        appendLine("Latency: ${session.llmLatencyMs}ms")
        appendLine("Tokens: ${session.llmTokensUsed}")
    } else {
        appendLine("Status: not fired")
    }
    appendLine()
    appendLine("--- Device ---")
    appendLine("Model: ${session.deviceModel}")
    appendLine("App version: ${session.appVersion}")

    if (session.perfJson != null) {
        appendLine()
        appendLine("--- Performance Timings ---")
        val perfMap = parsePerfJson(session.perfJson)
        if (perfMap.isNotEmpty()) {
            perfMap.forEach { (name, stats) ->
                val line = buildString {
                    append("$name: avg=${formatMs(stats.avg)}")
                    stats.p95?.let { append(", p95=${formatMs(it)}") }
                    stats.min?.let { append(", min=${formatMs(it)}") }
                    stats.max?.let { append(", max=${formatMs(it)}") }
                    stats.count?.let { append(", n=$it") }
                }
                appendLine(line)
            }
        } else {
            appendLine("(raw) ${session.perfJson}")
        }
    }
}
