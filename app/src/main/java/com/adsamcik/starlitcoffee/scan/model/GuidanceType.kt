package com.adsamcik.starlitcoffee.scan.model

/**
 * Type of micro-mission guidance shown to the user during live scanning.
 */
enum class GuidanceType {
    /** A specific field is missing — e.g. "Tilt for roast date". */
    MISSING_FIELD,

    /** Frame quality issue — e.g. "Hold steadier", "Reduce glare". */
    QUALITY_ISSUE,

    /** Suggest flipping the bag — e.g. "Show the back for more details". */
    FLIP_SUGGESTION,

    /** All required fields resolved — e.g. "Bag looks complete!". */
    SCAN_COMPLETE,
}
