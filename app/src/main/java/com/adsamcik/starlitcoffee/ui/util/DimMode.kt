package com.adsamcik.starlitcoffee.ui.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * Default inactivity timeout before the dim presentation engages.
 */
private const val DEFAULT_DIM_TIMEOUT_MS: Long = 25_000L

/**
 * Backlight target when the optional "reduce screen brightness" sub-toggle
 * is on. ~20 % matches typical AOD-style dim levels — clearly dimmer than the
 * user's regular brightness but still readable on a bright kitchen counter.
 */
private const val DIM_BRIGHTNESS_LEVEL: Float = 0.2f

/**
 * Duration of the color animation between vivid and dimmed theme. Long enough
 * to read as a deliberate fade (so it doesn't feel like a glitch), short
 * enough that wake feels immediate.
 */
private const val DIM_TRANSITION_MS: Int = 240

/**
 * Tracks user inactivity and exposes the current dim state. The presentation
 * itself (theme remapping, optional backlight) is owned by [DimModeScaffold].
 *
 * Wake triggers ([recordTouch] and explicit [wake] calls) clear the dim
 * state immediately so the very next frame renders in vivid colors.
 */
class DimModeController internal constructor(
    private val timeoutMs: Long,
    featureEnabled: Boolean,
) {
    private val _active = mutableStateOf(false)
    val active: State<Boolean> get() = _active

    private val _lastInteractionMs = mutableLongStateOf(System.currentTimeMillis())
    internal val lastInteractionMs: State<Long> get() = _lastInteractionMs

    internal var featureEnabled: Boolean = featureEnabled
        set(value) {
            field = value
            if (!value) _active.value = false
        }

    /** Record user activity; wakes immediately if already dimmed. */
    fun recordTouch() {
        _lastInteractionMs.longValue = System.currentTimeMillis()
        if (_active.value) _active.value = false
    }

    /** Programmatic wake (also re-arms the idle timer). */
    fun wake() {
        recordTouch()
    }

    internal fun activate() {
        if (featureEnabled) _active.value = true
    }

    internal val effectiveTimeoutMs: Long get() = timeoutMs
}

@Composable
fun rememberDimModeController(
    featureEnabled: Boolean,
    timeoutMs: Long = DEFAULT_DIM_TIMEOUT_MS,
): DimModeController {
    val controller = remember(timeoutMs) {
        DimModeController(timeoutMs = timeoutMs, featureEnabled = featureEnabled)
    }
    LaunchedEffect(featureEnabled) {
        controller.featureEnabled = featureEnabled
    }
    return controller
}

/**
 * True when the dim presentation is currently engaged. Provided by
 * [DimModeScaffold] so custom components can opt into bespoke dim behavior
 * (e.g. swapping a vibrant background for a muted outline) if the global
 * theme override isn't enough.
 */
val LocalDimModeActive = compositionLocalOf { false }

/**
 * Wraps a screen with the dim-mode framework.
 *
 * After the controller's inactivity timeout, the scaffold re-provides
 * [MaterialTheme] with a derived [androidx.compose.material3.ColorScheme]:
 *
 *  - All colored container backgrounds (`primaryContainer`, `secondaryContainer`,
 *    `tertiaryContainer`, `surfaceVariant`, `surfaceContainer*`) collapse to
 *    [Color.Transparent], stripping the "colored cards" feel.
 *  - Non-error accents (`primary`, `secondary`, `tertiary`) collapse to
 *    `onSurfaceVariant` so tints/icons/progress bars read as neutral gray.
 *  - The `error` color family is preserved untouched so warning cards and
 *    state flashes stay loud enough to notice.
 *  - When [trueBlackBackground] is true **and** the active theme is dark, the
 *    `surface` and `background` colors also animate to pure [Color.Black] —
 *    leaving every transparent card sitting directly on an OLED-friendly
 *    black canvas. In a light theme the flag is a no-op.
 *  - `onSurface`, `onSurfaceVariant`, `outline` and `outlineVariant` are kept
 *    so the neutral text stays readable against whichever surface ends up
 *    underneath.
 *
 * The composable tree is **never re-laid out, never disposed, and never
 * replaced**. Only the values that the theme `CompositionLocal`s emit change.
 * That gives three properties critical to the dim mode feeling right:
 *
 *  - Zero layout shift across the dim/wake transition.
 *  - Gestures complete normally — a single tap wakes the screen **and** fires
 *    the underlying click in the same gesture, because the receiving
 *    composable node is identical before and after.
 *  - Animations (the bloom flower spritesheet, progress bars, hero color
 *    tweens) keep running uninterrupted.
 *
 * @param controller wake / inactivity book-keeping.
 * @param trueBlackBackground when true and the active theme is dark, also
 *   animates `surface` + `background` to [Color.Black] while dim is active.
 * @param reduceBrightness when true, additionally lowers the window backlight
 *   via [WindowManager.LayoutParams.screenBrightness] while dim is active.
 *   Restored on dispose or when dim disengages.
 * @param hideSystemBars when true, additionally hides the status bar and
 *   navigation bar while dim is active. A swipe from any screen edge will
 *   reveal them transiently without breaking the dim state. Restored on
 *   dispose or when dim disengages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DimModeScaffold(
    controller: DimModeController,
    modifier: Modifier = Modifier,
    trueBlackBackground: Boolean = false,
    reduceBrightness: Boolean = false,
    hideSystemBars: Boolean = false,
    content: @Composable () -> Unit,
) {
    val active by controller.active
    val lastTouchMs by controller.lastInteractionMs

    LaunchedEffect(lastTouchMs, controller.featureEnabled) {
        if (!controller.featureEnabled) return@LaunchedEffect
        if (active) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - lastTouchMs
        val remaining = controller.effectiveTimeoutMs - elapsed
        if (remaining > 0L) delay(remaining)
        controller.activate()
    }

    if (reduceBrightness) {
        WindowBrightnessEffect(dimActive = active)
    }

    if (hideSystemBars) {
        ImmersiveModeEffect(dimActive = active)
    }

    Box(
        modifier = modifier.pointerInput(controller) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                        controller.recordTouch()
                        // Intentionally NOT consuming — the gesture should
                        // still reach its target so a single tap both wakes
                        // the screen and fires the underlying click.
                    }
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalDimModeActive provides active) {
            DimAwareTheme(active = active, trueBlackBackground = trueBlackBackground) {
                // Prevent any layout shift during Android's system bar
                // hide/show animation. The Android system animates the
                // bars smoothly (~300 ms); during that animation
                // `WindowInsets.systemBars` interpolates from real → 0,
                // and any descendant that pads based on live insets
                // shrinks in lockstep — visibly sliding the content up.
                //
                // We counteract that by padding the *difference* between
                // what the bars would take if visible
                // (`statusBarsIgnoringVisibility ∪ navigationBarsIgnoringVisibility`,
                // a constant) and what they currently take
                // (`WindowInsets.systemBars`, animating). The two values
                // are inversely correlated, so descendant_padding +
                // filler_padding = constant for the entire animation.
                //
                // This works the same whether the screen content reads
                // live insets (e.g. via a Scaffold) or not, and reduces
                // to zero outer padding when the bars are fully visible
                // — so vivid mode is byte-identical to the no-fullscreen
                // baseline.
                val fillerInsets = WindowInsets.statusBarsIgnoringVisibility
                    .union(WindowInsets.navigationBarsIgnoringVisibility)
                    .exclude(WindowInsets.systemBars)
                Box(
                    modifier = if (hideSystemBars) {
                        Modifier.windowInsetsPadding(fillerInsets)
                    } else {
                        Modifier
                    },
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Re-provides [MaterialTheme] with a tween-animated derivation of the active
 * scheme. When [active] is false the inner content is provided the current
 * scheme verbatim, so the transformation is allocation-free in the common
 * (un-dimmed) case.
 */
@Composable
private fun DimAwareTheme(
    active: Boolean,
    trueBlackBackground: Boolean,
    content: @Composable () -> Unit,
) {
    val source = MaterialTheme.colorScheme
    val gray = source.onSurfaceVariant
    val transparent = Color.Transparent
    val animation = tween<Color>(durationMillis = DIM_TRANSITION_MS)

    // True-black only kicks in on dark themes — going to Color.Black in a
    // light theme would just invert the canvas and look broken. We detect
    // "dark" by the luminance of the active surface colour rather than
    // isSystemInDarkTheme(), so explicit theme overrides are honoured too.
    val isDarkTheme = source.surface.luminance() < 0.5f
    val applyTrueBlack = active && trueBlackBackground && isDarkTheme

    // Crossfade each colour individually so the wake transition is a smooth
    // colour return rather than a flash. animateColorAsState handles the
    // Color.Transparent ↔ opaque interpolation correctly for us.
    val primary by animateColorAsState(
        targetValue = if (active) gray else source.primary,
        animationSpec = animation,
        label = "dim.primary",
    )
    val primaryContainer by animateColorAsState(
        targetValue = if (active) transparent else source.primaryContainer,
        animationSpec = animation,
        label = "dim.primaryContainer",
    )
    val onPrimaryContainer by animateColorAsState(
        targetValue = if (active) gray else source.onPrimaryContainer,
        animationSpec = animation,
        label = "dim.onPrimaryContainer",
    )
    val secondary by animateColorAsState(
        targetValue = if (active) gray else source.secondary,
        animationSpec = animation,
        label = "dim.secondary",
    )
    val secondaryContainer by animateColorAsState(
        targetValue = if (active) transparent else source.secondaryContainer,
        animationSpec = animation,
        label = "dim.secondaryContainer",
    )
    val onSecondaryContainer by animateColorAsState(
        targetValue = if (active) gray else source.onSecondaryContainer,
        animationSpec = animation,
        label = "dim.onSecondaryContainer",
    )
    val tertiary by animateColorAsState(
        targetValue = if (active) gray else source.tertiary,
        animationSpec = animation,
        label = "dim.tertiary",
    )
    val tertiaryContainer by animateColorAsState(
        targetValue = if (active) transparent else source.tertiaryContainer,
        animationSpec = animation,
        label = "dim.tertiaryContainer",
    )
    val onTertiaryContainer by animateColorAsState(
        targetValue = if (active) gray else source.onTertiaryContainer,
        animationSpec = animation,
        label = "dim.onTertiaryContainer",
    )
    val surfaceVariant by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceVariant,
        animationSpec = animation,
        label = "dim.surfaceVariant",
    )
    val surfaceContainerLowest by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceContainerLowest,
        animationSpec = animation,
        label = "dim.surfaceContainerLowest",
    )
    val surfaceContainerLow by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceContainerLow,
        animationSpec = animation,
        label = "dim.surfaceContainerLow",
    )
    val surfaceContainer by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceContainer,
        animationSpec = animation,
        label = "dim.surfaceContainer",
    )
    val surfaceContainerHigh by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceContainerHigh,
        animationSpec = animation,
        label = "dim.surfaceContainerHigh",
    )
    val surfaceContainerHighest by animateColorAsState(
        targetValue = if (active) transparent else source.surfaceContainerHighest,
        animationSpec = animation,
        label = "dim.surfaceContainerHighest",
    )
    val surface by animateColorAsState(
        targetValue = if (applyTrueBlack) Color.Black else source.surface,
        animationSpec = animation,
        label = "dim.surface",
    )
    val background by animateColorAsState(
        targetValue = if (applyTrueBlack) Color.Black else source.background,
        animationSpec = animation,
        label = "dim.background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) gray else LocalContentColor.current,
        animationSpec = animation,
        label = "dim.contentColor",
    )

    val scheme = source.copy(
        primary = primary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surface = surface,
        background = background,
        surfaceVariant = surfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        // error / errorContainer / onError / onErrorContainer kept verbatim —
        // warnings and crucial state flashes must stay loud.
        // onSurface / onSurfaceVariant / outline / outlineVariant kept so
        // neutral text stays readable against whichever surface ends up
        // underneath (black in true-black mode, otherwise the theme default).
    )

    MaterialTheme(
        colorScheme = scheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * Lowers the activity window's backlight while dim mode is active and restores
 * it whenever dim disengages or the scaffold leaves composition.
 */
@Composable
private fun WindowBrightnessEffect(dimActive: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val lp = window.attributes ?: return@SideEffect
        val target = if (dimActive) {
            DIM_BRIGHTNESS_LEVEL
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (lp.screenBrightness != target) {
            lp.screenBrightness = target
            window.attributes = lp
        }
    }
    DisposableEffect(view) {
        onDispose {
            val window = (view.context as? Activity)?.window ?: return@onDispose
            val lp = window.attributes ?: return@onDispose
            if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }
}

/**
 * Hides the status and navigation bars while dim mode is active so the
 * "at rest" screen carries no system chrome — no clock, no notifications,
 * no nav handle. Uses [WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]
 * so the user can still pull the bars up by swiping from any edge (the same
 * gesture also wakes the screen via the scaffold's touch handler, which is
 * intentional — peeking notifications and waking the screen are the same
 * intent).
 *
 * Restores the bars on dispose or whenever dim disengages.
 */
@Composable
private fun ImmersiveModeEffect(dimActive: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (dimActive) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view) {
        onDispose {
            val window = (view.context as? Activity)?.window ?: return@onDispose
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
