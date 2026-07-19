package com.adsamcik.starlitcoffee.ui.component

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R

/**
 * Simple filter helper for decaf status. Used by inventory, brew log, and saved recipe
 * screens to show a consistent segmented filter chip row.
 *
 * Kept as a tiny enum + predicate rather than a shared composable: each screen has subtly
 * different visual context, and extracting a composable too early creates premature abstraction.
 */
enum class DecafFilter {
    ALL,
    REGULAR,
    DECAF;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            ALL -> R.string.label_all
            REGULAR -> R.string.label_regular
            DECAF -> R.string.label_decaf
        }

    fun matches(isDecaf: Boolean): Boolean = when (this) {
        ALL -> true
        REGULAR -> !isDecaf
        DECAF -> isDecaf
    }
}

fun DecafFilter.normalizedForCounts(
    regularCount: Int,
    decafCount: Int,
): DecafFilter {
    if (regularCount + decafCount == 0) return this
    return when {
        this == DecafFilter.REGULAR && regularCount == 0 -> DecafFilter.ALL
        this == DecafFilter.DECAF && decafCount == 0 -> DecafFilter.ALL
        else -> this
    }
}
