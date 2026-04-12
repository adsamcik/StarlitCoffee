package com.adsamcik.starlitcoffee.scan.observability

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects per-component timing markers during a scan session.
 * Thread-safe — called from camera thread, consensus thread, and main thread.
 *
 * Usage:
 *   tracer.mark("ocr_latency_ms", 47.3f)
 *   tracer.startTimer("llm_inference")
 *   // ... do work ...
 *   tracer.stopTimer("llm_inference")
 */
class ScanPerfTracer(private val bufferSize: Int = 100) {

    private val buffers = ConcurrentHashMap<String, CircularFloatBuffer>()
    private val activeTimers = ConcurrentHashMap<String, Long>()

    /** Record a single measurement for [name]. */
    fun mark(name: String, value: Float) {
        buffers.getOrPut(name) { CircularFloatBuffer(bufferSize) }.add(value)
    }

    /** Start a named timer (monotonic nanos). */
    fun startTimer(name: String) {
        activeTimers[name] = SystemClock.elapsedRealtimeNanos()
    }

    /** Stop a named timer, record duration in ms, return it. Null if timer was never started. */
    fun stopTimer(name: String): Float? {
        val startNanos = activeTimers.remove(name) ?: return null
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000f
        mark(name, durationMs)
        return durationMs
    }

    /** Return stats for every recorded metric. */
    fun getStats(): Map<String, PerfStats> {
        val result = mutableMapOf<String, PerfStats>()
        for ((name, buf) in buffers) {
            buf.snapshot()?.let { result[name] = it.toStats(name) }
        }
        return result
    }

    /** Return the most recent value for each metric. */
    fun getLatest(): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        for ((name, buf) in buffers) {
            buf.latest()?.let { result[name] = it }
        }
        return result
    }

    /** Serialize all stats as JSON (no external library). */
    fun toJson(): String = buildString {
        append('{')
        val entries = getStats().entries.toList()
        entries.forEachIndexed { i, (name, stats) ->
            if (i > 0) append(',')
            append("\"${escapeJson(name)}\":")
            append(stats.toJson())
        }
        append('}')
    }

    /** Clear all data and active timers. */
    fun reset() {
        buffers.clear()
        activeTimers.clear()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}

/**
 * Summary stats for a single metric.
 */
data class PerfStats(
    val name: String,
    val count: Int,
    val min: Float,
    val max: Float,
    val avg: Float,
    val latest: Float,
    val p95: Float?,
) {
    fun toJson(): String = buildString {
        append("{\"count\":$count")
        append(",\"min\":$min")
        append(",\"max\":$max")
        append(",\"avg\":$avg")
        append(",\"latest\":$latest")
        if (p95 != null) append(",\"p95\":$p95")
        append('}')
    }
}

/**
 * Serializable snapshot of [PerfStats] for telemetry payloads.
 */
data class PerfStatsSnapshot(
    val count: Int,
    val min: Float,
    val max: Float,
    val avg: Float,
    val latest: Float,
    val p95: Float?,
) {
    companion object {
        fun from(stats: PerfStats) = PerfStatsSnapshot(
            count = stats.count,
            min = stats.min,
            max = stats.max,
            avg = stats.avg,
            latest = stats.latest,
            p95 = stats.p95,
        )
    }
}

/**
 * Fixed-size circular buffer for float samples. Thread-safe via synchronized.
 */
internal class CircularFloatBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    private var count = 0

    @Synchronized
    fun add(value: Float) {
        data[head] = value
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun latest(): Float? {
        if (count == 0) return null
        return data[(head - 1 + capacity) % capacity]
    }

    @Synchronized
    fun snapshot(): FloatSnapshot? {
        if (count == 0) return null
        val copy = FloatArray(count)
        for (i in 0 until count) {
            copy[i] = data[((head - count + i) + capacity * 2) % capacity]
        }
        return FloatSnapshot(copy)
    }

    internal class FloatSnapshot(private val values: FloatArray) {
        fun toStats(name: String): PerfStats {
            val sorted = values.copyOf().also { it.sort() }
            val count = sorted.size
            val min = sorted.first()
            val max = sorted.last()
            val avg = sorted.sum() / count
            val latest = values.last()
            val p95 = if (count >= 20) {
                sorted[((count - 1) * 0.95f).toInt()]
            } else {
                null
            }
            return PerfStats(name, count, min, max, avg, latest, p95)
        }
    }
}
