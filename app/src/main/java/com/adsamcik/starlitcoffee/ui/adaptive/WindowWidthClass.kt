package com.adsamcik.starlitcoffee.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Coarse window-width buckets used to drive adaptive layouts.
 *
 * Breakpoints follow Android's official window size classes
 * (Compact `< 600dp`, Medium `600..839dp`, Expanded `>= 840dp`). On apps
 * targeting Android 17 (SDK 37) the portrait orientation lock is ignored on
 * `sw >= 600dp`, so the app is shown in landscape / resizable windows on
 * tablets, foldables, and desktop mode; these buckets let key screens switch
 * to side-by-side and list-detail arrangements instead of a single column.
 *
 * The class is derived from `screenWidthDp`, which recomposes on rotation and
 * window resize, so it tracks free-form multi-window resizing live.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    /** True for Medium / Expanded — i.e. wide enough for a side rail / two panes. */
    val isWide: Boolean get() = this != COMPACT

    /** True only for Expanded — wide enough for a persistent list-detail split. */
    val isExpanded: Boolean get() = this == EXPANDED
}

/** Maps a window width in dp to its [WindowWidthClass] bucket. */
fun windowWidthClassFor(widthDp: Int): WindowWidthClass = when {
    widthDp < MEDIUM_WIDTH_DP -> WindowWidthClass.COMPACT
    widthDp < EXPANDED_WIDTH_DP -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840

/**
 * The current window width class, defaulting to [WindowWidthClass.COMPACT] until
 * provided by [ProvideWindowWidthClass] near the root of the UI tree.
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.COMPACT }

/** Computes the live [WindowWidthClass] from the current configuration. */
@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { windowWidthClassFor(widthDp) }
}

/**
 * Provides [LocalWindowWidthClass] to [content] from the live configuration.
 * Place once near the root (above the navigation host) so every screen can read
 * the current width class without recomputing it.
 */
@Composable
fun ProvideWindowWidthClass(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWindowWidthClass provides rememberWindowWidthClass(),
        content = content,
    )
}
