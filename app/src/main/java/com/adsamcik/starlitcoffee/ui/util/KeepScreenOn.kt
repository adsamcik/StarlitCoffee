package com.adsamcik.starlitcoffee.ui.util

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps the device screen awake while the surrounding composable is in the composition.
 *
 * Used on screens where the user is actively making coffee with both hands busy
 * (grinding, weighing, pouring) and cannot tap the screen to keep it alive.
 * The flag is cleared on dispose so other screens fall back to the system timeout.
 */
@Composable
fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
