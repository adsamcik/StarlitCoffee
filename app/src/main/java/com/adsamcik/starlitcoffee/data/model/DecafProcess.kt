package com.adsamcik.starlitcoffee.data.model

/**
 * Coffee decaffeination process. Tracked per bag (optional) so the brew rule can
 * lightly bias the decaf grind offset based on how the beans were processed.
 *
 * Only "soft" biasing is supported — the underlying research (ACS C&EN 2024,
 * Swiss Water brew guides, Descafecol product notes, Sci. Rep. 2024 fines paper)
 * does not justify hard grinder-step rules per process. See
 * [com.adsamcik.starlitcoffee.viewmodel.BrewViewModel.decafCoarserStepsFor].
 */
enum class DecafProcess(val displayName: String, val shortLabel: String) {
    UNKNOWN("Unknown / not specified", "Unknown"),
    SWISS_WATER("Swiss Water (water process)", "Swiss Water"),
    MOUNTAIN_WATER("Mountain Water (water process)", "Mountain Water"),
    CO2_SUPERCRITICAL("Supercritical CO₂", "CO₂"),
    EA_SUGARCANE("Ethyl Acetate (sugarcane / natural EA)", "EA (sugarcane)"),
    MC_DIRECT("Methylene Chloride (direct solvent)", "MC"),
    EA_DIRECT("Ethyl Acetate (direct solvent)", "EA (solvent)");

    companion object {
        fun fromStorageKey(key: String?): DecafProcess? =
            key?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
