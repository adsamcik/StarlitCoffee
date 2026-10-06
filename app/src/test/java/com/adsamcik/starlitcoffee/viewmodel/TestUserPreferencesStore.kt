package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesStore
import com.adsamcik.starlitcoffee.data.repository.BrewingSetWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal open class TestUserPreferencesStore(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesStore, BrewingSetWriter by NoBrewingSetWriter {
    protected val state = MutableStateFlow(initial)
    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun completeOnboarding(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
        defaultFilterType: FilterType?,
        selectedGrinderId: String?,
    ) = Unit

    override suspend fun updateMethodSelection(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
    ) = Unit

    override suspend fun updateDefaultFilterType(filterType: FilterType?) = Unit
    override suspend fun updateSelectedGrinder(grinderId: String?) = Unit
    override suspend fun updateSkipMethodSelection(enabled: Boolean) = Unit
    override suspend fun updateShowCupPresets(enabled: Boolean) {
        state.value = state.value.copy(showCupPresets = enabled)
    }
    override suspend fun updateShowBrewingInstructions(enabled: Boolean) = Unit
    override suspend fun updateShowEnglishBrewingTerms(enabled: Boolean) = Unit
    override suspend fun updateBloomSpritesheetWeights(weights: Map<String, Int>) = Unit
    override suspend fun updateRatingReminderEnabled(enabled: Boolean) = Unit
    override suspend fun updateScanCorrectionLoggingEnabled(enabled: Boolean) = Unit
    override suspend fun updateBrewVibrationTheme(
        theme: com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme,
    ) = Unit
    override suspend fun updateDimModeEnabled(enabled: Boolean) = Unit
    override suspend fun updateDimModeTrueBlack(enabled: Boolean) = Unit
    override suspend fun updateDimModeReduceBrightness(enabled: Boolean) = Unit
    override suspend fun updateDimModeFullscreen(enabled: Boolean) = Unit
    override suspend fun updateDimModeForceDarkInLight(enabled: Boolean) = Unit
}
