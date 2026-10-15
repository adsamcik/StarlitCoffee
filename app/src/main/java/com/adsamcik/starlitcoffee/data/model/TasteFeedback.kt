package com.adsamcik.starlitcoffee.data.model

enum class TasteFeedback {
    TOO_SOUR,
    BALANCED,
    TOO_BITTER,
    ASTRINGENT,
    CLOGGED,
    // Stored by name. Older sour/bitter records cannot safely be reclassified as strength feedback.
    TOO_WEAK,
    TOO_STRONG,
}
