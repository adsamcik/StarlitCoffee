package com.adsamcik.starlitcoffee.ui.component

import kotlin.math.roundToInt

// Spend more of the countdown growing stems and closed buds. Opening then
// develops through the final quarter, with the finished pose at completion.
private val BloomGrowthLandmarks = listOf(
    0f to 0f,
    0.10f to 0.08f,
    0.25f to 0.16f,
    0.50f to 0.33f,
    0.75f to 0.58f,
    0.90f to 0.83f,
    1f to 1f,
)

internal fun resolveBloomProgress(countdownSeconds: Int?, durationSeconds: Int): Float {
    if (countdownSeconds == null || durationSeconds <= 0) return 0f
    val remainingSeconds = countdownSeconds.coerceIn(0, durationSeconds)
    return (durationSeconds - remainingSeconds) / durationSeconds.toFloat()
}

/** Map smoothed elapsed time to growth, without adding lag to the countdown. */
internal fun resolveBloomArtProgress(elapsedProgress: Float): Float {
    val elapsed = elapsedProgress.coerceIn(0f, 1f)
    for (index in 1 until BloomGrowthLandmarks.size) {
        val (endTime, endArt) = BloomGrowthLandmarks[index]
        if (elapsed <= endTime) {
            val (startTime, startArt) = BloomGrowthLandmarks[index - 1]
            val fraction = (elapsed - startTime) / (endTime - startTime)
            return startArt + fraction * (endArt - startArt)
        }
    }
    return 1f
}

internal fun resolveBloomFrameIndex(progress: Float, frameCount: Int): Int {
    require(frameCount > 0)
    if (frameCount == 1) return 0
    if (progress >= 1f) return frameCount - 1
    // Rounding must not expose the completed flower while time remains.
    return (resolveBloomArtProgress(progress) * (frameCount - 1))
        .roundToInt()
        .coerceIn(0, frameCount - 2)
}
