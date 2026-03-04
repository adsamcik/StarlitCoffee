package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Phase-specific vibration patterns for the brew timer.
 * Each phase type has a distinct haptic signature so users can
 * identify transitions without looking at the screen.
 */
object VibrationHelper {

    enum class BrewHaptic {
        /** Bloom start — gentle double-pulse (heartbeat). */
        BLOOM,
        /** Pour start — quick burst. */
        POUR,
        /** Drain/refill start — long sustained pulse. */
        DRAIN,
        /** Drawdown start — soft descending double. */
        DRAWDOWN,
        /** Get-ready warning ~5 s before phase ends. */
        GET_READY,
        /** Brew complete — celebration triple-burst. */
        BREW_COMPLETE,
    }

    fun vibrate(context: Context, haptic: BrewHaptic) {
        val vibrator = vibrator(context) ?: return
        val effect = buildEffect(haptic) ?: return
        vibrator.vibrate(effect)
    }

    private fun vibrator(context: Context): Vibrator? {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return v?.takeIf { it.hasVibrator() }
    }

    private fun buildEffect(haptic: BrewHaptic): VibrationEffect? {
        val (timings, amplitudes) = when (haptic) {
            BrewHaptic.BLOOM -> longArrayOf(0, 80, 120, 80) to
                intArrayOf(0, 180, 0, 140)

            BrewHaptic.POUR -> longArrayOf(0, 50) to
                intArrayOf(0, 160)

            BrewHaptic.DRAIN -> longArrayOf(0, 300) to
                intArrayOf(0, 200)

            BrewHaptic.DRAWDOWN -> longArrayOf(0, 60, 80, 40) to
                intArrayOf(0, 140, 0, 80)

            BrewHaptic.GET_READY -> longArrayOf(0, 30) to
                intArrayOf(0, 100)

            BrewHaptic.BREW_COMPLETE -> longArrayOf(0, 60, 80, 80, 80, 120) to
                intArrayOf(0, 120, 0, 160, 0, 220)
        }
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }
}
