package com.adsamcik.starlitcoffee.ui.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the device screen awake while the surrounding composable is in the composition.
 *
 * Used on screens where the user is actively making coffee with both hands busy
 * (grinding, weighing, pouring) and cannot tap the screen to keep it alive.
 * The flag is cleared on dispose so other screens fall back to the system timeout.
 */
@Composable
fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
