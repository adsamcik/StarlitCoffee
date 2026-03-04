package com.adsamcik.starlitcoffee.data.model

enum class FilterType(
    val displayName: String,
    val description: String,
    val cupProfile: String,
) {
    PAPER(
        displayName = "Paper",
        description = "Cleanest cup, least body and oil",
        cupProfile = "Pure, bright, delicate",
    ),
    METAL_19K(
        displayName = "19K",
        description = "19,000 holes — more body, oils, richness",
        cupProfile = "Full-bodied, bold, chewy",
    ),
    METAL_40K(
        displayName = "40K",
        description = "40,000 holes — cleaner than 19K, more oils than paper",
        cupProfile = "Balanced clarity with body",
    ),
}
