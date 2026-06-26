package com.adsamcik.starlitcoffee.ui.util

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Reference counts active keep-awake requests per window.
 *
 * [FLAG_KEEP_SCREEN_ON][WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] is a
 * single, window-wide flag, but several brew-flow screens
 * (GrindPrep -> Bloom -> Brew) each request it. During a navigation transition
 * both the entering and the exiting screen are composed at the same time, so the
 * exiting screen's `onDispose` would otherwise clear the flag that the entering
 * screen just set — leaving the screen the user lands on free to time out.
 *
 * Counting the requests means the flag is added on the first request and only
 * cleared once the last requester leaves the composition, so it survives the
 * brief overlap during transitions.
 *
 * Compose effects (`DisposableEffect` activate/dispose) run on the main thread,
 * so the map needs no extra synchronization.
 */
private val keepScreenOnCounts = HashMap<Window, Int>()

private fun Window.acquireKeepScreenOn() {
    val count = keepScreenOnCounts[this] ?: 0
    if (count == 0) {
        addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    keepScreenOnCounts[this] = count + 1
}

private fun Window.releaseKeepScreenOn() {
    val count = keepScreenOnCounts[this] ?: 0
    if (count <= 1) {
        clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        keepScreenOnCounts.remove(this)
    } else {
        keepScreenOnCounts[this] = count - 1
    }
}

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Safety factor applied to a screen's estimated active duration before the
 * keep-awake hold is voluntarily released. Generous on purpose: a normal
 * (even slightly slow) brew should never trip the timeout, but a phone left
 * face-up on the counter after the user wandered off still gets to sleep.
 */
private const val KEEP_SCREEN_ON_SAFETY_FACTOR = 2L

/**
 * Floor for derived timeouts. Guards against a missing or not-yet-computed
 * estimate (e.g. `timeTargetHighS == 0` before the first recalculation)
 * collapsing the keep-awake window to an instant — or near-instant — timeout.
 */
private const val MIN_KEEP_SCREEN_ON_SECONDS = 60L

/**
 * Derives a generous keep-awake timeout from a screen's estimated active
 * duration: [estimatedSeconds] x [KEEP_SCREEN_ON_SAFETY_FACTOR], floored at
 * [MIN_KEEP_SCREEN_ON_SECONDS]. Pass the result to [KeepScreenOn] so the
 * screen stops being held awake once the brew has run well past its estimate.
 */
fun keepScreenOnTimeoutMillis(estimatedSeconds: Int): Long {
    val safeSeconds = estimatedSeconds.toLong().coerceAtLeast(MIN_KEEP_SCREEN_ON_SECONDS)
    return safeSeconds * KEEP_SCREEN_ON_SAFETY_FACTOR * MILLIS_PER_SECOND
}

/**
 * A single keep-screen-on reference that can be released at most once — whether
 * the trigger is the safety timeout or the composable leaving the composition.
 * The idempotent release means the two paths never double-decrement a count
 * that has since been handed to another screen during a navigation overlap.
 */
private class KeepScreenOnHold(private val window: Window?) {
    private var active = false

    fun acquire() {
        if (!active) {
            active = true
            window?.acquireKeepScreenOn()
        }
    }

    fun release() {
        if (active) {
            active = false
            window?.releaseKeepScreenOn()
        }
    }
}

/**
 * Keeps the device screen awake while the surrounding composable is in the
 * composition.
 *
 * Used on screens where the user is actively making coffee with both hands busy
 * (grinding, weighing, pouring) and cannot tap the screen to keep it alive.
 * The request is reference counted (see [keepScreenOnCounts]) so navigating
 * between brew-flow screens keeps the screen on continuously; the flag is only
 * cleared once the last such screen leaves the composition, so other screens
 * fall back to the system timeout.
 *
 * @param timeoutMillis upper bound, in milliseconds, on how long this screen
 * holds the display awake. Once it elapses the hold is released so the system
 * display timeout can reclaim the screen, preventing a forgotten brew from
 * pinning the display on indefinitely. Pass `null` to hold for as long as the
 * composable is shown (legacy behaviour). Use [keepScreenOnTimeoutMillis] to
 * derive a value from the screen's estimated duration.
 */
@Composable
fun KeepScreenOn(timeoutMillis: Long? = null) {
    val activity = LocalActivity.current
    val window = activity?.window
    val hold = remember(window) { KeepScreenOnHold(window) }

    DisposableEffect(hold) {
        hold.acquire()
        onDispose {
            hold.release()
        }
    }

    if (timeoutMillis != null) {
        LaunchedEffect(hold, timeoutMillis) {
            delay(timeoutMillis)
            hold.release()
        }
    }
}
