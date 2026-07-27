package com.adsamcik.starlitcoffee.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Raw stable preference values are intentionally not parsed as enums. A newer
 * app's profile, guidance, or utility ID must survive a downgrade untouched.
 */
data class StableBrewingPreferences(
    val enabledBrewerProfileIds: Set<String>,
    val defaultBrewerProfileId: String?,
    val guidanceByMethodFamilyId: Map<String, String> = emptyMap(),
    val guidanceByBrewerProfileId: Map<String, String> = emptyMap(),
    val utilityModulesByMethodFamilyId: Map<String, Set<String>> = emptyMap(),
)

interface BrewingPreferenceStore {
    val brewingPreferences: Flow<StableBrewingPreferences>

    suspend fun updateBrewingPreferences(preferences: StableBrewingPreferences)
}
