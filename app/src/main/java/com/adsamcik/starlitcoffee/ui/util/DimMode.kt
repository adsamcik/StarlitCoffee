package com.adsamcik.starlitcoffee.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Default inactivity timeout before the dim overlay engages.
 */
private const val DEFAULT_DIM_TIMEOUT_MS: Long = 25_000L

/**
 * Role of a tagged slot. Drives layout order on the dim overlay:
 * [Secondary] sits at the top as quiet context, [Hero] + [Primary] occupy
 * the center as the focal point, and [Action] anchors the bottom for
 * primary controls that must stay reachable in dim mode.
 */
enum class DimRole {
    Hero,
    Primary,
    Secondary,
    Action,
}

/**
 * A single entry in the dim overlay's slot table.
 *
 * @param role layout group on the overlay
 * @param insertOrder monotonically increasing source order used to preserve
 *   the screen's reading order within a role group
 * @param content the composable to render. Treat this as render-only — see
 *   the [DimImportant] contract for state-bound side-effects.
 */
@Stable
data class DimSlot(
    val role: DimRole,
    val insertOrder: Long,
    val content: @Composable () -> Unit,
)

/**
 * Internal registry that collects [DimImportant] slots from the current
 * screen and exposes them to [DimModeScaffold]. Recreated per scaffold
 * instance via [remember].
 */
@Stable
class DimModeRegistry internal constructor() {
    private var nextOrder: Long = 0L
    private val _slots = mutableStateMapOf<Any, DimSlot>()
    internal val slots: Map<Any, DimSlot> get() = _slots

    internal fun register(id: Any, role: DimRole, content: @Composable () -> Unit) {
        val existing = _slots[id]
        val order = existing?.insertOrder ?: nextOrder++
        _slots[id] = DimSlot(role = role, insertOrder = order, content = content)
    }

    internal fun unregister(id: Any) {
        _slots.remove(id)
    }
}

/**
 * Tracks user inactivity to drive an automatic, opt-in "dim" mode that
 * replaces the screen with a calm, colorless overlay showing only the
 * fields each screen has tagged with [DimImportant]. Wake triggers
 * (touch and explicit [wake] calls) are routed through this controller.
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

/** True when the dim overlay is currently engaged. Provided by [DimModeScaffold]. */
val LocalDimModeActive = compositionLocalOf { false }

internal val LocalDimModeRegistry = compositionLocalOf<DimModeRegistry?> { null }

/**
 * Tags a piece of UI as "important enough to keep visible in dim mode".
 *
 * Screens wrap the few fields that genuinely matter at a glance — the hero
 * timer, the current step's pour instruction, the play/pause button — with
 * [DimImportant]. Everything else stays in the normal layout and is
 * automatically hidden when [DimModeScaffold] engages.
 *
 * ### Contract for [content]
 *
 * The wrapped composable is invoked exactly once per recomposition — either
 * inline (when dim mode is inactive) or on the overlay (when active), never
 * both. The call site changes across the inactive ↔ active transition, so
 * any `remember`-based local state inside [content] will reset across that
 * boundary. Keep [content] render-only: stateless [androidx.compose.material3.Text],
 * [androidx.compose.material3.Icon], buttons and similar leaf composables.
 * Stateful work (timers, animations, side-effects) belongs in the screen's
 * outer composition.
 *
 * If invoked outside a [DimModeScaffold] (e.g. previews), [DimImportant]
 * degrades to a no-op wrapper that just renders [content].
 *
 * @param role layout group on the overlay; defaults to [DimRole.Secondary]
 */
@Composable
fun DimImportant(
    role: DimRole = DimRole.Secondary,
    content: @Composable () -> Unit,
) {
    val registry = LocalDimModeRegistry.current
    if (registry == null) {
        content()
        return
    }
    val active = LocalDimModeActive.current
    // Per-instance stable id so re-registration across recomposition updates
    // the same slot rather than appending duplicates.
    val slotId: Any = remember { Any() }
    val currentContent by rememberUpdatedState(content)

    DisposableEffect(slotId, role) {
        registry.register(slotId, role) { currentContent() }
        onDispose { registry.unregister(slotId) }
    }

    if (!active) {
        currentContent()
    }
}

/**
 * Wraps a screen with the dim-mode framework.
 *
 *  - **Touch tracking** — a [PointerEventPass.Initial]-pass pointer input
 *    on the outer Box records every pointer-down without consuming it, so
 *    controls underneath stay interactive and a single tap both wakes the
 *    screen and fires its underlying click in the same gesture.
 *  - **Inactivity watcher** — after the controller's timeout of no touches
 *    and while the feature toggle is enabled, the dim overlay engages.
 *  - **Overlay** — when active, an opaque overlay covers the original screen
 *    and re-renders only the fields tagged with [DimImportant], laid out by
 *    role (secondary on top, hero/primary in the center, actions at the
 *    bottom). The underlying screen still composes (so registrations and
 *    other side-effects keep running) but is drawn at alpha 0 and shielded
 *    from input by the overlay.
 *
 * Screens still need to call [rememberDimModeController] and tag the few
 * fields that matter; everything else is invisible in dim mode by design.
 */
@Composable
fun DimModeScaffold(
    controller: DimModeController,
    modifier: Modifier = Modifier,
    overlayPadding: PaddingValues = PaddingValues(horizontal = 32.dp, vertical = 48.dp),
    content: @Composable () -> Unit,
) {
    val active by controller.active
    val lastTouchMs by controller.lastInteractionMs
    val registry = remember { DimModeRegistry() }

    LaunchedEffect(lastTouchMs, controller.featureEnabled) {
        if (!controller.featureEnabled) return@LaunchedEffect
        if (active) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - lastTouchMs
        val remaining = controller.effectiveTimeoutMs - elapsed
        if (remaining > 0L) delay(remaining)
        controller.activate()
    }

    Box(
        modifier = modifier.pointerInput(controller) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                        controller.recordTouch()
                        // Intentionally NOT consuming — the gesture should
                        // still reach its target so users don't have to
                        // double-tap to interact.
                    }
                }
            }
        },
    ) {
        CompositionLocalProvider(
            LocalDimModeRegistry provides registry,
            LocalDimModeActive provides active,
        ) {
            // Always compose the original screen so DimImportant
            // registrations (DisposableEffects) keep running. Just hide it
            // when dim mode is engaged.
            Box(modifier = if (active) Modifier.alpha(0f) else Modifier) {
                content()
            }

            if (active) {
                DimOverlay(
                    registry = registry,
                    padding = overlayPadding,
                )
            }
        }
    }
}

/**
 * Opaque overlay drawn over the dimmed screen. Lays out registered slots
 * by [DimRole] in a fixed three-band Column (secondary / hero+primary /
 * action). Empty regions absorb taps via a no-op [clickable] so they don't
 * leak through to the alpha-hidden screen behind.
 */
@Composable
private fun DimOverlay(
    registry: DimModeRegistry,
    padding: PaddingValues,
) {
    val slots = registry.slots.values.sortedBy { it.insertOrder }
    val secondary = slots.filter { it.role == DimRole.Secondary }
    val hero = slots.filter { it.role == DimRole.Hero }
    val primary = slots.filter { it.role == DimRole.Primary }
    val action = slots.filter { it.role == DimRole.Action }

    val emptyInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = emptyInteractionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                DimSection(slots = secondary, spacing = 8.dp)
                Spacer(modifier = Modifier.fillMaxWidth())
                DimSection(slots = hero + primary, spacing = 16.dp)
                Spacer(modifier = Modifier.fillMaxWidth())
                DimSection(slots = action, spacing = 12.dp)
            }
        }
    }
}

@Composable
private fun DimSection(
    slots: List<DimSlot>,
    spacing: androidx.compose.ui.unit.Dp,
) {
    if (slots.isEmpty()) {
        Spacer(modifier = Modifier.fillMaxWidth())
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) {
        slots.forEach { it.content() }
    }
}
