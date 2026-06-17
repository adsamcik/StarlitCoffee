package com.adsamcik.starlitcoffee.ui.util

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

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

/**
 * Keeps the device screen awake while the surrounding composable is in the composition.
 *
 * Used on screens where the user is actively making coffee with both hands busy
 * (grinding, weighing, pouring) and cannot tap the screen to keep it alive.
 * The request is reference counted (see [keepScreenOnCounts]) so navigating
 * between brew-flow screens keeps the screen on continuously; the flag is only
 * cleared once the last such screen leaves the composition, so other screens
 * fall back to the system timeout.
 */
@Composable
fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        window?.acquireKeepScreenOn()
        onDispose {
            window?.releaseKeepScreenOn()
        }
    }
}
