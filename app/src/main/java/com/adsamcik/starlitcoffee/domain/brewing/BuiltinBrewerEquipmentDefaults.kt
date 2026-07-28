package com.adsamcik.starlitcoffee.domain.brewing

import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory

/**
 * Conservative, explicit equipment snapshots for the built-in P1 start flow.
 *
 * The factory selects only a catalogued primary filter when the profile names
 * compatible filters. It never guesses a capacity, an accessory, a basket, or
 * a heat source. Cezve is explicitly unfiltered rather than ambiguously empty.
 */
class BuiltinBrewerEquipmentDefaults(
    private val catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
) {
    fun create(brewerProfileId: BrewerProfileId): EquipmentConfiguration? {
        if (brewerProfileId !in BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds) return null
        val profile = catalog.findBrewerProfile(brewerProfileId) ?: return null
        val filterSelection = when {
            profile.compatibleFilterIds.isNotEmpty() -> FilterSelection.Stack(
                entries = listOf(
                    FilterStackEntry(
                        filterProfileId = profile.compatibleFilterIds.minBy(FilterProfileId::value),
                        position = 0,
                        role = FilterStackRole.PRIMARY,
                    ),
                ),
            )

            profile.allowsIntentionallyUnfiltered -> FilterSelection.IntentionallyUnfiltered
            else -> FilterSelection.Unspecified
        }
        return EquipmentConfiguration(
            brewerProfileId = profile.id,
            filterSelection = filterSelection,
        )
    }
}
