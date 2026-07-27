package com.adsamcik.starlitcoffee.data.brewing

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.FilterStackEntry

data class LegacyEquipmentMapping(
    val filterSelection: FilterSelection,
    val rawLegacyFilterId: String? = null,
    val wasInvalidForMethod: Boolean = false,
)

data class LegacyBrewingReference(
    val rawMethodId: String,
    val brewerProfile: CatalogResolution<BrewerProfileId>,
    val equipment: LegacyEquipmentMapping,
)

/**
 * Centralizes the old enum/string mappings. The legacy values remain readable
 * while all new writes use stable profile and equipment identifiers.
 */
object LegacyBrewingAdapter {
    fun fromLegacy(
        method: BrewMethod,
        filterType: FilterType?,
    ): LegacyBrewingReference = fromLegacy(method.name, filterType?.name)

    fun fromLegacy(
        rawMethodId: String,
        rawFilterId: String?,
    ): LegacyBrewingReference {
        val profile = BuiltinBrewingCatalog.instance.resolveLegacyMethod(rawMethodId)
        return LegacyBrewingReference(
            rawMethodId = rawMethodId,
            brewerProfile = profile,
            equipment = mapLegacyFilter(rawMethodId, rawFilterId),
        )
    }

    private fun mapLegacyFilter(
        rawMethodId: String,
        rawFilterId: String?,
    ): LegacyEquipmentMapping {
        if (rawFilterId == null) return LegacyEquipmentMapping(FilterSelection.Unspecified)
        if (rawMethodId != BrewMethod.PULSAR.name) {
            return LegacyEquipmentMapping(
                filterSelection = FilterSelection.Unspecified,
                rawLegacyFilterId = rawFilterId,
                wasInvalidForMethod = true,
            )
        }
        val filterProfileId = when (rawFilterId) {
            FilterType.PAPER.name -> FilterProfileId("pulsar_paper")
            FilterType.METAL_19K.name -> FilterProfileId("pulsar_19k_metal")
            FilterType.METAL_40K.name -> FilterProfileId("pulsar_40k_metal")
            else -> null
        }
        return if (filterProfileId == null) {
            LegacyEquipmentMapping(
                filterSelection = FilterSelection.Unspecified,
                rawLegacyFilterId = rawFilterId,
            )
        } else {
            LegacyEquipmentMapping(
                filterSelection = FilterSelection.Stack(
                    listOf(FilterStackEntry(filterProfileId, position = 0)),
                ),
            )
        }
    }
}
