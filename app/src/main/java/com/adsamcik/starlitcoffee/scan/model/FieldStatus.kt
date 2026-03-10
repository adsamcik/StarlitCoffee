package com.adsamcik.starlitcoffee.scan.model

/**
 * Lifecycle status of a single field within the accumulator.
 */
enum class FieldStatus {
    /** Actively accumulating evidence — no clear winner yet. */
    SCANNING,

    /** Top candidate exceeded resolve threshold but hasn't held for enough cycles to lock. */
    PROVISIONAL,

    /** Top candidate held above threshold for [LOCK_CYCLES] consecutive consensus rounds. */
    LOCKED,

    /** Two or more candidates both have strong support — user resolution needed. */
    CONFLICT,

    /** User explicitly chose a value (tap or force-reset). Immune to further OCR updates. */
    USER_LOCKED,
}
