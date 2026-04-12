package com.adsamcik.starlitcoffee.scan.observability

import kotlinx.serialization.Serializable

@Serializable
data class ScanSessionSummary(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val outcome: String,
    val framesProcessed: Int,
    val framesRejected: Int,
    val goldenFrameCount: Int,
    val llmFired: Boolean,
    val llmCallCount: Int,
    val llmLatencyMs: Long,
    val llmTokensUsed: Int,
    val fieldsResolved: Int,
    val fieldsTotal: Int,
    val bestGoldenFrameScore: Float,
    val failureReason: String?,
    val deviceModel: String,
    val appVersion: String,
)
