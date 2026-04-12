package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.scan.model.AccumulatedEvidence
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.observability.PerfStats
import com.adsamcik.starlitcoffee.viewmodel.LiveScanViewModel

private val MONO = FontFamily.Monospace
private val LABEL_SIZE = 11.sp
private val GREEN = Color(0xFF5ED18D)
private val YELLOW = Color(0xFFFFD166)
private val RED = Color(0xFFFF6B6B)

/**
 * Real-time performance debug overlay for the live scan screen.
 * Shows perf timings, LLM status, and per-field accumulation state.
 */
@Composable
fun ScanDebugOverlay(
    perfStats: Map<String, PerfStats>,
    evidence: AccumulatedEvidence?,
    debugInfo: LiveScanViewModel.DebugInfo,
    rejectionReason: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier.width(240.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header
            MonoText("─── SCAN DEBUG ───", color = YELLOW, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            // Frame timings
            val frameIntervalAvg = perfStats["frame_interval_ms"]?.avg
            val fps = if (frameIntervalAvg != null && frameIntervalAvg > 0f) {
                1000f / frameIntervalAvg
            } else {
                null
            }
            PerfRow("FPS", fps?.fmt(1) ?: "—", "Frame", perfStats["frame_interval_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—")
            PerfRow("OCR", perfStats["ocr_ms"]?.avg?.fmt(0)?.plus("ms") ?: "—", "Extract", perfStats["extract_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—")
            PerfRow("Quality", perfStats["quality_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—", "JPEG", perfStats["jpeg_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—")
            PerfRow("Consensus", perfStats["consensus_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—", "Integ", perfStats["integration_ms"]?.avg?.fmt(1)?.plus("ms") ?: "—")

            // Blur & score from latest values
            val blur = perfStats["blur_score"]?.latest
            val score = perfStats["quality_score"]?.latest
            if (blur != null || score != null) {
                PerfRow("Blur", blur?.fmt(1) ?: "—", "Score", score?.fmt(1) ?: "—")
            }

            SectionDivider()

            // LLM / Connection status
            val connLabel = if (debugInfo.llmAvailable) "● Connected" else "○ Unavailable"
            val connColor = if (debugInfo.llmAvailable) GREEN else Color.White.copy(alpha = 0.5f)
            MonoText("Mindlayer: $connLabel", color = connColor)

            val latencyStr = debugInfo.lastLlmLatencyMs?.let { "${it / 1000f}s" } ?: "—"
            MonoText("LLM calls: ${debugInfo.llmCallCount}  Latency: $latencyStr")

            SectionDivider()

            // Evidence summary
            val ev = evidence ?: AccumulatedEvidence.EMPTY
            val totalFields = ev.fields.size
            val resolved = ev.resolvedFieldCount
            MonoText("Fields: $resolved/$totalFields  Golden: ${debugInfo.goldenFrameCount}")
            MonoText("Frames: ${debugInfo.frameIndex}  Best: ${debugInfo.bestGoldenFrameScore.fmt(1)}")

            rejectionReason?.let {
                MonoText("Reject: $it", color = RED)
            }

            // Per-field status breakdown
            if (ev.fields.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                ev.fields.values.forEach { field ->
                    val statusLabel = when (field.status) {
                        FieldStatus.LOCKED, FieldStatus.USER_LOCKED -> "LOCKED"
                        FieldStatus.PROVISIONAL -> "PROV"
                        FieldStatus.SCANNING -> "SCAN"
                        FieldStatus.CONFLICT -> "CONFLICT"
                    }
                    val statusColor = when (field.status) {
                        FieldStatus.LOCKED, FieldStatus.USER_LOCKED -> GREEN
                        FieldStatus.PROVISIONAL -> YELLOW
                        FieldStatus.CONFLICT -> RED
                        FieldStatus.SCANNING -> Color.White
                    }
                    val prob = field.topCandidate?.posteriorProbability?.fmt(2) ?: "—"
                    FieldRow(field.fieldName, statusLabel, prob, statusColor)
                }
            }
        }
    }
}

@Composable
private fun MonoText(
    text: String,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        fontFamily = MONO,
        fontSize = LABEL_SIZE,
        color = color,
        fontWeight = fontWeight,
    )
}

@Composable
private fun PerfRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label1: $value1",
            fontFamily = MONO,
            fontSize = LABEL_SIZE,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$label2: $value2",
            fontFamily = MONO,
            fontSize = LABEL_SIZE,
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FieldRow(name: String, status: String, probability: String, statusColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            fontFamily = MONO,
            fontSize = LABEL_SIZE,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = status,
            fontFamily = MONO,
            fontSize = LABEL_SIZE,
            color = statusColor,
        )
        Text(
            text = " $probability",
            fontFamily = MONO,
            fontSize = LABEL_SIZE,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.3f),
    )
}

private fun Float.fmt(decimals: Int): String = "%.${decimals}f".format(this)
