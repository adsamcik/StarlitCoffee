package com.adsamcik.starlitcoffee.ui.component

/**
 * Simple filter helper for decaf status. Used by inventory, brew log, and saved recipe
 * screens to show a consistent segmented filter chip row.
 *
 * Kept as a tiny enum + predicate rather than a shared composable: each screen has subtly
 * different visual context, and extracting a composable too early creates premature
 * abstraction. The [matches] predicate and [label] keep the logic DRY.
 */
enum class DecafFilter(val label: String) {
    ALL("All"),
    REGULAR("Regular"),
    DECAF("Decaf");

    fun matches(isDecaf: Boolean): Boolean = when (this) {
        ALL -> true
        REGULAR -> !isDecaf
        DECAF -> isDecaf
    }
}
