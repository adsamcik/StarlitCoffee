package com.adsamcik.starlitcoffee.data.model

enum class GrindDescriptor(
    val displayName: String,
    val visualCue: String,
) {
    VERY_FINE("Very Fine", "Like powdered sugar"),
    FINE("Fine", "Like table salt"),
    MEDIUM_FINE("Medium Fine", "Like sand"),
    MEDIUM("Medium", "Like sea salt"),
    MEDIUM_COARSE("Medium Coarse", "Like rough sand"),
    COARSE("Coarse", "Like coarse sea salt"),
}
